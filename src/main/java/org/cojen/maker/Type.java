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

import java.lang.ref.WeakReference;

import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 
 *
 * @author Brian S O'Neill
 */
abstract class Type {
    // Note: These codes match those used by the stack map table attribute.
    static final int
        SM_TOP = 0,
        SM_INT = 1,
        SM_FLOAT = 2,
        SM_DOUBLE = 3,
        SM_LONG = 4,
        SM_NULL = 5,
        SM_UNINIT_THIS = 6,
        SM_OBJECT = 7,
        SM_UNINIT = 8;

    // Note: These codes are ordered such that "cheaper" types are ordered first.
    static final int
        T_VOID = 1,
        T_BOOLEAN = 2,
        T_BYTE = 3,
        T_CHAR = 4,
        T_SHORT = 5,
        T_INT = 6,
        T_FLOAT = 7,
        T_LONG = 8,
        T_DOUBLE = 9,
        T_OBJECT = 10;

    static final Type
        BOOLEAN = new Primitive(SM_INT, T_BOOLEAN),
        BYTE = new Primitive(SM_INT, T_BYTE),
        SHORT = new Primitive(SM_INT, T_SHORT),
        CHAR = new Primitive(SM_INT, T_CHAR),
        INT = new Primitive(SM_INT, T_INT),
        FLOAT = new Primitive(SM_FLOAT, T_FLOAT),
        LONG = new Primitive(SM_LONG, T_LONG),
        DOUBLE = new Primitive(SM_DOUBLE, T_DOUBLE),
        VOID = new Primitive(SM_TOP, T_VOID);

    static final Type
        NULL = new Special(SM_NULL),
        UNINIT_THIS = new Special(SM_UNINIT_THIS),
        UNINIT = new Special(SM_UNINIT);

    /**
     * Called when making a new class.
     */
    static Type begin(ClassLoader loader, TheClassMaker maker, String name) {
        ConcurrentHashMap<Object, Type> cache = cache(loader);
        var type = new NewClazz(loader, maker, name);
        if (cachePut(cache, name, type) != type) {
            throw new IllegalStateException("Already being defined: " + name);
        }
        maker.mTypeCache = cache;
        return type;
    }

    /**
     * Should be called after the class has been defined, to eagerly remove the cache entry.
     */
    static void uncache(Object cache, String name) {
        if (cache instanceof ConcurrentHashMap) {
            ((ConcurrentHashMap) cache).remove(name);
        }
    }

