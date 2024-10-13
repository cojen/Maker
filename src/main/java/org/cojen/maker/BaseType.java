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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
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
abstract class BaseType implements Type, Typed {
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

    static final BaseType
        BOOLEAN = new Primitive(SM_INT, T_BOOLEAN),
        BYTE = new Primitive(SM_INT, T_BYTE),
        SHORT = new Primitive(SM_INT, T_SHORT),
        CHAR = new Primitive(SM_INT, T_CHAR),
        INT = new Primitive(SM_INT, T_INT),
        FLOAT = new Primitive(SM_FLOAT, T_FLOAT),
        LONG = new Primitive(SM_LONG, T_LONG),
        DOUBLE = new Primitive(SM_DOUBLE, T_DOUBLE),
        VOID = new Primitive(SM_TOP, T_VOID);

    static final int
        FLAG_PUBLIC = Modifier.PUBLIC,
        FLAG_PRIVATE = Modifier.PRIVATE,
        FLAG_PROTECTED = Modifier.PROTECTED,
        FLAG_STATIC = Modifier.STATIC,
        FLAG_FINAL = Modifier.FINAL,
        FLAG_BRIDGE = 0x40,
        FLAG_VARARGS = 0x80;

    static final Object[] NO_ARGS = new Object[0];

    /**
     * Called when making a new class.
     */
    static BaseType begin(ClassLoader loader, TheClassMaker maker, String name) {
        return new NewClazz(loader, maker, name);
    }

    static BaseType from(ClassLoader loader, Object type) {
        if (type instanceof Class clazz) {
            return from(clazz);
        } else if (type instanceof Typed typed) {
            return typed.type();
        } else if (type instanceof String str) {
            return from(loader, str);
        } else if (type == null) {
            throw new NullPointerException();
        } else {
            String desc = ConstableSupport.toTypeDescriptor(type);
            if (desc == null) {
                throw new IllegalArgumentException("Unknown type: " + type);
            }
            return from(loader, desc);
        }
    }

    /**
     * @param type class name, primitive type, or type descriptor
     */
    static BaseType from(ClassLoader loader, String type) {
        ConcurrentHashMap<Object, BaseType> cache = cache(loader);
        BaseType t = cache.get(type);
        return t != null ? t : cachePut(cache, type, find(loader, type));
    }

    private static BaseType find(ClassLoader loader, String type) {
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
        case "": throw new IllegalArgumentException();
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

    static BaseType from(Class type) {
        ConcurrentHashMap<Object, BaseType> cache = cache(type.getClassLoader());
        BaseType t = cache.get(type);
        return t != null ? t : cachePut(cache, type, find(type));
    }

    private static BaseType find(Class type) {
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
            Array arr = new Array(from(type.getComponentType()));
            arr.mClass = type;
            return arr;
        }

        if (type.getPackageName().equals("java.lang")) {
            // Special type for detecting unbox types.
            return new JavaLang(type);
        }

        return new Clazz(type);
    }

    static String makeDescriptor(BaseType returnType, List<BaseType> paramTypes) {
        var b = new StringBuilder().append('(');
        for (var type : paramTypes) {
            b.append(type.descriptor());
        }
        return b.append(')').append(returnType.descriptor()).toString();
    }

    static String makeDescriptor(BaseType returnType, BaseType[] paramTypes) {
        var b = new StringBuilder().append('(');
        for (var type : paramTypes) {
            b.append(type.descriptor());
        }
        return b.append(')').append(returnType.descriptor()).toString();
    }

    @Override
    public final BaseType type() {
        return this;
    }

    @Override
    public final boolean isObject() {
        return !isPrimitive();
    }

    @Override
    public abstract BaseType elementType();

    @Override
    public final int dimensions() {
        int dims = 0;
        for (BaseType type = elementType(); type != null; type = type.elementType()) {
            dims++;
        }
        return dims;
    }

