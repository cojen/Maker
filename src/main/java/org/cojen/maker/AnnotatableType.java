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

import java.lang.reflect.Array;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
final class AnnotatableType implements Type, Typed {
    private final BaseType mBase;

    private final ArrayList<AnnMaker> mAnnotations;

    AnnotatableType(BaseType base) {
        mBase = base;
        mAnnotations = new ArrayList<>();
    }

    private AnnotatableType(BaseType base, AnnotatableType at) {
        mBase = base;
        mAnnotations = new ArrayList<>(at.mAnnotations);
    }

    @Override
    public BaseType type() {
        return mBase;
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
    public boolean isObject() {
        return mBase.isObject();
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
    public Type elementType() {
        return mBase.elementType();
    }

    @Override
    public int dimensions() {
        return mBase.dimensions();
    }

    @Override
    public Type asArray() {
        return new AnnotatableType(mBase.asArray(), this);
    }

    @Override
    public Type box() {
        // Always return a new instance because this type isn't immutable.
        return new AnnotatableType(mBase.box(), this);
    }

    @Override
    public Type unbox() {
        BaseType unboxed = mBase.unbox();
        // When possible, always return a new instance because this type isn't immutable.
        return unboxed == null ? null : new AnnotatableType(unboxed, this);
    }

    @Override
    public boolean isAnnotated() {
        return !mAnnotations.isEmpty();
    }

    @Override
    public Type annotatable() {
        return new AnnotatableType(mBase, this);
    }

    @Override
    public AnnotationMaker addAnnotation(Object annotationType, boolean visible) {
        var am = new AnnMaker(annotationType, visible);
        mAnnotations.add(am);
        return am;
    }

    @Override
    public int hashCode() {
        return mBase.hashCode() * 31 + mAnnotations.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof AnnotatableType other
            && mBase.equals(other.mBase)
            && mAnnotations.equals(other.mAnnotations);
    }

    private static final class AnnMaker implements AnnotationMaker {
        private final Object mType;
        private final boolean mVisible;
        private final LinkedHashMap<String, Object> mValues;

        private AnnMaker mParent;

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
        public void put(String name, Object value) {
            if (mValues.containsKey(name)) {
                throw new IllegalStateException();
            }
            mValues.put(name, consume(this, value));
        }

        private static Object consume(AnnMaker parent, Object value) {
            Class<?> clazz = value.getClass();

            if (Variable.unboxedType(clazz).isPrimitive() ||
                value instanceof String || value instanceof Enum || value instanceof Class ||
                value instanceof Typed)
            {
                // Okay.
            } else if (clazz.isArray()) {
                int length = Array.getLength(value);
                for (int i=0; i<length; i++) {
                    consume(parent, Array.get(value, i));
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
        public AnnotationMaker newAnnotation(Object annotationType) {
            var am = new AnnMaker(annotationType, mVisible);
            am.mParent = this;
            return am;
        }

        @Override
        public int hashCode() {
            int hash = mType.hashCode();
            hash *= (mVisible ? 31 : 63);
            hash = hash * 31 + mValues.hashCode();
            if (mParent != null) {
                hash = hash * 31 + mParent.hashCode();
            }
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof AnnMaker other
                && mType.equals(other.mType) && mVisible == other.mVisible
                && mValues.equals(other.mValues) && Objects.equals(mParent, other.mParent);
        }
    }
}