    static Type from(ClassLoader loader, Object type) {
        if (type instanceof Class) {
            return from((Class) type);
        } else if (type instanceof String) {
            return from(loader, (String) type);
        } else if (type == null) {
            return NULL;
        } else {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    /**
     * @param type class name, primitive type, or type descriptor
     */
    static Type from(ClassLoader loader, String type) {
        if (type == null) {
            return NULL;
        }
        ConcurrentHashMap<Object, Type> cache = cache(loader);
        Type t = cache.get(type);
        return t != null ? t : cachePut(cache, type, find(loader, type));
    }

    private static Type find(ClassLoader loader, String type) {
        type = type.trim();

        switch (type) {
        case "boolean": case "Z": return BOOLEAN;
        case "byte":    case "B": return BYTE;
        case "short":   case "S": return SHORT;
        case "char":    case "C": return CHAR;
        case "int":     case "I": return INT;
        case "float":   case "F": return FLOAT;
        case "double":  case "D": return DOUBLE;
        case "long":    case "J": return LONG;
        case "void":    case "V": return VOID;
        case "null":    case "":  return NULL;
        }

        if (type.endsWith("[]")) {
            return new Array(from(loader, type.substring(0, type.length() - 2)));
        }

        char first = type.charAt(0);

        if (first == '[') {
            return new Array(from(loader, type.substring(1)));
        }

        isDescriptor: if (first == 'L') {
            String desc;
            if (type.charAt(type.length() - 1) == ';') {
                desc = type.replace('.', '/');
            } else if (type.indexOf('/') > 0) {
                desc = type.replace('.', '/') + ';';
            } else {
                break isDescriptor;
            }
            return new Clazz(loader, null, desc, null);
        }

        if (type.charAt(type.length() - 1) == ';') {
            type = type.substring(0, type.length() - 1);
        }

        String name = type.replace('/', '.');

        if (name.startsWith("java.lang.")) {
            try {
                return new JavaLang(Class.forName(name, true, loader));
            } catch (ClassNotFoundException e) {
                // Ignore.
            }
        }

        return new Clazz(loader, name, null, null);
    }

    static Type from(Class type) {
        if (type == null) {
            return NULL;
        }
        ConcurrentHashMap<Object, Type> cache = cache(type.getClassLoader());
        Type t = cache.get(type);
        return t != null ? t : cachePut(cache, type, find(type));
    }

    private static Type find(Class type) {
        if (type.isPrimitive()) {
            if (type == int.class) {
                return INT;
            } else if (type == long.class) {
                return LONG;
            } else if (type == boolean.class) {
                return BOOLEAN;
            } else if (type == double.class) {
                return DOUBLE;
            } else if (type == float.class) {
                return FLOAT;
            } else if (type == byte.class) {
                return BYTE;
            } else if (type == char.class) {
                return CHAR;
            } else if (type == short.class) {
                return SHORT;
            } else {
                return VOID;
            }
        }

        if (type.isArray()) {
            return new Array(from(type.componentType()));
        }

        if (type.getPackageName().equals("java.lang")) {
            // Special type for detecting unbox types.
            return new JavaLang(type);
        }

        return new Clazz(type);
    }

    static String makeDescriptor(Type returnType, List<Type> paramTypes) {
        var b = new StringBuilder().append('(');
        for (var type : paramTypes) {
            b.append(type.descriptor());
        }
        return b.append(')').append(returnType.descriptor()).toString();
    }

    static String makeDescriptor(Type returnType, Type[] paramTypes) {
        var b = new StringBuilder().append('(');
        for (var type : paramTypes) {
            b.append(type.descriptor());
        }
        return b.append(')').append(returnType.descriptor()).toString();
    }

    /**
     * Returns true if type is an int, boolean, double, etc.
     */
    abstract boolean isPrimitive();

    /**
     * Returns true if type is an array, an interface, or a class.
     */
    final boolean isObject() {
        return !isPrimitive();
    }

    /**
     * Returns true if type is known to be an interface.
     */
    abstract boolean isInterface();

    /**
     * Returns true if type is an object or an interface, but not an array.
     */
    abstract boolean isClass();

    /**
     * Returns true if type is an array and has no methods.
     */
    abstract boolean isArray();

    /**
     * @return null if not an array
     */
    abstract Type elementType();

    /**
     * @return 0 if not an array
     */
    int dimensions() {
        int dims = 0;
        for (Type type = elementType(); type != null; type = type.elementType()) {
            dims++;
        }
        return dims;
    }

    /**
     * Returns the type code used by the stack map table attribute.
     *
     * @return SM_*
     */
    abstract int stackMapCode();

    /**
     * @return T_*
     */
    abstract int typeCode();

    /**
     * Returns the type in Java syntax. If not applicable, it starts with an asterisk and is
     * illegal.
     */
    abstract String name();

    /**
     * Returns a class file type descriptor. If not applicable, it starts with an asterisk
     * and is illegal.
     */
    abstract String descriptor();

    /**
     * Returns an object type for a primitive type.
     */
    abstract Type box();

    /**
     * Returns a primitive type for an object type. Returns null if not applicable.
     */
    abstract Type unbox();

    /**
     * @return T_* or T_OBJECT
     */
    int unboxTypeCode() {
        Type t = unbox();
        return t == null ? T_OBJECT : t.typeCode();
    }

    /**
     * Returns true if assignment is allowed without any conversion.
     */
    abstract boolean isAssignableFrom(Type other);

    /**
     * Checks if a type can be converted without losing information. Lower codes have a cheaper
     * conversion cost.
     *
     *      0: Equal types.
     *   1..4: Primitive to wider primitive type (strict).
     *      5: Primitive to specific boxed instance.
     *   6..9: Primitive to converted boxed instance (wider type, Number, or Object).
     *      0: Specific instance to superclass or implemented interface (no-op cast)
     * 10..14: Reboxing to wider object type (NPE isn't possible, code 10 isn't really used).
     *     15: Unboxing to specific primitive type (NPE is possible).
     * 16..19: Unboxing to wider primitive type (NPE is possible).
     *    max: Disallowed.
     *
     * @return conversion code, which is max value if disallowed
     */
    int canConvertTo(Type to) {
        if (this.equals(to)) {
            return 0;
        }

        if (this.isPrimitive()) {
            if (to.isPrimitive()) {
                switch (this.typeCode()) {
                case T_BYTE:
                    switch (to.typeCode()) {
                    case T_SHORT:
                    case T_INT:    return 0;
                    case T_LONG:   return 1; // I2L
                    case T_FLOAT:  return 2; // I2F
                    case T_DOUBLE: return 3; // I2D
                    }
                    return Integer.MAX_VALUE;
                case T_CHAR: case T_SHORT:
                    switch (to.typeCode()) {
                    case T_INT:    return 0;
                    case T_LONG:   return 1; // I2L
                    case T_FLOAT:  return 2; // I2F
                    case T_DOUBLE: return 3; // I2D
                    }
                    return Integer.MAX_VALUE;
                case T_INT:
                    switch (to.typeCode()) {
                    case T_LONG:   return 1; // I2L
                    case T_DOUBLE: return 3; // I2D
                    }
                    return Integer.MAX_VALUE;
                case T_FLOAT:
                    return to != DOUBLE ? Integer.MAX_VALUE : 4; // F2D
                }

                return Integer.MAX_VALUE;
            }

            Type toUnboxed = to.unbox();
            if (toUnboxed != null) {
                int code = this.canConvertTo(toUnboxed);
                if (code != Integer.MAX_VALUE) {
                    // 5: Simple boxing, 6..9: Convert then box.
                    code += 5;
                }
                return code;
            }

            if (to.isAssignableFrom(from(Number.class))) {
                return 5; // Simple boxing.
            }

            return Integer.MAX_VALUE;
        }

        // This point is reached when converting from an object.

        if (to.isObject() && to.isAssignableFrom(this)) {
            return 0;
        }

        Type thisUnboxed, toUnboxed;
        if ((thisUnboxed = this.unbox()) == null || (toUnboxed = to.unbox()) == null) {
            return Integer.MAX_VALUE;
        }

        // This point is reached when converting boxed primitives.

        // Expect 0..4 or max
        int code = thisUnboxed.canConvertTo(toUnboxed);

        if (code != Integer.MAX_VALUE) {
            code += to.isObject() ? 10 : 15;
        }

        return code;
    }

    /**
     * Returns null if no matching class is found.
     */
    abstract Class clazz();

    /**
     * Returns null if not applicable or unknown.
     */
    Type superType() {
        return null;
    }

    /**
     * Returns null if not applicable or unknown.
     */
    Set<Type> interfaces() {
        return null;
    }

    /**
     * Resets the known inherited members, forcing them to be checked again later. Is only
     * useful for types returned from the begin method.
     */
    void resetInherited() {
    }

    /**
     * Set the type as being an interface, which is only applicable to types returned from the
     * begin method.
     */
    void toInterface() {
    }

    /**
     * Returns all fields declared in this type, never null.
     */
    Map<String, Field> fields() {
        return Collections.emptyMap();
    }

    /**
     * Tries to find a field in this type or in a super type.
     */
    Field findField(String name) {
        Type type = this;
        do {
            Field field = type.fields().get(name);
            if (field != null) {
                return field;
            }
        } while ((type = type.superType()) != null);
        return null;
    }

    /**
     * @throws IllegalStateException if type cannot have fields or if a conflict exists
     */
    Field defineField(boolean isStatic, Type type, String name) {
        throw new IllegalStateException();
    }

    Field inventField(boolean isStatic, Type type, String name) {
        throw new IllegalStateException();
    }

    /**
     * Returns all methods and constructors declared in this type, never null.
     */
    Map<MethodKey, Method> methods() {
        return Collections.emptyMap();
    }

    /**
     * Returns all the best candidate methods that match the given criteria. Parameter type
     * conversion might be required to call any of the methods.
     *
     * @param inherit -1: cannot invoke inherited method, 0: can invoke inherited method,
     * 1: can only invoke super class method
     * @param staticAllowed -1: not static, 0: maybe static, 1: only static
     * @param specificReturnType optional
     * @param specificParamTypes optional
     * @return all matching results
     */
    Set<Method> findMethods(String methodName, Type[] params, int inherit, int staticAllowed,
                            Type specificReturnType, Type[] specificParamTypes)
    {
        return Collections.emptySet();
    }

    /**
     * @throws IllegalStateException if type cannot have methods or if a conflict exists
     */
    Method defineMethod(boolean isStatic, Type returnType, String name, Type... paramTypes) {
        throw new IllegalStateException();
    }

    Method inventMethod(boolean isStatic, Type returnType, String name, Type... paramTypes) {
        throw new IllegalStateException();
    }

    /**
     * @return null if not found
     */
    Method findMethod(String name, Type... paramTypes) {
        return null;
    }

    @Override
    public String toString() {
        return "Type {name=" + name() + ", descriptor=" + descriptor() +
            ", isPrimitive=" + isPrimitive() + ", isInterface=" + isInterface() +
            ", isClass=" + isClass() + ", isArray=" + isArray() +
            ", elementType=" + toString(elementType()) +
            ", stackMapCode=" + stackMapCode() + ", typeCode=" + typeCode() +
            ", box=" + toString(box()) + ", unbox=" + toString(unbox()) +
            '}';
    }

    private String toString(Type type) {
        return type == null ? null : type.name();
    }

    /**
     * Base class for Field and Method.
     */
    abstract class Member {
        private boolean mIsStatic;
        private final String mName;

        Member(boolean isStatic, String name) {
            Objects.requireNonNull(name);
            mIsStatic = isStatic;
            mName = name;
        }

        final Type enclosingType() {
            return Type.this;
        }

        final boolean isStatic() {
            return mIsStatic;
        }

        final void toStatic() {
            mIsStatic = true;
        }

        final String name() {
            return mName;
        }

        @Override
        public int hashCode() {
            return name().hashCode();
        }
    }

    final class Field extends Member {
        private final Type mType;

        Field(boolean isStatic, Type type, String name) {
            super(isStatic, name);
            Objects.requireNonNull(type);
            mType = type;
        }

        Type type() {
            return mType;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof Field) {
                var other = (Field) obj;
                return name().equals(other.name())
                    && type().equals(other.type())
                    && isStatic() == other.isStatic();
            }
            return false;
        }

        @Override
        public String toString() {
            return "Field {name=" + name() + ", type=" + type().name() +
                ", isStatic=" + isStatic() + ", enclosingType=" + enclosingType().name() + '}';
        }
    }

    final class Method extends Member {
        private final boolean mIsBridge;
        private final Type mReturnType;
        private final Type[] mParamTypes;

        private int mHash;

        private volatile String mDesc;

        Method(boolean isStatic, boolean isBridge,
               Type returnType, String name, Type... paramTypes)
        {
            super(isStatic, name);
            Objects.requireNonNull(returnType);
            Objects.requireNonNull(paramTypes);
            mIsBridge = isBridge;
            mReturnType = returnType;
            mParamTypes = paramTypes;
        }

        boolean isBridge() {
            return mIsBridge;
        }

        Type returnType() {
            return mReturnType;
        }

        Type[] paramTypes() {
            return mParamTypes;
        }

        String descriptor() {
            String desc = mDesc;
            if (desc == null) {
                mDesc = desc = makeDescriptor(mReturnType, mParamTypes);
            }
            return desc;
        }

        @Override
        public int hashCode() {
            int hash = mHash;
            if (hash == 0) {
                hash = name().hashCode();
                hash = hash * 31 + mReturnType.hashCode();
                hash = hash * 31 + Arrays.hashCode(mParamTypes);
                mHash = hash;
            }
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof Method) {
                var other = (Method) obj;
                return name().equals(other.name())
                    && returnType().equals(other.returnType())
                    && isStatic() == other.isStatic()
                    && Arrays.equals(mParamTypes, other.mParamTypes);
            }
            return false;
        }

        @Override
        public String toString() {
            return "Method {name=" + name() + ", returnType=" + returnType().name() +
                ", paramTypes=" + paramsToString() +
                ", isStatic=" + isStatic() + ", isBridge=" + isBridge() +
                ", enclosingType=" + enclosingType().name() + '}';
        }

        private String paramsToString() {
            var b = new StringBuilder().append('[');
            for (int i=0; i<mParamTypes.length; i++) {
                if (i > 0) {
                    b.append(", ");
                }
                b.append(mParamTypes[i].name());
            }
            return b.append(']').toString();
        }
    }

