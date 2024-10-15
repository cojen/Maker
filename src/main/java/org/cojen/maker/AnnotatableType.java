/*
 *  Copyright 2024 Cojen.org
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
final class AnnotatableType extends BaseType {
    private final BaseType mBase;
    private final ArrayList<AnnMaker> mAnnotations;

    private boolean mFrozen;

    AnnotatableType(BaseType base) {
        mBase = base;
        mAnnotations = new ArrayList<>(1);
    }

    private AnnotatableType(BaseType base, AnnotatableType at) {
        mBase = base;
        synchronized (at) {
            mAnnotations = new ArrayList<>(at.mAnnotations);
        }
    }

    private AnnotatableType(BaseType base, ArrayList<AnnMaker> annotations) {
        mBase = base;
        mAnnotations = annotations;
    }

    @Override
    public String name() {
        return mBase.name();
    }

    @Override
    public String descriptor() {
        return mBase.descriptor();
    }

    @Override
    public Class<?> classType() {
        return mBase.classType();
    }

    @Override
    public ClassMaker makerType() {
        return mBase.makerType();
    }

    @Override
    public boolean isPrimitive() {
        return mBase.isPrimitive();
    }

    @Override
    public boolean isInterface() {
        return mBase.isInterface();
    }

    @Override
    public boolean isArray() {
        return mBase.isArray();
    }

    @Override
    public BaseType elementType() {
        return mBase.elementType();
    }

    @Override
    public BaseType asArray() {
        return new AnnotatableType(super.asArray());
    }

    @Override
    public BaseType box() {
        return withBaseType(mBase.box());
    }

    @Override
    public BaseType unbox() {
        BaseType unboxed = mBase.unbox();
        return unboxed == null ? null : withBaseType(unboxed);
    }

    private synchronized AnnotatableType withBaseType(BaseType base) {
        if (!mFrozen) {
            return new AnnotatableType(base, this);
        } else if (base == mBase) {
            // Can only return this instance if it's immutable/frozen.
            return this;
        } else {
            // No need to copy the array when frozen.
            var at = new AnnotatableType(base, mAnnotations);
            at.mFrozen = true;
            return at;
        }
    }

    @Override
    public Type annotatable() {
        return new AnnotatableType(mBase, this);
    }

    @Override
    public synchronized AnnotationMaker addAnnotation(Object annotationType, boolean visible) {
        if (mFrozen) {
            throw new IllegalStateException("Type is frozen");
        }
        var am = new AnnMaker(annotationType, visible);
        mAnnotations.add(am);
        return am;
    }

    @Override
    public synchronized void freeze() {
        if (!mFrozen) {
            mFrozen = true;
            for (AnnMaker am : mAnnotations) {
                am.freeze();
            }
        }
    }

    @Override
    boolean isAnnotatable() {
        return true;
    }

    @Override
    public synchronized int hashCode() {
        return mBase.hashCode() * 31 + mAnnotations.hashCode();
    }

    @Override
    public synchronized boolean equals(Object obj) {
        return this == obj || obj instanceof AnnotatableType other
            && mBase.equals(other.mBase)
            && mAnnotations.equals(other.mAnnotations);
    }

    @Override
    boolean isAssignableFrom(BaseType other) {
        return mBase.isAssignableFrom(other);
    }

    @Override
    int stackMapCode() {
        return mBase.stackMapCode();
    }

    @Override
    int typeCode() {
        return mBase.typeCode();
    }

    @Override
    void applyAnnotations(TheClassMaker classMaker, TypeAnnotationMaker.Target target) {
        applyAnnotations(classMaker, classMaker, target);
    }

    @Override
    void applyAnnotations(ClassMember member, TypeAnnotationMaker.Target target) {
        applyAnnotations(member, member.mClassMaker, target);
    }

    @Override
    void applyAnnotations(Attributed dest, TheClassMaker classMaker,
                          TypeAnnotationMaker.Target target)
    {
        freeze();

        for (AnnMaker am : mAnnotations) {
            TypeAnnotationMaker tam = dest.addTypeAnnotationMaker
                (new TypeAnnotationMaker(classMaker, am.mType, target), am.mVisible);
            am.apply(tam);
        }
    }

    @Override
    boolean isHidden() {
        return mBase.isHidden();
    }

    @Override
    BaseType nonHiddenBase() {
        return mBase.nonHiddenBase();
    }

    @Override
    BaseType superType() {
        return mBase.superType();
    }

    @Override
    Set<BaseType> interfaces() {
        return mBase.interfaces();
    }

    @Override
    void resetInherited() {
        mBase.resetInherited();
    }

    @Override
    void toInterface() {
        mBase.toInterface();
    }

    @Override
    Map<String, Field> fields() {
        return mBase.fields();
    }

    @Override
    Field defineField(int flags, BaseType type, String name) {
        return mBase.defineField(flags, type, name);
    }

    @Override
    Field inventField(int flags, BaseType type, String name) {
        return mBase.inventField(flags, type, name);
    }

    @Override
    Map<MethodKey, Method> methods() {
        return mBase.methods();
    }

    @Override
    Set<Method> findMethods(String methodName, BaseType[] params, int inherit, int staticAllowed,
                            BaseType specificReturnType, BaseType[] specificParamTypes)
    {
        return mBase.findMethods(methodName, params, inherit, staticAllowed,
                                 specificReturnType, specificParamTypes);
    }

    @Override
    Method defineMethod(int flags, BaseType returnType, String name, BaseType... paramTypes) {
        return mBase.defineMethod(flags, returnType, name, paramTypes);
    }

    @Override
    Method inventMethod(int flags, BaseType returnType, String name, BaseType... paramTypes) {
        return mBase.inventMethod(flags, returnType, name, paramTypes);
    }

    private static final class AnnMaker implements AnnotationMaker {
        private final Object mType;
        private final boolean mVisible;
        private final LinkedHashMap<String, Object> mValues;

        private AnnMaker mParent;

        private boolean mFrozen;

        AnnMaker(Object type, boolean visible) {
            Objects.requireNonNull(type);

            if (type instanceof Typed typed) {
                // Avoid maintaining a long-lived reference to a ClassMaker, etc.
                type = typed.type();
            }

            mType = type;
            mVisible = visible;
            mValues = new LinkedHashMap<>();
        }

        @Override
        public synchronized void put(String name, Object value) {
            if (mFrozen) {
                throw new IllegalStateException("Type is frozen");
            }
            if (mValues.containsKey(name)) {
                throw new IllegalStateException();
            }
            mValues.put(name, consume(this, value));
        }

        private static Object consume(AnnMaker parent, Object value) {
            Class<?> clazz = value.getClass(), unboxed;

            if (((unboxed = Variable.unboxedType(clazz)) != null && unboxed.isPrimitive()) ||
                value instanceof String || value instanceof Enum || value instanceof Class ||
                value instanceof Typed)
            {
                // Okay.
            } else if (clazz.isArray()) {
                int length = java.lang.reflect.Array.getLength(value);
                for (int i=0; i<length; i++) {
                    consume(parent, java.lang.reflect.Array.get(value, i));
                }
            } else if (value instanceof AnnMaker am) {
                if (am.mParent != parent) {
                    throw new IllegalStateException();
                }
                am.mParent = null;
            } else {
                throw new IllegalArgumentException();
            }

            return value;
        }

        @Override
        public synchronized AnnotationMaker newAnnotation(Object annotationType) {
            if (mFrozen) {
                throw new IllegalStateException("Type is frozen");
            }
            var am = new AnnMaker(annotationType, mVisible);
            am.mParent = this;
            return am;
        }

        @Override
        public synchronized int hashCode() {
            int hash = mType.hashCode();
            hash *= (mVisible ? 31 : 63);
            hash = hash * 31 + mValues.hashCode();
            if (mParent != null) {
                hash = hash * 31 + mParent.hashCode();
            }
            return hash;
        }

        @Override
        public synchronized boolean equals(Object obj) {
            return this == obj || obj instanceof AnnMaker other
                && mType.equals(other.mType) && mVisible == other.mVisible
                && mValues.equals(other.mValues) && Objects.equals(mParent, other.mParent);
        }

        private synchronized void freeze() {
            if (!mFrozen) {
                mFrozen = true;
                for (Object value : mValues.values()) {
                    if (value instanceof AnnMaker am) {
                        am.freeze();
                    }
                }
            }
        }

        private void apply(AnnotationMaker dest) {
            for (Map.Entry<String, Object> e : mValues.entrySet()) {
                Object value = e.getValue();
                if (value instanceof AnnMaker am) {
                    AnnotationMaker newAm = dest.newAnnotation(am.mType);
                    am.apply(newAm);
                    value = newAm;
                }
                dest.put(e.getKey(), value);
            }
        }
    }
}
