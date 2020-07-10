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
import java.util.TreeMap;

/**
 * The wonderful StackMapTable attribute!
 *
 * @author Brian S O'Neill
 */
class StackMapTable extends Attribute {
    private final Frame mInitFrame;
    private TreeMap<Integer, Frame> mFrames;
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
     * @param address code address the new frame refers to; pass -1 if not known yet
     * @param localCodes can be null
     * @param stackCodes can be null
     */
    Frame add(int address, int[] localCodes, int[] stackCodes) {
        if (mFrames == null) {
            mFrames = new TreeMap<>();
        }

        Integer key = address;

        if (address >= 0 && mFrames != null) {
            Frame frame = mFrames.get(key);
            if (frame != null) {
                frame.merge(localCodes, stackCodes);
                return frame;
            }
        }

        Frame frame = new Frame(address, localCodes, stackCodes);

        if (address >= 0) {
            mFrames.put(key, frame);
        }

        return frame;
    }

    /**
     * @return false if table is empty and should not be written
     */
    boolean finish() {
        if (mFrames == null || mFrames.isEmpty()) {
            return false;
        }

        var out = new BytesOut(null, Math.max(8, mFrames.size() * 4));

        try {
            out.writeShort(mFrames.size());
            Frame prev = mInitFrame;
            for (Frame frame : mFrames.values()) {
                frame.writeTo(prev, out);
                prev = frame;
            }
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

    private void add(Frame frame) {
        Integer key = frame.mAddress;
        Frame existing = mFrames.get(key);
        if (existing != null) {
            existing.merge(frame);
        } else {
            mFrames.put(key, frame);
        }
    }

    static class Frame {
        int mAddress;
        int[] mLocalCodes;
        final int[] mStackCodes;

        Frame(int address, int[] localCodes, int[] stackCodes) {
            mAddress = address;
            mLocalCodes = localCodes;
            mStackCodes = stackCodes;
        }

        /**
         * @return stack size
         */
        public int setAddress(StackMapTable table, int address) {
            if (mAddress < 0) {
                mAddress = address;
                table.add(this);
            } else if (address != mAddress) {
                throw new IllegalStateException("Frame address changed");
            }
            return mStackCodes == null ? 0 : mStackCodes.length;
        }

        private void merge(Frame other) {
            merge(other.mLocalCodes, other.mStackCodes);
        }

        private void merge(int[] localCodes, int[] stackCodes) {
            merge(localCodes, localCodes == null ? 0 : localCodes.length,
                   stackCodes, stackCodes == null ? 0 : stackCodes.length);
        }

        private void merge(int[] localCodes, int localLen, int[] stackCodes, int stackLen) {
            // Stacks must be identical.
            verify("stack", mStackCodes, stackCodes, stackLen);

            // Apply the intersection of the local variable sets.

            int[] thisCodes = mLocalCodes;
            int thisLen = thisCodes == null ? 0 : thisCodes.length;

            if (thisLen <= localLen) {
                localLen = thisLen;
            } else {
                // Swap and keep the smaller set.
                thisCodes = localCodes;
                localCodes = mLocalCodes;
                mLocalCodes = thisCodes;
            }

            // If any mismatches, use the "top" type instead.
            for (int i=0; i<localLen; i++) {
                if (thisCodes[i] != localCodes[i]) {
                    thisCodes[i] = Type.SM_TOP;
                }
            }
        }

        private void verify(String which, int[] expect, int[] actual, int actualLen) {
            if (actual == null || actualLen == 0) {
                if (expect == null || expect.length == 0) {
                    return;
                }
            } else if (expect != null && expect.length == actualLen) {
                check: {
                    for (int i=0; i<actualLen; i++) {
                        if (actual[i] != expect[i]) {
                            break check;
                        }
                    }
                    return;
                }
            }

            throw new IllegalStateException("Mismatched " + which + " at branch target");
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