    static final class MethodKey {
        final Type returnType;
        final String name;
        final Type[] paramTypes;

        MethodKey(Type returnType, String name, Type... paramTypes) {
            this.returnType = returnType;
            this.name = name;
            this.paramTypes = paramTypes;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof MethodKey) {
                var other = (MethodKey) obj;
                return name.equals(other.name)
                    && returnType.equals(other.returnType)
                    && Arrays.equals(paramTypes, other.paramTypes);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return name.hashCode() * 31 + Arrays.hashCode(paramTypes);
        }
    }

    private static final WeakHashMap<ClassLoader, WeakReference<ConcurrentHashMap<Object, Type>>>
        cCacheMap = new WeakHashMap<>();

    private static synchronized ConcurrentHashMap<Object, Type> cache(ClassLoader loader) {
        WeakReference<ConcurrentHashMap<Object, Type>> cacheRef = cCacheMap.get(loader);
        ConcurrentHashMap<Object, Type> cache;
        if (cacheRef == null || (cache = cacheRef.get()) == null) {
            cache = new ConcurrentHashMap<>();
            cCacheMap.put(loader, new WeakReference<>(cache));
        }
        return cache;
    }

    private static Type cachePut(ConcurrentHashMap<Object, Type> cache, Object key, Type type) {
        Type existing = cache.putIfAbsent(key, type);
        return existing == null ? type : existing;
    }

