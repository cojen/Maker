/*
 *  Copyright 2020 Cojen.org
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import java.nio.ByteOrder;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Simple buffer/stream for writing ClassFile output.
 *
 * @author Brian S O'Neill
 */
class BytesOut {
    private static final VarHandle cShortArrayBEHandle;
    private static final VarHandle cIntArrayBEHandle;
    private static final VarHandle cLongArrayBEHandle;

    static {
        try {
            cShortArrayBEHandle = MethodHandles.byteArrayViewVarHandle
                (short[].class, ByteOrder.BIG_ENDIAN);
            cIntArrayBEHandle = MethodHandles.byteArrayViewVarHandle
                (int[].class, ByteOrder.BIG_ENDIAN);
            cLongArrayBEHandle = MethodHandles.byteArrayViewVarHandle
                (long[].class, ByteOrder.BIG_ENDIAN);
        } catch (Throwable e) {
            throw new ExceptionInInitializerError();
        }
    }

    private final OutputStream mOut;
    private byte[] mBuffer;
    private int mSize;

    /**
     * Pass an OutputStream instance to behave like BufferedOutputStream, or pass null to act
     * like ByteArrayOutputStream.
     *
     * @param bufferSize at least 8
     */
    BytesOut(OutputStream out, int bufferSize) {
        mOut = out;
        mBuffer = new byte[bufferSize];
    }

    public int size() {
        return mSize;
    }

    public void writeByte(int v) throws IOException {
        ensureCapacity(1);
        mBuffer[mSize++] = (byte) v;
    }

    public void writeShort(int v) throws IOException {
        ensureCapacity(2);
        cShortArrayBEHandle.set(mBuffer, mSize, (short) v);
        mSize += 2;
    }

    public void writeInt(int v) throws IOException {
        ensureCapacity(4);
        cIntArrayBEHandle.set(mBuffer, mSize, v);
        mSize += 4;
    }

    public void writeLong(long v) throws IOException {
        ensureCapacity(8);
        cLongArrayBEHandle.set(mBuffer, mSize, v);
        mSize += 8;
    }

    public void writeFloat(float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    public void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToLongBits(v));
    }

    public void write(byte[] b, int off, int len) throws IOException {
        int avail = mBuffer.length - mSize;

        if (len > avail) {
            if (mOut == null) {
                flushOrExpand(len);
            } else {
                System.arraycopy(b, off, mBuffer, mSize, avail);
                off += avail;
                len -= avail;
                mOut.write(mBuffer);
                mSize = 0;
                if (len >= mBuffer.length) {
                    mOut.write(b, off, len);
                    return;
                }
            }
        }

        System.arraycopy(b, off, mBuffer, mSize, len);
        mSize += len;
    }

    public void writeUTF(String str) throws IOException {
        final int length = str.length();

        // Assume ASCII string for now.
        strictEnsureCapacity(2 + length);

        final int start = mSize;
        mSize += 2; // reserve slot for byte length field

        for (int i=0; i<length; i++) {
            int c = str.charAt(i);
            if (c < 0x80 && c != 0) {
                mBuffer[mSize++] = (byte) c;
            } else if (c < 0x800) {
                strictEnsureCapacity(2);
                mBuffer[mSize++] = (byte) (0xc0 | (c >> 6));
                mBuffer[mSize++] = (byte) (0x80 | (c & 0x3f));
            } else {
                strictEnsureCapacity(3);
                mBuffer[mSize++] = (byte) (0xe0 | (c >> 12));
                mBuffer[mSize++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                mBuffer[mSize++] = (byte) (0x80 | (c & 0x3f));
            }
        }

        cShortArrayBEHandle.set(mBuffer, start, (short) (mSize - start - 2));
    }

    public void write(BytesOut out) throws IOException {
        write(out.mBuffer, 0, out.mSize);
    }

    public void flush() throws IOException {
        if (mOut != null && mSize > 0) {
            mOut.write(mBuffer, 0, mSize);
            mSize = 0;
        }
    }

    public byte[] toByteArray() {
        byte[] copy = new byte[mSize];
        System.arraycopy(mBuffer, 0, copy, 0, mSize);
        return copy;
    }

    private void ensureCapacity(int amt) throws IOException {
        if (mSize + amt > mBuffer.length) {
            flushOrExpand(amt);
        }
    }

    private void strictEnsureCapacity(int amt) throws IOException {
        if (mSize + amt > mBuffer.length) {
            flushAndExpand(amt);
        }
    }

    private void flushOrExpand(int amt) throws IOException {
        if (mOut != null) {
            mOut.write(mBuffer, 0, mSize);
            mSize = 0;
        } else {
            expand(amt);
        }
    }

    private void flushAndExpand(int amt) throws IOException {
        if (mOut != null) {
            mOut.write(mBuffer, 0, mSize);
            mSize = 0;
        }
        expand(amt);
    }

    private void expand(int amt) {
        byte[] newBuffer = new byte[Math.max(mSize + amt, mSize << 1)];
        System.arraycopy(mBuffer, 0, newBuffer, 0, mSize);
        mBuffer = newBuffer;
    }
}
