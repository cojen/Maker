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
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Defines an attribute, which itself can have attributes.
 *
 * @author Brian S O'Neill
 */
abstract class Attribute extends Attributed {
    final ConstantPool.C_UTF8 mAttrName;

    Attribute(ConstantPool cp, String name) {
        super(cp);
        mAttrName = cp.addUTF8(name);
    }

    /**
     * Returns the length (in bytes) of this attribute in the class file.
     */
    abstract int length();

    /**
     * This method writes the 16 bit name constant index followed by the 32 bit attribute
     * length, followed by the attribute specific data.
     */
    final void writeTo(BytesOut out) throws IOException {
        out.writeShort(mAttrName.mIndex);
        out.writeInt(length());
        writeDataTo(out);
    }

    /**
     * Write just the attribute specific data.
     */
    abstract void writeDataTo(BytesOut out) throws IOException;

    static class Constant extends Attribute {
        private final ConstantPool.Constant mConstant;

        Constant(ConstantPool cp, ConstantPool.Constant constant) {
            super(cp, "ConstantValue");
            mConstant = constant;
        }

        @Override
        int length() {
            return 2;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            out.writeShort(mConstant.mIndex);
        }
    }

    static class SourceFile extends Attribute {
        private final ConstantPool.C_UTF8 mSourceFile;

        SourceFile(ConstantPool cp, String fileName) {
            super(cp, "SourceFile");
            mSourceFile = cp.addUTF8(fileName);
        }

        @Override
        int length() {
            return 2;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            out.writeShort(mSourceFile.mIndex);
        }
    }

    static class Code extends Attribute {
        private final int mMaxStack, mMaxLocals;
        private final byte[] mCode;
        private final int mCodeLen;
        private final List<? extends ExceptionHandler> mExceptionHandlers;

        /**
         * @param exceptionHandlers optional
         */
        Code(ConstantPool cp, int maxStack, int maxLocals, byte[] code, int codeLen,
             List<? extends ExceptionHandler> exceptionHandlers)
        {
            super(cp, "Code");
            mMaxStack = maxStack;
            mMaxLocals = maxLocals;
            mCode = code;
            mCodeLen = codeLen;
            mExceptionHandlers = exceptionHandlers;
        }

        @Override
        int length() {
            int length = (2 + 2 + 4 + 2 + 2) + mCodeLen;

            if (mExceptionHandlers != null) {
                length += 8 * mExceptionHandlers.size();
            }

            if (mAttributes != null) {
                for (Attribute attr : mAttributes) {
                    length += 6 + attr.length();
                }
            }

            return length;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            out.writeShort(mMaxStack);
            out.writeShort(mMaxLocals);
            out.writeInt(mCodeLen);
            out.write(mCode, 0, mCodeLen);

            if (mExceptionHandlers == null) {
                out.writeShort(0);
            } else {
                out.writeShort(mExceptionHandlers.size());
                for (ExceptionHandler handler : mExceptionHandlers) {
                    out.writeShort(handler.startAddr());
                    out.writeShort(handler.endAddr());
                    out.writeShort(handler.handlerAddr());
                    ConstantPool.C_Class catchClass = handler.catchClass();
                    out.writeShort(catchClass == null ? 0 : catchClass.mIndex);
                }
            }

            writeAttributesTo(out);
        }
    }

    static class LineNumberTable extends Attribute {
        private int[] mTable;
        private int mLength;

        LineNumberTable(ConstantPool cp) {
            super(cp, "LineNumberTable");
            mTable = new int[8];
        }

        void add(int offset, int number) {
            if (offset < 65536 && number < 65536 && mLength < 65535) {
                if (mLength >= mTable.length) {
                    mTable = Arrays.copyOf(mTable, mTable.length << 1);
                }
                mTable[mLength++] = (offset << 16) | (number & 0xffff);
            }
        }

        /**
         * @return false if table is empty now
         */
        boolean finish(int offset) {
            if (mLength > 0 && (mTable[mLength - 1] >>> 16) >= offset) {
                mLength--;
            }
            return mLength != 0;
        }

        @Override
        int length() {
            return 2 + mLength * 4;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            out.writeShort(mLength);
            for (int i=0; i<mLength; i++) {
                int entry = mTable[i];
                out.writeShort(entry >>> 16);
                out.writeShort(entry);
            }
        }
    }

    static class LocalVariableTable extends Attribute {
        private Entry[] mEntries;
        private int mSize;
        private int mMaxOffset;

