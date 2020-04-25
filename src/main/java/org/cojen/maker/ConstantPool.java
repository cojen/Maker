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

import java.io.DataOutput;
import java.io.IOException;

import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;

import java.lang.reflect.Field;

import java.util.LinkedHashMap;
import java.util.Map;

import static java.lang.invoke.MethodHandleInfo.*;

import static java.util.Objects.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
class ConstantPool {
    private final Map<Constant, Constant> mConstants;
    private int mSize;

    ConstantPool() {
        mConstants = new LinkedHashMap<>();
        mSize = 1; // constant 0 is reserved
    }

    void writeTo(DataOutput dout) throws IOException {
        int size = mSize;
        if (size >= 65535) {
            throw new IllegalStateException
                ("Constant pool entry count cannot exceed 65535: " + size);
        }
        dout.writeShort(size);
        for (Constant c : mConstants.keySet()) {
            c.writeTo(dout);
        }
    }

    C_UTF8 addUTF8(String value) {
        requireNonNull(value);
        return addConstant(new C_UTF8(value));
    }

    C_Integer addInteger(int value) {
        return addConstant(new C_Integer(value));
    }

    C_Float addFloat(float value) {
        return addConstant(new C_Float(value));
    }

    C_Long addLong(long value) {
        var constant = new C_Long(value);
        C_Long actual = addConstant(constant);
        if (constant == actual) {
            mSize++; // takes up two slots
        }
        return actual;
    }

    C_Double addDouble(double value) {
        var constant = new C_Double(value);
        C_Double actual = addConstant(constant);
        if (constant == actual) {
            mSize++; // takes up two slots
        }
        return actual;
    }

    /**
     * @param type can be a class, an interface, or an array
     */
    C_Class addClass(Type type) {
        if (!type.isObject()) {
            throw new IllegalArgumentException(type.name());
        }
        String name = type.isArray() ? type.descriptor() : type.name().replace('.', '/');
        return addConstant(new C_Class(addUTF8(name), type));
    }

    C_String addString(String value) {
        return addConstant(new C_String(addUTF8(value)));
    }

    C_Field addField(Type.Field field) {
        C_Class clazz = addClass(field.enclosingType());
        C_NameAndType nameAndType = addNameAndType(field.name(), field.type().descriptor());
        return addConstant(new C_Field(clazz, nameAndType, field));
    }

    C_Method addMethod(Type.Method method) {
        if (method.enclosingType().isInterface()) {
            return addInterfaceMethod(method);
        }
        C_Class clazz = addClass(method.enclosingType());
        C_NameAndType nameAndType = addNameAndType(method.name(), method.descriptor());
        return addConstant(new C_Method(clazz, nameAndType, method));
    }

    C_InterfaceMethod addInterfaceMethod(Type.Method method) {
        C_Class clazz = addClass(method.enclosingType());
        C_NameAndType nameAndType = addNameAndType(method.name(), method.descriptor());
        return addConstant(new C_InterfaceMethod(clazz, nameAndType, method));
    }

    C_MethodType addMethodType(Type.Method method) {
        return addMethodType(method.descriptor());
    }

    C_MethodType addMethodType(MethodType type) {
        return addMethodType(type.descriptorString());
    }

    C_MethodHandle addMethodHandle(MethodHandleInfo info) {
        final int kind = info.getReferenceKind();
        final Type decl = Type.from(info.getDeclaringClass());
        final MethodType mtype = info.getMethodType();
        final String name = info.getName();

        final Constant ref;

        switch (kind) {
        default:
            throw new AssertionError();

        case REF_getField: case REF_getStatic:
            ref = addField(decl.inventField
                           (kind == REF_getStatic, Type.from(mtype.returnType()), name));
            break;

        case REF_putField: case REF_putStatic:
            ref = addField(decl.inventField
                           (kind == REF_getStatic, Type.from(mtype.lastParameterType()), name));
            break;

        case REF_invokeVirtual: case REF_newInvokeSpecial:
        case REF_invokeStatic: case REF_invokeSpecial: case REF_invokeInterface:
            Type ret = Type.from(mtype.returnType());
            Type[] params = new Type[mtype.parameterCount()];
            for (int i=0; i<params.length; i++) {
                params[i] = Type.from(mtype.parameterType(i));
            }
            ref = addMethod(decl.inventMethod(kind == REF_invokeStatic, ret, name, params));
            break;
        }

        return addMethodHandle(kind, ref);
    }

    C_MethodHandle addMethodHandle(int kind, Constant ref) {
        return addConstant(new C_MethodHandle((byte) kind, ref));
    }

    C_Dynamic addInvokeDynamic(int bootstrapIndex, String name, MethodType type) {
        return addInvokeDynamic(bootstrapIndex, name, type.descriptorString());
    }

    C_Dynamic addInvokeDynamic(int bootstrapIndex, String name, String descriptor) {
        C_NameAndType nameAndType = addNameAndType(name, descriptor);
        return addConstant(new C_Dynamic(18, bootstrapIndex, nameAndType));
    }

