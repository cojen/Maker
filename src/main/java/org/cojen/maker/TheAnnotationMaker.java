/*
 *  Copyright 2021 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.maker;

import java.io.IOException;

import java.lang.reflect.Array;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Map;

/**
 * 
 *
 * @author Brian S O'Neill
 */
class TheAnnotationMaker implements AnnotationMaker {
    private final TheClassMaker mClassMaker;
    private final ConstantPool.C_UTF8 mType;
    private final Map<ConstantPool.C_UTF8, Element> mElements;

    private TheAnnotationMaker mParent;

    TheAnnotationMaker(TheClassMaker classMaker, Object annotationType) {
        mClassMaker = classMaker;
        mType = classMaker.mConstants.addUTF8(classMaker.typeFrom(annotationType).descriptor());
        mElements = new LinkedHashMap<>();
    }

    @Override
    public void put(String name, Object value) {
        if (mElements.size() >= 65535) {
            throw new IllegalStateException();
        }
        ConstantPool.C_UTF8 utf = mClassMaker.mConstants.addUTF8(name);
        if (mElements.containsKey(utf)) {
            throw new IllegalStateException();
        }
        mElements.put(utf, toElement(value));
    }

    @Override
    public AnnotationMaker newAnnotation(Object annotationType) {
        var am = new TheAnnotationMaker(mClassMaker, annotationType);
        am.mParent = this;
        return am;
    }

    int length() {
        int length = (2 + 2) + mElements.size() * 2;
        for (Element e : mElements.values()) {
            length += e.length();
        }
        return length;
    }

    void writeTo(BytesOut out) throws IOException {
        out.writeShort(mType.mIndex);
        out.writeShort(mElements.size());
        for (Map.Entry<ConstantPool.C_UTF8, Element> e : mElements.entrySet()) {
            out.writeShort(e.getKey().mIndex);
            e.getValue().writeTo(out);
        }
    }

    private Element toElement(Object value) {
        Objects.requireNonNull(value);

        ConstantPool cp = mClassMaker.mConstants;
        if (value instanceof String) {
            return new ConstElement('s', cp.addUTF8((String) value));
        } else if (value instanceof Integer) {
            return new ConstElement('I', cp.addInteger((Integer) value));
        } else if (value instanceof Boolean) {
            return new ConstElement('Z', cp.addInteger(((Boolean) value) ? 1 : 0));
        } else if (value instanceof Enum) {
            var ev = (Enum) value;
            return new EnumElement(cp.addUTF8(Type.from(ev.getDeclaringClass()).descriptor()),
                                   cp.addUTF8(ev.name()));
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            if (length > 65535) {
                throw new IllegalArgumentException();
            }
            var elements = new Element[length];
            for (int i=0; i<length; i++) {
                elements[i] = toElement(Array.get(value, i));
            }
            return new ArrayElement(elements);
        } else if (value instanceof TheAnnotationMaker) {
            var am = (TheAnnotationMaker) value;
            if (am.mParent != this) {
                throw new IllegalStateException();
            }
            am.mParent = null;
            return new AnnotationElement(am);
        } else if (value instanceof Long) {
            return new ConstElement('J', cp.addLong((Long) value));
        } else if (value instanceof Double) {
            return new ConstElement('D', cp.addDouble((Double) value));
        } else if (value instanceof Class) {
            return new ConstElement('c', cp.addUTF8(Type.from((Class) value).descriptor()));
        } else if (value instanceof Character) {
            return new ConstElement('C', cp.addInteger(((Character) value).charValue()));
        } else if (value instanceof Byte) {
            return new ConstElement('B', cp.addInteger(((Byte) value).intValue()));
        } else if (value instanceof Short) {
            return new ConstElement('S', cp.addInteger(((Short) value).intValue()));
        } else if (value instanceof Float) {
            return new ConstElement('F', cp.addFloat((Float) value));
        } else if (value instanceof Typed) {
            return new ConstElement('c', cp.addUTF8(((Typed) value).type().descriptor()));
        } else {
            throw new IllegalArgumentException();
        }
    }

    abstract static class Element {
        private final char mTag;

        Element(char tag) {
            mTag = tag;
        }

        /**
         * Length includes the one-byte tag.
         */
        abstract int length();

        final void writeTo(BytesOut out) throws IOException {
            out.writeByte((byte) mTag);
            writeDataTo(out);
        }

        abstract void writeDataTo(BytesOut out) throws IOException;
    }

    static class ConstElement extends Element {
        private final ConstantPool.Constant mConstant;

        ConstElement(char tag, ConstantPool.Constant constant) {
            super(tag);
            mConstant = constant;
        }

        @Override
        int length() {
            return 1 + 2;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            out.writeShort(mConstant.mIndex);
        }
    }

    static class EnumElement extends Element {
        private final ConstantPool.C_UTF8 mType;
        private final ConstantPool.C_UTF8 mName;

        EnumElement(ConstantPool.C_UTF8 type, ConstantPool.C_UTF8 name) {
            super('e');
            mType = type;
            mName = name;
        }

        @Override
        int length() {
            return 1 + 2 + 2;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            out.writeShort(mType.mIndex);
            out.writeShort(mName.mIndex);
        }
    }

    static class ArrayElement extends Element {
        private final Element[] mElements;

        ArrayElement(Element[] elements) {
            super('[');
            mElements = elements;
        }

        @Override
        int length() {
            int length = 1 + 2;
            for (Element e : mElements) {
                length += e.length();
            }
            return length;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            out.writeShort(mElements.length);
            for (Element e : mElements) {
                e.writeTo(out);
            }
        }
    }

    static class AnnotationElement extends Element {
        private final TheAnnotationMaker mAnnotation;

        AnnotationElement(TheAnnotationMaker ann) {
            super('@');
            mAnnotation = ann;
        }

        @Override
        int length() {
            return 1 + mAnnotation.length();
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            mAnnotation.writeTo(out);
        }
    }
}