    private static final class Primitive extends Type {
        private final int mStackMapCode, mTypeCode;
        private volatile Type mBox;

        private Primitive(int stackMapCode, int typeCode) {
            mStackMapCode = stackMapCode;
            mTypeCode = typeCode;
        }

        @Override
        boolean isPrimitive() {
            return true;
        }

        @Override
        boolean isInterface() {
            return false;
        }

        @Override
        boolean isClass() {
            return false;
        }

        @Override
        boolean isArray() {
            return false;
        }

        @Override
        Type elementType() {
            return null;
        }

        @Override
        int stackMapCode() {
            return mStackMapCode;
        }

        @Override
        int typeCode() {
            return mTypeCode;
        }

        @Override
        String name() {
            switch (mTypeCode) {
            default:        return "void";
            case T_BOOLEAN: return "boolean";
            case T_BYTE:    return "byte";
            case T_CHAR:    return "char";
            case T_SHORT:   return "short";
            case T_INT:     return "int";
            case T_FLOAT:   return "float";
            case T_LONG:    return "long";
            case T_DOUBLE:  return "double";
            }
        }

        @Override
        String descriptor() {
            switch (mTypeCode) {
            default:        return "V";
            case T_BOOLEAN: return "Z";
            case T_BYTE:    return "B";
            case T_CHAR:    return "C";
            case T_SHORT:   return "S";
            case T_INT:     return "I";
            case T_FLOAT:   return "F";
            case T_LONG:    return "J";
            case T_DOUBLE:  return "D";
            }
        }