    Constant tryAddLoadableConstant(Object value) {
        if (value instanceof String) {
            return addString((String) value);
        } else if (value instanceof Class) {
            Class clazz = (Class) value;
            if (!clazz.isPrimitive()) {
                return addClass(Type.from(clazz));
            }
        } else if (value instanceof Number) {
            if (value instanceof Integer) {
                return addInteger((Integer) value);
            } else if (value instanceof Long) {
                return addLong((Long) value);
            } else if (value instanceof Float) {
                return addFloat((Float) value);
            } else if (value instanceof Double) {
                return addDouble((Double) value);
            }
        } else if (value instanceof MethodType) {
            return addMethodType((MethodType) value);
        } else if (value instanceof MethodHandleInfo) {
            return addMethodHandle((MethodHandleInfo) value);
        }
        return null;
    }

    private C_MethodType addMethodType(String typeDesc) {
        return addConstant(new C_MethodType(addUTF8(typeDesc)));
    }

    private C_NameAndType addNameAndType(String name, String typeDesc) {
        return addNameAndType(addUTF8(name), addUTF8(typeDesc));
    } 

    private C_NameAndType addNameAndType(C_UTF8 name, C_UTF8 typeDesc) {
        return addConstant(new C_NameAndType(name, typeDesc));
    }

    @SuppressWarnings("unchecked")
    private <C extends Constant> C addConstant(C constant) {
        Constant existing = mConstants.get(constant);
        if (existing == null) {
            constant.mIndex = mSize;
            mConstants.put(constant, constant);
            mSize++;
        } else {
            constant = (C) existing;
        }
        return constant;
    }

    abstract static class Constant {
        final int mTag;
        int mIndex;

        Constant(int tag) {
            mTag = tag;
        }

        void writeTo(DataOutput dout) throws IOException {
            dout.writeByte(mTag);
        }
    }

    static class C_UTF8 extends Constant {
        final String mValue;

        C_UTF8(String value) {
            super(1);
            mValue = value;
        }