    @Override
    public BaseType asArray() {
        return new Array(this);
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

    @Override
    public abstract BaseType box();

    @Override
    public abstract BaseType unbox();

    /**
     * @return T_* or T_OBJECT
     */
    final int unboxTypeCode() {
        BaseType t = unbox();
        return t == null ? T_OBJECT : t.typeCode();
    }

    @Override
    public boolean isAnnotated() {
        return false;
    }

    @Override
    public Type annotatable() {
        return new AnnotatableType(this);
    }

    @Override
    public AnnotationMaker addAnnotation(Object annotationType, boolean visible) {
        throw new IllegalStateException();
    }

    /**
     * Returns true if assignment is allowed without any conversion.
     */
    abstract boolean isAssignableFrom(BaseType other);

    /**
     * Checks if a type can be converted without losing information. Lower codes have a cheaper
     * conversion cost.
     *
     *      0: Equal types.
     *   1..4: Primitive to wider primitive type (strict).
     *      5: Primitive to specific boxed instance.
     *   6..9: Primitive to converted boxed instance (wider type, Number, or Object).
     *      0: Specific instance to superclass or implemented interface (no-op cast)
     * 10..14: Reboxing to wider object type (NPE isn't possible).
     *     15: Unboxing to specific primitive type (NPE is possible).
     * 16..19: Unboxing to wider primitive type (NPE is possible).
     *    max: Disallowed.
     *
     * @return conversion code, which is max value if disallowed
     */
    final int canConvertTo(BaseType to) {
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

            BaseType toUnboxed = to.unbox();
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

        BaseType thisUnboxed, toUnboxed;
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

    @Override
    public ClassMaker makerType() {
        return null;
    }

    /**
     * If true is returned, then clazz isn't null.
     */
    boolean isHidden() {
        return false;
    }

    /**
     * Returns the nearest parent type which isn't hidden.
     */
    BaseType nonHiddenBase() {
        return this;
    }

    /**
     * Returns null if not applicable or unknown.
     */
    BaseType superType() {
        return null;
    }

    /**
     * Returns the set of all interfaces, or null if not applicable or unknown.
     */
    Set<BaseType> interfaces() {
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
    final Field findField(String name) {
        BaseType type = this;
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
    Field defineField(int flags, BaseType type, String name) {
        throw new IllegalStateException();
    }

    Field inventField(int flags, BaseType type, String name) {
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
    Set<Method> findMethods(String methodName, BaseType[] params, int inherit, int staticAllowed,
                            BaseType specificReturnType, BaseType[] specificParamTypes)
    {
        return Collections.emptySet();
    }

    /**
     * See comments for findMethods above.
     *
     * @throws IllegalStateException if not exactly one matching method is found
     */
    final Method findMethod(String methodName, BaseType[] params, int inherit, int staticAllowed,
                            BaseType specificReturnType, BaseType[] specificParamTypes)
    {
        Set<Method> candidates = findMethods
            (methodName, params, inherit, staticAllowed, specificReturnType, specificParamTypes);

        if (candidates.size() == 1) {
            Method method = candidates.iterator().next();

            // Check if a signature polymorphic method should be invented.
            if (!method.isStatic() && method.isVarargs()) {
                BaseType[] paramTypes;
                if (possiblySignaturePolymorphic(methodName)
                    && (paramTypes = verifyTypes(params, specificParamTypes)) != null)
                {
                    BaseType returnType = specificReturnType != null
                        ? specificReturnType : method.returnType();
                    method = inventMethod(0, returnType, methodName, paramTypes);
                }
            }

            return method;
        }

        if (candidates.isEmpty()) {
            // Check if a signature polymorphic method should be invented.
            if (specificReturnType != null && possiblySignaturePolymorphic(methodName)) {
                candidates = findMethods(methodName, params, -1, -1, null, null);
                if (candidates.size() == 1) {
                    Method method = candidates.iterator().next();
                    BaseType[] paramTypes;
                    if (method != null && method.isVarargs()
                        && (paramTypes = verifyTypes(params, specificParamTypes)) != null)
                    {
                        return inventMethod(0, specificReturnType, methodName, paramTypes);
                    }
                }
            }

            throw new IllegalStateException
                ("No matching methods found for: " + name() + '.' + methodName);
        }

        var b = new StringBuilder()
            .append("No best matching method found for: ")
            .append(name()).append('.').append(methodName)
            .append(". Remaining candidates: ");
        int amt = 0;
        for (BaseType.Method m : candidates) {
            if (amt > 0) {
                b.append(", ");
            }
            b.append(m);
            amt++;
        }

        throw new IllegalStateException(b.toString());
    }

    private boolean possiblySignaturePolymorphic(String methodName) {
        Class clazz = classType();
        return (clazz == MethodHandle.class && !methodName.equals("invokeWithArguments"))
            || clazz == VarHandle.class;
    }

    /**
     * Verifies that the param types can be assigned by the specific types (if provided).
     * Returns null if assignment isn't allowed, or else return the actual param types to use.
     */
    private static BaseType[] verifyTypes(BaseType[] params, BaseType[] specificParamTypes) {
        if (specificParamTypes != null && params.length == specificParamTypes.length) {
            for (int i=0; i<specificParamTypes.length; i++) {
                if (!specificParamTypes[i].isAssignableFrom(params[i])) {
                    return null;
                }
            }
            return specificParamTypes;
        }
        return params;
    }

    /**
     * @throws IllegalStateException if type cannot have methods or if a conflict exists
     */
    Method defineMethod(int flags, BaseType returnType, String name, BaseType... paramTypes) {
        throw new IllegalStateException();
    }

    Method inventMethod(int flags, BaseType returnType, String name, BaseType... paramTypes) {
        throw new IllegalStateException();
    }

    /**
     * Finds a common catch type, modifying the map as a side effect. New lists are added into
     * the map, and some map entries might be removed. Each list is the hierarchy for the type.
     */
    static BaseType commonCatchType(Map<BaseType, List<BaseType>> catchMap) {
        // For each catch type, fill up a list with the corresponding class hierarchy.

        int minPos = Integer.MIN_VALUE;
        for (var entry : catchMap.entrySet()) {
            var list = new ArrayList<BaseType>();
            var catchType = entry.getKey();
            do {
                list.add(catchType);
            } while ((catchType = catchType.superType()) != null);
            entry.setValue(list);
            minPos = Math.max(minPos, -list.size());
        }

        // Compare the type at the same level of each hierarchy. If all types are the same at a
        // given level, then that's a common type. Keep going to a lower level to find a more
        // specific common type.

        BaseType commonType = null;

        for (int pos = -1; pos >= minPos; pos--) {
            Iterator<Map.Entry<BaseType, List<BaseType>>> it = catchMap.entrySet().iterator();

            List<BaseType> list = it.next().getValue();
            BaseType levelType = list.get(list.size() + pos);

            while (it.hasNext()) {
                list = it.next().getValue();
                BaseType type = list.get(list.size() + pos);
                if (!levelType.equals(type)) {
                    return commonType;
                }
            }

            commonType = levelType;
        }

        // This point is reached when the common type is a specific catch type. All the other
        // catch types are subclasses, and so they don't need to be caught.

        Iterator<BaseType> it = catchMap.keySet().iterator();
        while (it.hasNext()) {
            BaseType type = it.next();
            if (type != commonType) {
                it.remove();
            }
        }

        return commonType;
    }

    @Override
    public final String toString() {
        return "Type {name=" + name() + ", descriptor=" + descriptor() +
            ", isPrimitive=" + isPrimitive() + ", isInterface=" + isInterface() +
            ", isArray=" + isArray() + ", elementType=" + toString(elementType()) +
            ", stackMapCode=" + stackMapCode() + ", typeCode=" + typeCode() +
            ", box=" + toString(box()) + ", unbox=" + toString(unbox()) +
            '}';
    }

    private String toString(BaseType type) {
        return type == null ? null : type.name();
    }

    /**
     * Base class for Field and Method.
     */
    abstract class Member {
        protected int mFlags;
        private final String mName;

        Member(int flags, String name) {
            Objects.requireNonNull(name);
            mFlags = flags;
            mName = name;
        }

        final BaseType enclosingType() {
            return BaseType.this;
        }

        final boolean isPrivate() {
            return (mFlags & FLAG_PRIVATE) != 0;
        }

        final void toPrivate() {
            mFlags |= FLAG_PRIVATE;
        }

        final boolean isPackagePrivate() {
            return (mFlags & (FLAG_PUBLIC | FLAG_PRIVATE | FLAG_PROTECTED)) == 0;
        }

        final boolean isStatic() {
            return (mFlags & FLAG_STATIC) != 0;
        }

        final void toStatic() {
            mFlags |= FLAG_STATIC;
        }

        final boolean isFinal() {
            return (mFlags & FLAG_FINAL) != 0;
        }

        final void toFinal() {
            mFlags |= FLAG_FINAL;
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
        private final BaseType mType;

        Field(int flags, BaseType type, String name) {
            super(flags, name);
            Objects.requireNonNull(type);
            mType = type;
        }

        BaseType type() {
            return mType;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof Field other
                && name().equals(other.name())
                && type().equals(other.type())
                && isStatic() == other.isStatic();
        }

        @Override
        public String toString() {
            return "Field {name=" + name() + ", type=" + type().name() +
                ", isPrivate=" + isPrivate() + ", isStatic=" + isStatic() +
                ", isFinal=" + isFinal() + ", enclosingType=" + enclosingType().name() + '}';
        }
    }

    final class Method extends Member {
        private final BaseType mReturnType;
        private final BaseType[] mParamTypes;

        private int mHash;

        private volatile String mDesc;

        Method(int flags, BaseType returnType, String name, BaseType... paramTypes) {
            super(flags, name);
            Objects.requireNonNull(returnType);
            Objects.requireNonNull(paramTypes);
            mReturnType = returnType;
            mParamTypes = paramTypes;
        }

        boolean isBridge() {
            return (mFlags & FLAG_BRIDGE) != 0;
        }

        void toBridge() {
            mFlags |= FLAG_BRIDGE;
        }

        boolean isVarargs() {
            return (mFlags & FLAG_VARARGS) != 0;
        }

        void toVarargs() {
            mFlags |= FLAG_VARARGS;
        }

        BaseType returnType() {
            return mReturnType;
        }

        BaseType[] paramTypes() {
            return mParamTypes;
        }

        String descriptor() {
            String desc = mDesc;
            if (desc == null) {
                mDesc = desc = makeDescriptor(mReturnType, mParamTypes);
            }
            return desc;
        }

        /**
         * Returns a string which matches how the method would appear in a Java source file,
         * sans parameter names.
         */
        String signature() {
            var b = new StringBuilder().append(returnType().name()).append(' ').append(name());
            appendParams(b);
            return b.toString();
        }

        /**
         * If the enclosingType of this method is hidden, attempt to find a parent overridable
         * method whose enclosingType isn't hidden. If the enclosingType of this method isn't
         * hidden, then this method is simply returned.
         */
        Method tryNonHidden() {
            final BaseType type = enclosingType();

            if (!type.isHidden()) {
                return this;
            }

            if ((mFlags & (FLAG_PRIVATE | FLAG_STATIC)) != 0 || "<init>".equals(name())) {
                return null;
            }

            final var key = new MethodKey(returnType(), name(), paramTypes());

            for (BaseType s = type.superType(); s != null; s = s.superType()) {
                Method parent = s.methods().get(key);
                if (parent != null && parent.allowHiddenOverride()) {
                    return parent;
                }
            }

            for (BaseType iface : type.interfaces()) {
                Method parent = iface.methods().get(key);
                if (parent != null && parent.allowHiddenOverride()) {
                    return parent;
                }
            }

            return null;
        }

        private boolean allowHiddenOverride() {
            // Note that package-private overrides are simply rejected. A proper check requires
            // that both classes be in the same package and ClassLoader.
            return
                (mFlags & (FLAG_PRIVATE | FLAG_STATIC | FLAG_FINAL)) == 0 &&
                !isPackagePrivate() && !enclosingType().isHidden();
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
            return this == obj || obj instanceof Method other
                && name().equals(other.name())
                && returnType().equals(other.returnType())
                && isStatic() == other.isStatic()
                && Arrays.equals(mParamTypes, other.mParamTypes);
        }

        @Override
        public String toString() {
            return "Method {name=" + name() + ", returnType=" + returnType().name() +
                ", paramTypes=" + paramsToString() + ", isPrivate=" + isPrivate() +
                ", isStatic=" + isStatic() + ", isFinal=" + isFinal() +
                ", isBridge=" + isBridge() + ", enclosingType=" + enclosingType().name() + '}';
        }

        private String paramsToString() {
            var b = new StringBuilder();
            appendParams(b);
            return b.toString();
        }

        private void appendParams(StringBuilder b) {
            b.append('(');
            for (int i=0; i<mParamTypes.length; i++) {
                if (i > 0) {
                    b.append(", ");
                }
                b.append(mParamTypes[i].name());
            }
            b.append(')');
        }
    }

    static final class MethodKey {
        final BaseType returnType;
        final String name;
        final BaseType[] paramTypes;

        MethodKey(BaseType returnType, String name, BaseType... paramTypes) {
            this.returnType = returnType;
            this.name = name;
            this.paramTypes = paramTypes;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof MethodKey other
                && name.equals(other.name)
                && returnType.equals(other.returnType)
                && Arrays.equals(paramTypes, other.paramTypes);
        }

        @Override
        public int hashCode() {
            return name.hashCode() * 31 + Arrays.hashCode(paramTypes);
        }
    }

    private static final WeakHashMap<ClassLoader, SoftReference<ConcurrentHashMap<Object, BaseType>>>
        cCacheMap = new WeakHashMap<>();

    private static synchronized ConcurrentHashMap<Object, BaseType> cache(ClassLoader loader) {
        SoftReference<ConcurrentHashMap<Object, BaseType>> cacheRef = cCacheMap.get(loader);
        ConcurrentHashMap<Object, BaseType> cache;
        if (cacheRef == null || (cache = cacheRef.get()) == null) {
            cache = new ConcurrentHashMap<>();
            cCacheMap.put(loader, new SoftReference<>(cache));
        }
        return cache;
    }

    private static BaseType cachePut(ConcurrentHashMap<Object, BaseType> cache,
                                     Object key, BaseType type)
    {
        BaseType existing = cache.putIfAbsent(key, type);
        return existing == null ? type : existing;
    }

    // Called by InjectorTest to ensure that classes get unloaded. Soft references aren't
    // typically cleared right away.
    static synchronized void clearCaches() {
        cCacheMap.clear();
    }

    private static final class Primitive extends BaseType {
        private final int mStackMapCode, mTypeCode;
        private volatile SoftReference<BaseType> mBoxRef;

        private Primitive(int stackMapCode, int typeCode) {
            mStackMapCode = stackMapCode;
            mTypeCode = typeCode;
        }

        @Override
        public boolean isPrimitive() {
            return true;
        }

        @Override
        public boolean isInterface() {
            return false;
        }

        @Override
        public boolean isArray() {
            return false;
        }

        @Override
        public BaseType elementType() {
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
        public String name() {
            return switch (mTypeCode) {
                default -> "void";
                case T_BOOLEAN -> "boolean";
                case T_BYTE -> "byte";
                case T_CHAR -> "char";
                case T_SHORT -> "short";
                case T_INT -> "int";
                case T_FLOAT -> "float";
                case T_LONG -> "long";
                case T_DOUBLE -> "double";
            };
        }

        @Override
        public String descriptor() {
            return switch (mTypeCode) {
                default -> "V";
                case T_BOOLEAN -> "Z";
                case T_BYTE -> "B";
                case T_CHAR -> "C";
                case T_SHORT -> "S";
                case T_INT -> "I";
                case T_FLOAT -> "F";
                case T_LONG -> "J";
                case T_DOUBLE -> "D";
            };
        }

        @Override
        public BaseType box() {
            SoftReference<BaseType> boxRef = mBoxRef;
            BaseType box;

            if (boxRef == null || (box = boxRef.get()) == null) {
                synchronized (this) {
                    boxRef = mBoxRef;
                    if (boxRef == null || (box = boxRef.get()) == null) {
                        box = switch (mTypeCode) {
                            default -> from(Void.class);
                            case T_BOOLEAN -> from(Boolean.class);
                            case T_BYTE -> from(Byte.class);
                            case T_CHAR -> from(Character.class);
                            case T_SHORT -> from(Short.class);
                            case T_INT -> from(Integer.class);
                            case T_FLOAT -> from(Float.class);
                            case T_LONG -> from(Long.class);
                            case T_DOUBLE -> from(Double.class);
                        };

                        mBoxRef = new SoftReference<>(box);
                    }
                }
            }

            return box;
        }

        @Override
        public BaseType unbox() {
            return this;
        }

        @Override
        boolean isAssignableFrom(BaseType other) {
            return this.equals(other);
        }

        @Override
        public Class<?> classType() {
            return switch (mTypeCode) {
                default -> void.class;
                case T_BOOLEAN -> boolean.class;
                case T_BYTE -> byte.class;
                case T_CHAR -> char.class;
                case T_SHORT -> short.class;
                case T_INT -> int.class;
                case T_FLOAT -> float.class;
                case T_LONG -> long.class;
                case T_DOUBLE -> double.class;
            };
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Primitive other && mTypeCode == other.mTypeCode;
        }

        @Override
        public int hashCode() {
            return mTypeCode;
        }
    }

    private static abstract class Obj extends BaseType {
        @Override
        public final boolean isPrimitive() {
            return false;
        }

        @Override
        int stackMapCode() {
            return SM_OBJECT;
        }

        @Override
        final int typeCode() {
            return T_OBJECT;
        }

        @Override
        public final BaseType box() {
            return this;
        }
    }

    static final class Null extends Obj {
        static final Null THE = new Null();

        @Override
        public boolean isInterface() {
            return false;
        }

        @Override
        public boolean isArray() {
            return false;
        }

        @Override
        public BaseType elementType() {
            return null;
        }

        @Override
        int stackMapCode() {
            return SM_NULL;
        }

        @Override
        public String name() {
            return Object.class.getName();
        }

        @Override
        public String descriptor() {
            return Object.class.descriptorString();
        }

        @Override
        public BaseType unbox() {
            return null;
        }

        @Override
        boolean isAssignableFrom(BaseType other) {
            return this.equals(other);
        }

        @Override
        public Class<?> classType() {
            return null;
        }
    }

    private static final class Array extends Obj {
        private final BaseType mElementType;

        private volatile String mName;
        private volatile String mDesc;

        private volatile Class mClass;

        private Array(BaseType elementType) {
            mElementType = elementType;
        }

        @Override
        public boolean isInterface() {
            return false;
        }

        @Override
        public boolean isArray() {
            return true;
        }

        @Override
        public BaseType elementType() {
            return mElementType;
        }

        @Override
        public String name() {
            String name = mName;
            if (name == null) {
                mName = name = mElementType.name() + "[]";
            }
            return name;
        }

        @Override
        public String descriptor() {
            String desc = mDesc;
            if (desc == null) {
                mDesc = desc = '[' + mElementType.descriptor();
            }
            return desc;
        }

        @Override
        public BaseType unbox() {
            return null;
        }

        @Override
        boolean isAssignableFrom(BaseType other) {
            return other == Null.THE || this.equals(other) ||
                (other.isArray() && elementType().isAssignableFrom(other.elementType()));
        }

        @Override
        public Class<?> classType() {
            Class clazz = mClass;
            if (clazz == null) {
                Class element = mElementType.classType();
                if (element != null) {
                    mClass = clazz = java.lang.reflect.Array.newInstance(element, 0).getClass();
                }
            }
            return clazz;
        }

        @Override
        BaseType superType() {
            return from(Object.class);
        }

        @Override
        Set<Method> findMethods(String methodName, BaseType[] params,
                                int inherit, int staticAllowed,
                                BaseType specificReturnType, BaseType[] specificParamTypes)
        {
            return superType().findMethods(methodName, params, inherit, staticAllowed,
                                           specificReturnType, specificParamTypes);
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this || obj instanceof Array other
                && mElementType.equals(other.mElementType);
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

        protected volatile BaseType mSuperType;
        protected volatile Set<BaseType> mInterfaces;

        private volatile ConcurrentHashMap<String, Field> mFields;

        private volatile ConcurrentHashMap<MethodKey, Method> mMethods;

        private volatile ConcurrentHashMap<String, Map<FindKey, Set<Method>>> mFindMethods;

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
        public final boolean isInterface() {
            Boolean is = mIsInterface;
            if (is == null) {
                Class clazz = classType();
                mIsInterface = is = clazz != null && clazz.isInterface();
            }
            return is;
        }

        @Override
        public final boolean isArray() {
            return false;
        }

        @Override
        public final BaseType elementType() {
            return null;
        }

        @Override
        public final String name() {
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
        public final String descriptor() {
            String desc = mDesc;
            if (desc == null) {
                mDesc = desc = 'L' + mName.replace('.', '/') + ';';
            }
            return desc;
        }

        @Override
        public BaseType unbox() {
            return null;
        }

        @Override
        boolean isAssignableFrom(BaseType other) {
            if (other == Null.THE || this.equals(other)) {
                return true;
            }

            Class<?> thisClass = classType();
            Class<?> otherClass = other.classType();

            if (thisClass != null && otherClass != null) {
                return thisClass.isAssignableFrom(otherClass);
            }

            if (other.isPrimitive()) {
                return false;
            }

            if (thisClass == Object.class) {
                return true;
            }

            BaseType otherSuperType = other.superType();
            if (otherSuperType != null && isAssignableFrom(otherSuperType)) {
                return true;
            }

            Set<BaseType> interfaces = other.interfaces();
            if (interfaces != null) {
                for (BaseType iface : interfaces) {
                    if (isAssignableFrom(iface)) {
                        return true;
                    }
                }
            }

            return false;
        }

        @Override
        public Class<?> classType() {
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
        boolean isHidden() {
            Class clazz = classType();
            return clazz != null && clazz.isHidden();
        }

        @Override
        BaseType nonHiddenBase() {
            Class clazz = classType();
            return (clazz == null || !clazz.isHidden()) ? this : findNonHiddenBase(clazz);
        }

        private static BaseType findNonHiddenBase(Class clazz) {
            do {
                clazz = clazz.getSuperclass();
            } while (clazz.isHidden());
            return BaseType.from(clazz);
        }

        @Override
        BaseType superType() {
            BaseType superType = mSuperType;
            if (superType == null) {
                Class clazz = classType();
                if (clazz != null) {
                    clazz = clazz.getSuperclass();
                    assign: {
                        if (clazz == null) {
                            if (!isInterface()) {
                                break assign;
                            }
                            clazz = Object.class;
                        }
                        mSuperType = superType = from(clazz);
                    }
                }
            }
            return superType;
        }

        @Override
        Set<BaseType> interfaces() {
            Set<BaseType> interfaces = mInterfaces;
            if (interfaces == null) {
                synchronized (this) {
                    interfaces = mInterfaces;
                    if (interfaces == null) {
                        Class clazz = classType();
                        if (clazz != null) {
                            Set<BaseType> all = allInterfaces(null, clazz);
                            interfaces = all == null ? Collections.emptySet() : all;
                            mInterfaces = interfaces;
                        }
                    }
                }
            }
            return interfaces;
        }

        private static Set<BaseType> allInterfaces(Set<BaseType> all, Class clazz) {
            Class[] interfaces = clazz.getInterfaces();

            if (interfaces != null && interfaces.length != 0) {
                if (all == null) {
                    all = new LinkedHashSet<>(1);
                }
                for (Class iface : interfaces) {
                    all.add(BaseType.from(iface));
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
        Field defineField(int flags, BaseType type, String name) {
            return defineField(false, flags, type, name);
        }

        @Override
        Field inventField(int flags, BaseType type, String name) {
            return defineField(true, flags, type, name);
        }

        private Field defineField(boolean invent, int flags, BaseType type, String name) {
            var field = new Field(flags, type, name);

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

            Class clazz = classType();
            if (clazz != null) {
                for (var field : clazz.getDeclaredFields()) {
                    int flags = field.getModifiers();
                    String name = field.getName();
                    BaseType type = from(field.getType());
                    fields.put(name, new Field(flags, type, name));
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
        Set<Method> findMethods(String methodName, BaseType[] params,
                                int inherit, int staticAllowed,
                                BaseType specificReturnType, BaseType[] specificParamTypes)
        {
            BaseType type = this;

            if (inherit > 0) {
                type = type.superType();
                if (type == null) {
                    return Collections.emptySet();
                }
            }

            var findMethods = mFindMethods;
            if (findMethods == null) {
                synchronized (this) {
                    findMethods = mFindMethods;
                    if (findMethods == null) {
                        mFindMethods = findMethods = new ConcurrentHashMap<>();
                    }
                }
            }

            var subMap = findMethods.get(methodName);
            if (subMap == null) {
                subMap = new ConcurrentHashMap<>();
                var existing = findMethods.putIfAbsent(methodName, subMap);
                if (existing != null) {
                    subMap = existing;
                }
            }

            var findKey = new FindKey(params, inherit, staticAllowed,
                                      specificReturnType, specificParamTypes);

            Set<Method> results = subMap.get(findKey);

            if (results != null) {
                return results;
            }

            results = doFindMethods(type, methodName, params, inherit, staticAllowed,
                                    specificReturnType, specificParamTypes);

            subMap.put(findKey, results);

            return results;
        }

        private void uncacheFindMethod(String methodName) {
            var findMethods = mFindMethods;
            if (findMethods != null) {
                findMethods.remove(methodName);
            }
        }

        private static Set<Method> doFindMethods(BaseType type, String methodName,
                                                 BaseType[] params, int inherit, int staticAllowed,
                                                 BaseType specificReturnType,
                                                 BaseType[] specificParamTypes)
        {
            if (methodName.equals("<clinit>")) {
                // Can't be invoked.
                return Collections.emptySet();
            }

            var methods = new LinkedHashSet<Method>(4);
            addMethods(methods, type, methodName, params, staticAllowed);

            if (inherit >= 0) {
                BaseType superType = type.superType();
                while (superType != null) {
                    addMethods(methods, superType, methodName, params, staticAllowed);
                    superType = superType.superType();
                }
            }

            if (inherit == 0) {
                Set<BaseType> interfaces = type.interfaces();
                if (interfaces != null) {
                    for (BaseType iface : interfaces) {
                        addMethods(methods, iface, methodName, params, staticAllowed);
                    }
                }
            }

            if (specificReturnType != null && !methods.isEmpty()) {
                methods.removeIf(m -> !specificReturnType.equals(m.returnType()));
            }

            if (specificParamTypes != null && !methods.isEmpty()) {
                methods.removeIf(m -> !Arrays.equals(specificParamTypes, m.paramTypes()));
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
                    methods.removeIf(Method::isBridge);
                }
            }

            if (methods.isEmpty()) {
                return Collections.emptySet();
            }

            return methods;
        }

        private static void addMethods(Set<Method> methods, BaseType type, String methodName,
                                       BaseType[] params, int staticAllowed)
        {
            outer: for (Method m : type.methods().values()) {
                if (!m.name().equals(methodName)) {
                    continue;
                }

                if (m.isStatic()) {
                    if (staticAllowed < 0) {
                        continue;
                    }
                } else if (staticAllowed > 0) {
                    continue;
                }

                BaseType[] actualParams = m.paramTypes();

                if (!m.isVarargs()) {
                    if (actualParams.length != params.length) {
                        continue;
                    }

                    for (int i=0; i<params.length; i++) {
                        if (params[i].canConvertTo(actualParams[i]) == Integer.MAX_VALUE) {
                            continue outer;
                        }
                    }
                } else {
                    if (params.length < actualParams.length - 1) {
                        continue;
                    }

                    BaseType varType = actualParams[actualParams.length - 1].elementType();

                    for (int i=0; i<params.length; i++) {
                        BaseType actual = (i < actualParams.length - 1) ? actualParams[i] : varType;
                        if (params[i].canConvertTo(actual) == Integer.MAX_VALUE) {
                            if (i == actualParams.length - 1) {
                                if (params[i].canConvertTo(actualParams[i]) != Integer.MAX_VALUE) {
                                    // Pass along array parameter as-is.
                                    break;
                                }
                            }
                            continue outer;
                        }
                    }
                }

                methods.add(m);
            }
        }

        @Override
        Method defineMethod(int flags, BaseType returnType, String name, BaseType... paramTypes) {
            return defineMethod(false, flags, returnType, name, paramTypes);
        }

        @Override
        Method inventMethod(int flags, BaseType returnType, String name, BaseType... paramTypes) {
            return defineMethod(true, flags, returnType, name, paramTypes);
        }

        private Method defineMethod(boolean invent, int flags,
                                    BaseType returnType, String name, BaseType... paramTypes)
        {
            var key = new MethodKey(returnType, name, paramTypes);
            var method = new Method(flags, returnType, name, paramTypes);

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
                        uncacheFindMethod(name);
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

            Class clazz = classType();
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
                               String name, Executable method, BaseType returnType)
        {
            int flags = method.getModifiers();

            Class<?>[] params = method.getParameterTypes();
            var paramTypes = new BaseType[params.length];
            for (int i=0; i<params.length; i++) {
                paramTypes[i] = from(params[i]);
            }

            var key = new MethodKey(returnType, name, paramTypes);
            methods.put(key, new Method(flags, returnType, name, paramTypes));
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this || obj instanceof Clazz other
                && name().equals(other.name());
        }

        @Override
        public int hashCode() {
            return name().hashCode();
        }

        /**
         * Composite key used to cache the results of doFindMethods.
         */
        private static final class FindKey {
            final BaseType[] params;
            final int inherit;
            final int staticAllowed;
            final BaseType specificReturnType;
            final BaseType[] specificParamTypes;

            final int hash;

            FindKey(BaseType[] params, int inherit, int staticAllowed,
                    BaseType specificReturnType,
                    BaseType[] specificParamTypes)
            {
                this.params = params;
                this.inherit = inherit;
                this.staticAllowed = staticAllowed;
                this.specificReturnType = specificReturnType;
                this.specificParamTypes = specificParamTypes;

                int hash = Arrays.hashCode(params);
                hash = hash * 31 + Objects.hashCode(specificReturnType);
                hash = hash * 31 + Arrays.hashCode(specificParamTypes);
                hash = hash + inherit;
                hash = hash + (staticAllowed << 1);

                this.hash = hash;
            }

            @Override
            public boolean equals(Object obj) {
                return this == obj ||  obj instanceof FindKey other
                    && Arrays.equals(params, other.params)
                    && inherit == other.inherit
                    && staticAllowed == other.staticAllowed
                    && Objects.equals(specificReturnType, other.specificReturnType)
                    && Arrays.equals(specificParamTypes, other.specificParamTypes);
            }

            @Override
            public int hashCode() {
                return hash;
            }
        }
    }

    private static final class JavaLang extends Clazz {
        private volatile BaseType mUnbox;

        JavaLang(Class clazz) {
            super(clazz);
        }

        @Override
        public BaseType unbox() {
            BaseType unbox = mUnbox;

            if (unbox == null) {
                switch (name().substring(10)) {
                    case "Boolean" -> unbox = BOOLEAN;
                    case "Byte" -> unbox = BYTE;
                    case "Short" -> unbox = SHORT;
                    case "Character" -> unbox = CHAR;
                    case "Integer" -> unbox = INT;
                    case "Float" -> unbox = FLOAT;
                    case "Double" -> unbox = DOUBLE;
                    case "Long" -> unbox = LONG;
                    case "Void" -> unbox = VOID;
                }

                mUnbox = unbox;
            }

            return unbox;
        }
    }

    private static final class NewClazz extends Clazz {
        // Keep a weak reference to TheClassMaker because NewClazz instances can live a long
        // time as FindKey parameters. A null reference should never be observed because access
        // to NewClazz instances is only possible via classes which maintain a strong reference
        // to TheClassMaker. They are: Variable, Field, FieldMaker, and TheClassMaker itself.
        private final WeakReference<TheClassMaker> mMakerRef;

        NewClazz(ClassLoader loader, TheClassMaker maker, String name) {
            super(loader, name, null, false);
            mMakerRef = new WeakReference<>(maker);
        }

        @Override
        public Class<?> classType() {
            // It doesn't exist yet, so don't try loading it. Doing so causes the ClassLoader
            // to allocate a lock object, and it might never be reclaimed.
            return null;
        }

        @Override
        public TheClassMaker makerType() {
            return mMakerRef.get();
        }

        @Override
        BaseType superType() {
            BaseType superType = mSuperType;
            if (superType == null) {
                mSuperType = superType = makerType().superType();
            }
            return superType;
        }

        @Override
        Set<BaseType> interfaces() {
            Set<BaseType> interfaces = mInterfaces;
            if (interfaces == null) {
                synchronized (this) {
                    interfaces = mInterfaces;
                    if (interfaces == null) {
                        mInterfaces = interfaces = makerType().allInterfaces();
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