        @Override
        Type box() {
            Type box = mBox;

            if (box == null) {
                switch (mTypeCode) {
                default:        box = from(Void.class); break;
                case T_BOOLEAN: box = from(Boolean.class); break;
                case T_BYTE:    box = from(Byte.class); break;
                case T_CHAR:    box = from(Character.class); break;
                case T_SHORT:   box = from(Short.class); break;
                case T_INT:     box = from(Integer.class); break;
                case T_FLOAT:   box = from(Float.class); break;
                case T_LONG:    box = from(Long.class); break;
                case T_DOUBLE:  box = from(Double.class); break;
                }

                mBox = box;
            }

            return box;
        }

        @Override
        Type unbox() {
            return this;
        }

        @Override
        boolean isAssignableFrom(Type other) {
            return this.equals(other);
        }

        @Override
        Class clazz() {
            switch (mTypeCode) {
            default:        return void.class;
            case T_BOOLEAN: return boolean.class;
            case T_BYTE:    return byte.class;
            case T_CHAR:    return char.class;
            case T_SHORT:   return short.class;
            case T_INT:     return int.class;
            case T_FLOAT:   return float.class;
            case T_LONG:    return long.class;
            case T_DOUBLE:  return double.class;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Primitive) {
                return mTypeCode == ((Primitive) obj).mTypeCode;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return mTypeCode;
        }
    }

    private static final class Special extends Type {
        private final int mStackMapCode;

        private Special(int stackMapCode) {
            mStackMapCode = stackMapCode;
        }

        @Override
        boolean isPrimitive() {
            return false;
        }

        @Override
        boolean isInterface() {
            return false;
        }

        @Override
        boolean isClass() {
            return false;
        }

        @Override
        boolean isArray() {
            return false;
        }

        @Override
        Type elementType() {
            return null;
        }

        @Override
        int stackMapCode() {
            return mStackMapCode;
        }

        @Override
        int typeCode() {
            return T_OBJECT;
        }

        @Override
        String name() {
            switch (mStackMapCode) {
            default:             return "*null*";
            case SM_UNINIT_THIS: return "*uninit_this*";
            case SM_UNINIT:      return "*uninit*";
            }
        }

        @Override
        String descriptor() {
            return name();
        }

        @Override
        Type box() {
            return this;
        }

        @Override
        Type unbox() {
            return null;
        }

        @Override
        boolean isAssignableFrom(Type other) {
            return this.equals(other);
        }

        @Override
        Class clazz() {
            return null;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Special) {
                return mStackMapCode == ((Special) obj).mStackMapCode;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return mStackMapCode;
        }
    }

    private static abstract class Obj extends Type {
        @Override
        final boolean isPrimitive() {
            return false;
        }

        @Override
        final int stackMapCode() {
            return SM_OBJECT;
        }

        @Override
        final int typeCode() {
            return T_OBJECT;
        }

        @Override
        final Type box() {
            return this;
        }
    }

    private static final class Array extends Obj {
        private final Type mElementType;

        private volatile String mName;
        private volatile String mDesc;

        private volatile Class mClass;

        private Array(Type elementType) {
            mElementType = elementType;
        }

        @Override
        boolean isInterface() {
            return false;
        }

        @Override
        boolean isClass() {
            return false;
        }

        @Override
        boolean isArray() {
            return true;
        }

        @Override
        Type elementType() {
            return mElementType;
        }

        @Override
        String name() {
            String name = mName;
            if (name == null) {
                mName = name = mElementType.name() + "[]";
            }
            return name;
        }

        @Override
        String descriptor() {
            String desc = mDesc;
            if (desc == null) {
                mDesc = desc = '[' + mElementType.descriptor();
            }
            return desc;
        }

        @Override
        Type unbox() {
            return null;
        }

        @Override
        boolean isAssignableFrom(Type other) {
            return other == NULL || this.equals(other) ||
                (other.isArray() && elementType().isAssignableFrom(other.elementType()));
        }

        @Override
        Class clazz() {
            Class clazz = mClass;
            if (clazz == null) {
                Class elementClass = mElementType.clazz();
                if (elementClass != null) {
                    mClass = clazz = elementClass.arrayType();
                }
            }
            return clazz;
        }