        @Override
        public int hashCode() {
            return mValue.hashCode();
        }
    
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof C_UTF8) {
                C_UTF8 other = (C_UTF8) obj;
                return mValue.equals(other.mValue);
            }
            return false;
        }

        @Override
        void writeTo(DataOutput dout) throws IOException {
            super.writeTo(dout);
            dout.writeUTF(mValue);
        }
    }

    static class C_Integer extends Constant {
        final int mValue;

        C_Integer(int value) {
            super(3);
            mValue = value;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(mValue);
        }
    
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof C_Integer) {
                C_Integer other = (C_Integer) obj;
                return mValue == other.mValue;
            }
            return false;
        }
    
        @Override
        void writeTo(DataOutput dout) throws IOException {
            super.writeTo(dout);
            dout.writeInt(mValue);
        }
    }

    static class C_Float extends Constant {
        final float mValue;

        C_Float(float value) {
            super(4);
            mValue = value;
        }

        @Override
        public int hashCode() {
            return Float.hashCode(mValue);
        }
    
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof C_Float) {
                C_Float other = (C_Float) obj;
                return Float.floatToIntBits(mValue) == Float.floatToIntBits(other.mValue);
            }
            return false;
        }
    
        @Override
        void writeTo(DataOutput dout) throws IOException {
            super.writeTo(dout);
            dout.writeFloat(mValue);
        }
    }

    static class C_Long extends Constant {
        final long mValue;

        C_Long(long value) {
            super(5);
            mValue = value;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(mValue);
        }
    
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof C_Long) {
                C_Long other = (C_Long) obj;
                return mValue == other.mValue;
            }
            return false;
        }
    
        @Override
        void writeTo(DataOutput dout) throws IOException {
            super.writeTo(dout);
            dout.writeLong(mValue);
        }
    }

    static class C_Double extends Constant {
        final double mValue;

        C_Double(double value) {
            super(6);
            mValue = value;
        }

        @Override
        public int hashCode() {
            return Double.hashCode(mValue);
        }
    
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof C_Double) {
                C_Double other = (C_Double) obj;
                return Double.doubleToLongBits(mValue) == Double.doubleToLongBits(other.mValue);
            }
            return false;
        }
    
        @Override
        void writeTo(DataOutput dout) throws IOException {
            super.writeTo(dout);
            dout.writeDouble(mValue);
        }
    }

    static abstract class C_StringRef extends Constant {
        C_UTF8 mValue;

        C_StringRef(int tag, C_UTF8 value) {
            super(tag);
            mValue = value;
        }

        @Override
        public int hashCode() {
            return mValue.hashCode() * 31 + mTag;
        }
    
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof C_StringRef) {
                C_StringRef other = (C_StringRef) obj;
                return mTag == other.mTag && mValue.equals(other.mValue);
            }
            return false;
        }

        @Override
        void writeTo(DataOutput dout) throws IOException {
            super.writeTo(dout);
            dout.writeShort(mValue.mIndex);
        }
    }

    static class C_Class extends C_StringRef {
        final Type mType;

        C_Class(C_UTF8 name, Type type) {
            super(7, name);
            mType = type;
        }

        void rename(C_UTF8 name) {
            mValue = name;
        }
    }

    static class C_String extends C_StringRef {
        C_String(C_UTF8 value) {
            super(8, value);
        }
    }

    static class C_NameAndType extends Constant {
        final C_UTF8 mName;
        final C_UTF8 mTypeDesc;

        C_NameAndType(C_UTF8 name, C_UTF8 typeDesc) {
            super(12);
            mName = name;
            mTypeDesc = typeDesc;
        }

        @Override
        public int hashCode() {
            return mName.hashCode() * 31 + mTypeDesc.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof C_NameAndType) {
                C_NameAndType other = (C_NameAndType) obj;
                return mName.equals(other.mName) && mTypeDesc.equals(other.mTypeDesc);
            }
            return false;
        }

        @Override
        void writeTo(DataOutput dout) throws IOException {
            super.writeTo(dout);
            dout.writeShort(mName.mIndex);
            dout.writeShort(mTypeDesc.mIndex);
        }
    }

    static class C_MemberRef extends Constant {
        final C_Class mClass;
        final C_NameAndType mNameAndType;

        C_MemberRef(int tag, C_Class clazz, C_NameAndType nameAndType) {
            super(tag);
            mClass = clazz;
            mNameAndType = nameAndType;
        }

        @Override
        public int hashCode() {
            return (mClass.hashCode() + mNameAndType.hashCode()) * 31 + mTag;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof C_MemberRef) {
                C_MemberRef other = (C_MemberRef) obj;
                return mTag == other.mTag && mClass.equals(other.mClass)
                    && mNameAndType.equals(other.mNameAndType);
            }
            return false;
        }
    
        @Override
        void writeTo(DataOutput dout) throws IOException {
            super.writeTo(dout);
            dout.writeShort(mClass.mIndex);
            dout.writeShort(mNameAndType.mIndex);
        }
    }

    static class C_Field extends C_MemberRef {
        final Type.Field mField;

        C_Field(C_Class clazz, C_NameAndType nameAndType, Type.Field field) {
            super(9, clazz, nameAndType);
            mField = field;
        }
    }

    static class C_Method extends C_MemberRef {
        final Type.Method mMethod;

        C_Method(C_Class clazz, C_NameAndType nameAndType, Type.Method method) {
            this(10, clazz, nameAndType, method);
        }

        C_Method(int tag, C_Class clazz, C_NameAndType nameAndType, Type.Method method) {
            super(tag, clazz, nameAndType);
            mMethod = method;
        }
    }

    static class C_InterfaceMethod extends C_Method {
        C_InterfaceMethod(C_Class clazz, C_NameAndType nameAndType, Type.Method method) {
            super(11, clazz, nameAndType, method);
        }
    }

    static class C_MethodType extends Constant {
        final C_UTF8 mTypeDesc;

        C_MethodType(C_UTF8 typeDesc) {
            super(16);
            mTypeDesc = typeDesc;
        }

        @Override
        public int hashCode() {
            return mTypeDesc.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof C_MethodType) {
                C_MethodType other = (C_MethodType) obj;
                return mTypeDesc.equals(other.mTypeDesc);
            }
            return false;
        }

        @Override
        void writeTo(DataOutput dout) throws IOException {
            super.writeTo(dout);
            dout.writeShort(mTypeDesc.mIndex);
        }
    }

    static class C_MethodHandle extends Constant {
        final byte mKind;
        final Constant mRef;

        C_MethodHandle(byte kind, Constant ref) {
            super(15);
            mKind = kind;
            mRef = ref;
        }

        @Override
        public int hashCode() {
            return mKind * 31 + mRef.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof C_MethodHandle) {
                C_MethodHandle other = (C_MethodHandle) obj;
                return mKind == other.mKind && mRef.equals(other.mRef);
            }
            return false;
        }

        @Override
        void writeTo(DataOutput dout) throws IOException {
            super.writeTo(dout);
            dout.writeByte(mKind);
            dout.writeShort(mRef.mIndex);
        }
    }

    static class C_Dynamic extends Constant {
        final int mBootstrapIndex;
        final C_NameAndType mNameAndType;

        C_Dynamic(int tag, int bootstrapIndex, C_NameAndType nameAndType) {
            super(tag);
            mBootstrapIndex = bootstrapIndex;
            mNameAndType = nameAndType;
        }

        @Override
        public int hashCode() {
            return mNameAndType.hashCode() * 31 + mTag;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof C_Dynamic) {
                C_Dynamic other = (C_Dynamic) obj;
                return mTag == other.mTag && mNameAndType.equals(other.mNameAndType);
            }
            return false;
        }
    
        @Override
        void writeTo(DataOutput dout) throws IOException {
            super.writeTo(dout);
            dout.writeShort(mBootstrapIndex);
            dout.writeShort(mNameAndType.mIndex);
        }
    }
}
