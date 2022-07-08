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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
        mAttrName = name == null ? null : cp.addUTF8(name);
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

    static class Empty extends Attribute {
        Empty(ConstantPool cp, String name) {
            super(cp, name);
        }

        @Override
        int length() {
            return 0;
        }

        @Override
        void writeDataTo(BytesOut out) {
        }
    }

    static class Composite extends Attribute {
        private final Attribute[] mEntries;

        /**
         * @param entries each entry must be unnamed
         */
        Composite(ConstantPool cp, String name, Attribute[] entries) {
            super(cp, name);
            mEntries = entries;
        }

        @Override
        int length() {
            int length = 0;
            for (Attribute entry : mEntries) {
                length += entry.length();
            }
            return length;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            for (Attribute entry : mEntries) {
                entry.writeDataTo(out);
            }
        }
    }

    static class Bytes extends Attribute {
        private final byte[] mBytes;

        Bytes(ConstantPool cp, String name, byte[] bytes) {
            super(cp, name);
            mBytes = bytes;
        }

        @Override
        int length() {
            return mBytes.length;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            out.write(mBytes, 0, mBytes.length);
        }
    }

    static class Constant extends Attribute {
        private final ConstantPool.Constant mConstant;

        Constant(ConstantPool cp, String name, ConstantPool.Constant constant) {
            super(cp, name);
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

    /**
     * Helpful base class for attributes that contain a list of entries.
     */
    abstract static class ListAttribute<E> extends Attribute {
        private E[] mEntries;
        private int mSize;

        @SuppressWarnings("unchecked")
        ListAttribute(ConstantPool cp, String name) {
            super(cp, name);
            mEntries = (E[]) new Object[4];
        }

        @Override
        int length() {
            return 2 + mSize * entryLength();
        }

        void writeDataTo(BytesOut out) throws IOException {
            out.writeShort(mSize);
            for (int i=0; i<mSize; i++) {
                writeEntryTo(out, mEntries[i]);
            }
        }

        protected void addEntry(E entry) {
            if (mSize >= mEntries.length) {
                mEntries = Arrays.copyOf(mEntries, mEntries.length << 1);
            }
            mEntries[mSize++] = entry;
        }

        protected int numEntries() {
            return mSize;
        }

        protected E entry(int i) {
            return mEntries[i];
        }

        protected abstract int entryLength();

        protected abstract void writeEntryTo(BytesOut out, E e) throws IOException;
    }

    static class ConstantList extends ListAttribute<ConstantPool.Constant> {
        ConstantList(ConstantPool cp, String name) {
            super(cp, name);
        }

        void add(ConstantPool.Constant member) {
            addEntry(member);
        }

        @Override
        protected int entryLength() {
            return 2;
        }

        @Override
        protected void writeEntryTo(BytesOut out, ConstantPool.Constant c) throws IOException {
            out.writeShort(c.mIndex);
        }
    }

    static class PermittedSubclasses extends ConstantList {
        PermittedSubclasses(ConstantPool cp, Iterable<ConstantPool.C_Class> subclasses) {
            super(cp, "PermittedSubclasses");
            for (ConstantPool.C_Class subclass : subclasses) {
                add(subclass);
            }
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

    static class LocalVariableTable extends ListAttribute<LocalVariableTable.Entry> {
        private int mMaxOffset;

        LocalVariableTable(ConstantPool cp, String name) {
            super(cp, name);
        }

        void add(int startOffset, int endOffset,
                 ConstantPool.C_UTF8 name, ConstantPool.C_UTF8 type, int slot)
        {
            addEntry(new Entry(startOffset, endOffset, name, type, slot));
        }

        /**
         * @return false if table is empty
         */
        boolean finish(int offset) {
            mMaxOffset = offset;
            return numEntries() != 0;
        }

        @Override
        protected int entryLength() {
            return 10;
        }

        @Override
        protected void writeEntryTo(BytesOut out, Entry entry) throws IOException {
            int start = entry.mStartOffset;
            out.writeShort(start);
            int end = Math.min(mMaxOffset, entry.mEndOffset);
            out.writeShort(Math.min(65535, Math.max(0, end - start)));
            out.writeShort(entry.mName.mIndex);
            out.writeShort(entry.mType.mIndex);
            out.writeShort(entry.mSlot);
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

    static class EnclosingMethod extends Attribute {
        private final ConstantPool.C_Class mHostClass;
        private final ConstantPool.C_NameAndType mHostMethod;

        EnclosingMethod(ConstantPool cp,
                        ConstantPool.C_Class hostClass, ConstantPool.C_NameAndType hostMethod)
        {
            super(cp, "EnclosingMethod");
            mHostClass = hostClass;
            mHostMethod = hostMethod;
        }

        @Override
        int length() {
            return 4;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            out.writeShort(mHostClass.mIndex);
            out.writeShort(mHostMethod.mIndex);
        }
    }

    static class InnerClasses extends ListAttribute<InnerClasses.Entry> {
        private HashMap<String, Integer> mClassNumbers;

        InnerClasses(ConstantPool cp) {
            super(cp, "InnerClasses");
        }

        int classNumberFor(String name) {
            int num;
            if (mClassNumbers == null) {
                mClassNumbers = new HashMap<>(4);
                num = 1;
            } else {
                Integer obj = mClassNumbers.get(name);
                num = obj == null ? 1 : (obj + 1);
            }
            mClassNumbers.put(name, num);
            return num;
        }

        /**
         * @param innerName null for anonymous
         */
        void add(TheClassMaker inner, TheClassMaker outer, String innerName) {
            ConstantPool.C_UTF8 cname = null;
            if (innerName != null) {
                cname = mConstants.addUTF8(innerName);
            }
            addEntry(new Entry(mConstants.addClass(inner.type()),
                               mConstants.addClass(outer.type()),
                               cname, inner));
        }

        @Override
        protected int entryLength() {
            return 8;
        }

        @Override
        protected void writeEntryTo(BytesOut out, Entry entry) throws IOException {
            out.writeShort(entry.mInnerClass.mIndex);
            out.writeShort(entry.mOuterClass.mIndex);
            out.writeShort(entry.mInnerName == null ? 0 : entry.mInnerName.mIndex);
            out.writeShort(entry.mInnerMaker.mModifiers);
        }

        static class Entry {
            final ConstantPool.C_Class mInnerClass;
            final ConstantPool.C_Class mOuterClass;
            final ConstantPool.C_UTF8 mInnerName;
            final TheClassMaker mInnerMaker;

            Entry(ConstantPool.C_Class inner, ConstantPool.C_Class outer,
                  ConstantPool.C_UTF8 innerName, TheClassMaker innerMaker)
            {
                mInnerClass = inner;
                mOuterClass = outer;
                mInnerName = innerName;
                mInnerMaker = innerMaker;
            }
        }
    }

    static class Annotations extends ListAttribute<TheAnnotationMaker> {
        Annotations(ConstantPool cp, boolean visible) {
            super(cp, visible ? "RuntimeVisibleAnnotations" : "RuntimeInvisibleAnnotations");
        }

        void add(TheAnnotationMaker am) {
            super.addEntry(am);
        }

        @Override
        int length() {
            int length = 2;
            for (int i=numEntries(); --i>=0; ) {
                length += entry(i).length();
            }
            return length;
        }

        @Override
        protected int entryLength() {
            throw new AssertionError();
        }

        @Override
        protected void writeEntryTo(BytesOut out, TheAnnotationMaker am) throws IOException {
            am.writeTo(out);
        }
    }

    static class ParameterAnnotations extends Attribute {
        private final Entry[] mEntries;

        ParameterAnnotations(ConstantPool cp, boolean visible, int numParams) {
            super(cp, visible ? "RuntimeVisibleParameterAnnotations"
                  : "RuntimeInvisibleParameterAnnotations");
            mEntries = new Entry[numParams];
        }

        Entry forParam(int index) {
            Entry entry = mEntries[index];
            if (entry == null) {
                mEntries[index] = entry = new Entry();
            }
            return entry;
        }

        @Override
        int length() {
            int numParams = Math.min(mEntries.length, 255);
            int length = 1;
            for (int i=0; i<numParams; i++) {
                Entry entry = mEntries[i];
                length += (entry == null ? 2 : entry.length());
            }
            return length;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            int numParams = Math.min(mEntries.length, 255);
            out.writeByte(numParams);
            for (int i=0; i<numParams; i++) {
                Entry entry = mEntries[i];
                if (entry == null) {
                    out.writeShort(0);
                } else {
                    entry.writeTo(out);
                }
            }
        }

        static class Entry extends ArrayList<TheAnnotationMaker> {
            int length() {
                int length = 2;
                int size = size();
                for (int i=0; i<size; i++) {
                    length += get(i).length();
                }
                return length;
            }

            void writeTo(BytesOut out) throws IOException {
                int size = size();
                out.writeShort(size);
                for (int i=0; i<size; i++) {
                    get(i).writeTo(out);
                }
            }
        }
    }

    static class MethodParameters extends Attribute {
        private final ConstantPool.C_UTF8[] mNames;

        MethodParameters(ConstantPool cp, int numParams) {
            super(cp, "MethodParameters");
            mNames = new ConstantPool.C_UTF8[Math.min(numParams, 255)];
        }

        void setName(int index, ConstantPool.C_UTF8 name) {
            if (index < mNames.length) {
                mNames[index] = name;
            }
        }

        @Override
        int length() {
            return 1 + mNames.length * 4;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            out.writeByte(mNames.length);
            for (ConstantPool.C_UTF8 name : mNames) {
                out.writeShort(name == null ? 0 : name.mIndex);
                out.writeShort(0); // access flags
            }
        }
    }
}