        @Override
        Type superType() {
            return from(Object.class);
        }

        @Override
        Set<Method> findMethods(String methodName, Type[] params, int inherit, int staticAllowed,
                                Type specificReturnType, Type[] specificParamTypes)
        {
            return superType().findMethods(methodName, params, inherit, staticAllowed,
                                           specificReturnType, specificParamTypes);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof Array) {
                return mElementType.equals(((Array) obj).mElementType);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return mElementType.hashCode() * 31;
        }
    }

    private static class Clazz extends Obj {
        private final ClassLoader mLoader;
        private volatile Class mClass;
        private volatile String mName;
        private volatile String mDesc;
        protected volatile Boolean mIsInterface;

        protected volatile Type mSuperType;
        protected volatile Set<Type> mInterfaces;

        private volatile ConcurrentHashMap<String, Field> mFields;

        private volatile ConcurrentHashMap<MethodKey, Method> mMethods;

        Clazz(Class clazz) {
            this(clazz.getClassLoader(), clazz.getName(), null, clazz.isInterface());
            mClass = clazz;
        }

        /**
         * @param name can be null if desc isn't null
         * @param desc can be null if name isn't null
         */
        Clazz(ClassLoader loader, String name, String desc, Boolean isInterface) {
            mLoader = loader;
            mName = name;
            mDesc = desc;
            mIsInterface = isInterface;
        }

        @Override
        final boolean isInterface() {
            Boolean is = mIsInterface;
            if (is == null) {
                Class clazz = clazz();
                mIsInterface = is = clazz == null ? false : clazz.isInterface();
            }
            return is;
        }

        @Override
        final boolean isClass() {
            return true;
        }

        @Override
        final boolean isArray() {
            return false;
        }

        @Override
        final Type elementType() {
            return null;
        }

        @Override
        final String name() {
            String name = mName;
            if (name == null) {
                String desc = mDesc;
                int end = desc.length();
                if (desc.charAt(end - 1) == ';') {
                    end--;
                }
                mName = name = desc.substring(1, end).replace('/', '.');
            }
            return name;
        }

        @Override
        final String descriptor() {
            String desc = mDesc;
            if (desc == null) {
                mDesc = desc = 'L' + mName.replace('.', '/') + ';';
            }
            return desc;
        }

        @Override
        Type unbox() {
            return null;
        }

