/*
 *  Copyright (C) 2019 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.maker;

import java.io.IOException;

import java.util.Arrays;

/**
 * The wonderful StackMapTable attribute!
 *
 * @author Brian S O'Neill
 */
class StackMapTable extends Attribute {
    private final Frame mInitFrame;
    private Frame mFirstFrame, mLastFrame;
    private int mNumFrames;
    private BytesOut mFinished;

    /**
     * @param initCodes can be null
     */
    StackMapTable(ConstantPool cp, int[] initCodes) {
        super(cp, "StackMapTable");
        mInitFrame = new Frame(Integer.MIN_VALUE, initCodes, null);
    }

    /**
     * Add a frame entry to the table.
     *
     * @param address code address the new frame refers to; must not be lower any other frame
     * @param localCodes can be null
     * @param stackCodes can be null
     */
    void add(int address, int[] localCodes, int[] stackCodes) {
        Frame frame = new Frame(address, localCodes, stackCodes);
        Frame last = mLastFrame;
        if (last == null) {
            mFirstFrame = frame;
        } else {
            if (address <= last.mAddress) {
                if (address < last.mAddress) {
                    throw new IllegalStateException("Reverse address ordering");
                } else if (Frame.diff(last.mStackCodes, stackCodes) != 0) {
                    throw new IllegalStateException("Mismatched stack at branch target");
                } else {
                    // There's no way to encode two frames at the same address, so assume that
                    // the new frame supercedes the existing one.
                    last.mLocalCodes = localCodes;
                    return;
                }
            }
            last.mNext = frame;
        }
        mLastFrame = frame;
        mNumFrames++;
    }

    void reset() {
        mFirstFrame = null;
        mLastFrame = null;
        mNumFrames = 0;
        mFinished = null;
    }

    /**
     * @return false if table is empty and should not be written
     */
    boolean finish() {
        if (mFirstFrame == null) {
            return false;
        }

        var out = new BytesOut(null, mNumFrames * 4);

        try {
            out.writeShort(mNumFrames);
            Frame prev = mInitFrame;
            Frame frame = mFirstFrame;
            do {
                frame.writeTo(prev, out);
                prev = frame;
                frame = frame.mNext;
            } while (frame != null);
            mFinished = out;
        } catch (IOException e) {
            // Not expected.
            throw new IllegalStateException(e);
        }

        return true;
    }

    @Override
    int length() {
        return mFinished.size();
    }

    @Override
    void writeDataTo(BytesOut out) throws IOException {
        out.write(mFinished);
    }

    private static class Frame {
        final int mAddress;
        int[] mLocalCodes;
        final int[] mStackCodes;
        Frame mNext;

        Frame(int address, int[] localCodes, int[] stackCodes) {
            mAddress = address;
            mLocalCodes = localCodes;
            mStackCodes = stackCodes;
        }

        private void writeTo(Frame prev, BytesOut out) throws IOException {
            int offsetDelta;
            if (prev.mAddress < 0) {
                if (prev.mAddress == Integer.MIN_VALUE) {
                    // Initial offset.
                    offsetDelta = mAddress;
                } else {
                    throw new IllegalStateException("Unpositioned frame");
                }
            } else {
                offsetDelta = mAddress - prev.mAddress - 1;
            }

            if (mStackCodes == null || mStackCodes.length <= 1) {
                int localsDiff = diff(prev.mLocalCodes, mLocalCodes);
                if (localsDiff == 0) {
                    if (offsetDelta < 64) {
                        if (mStackCodes == null || mStackCodes.length == 0) {
                            // same_frame
                            out.writeByte(offsetDelta);
                        } else {
                            // same_locals_1_stack_item_frame
                            out.writeByte(offsetDelta + 64);
                            writeCode(out, mStackCodes[0]);
                        }
                    } else {
                        if (mStackCodes == null || mStackCodes.length == 0) {
                            // same_frame_extended
                            out.writeByte(251);
                            out.writeShort(offsetDelta);
                        } else {
                            // same_locals_1_stack_item_frame_extended
                            out.writeByte(247);
                            out.writeShort(offsetDelta);
                            writeCode(out, mStackCodes[0]);
                        }
                    }
                    return;
                } else if (localsDiff >= -3 && localsDiff <= 3) {
                    if (mStackCodes == null || mStackCodes.length == 0) {
                        // chop_frame or append_frame
                        out.writeByte(251 + localsDiff);
                        out.writeShort(offsetDelta);
                        if (localsDiff > 0) {
                            int i = mLocalCodes.length - localsDiff;
                            for (; i < mLocalCodes.length; i++) {
                                writeCode(out, mLocalCodes[i]);
                            }
                        }
                        return;
                    }
                }
            }

            // full_frame
            out.writeByte(255);
            out.writeShort(offsetDelta);
            writeCodes(out, mLocalCodes);
            writeCodes(out, mStackCodes);
        }

        private static void writeCodes(BytesOut out, int[] codes) throws IOException {
            if (codes == null) {
                out.writeShort(0);
            } else {
                out.writeShort(codes.length);
                for (int code : codes) {
                    writeCode(out, code);
                }
            }
        }

        private static void writeCode(BytesOut out, int code) throws IOException {
            int smCode = code & 0xff;
            out.writeByte(smCode);
            if (smCode == Type.SM_OBJECT || smCode == Type.SM_UNINIT) {
                out.writeShort(code >> 8);
            }
        }

        /**
         * @return MIN_VALUE if mismatched; 0 if the same, -n if chopped, +n if appended
         */
        private static int diff(int[] from, int[] to) {
            if (from == null || from.length == 0) {
                return to == null ? 0 : to.length;
            }
            if (to == null || to.length == 0) {
                return from == null ? 0 : -from.length;
            }
            int mismatch = Arrays.mismatch(from, to);
            if (mismatch < 0) {
                return 0;
            }
            if (mismatch >= from.length || mismatch >= to.length) {
                return to.length - from.length;
            }
            return Integer.MIN_VALUE;
        }
    }
}