        LocalVariableTable(ConstantPool cp) {
            super(cp, "LocalVariableTable");
            mEntries = new Entry[8];
        }

        void add(int startOffset, int endOffset,
                 ConstantPool.C_UTF8 name, ConstantPool.C_UTF8 type, int slot)
        {
            Entry entry = new Entry(startOffset, endOffset, name, type, slot);
            if (mSize >= mEntries.length) {
                mEntries = Arrays.copyOf(mEntries, mEntries.length << 1);
            }
            mEntries[mSize++] = entry;
        }

        /**
         * @return false if table is empty
         */
        boolean finish(int offset) {
            mMaxOffset = offset;
            return mSize != 0;
        }

        @Override
        int length() {
            return 2 + mSize * 10;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            out.writeShort(mSize);
            for (int i=0; i<mSize; i++) {
                Entry entry = mEntries[i];
                int start = entry.mStartOffset;
                out.writeShort(start);
                int end = Math.min(mMaxOffset, entry.mEndOffset);
                out.writeShort(Math.min(65535, Math.max(0, end - start)));
                out.writeShort(entry.mName.mIndex);
                out.writeShort(entry.mType.mIndex);
                out.writeShort(entry.mSlot);
            }
        }

        static class Entry {
            final int mStartOffset, mEndOffset, mSlot;
            final ConstantPool.C_UTF8 mName, mType;

            Entry(int startOffset, int endOffset,
                 ConstantPool.C_UTF8 name, ConstantPool.C_UTF8 type, int slot)
            {
                mStartOffset = startOffset;
                mEndOffset = endOffset;
                mName = name;
                mType = type;
                mSlot = slot;
            }
        }
    }

    static class BootstrapMethods extends Attribute {
        private final LinkedHashMap<Entry, Entry> mEntries;
        private int mLength;

        BootstrapMethods(ConstantPool cp) {
            super(cp, "BootstrapMethods");
            mEntries = new LinkedHashMap<>(8);
            mLength = 2;
        }

        /**
         * @return bootstrap index
         */
        int add(ConstantPool.C_MethodHandle method, ConstantPool.Constant[] args) {
            Entry entry = new Entry(method, args);
            Entry existing = mEntries.putIfAbsent(entry, entry);
            if (existing == null) {
                entry.mIndex = mEntries.size() - 1;
                mLength += (2 + 2) + (2 * args.length);
            } else {
                entry = existing;
            }
            return entry.mIndex;
        }

        @Override
        int length() {
            return mLength;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            out.writeShort(mEntries.size());
            for (Entry entry : mEntries.keySet()) {
                out.writeShort(entry.mMethod.mIndex);
                ConstantPool.Constant[] args = entry.mArgs;
                out.writeShort(args.length);
                for (ConstantPool.Constant arg : args) {
                    out.writeShort(arg.mIndex);
                }
            }
        }

        static class Entry {
            final ConstantPool.C_MethodHandle mMethod;
            final ConstantPool.Constant[] mArgs;
            int mIndex;

            Entry(ConstantPool.C_MethodHandle method, ConstantPool.Constant[] args) {
                mMethod = method;
                mArgs = args;
            }

            @Override
            public int hashCode() {
                return mMethod.hashCode() * 31 + Arrays.hashCode(mArgs);
            }
    
            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj instanceof Entry) {
                    Entry other = (Entry) obj;
                    return mMethod.equals(other.mMethod) && Arrays.equals(mArgs, other.mArgs);
                }
                return false;
            }
        }
    }

    static class NestHost extends Attribute {
        private final ConstantPool.C_Class mHostClass;

        NestHost(ConstantPool cp, ConstantPool.C_Class hostClass) {
            super(cp, "NestHost");
            mHostClass = hostClass;
        }

        @Override
        int length() {
            return 2;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            out.writeShort(mHostClass.mIndex);
        }
    }

    static class NestMembers extends Attribute {
        private ConstantPool.C_Class[] mMembers;
        private int mSize;

        NestMembers(ConstantPool cp) {
            super(cp, "NestMembers");
            mMembers = new ConstantPool.C_Class[8];
        }

        void add(ConstantPool.C_Class member) {
            if (mSize >= mMembers.length) {
                mMembers = Arrays.copyOf(mMembers, mMembers.length << 1);
            }
            mMembers[mSize++] = member;
        }

        int size() {
            return mSize;
        }

        @Override
        int length() {
            return 2 + mSize * 2;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            out.writeShort(mSize);
            for (int i=0; i<mSize; i++) {
                out.writeShort(mMembers[i].mIndex);
            }
        }
    }
}