        @Override
        boolean isAssignableFrom(Type other) {
            // TODO: Cache the result?

            if (other == NULL || this.equals(other)) {
                return true;
            }

            Class<?> thisClass = clazz();
            Class<?> otherClass = other.clazz();

            if (thisClass != null && otherClass != null) {
                return thisClass.isAssignableFrom(otherClass);
            }

            if (other.isPrimitive()) {
                return false;
            }

            if (thisClass == Object.class) {
                return true;
            }

            Type otherSuperType = other.superType();
            if (otherSuperType != null && isAssignableFrom(otherSuperType)) {
                return true;
            }

            for (Type iface : other.interfaces()) {
                if (isAssignableFrom(iface)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        Class clazz() {
            Class clazz = mClass;
            if (clazz == null) {
                try {
                    mClass = clazz = Class.forName(name(), true, mLoader);
                } catch (ClassNotFoundException | LinkageError e) {
                    // Ignore.
                }
            }
            return clazz;
        }

        @Override
        Type superType() {
            Type superType = mSuperType;
            if (superType == null) {
                Class clazz = clazz();
                if (clazz != null) {
                    clazz = clazz.getSuperclass();
                    if (clazz != null) {
                        mSuperType = superType = from(clazz);
                    }
                }
            }
            return superType;
        }

        @Override
        Set<Type> interfaces() {
            Set<Type> interfaces = mInterfaces;
            if (interfaces == null) {
                synchronized (this) {
                    interfaces = mInterfaces;
                    if (interfaces == null) {
                        Class clazz = clazz();
                        if (clazz != null) {
                            Set<Type> all = allInterfaces(null, clazz);
                            interfaces = all == null ? Collections.emptySet() : all;
                            mInterfaces = interfaces;
                        }
                    }
                }
            }
            return interfaces;
        }

        private static Set<Type> allInterfaces(Set<Type> all, Class clazz) {
            Class[] interfaces = clazz.getInterfaces();

            if (interfaces != null && interfaces.length != 0) {
                if (all == null) {
                    all = new LinkedHashSet<>(1);
                }
                for (Class iface : interfaces) {
                    all.add(Type.from(iface));
                }
                for (Class iface : interfaces) {
                    all = allInterfaces(all, iface);
                }
            }

            Class superclass = clazz.getSuperclass();
            if (superclass != null) {
                all = allInterfaces(all, superclass);
            }

            return all;
        }

        @Override
        Map<String, Field> fields() {
            Map<String, Field> fields = mFields;
            if (fields == null) {
                synchronized (this) {
                    fields = mFields;
                    if (fields == null) {
                        fields = initFields();
                    }
                }
            }
            return fields;
        }

        @Override
        Field defineField(boolean isStatic, Type type, String name) {
            return defineField(false, isStatic, type, name);
        }

        @Override
        Field inventField(boolean isStatic, Type type, String name) {
            return defineField(true, isStatic, type, name);
        }

        private Field defineField(boolean invent, boolean isStatic, Type type, String name) {
            var field = new Field(isStatic, type, name);

            Field existing;
            synchronized (this) {
                Map<String, Field> fields = mFields;
                if (fields == null) {
                    fields = initFields();
                }
                existing = fields.get(name);
                if (field.equals(existing)) {
                    return existing;
                }
                if (existing == null) {
                    if (!invent) {
                        fields.put(name, field);
                    }
                    return field;
                }
            }

            if (invent) {
                return existing;
            }

            throw new IllegalStateException("Conflicting field exists: " + existing);
        }

        private Map<String, Field> initFields() {
            var fields = new ConcurrentHashMap<String, Field>();

            Class clazz = clazz();
            if (clazz != null) {
                for (var field : clazz.getDeclaredFields()) {
                    boolean isStatic = Modifier.isStatic(field.getModifiers());
                    String name = field.getName();
                    Type type = from(field.getType());
                    fields.put(name, new Field(isStatic, type, name));
                }
            }

            return mFields = fields;
        }

        @Override
        Map<MethodKey, Method> methods() {
            Map<MethodKey, Method> methods = mMethods;
            if (methods == null) {
                synchronized (this) {
                    methods = mMethods;
                    if (methods == null) {
                        methods = initMethods();
                    }
                }
            }
            return methods;
        }

        @Override
        Set<Method> findMethods(String methodName, Type[] params, int inherit, int staticAllowed,
                                Type specificReturnType, Type[] specificParamTypes)
        {
            Type type = this;

            if (inherit > 0) {
                type = type.superType();
                if (type == null) {
                    return Collections.emptySet();
                }
            }

            return findMethods(type, methodName, params, inherit, staticAllowed,
                               specificReturnType, specificParamTypes);
        }

        private static Set<Method> findMethods(Type type, String methodName,
                                               Type[] params, int inherit, int staticAllowed,
                                               Type specificReturnType,
                                               Type[] specificParamTypes)
        {
            // TODO: Cache the results.

            var methods = new LinkedHashSet<Method>(4);
            addMethods(methods, type, methodName, params, staticAllowed);

            if (inherit >= 0) {
                Type superType = type.superType();
                while (superType != null) {
                    addMethods(methods, superType, methodName, params, staticAllowed);
                    superType = superType.superType();
                }
            }

            if (inherit == 0) {
                Set<Type> interfaces = type.interfaces();
                if (interfaces != null) {
                    for (Type iface : interfaces) {
                        addMethods(methods, iface, methodName, params, staticAllowed);
                    }
                }
            }

            if (methods.size() > 1) {
                Iterator<Method> it = methods.iterator();
                Method best = it.next();
                var bestSet = new LinkedHashSet<Method>(1);
                bestSet.add(best);

                while (it.hasNext()) {
                    Method candidate = it.next();
                    int cmp = Candidate.compare(params, best, candidate);
                    if (cmp >= 0) {
                        if (cmp > 0) {
                            best = candidate;
                            bestSet.clear();
                            bestSet.add(best);
                        } else {
                            bestSet.add(candidate);
                        }
                    }
                }

                methods = bestSet;
            }

            if (methods.size() > 1 && specificReturnType != null) {
                Iterator<Method> it = methods.iterator();
                while (it.hasNext()) {
                    if (!specificReturnType.equals(it.next().returnType())) {
                        it.remove();
                    }
                }
            }

            if (methods.size() > 1 && specificParamTypes != null) {
                Iterator<Method> it = methods.iterator();
                while (it.hasNext()) {
                    if (!Arrays.equals(specificParamTypes, it.next().paramTypes())) {
                        it.remove();
                    }
                }
            }

            if (methods.size() > 1) {
                // If any non-bridge methods, remove the bridge methods.
                int nonBridges = 0, bridges = 0;
                for (Method m : methods) {
                    if (m.isBridge()) {
                        bridges++;
                    } else {
                        nonBridges++;
                    }
                }
                if (nonBridges > 0 && bridges > 0) {
                    Iterator<Method> it = methods.iterator();
                    while (it.hasNext()) {
                        if (it.next().isBridge()) {
                            it.remove();
                        }
                    }
                }
            }

            return methods;
        }

        private static void addMethods(Set<Method> methods, Type type, String methodName,
                                       Type[] params, int staticAllowed)
        {
            outer: for (Method m : type.methods().values()) {
                Type[] actualParams;
                if (!m.name().equals(methodName) ||
                    (actualParams = m.paramTypes()).length != params.length)
                {
                    continue;
                }

                if (m.isStatic()) {
                    if (staticAllowed < 0) {
                        continue;
                    }
                } else if (staticAllowed > 0) {
                    continue;
                }

                for (int i=0; i<params.length; i++) {
                    if (params[i].canConvertTo(actualParams[i]) == Integer.MAX_VALUE) {
                        continue outer;
                    }
                }

                methods.add(m);
            }
        }

        @Override
        Method defineMethod(boolean isStatic, Type returnType, String name, Type... paramTypes) {
            return defineMethod(false, isStatic, returnType, name, paramTypes);
        }

        @Override
        Method inventMethod(boolean isStatic, Type returnType, String name, Type... paramTypes) {
            return defineMethod(true, isStatic, returnType, name, paramTypes);
        }

        private Method defineMethod(boolean invent, boolean isStatic,
                                    Type returnType, String name, Type... paramTypes)
        {
            var key = new MethodKey(returnType, name, paramTypes);
            var method = new Method(isStatic, false, returnType, name, paramTypes);

            Method existing;
            synchronized (this) {
                Map<MethodKey, Method> methods = mMethods;
                if (methods == null) {
                    methods = initMethods();
                }
                existing = methods.get(key);
                if (method.equals(existing)) {
                    return existing;
                }
                if (existing == null) {
                    if (!invent) {
                        methods.put(key, method);
                    }
                    return method;
                }
            }

            if (invent) {
                return existing;
            }

            throw new IllegalStateException("Conflicting method exists: " + existing);
        }

        private Map<MethodKey, Method> initMethods() {
            var methods = new ConcurrentHashMap<MethodKey, Method>();

            Class clazz = clazz();
            if (clazz != null) {
                for (var method : clazz.getDeclaredMethods()) {
                    addMethod(methods, method.getName(), method, from(method.getReturnType()));
                }
                for (var ctor : clazz.getDeclaredConstructors()) {
                    addMethod(methods, "<init>", ctor, VOID);
                }
            }

            return mMethods = methods;
        }

        private void addMethod(Map<MethodKey, Method> methods,
                               String name, Executable method, Type returnType)
        {
            boolean isBridge = Modifier.isVolatile(method.getModifiers());
            boolean isStatic = Modifier.isStatic(method.getModifiers());

            Class<?>[] params = method.getParameterTypes();
            var paramTypes = new Type[params.length];
            for (int i=0; i<params.length; i++) {
                paramTypes[i] = from(params[i]);
            }

            var key = new MethodKey(returnType, name, paramTypes);
            methods.put(key, new Method(isStatic, isBridge, returnType, name, paramTypes));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof Clazz) {
                return name().equals(((Clazz) obj).name());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return name().hashCode();
        }
    }

    private static class JavaLang extends Clazz {
        private volatile Type mUnbox;

        JavaLang(Class clazz) {
            super(clazz);
        }

        @Override
        Type unbox() {
            Type unbox = mUnbox;

            if (unbox == null) {
                switch (name().substring(10)) {
                case "Boolean":   unbox = BOOLEAN; break;
                case "Byte":      unbox = BYTE; break;
                case "Short":     unbox = SHORT; break;
                case "Character": unbox = CHAR; break;
                case "Integer":   unbox = INT; break;
                case "Float":     unbox = FLOAT; break;
                case "Double":    unbox = DOUBLE; break;
                case "Long":      unbox = LONG; break;
                case "Void":      unbox = VOID; break;
                }

                mUnbox = unbox;
            }

            return unbox;
        }
    }

    private static class NewClazz extends Clazz {
        final TheClassMaker mMaker;

        NewClazz(ClassLoader loader, TheClassMaker maker, String name) {
            super(loader, name, null, false);
            mMaker = maker;
        }

        @Override
        Class clazz() {
            // It doesn't exist yet, so don't try loading it. Doing so causes the ClassLoader
            // to allocate a lock object, and it might never be reclaimed.
            return null;
        }

        @Override
        Type superType() {
            Type superType = mSuperType;
            if (superType == null) {
                mSuperType = superType = mMaker.superType();
            }
            return superType;
        }

        @Override
        Set<Type> interfaces() {
            Set<Type> interfaces = mInterfaces;
            if (interfaces == null) {
                synchronized (this) {
                    interfaces = mInterfaces;
                    if (interfaces == null) {
                        Set<Type> all = mMaker.allInterfaces(null);
                        interfaces = all == null ? Collections.emptySet() : all;
                        mInterfaces = interfaces;
                    }
                }
            }
            return interfaces;
        }

        @Override
        void resetInherited() {
            mSuperType = null;
            mInterfaces = null;
        }

        @Override
        void toInterface() {
            mIsInterface = true;
        }
    }
}
