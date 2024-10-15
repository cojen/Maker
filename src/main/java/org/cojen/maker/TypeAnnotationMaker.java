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

import java.io.IOException;

import java.util.Arrays;

/**
 * Support for RuntimeVisibleTypeAnnotations and RuntimeInvisibleTypeAnnotations.
 *
 * @author Brian S. O'Neill
 */
final class TypeAnnotationMaker extends TheAnnotationMaker {
    private final Target mTarget;

    TypeAnnotationMaker(TheClassMaker classMaker, Object annotationType, Target target) {
        super(classMaker, annotationType);
        mTarget = target;
    }

    @Override
    int length() {
        return mTarget.length() + super.length();
    }

    @Override
    void writeTo(BytesOut out) throws IOException {
        mTarget.writeTo(out);
        super.writeTo(out);
    }

    static abstract sealed class Target {
        private final int mType;
        private byte[] mPath;
        private int mPathLength;

        Target(int type) {
            mType = type;
            mPath = new byte[8];
        }

        /**
         * Add path kind 0: Annotation is deeper in an array type.
         */
        final Target addArrayType() {
            return add(0, 0);
        }

        /**
         * Add path kind 1: Annotation is deeper in a nested type.
         */
        final Target addNestedType() {
            return add(1, 0);
        }

        /**
         * Add path kind 2: Annotation is on the bound of a wildcard type argument of a
         * parameterized type.
         */
        final Target addWildcardType() {
            return add(2, 0);
        }

        /**
         * Add path kind 3: Annotation is on a type argument of a parameterized type.
         *
         * @param index which type argument of a parameterized type is annotated
         */
        final Target addParameterizedType(int index) {
            return add(3, index);
        }

        private Target add(int kind, int index) {
            if (mPathLength >= 255) {
                throw new IllegalStateException();
            }
            if (mPathLength >= mPath.length) {
                mPath = Arrays.copyOfRange(mPath, 0, mPathLength * 2);
            }
            mPath[mPathLength++] = (byte) kind;
            mPath[mPathLength++] = (byte) index;
            return this;
        }

        final int length() {
            return 1 + doLength() + 1 + mPathLength;
        }

        final void writeTo(BytesOut out) throws IOException {
            out.writeByte(mType);
            doWriteTo(out);
            out.writeByte(mPathLength);
            out.write(mPath, 0, mPathLength);
        }

        abstract int doLength();

        abstract void doWriteTo(BytesOut out) throws IOException;
    }

    /**
     * Corresponds to target_type 0x13, 0x14, and 0x15: empty_target.
     */
    static final class Target0 extends Target {
        Target0(int type) {
            super(type);
        }

        @Override
        int doLength() {
            return 0;
        }

        @Override
        void doWriteTo(BytesOut out) throws IOException {
        }
    }

    /**
     * Corresponds to target_type 0x00 and 0x01: type_parameter_target.
     * Corresponds to target_type 0x16: formal_parameter_target.
     */
    static final class Target1 extends Target {
        private final int mIndex;

        Target1(int type, int index) {
            super(type);
            mIndex = index;
        }

        @Override
        int doLength() {
            return 1;
        }

        @Override
        void doWriteTo(BytesOut out) throws IOException {
            out.writeByte(mIndex);
        }
    }

    /**
     * Corresponds to target_type 0x10: supertype_target.
     * Corresponds to target_type 0x11 and 0x12: type_parameter_bound_target.
     * Corresponds to target_type 0x17: throws_target.
     * Corresponds to target_type 0x42: catch_target.
     * Corresponds to target_type 0x43, 0x44, 0x45, and 0x46: offset_target.
     */
    static final class Target2 extends Target {
        private final int mIndex;

        Target2(int type, int index) {
            super(type);
            mIndex = index;
        }

        @Override
        int doLength() {
            return 2;
        }

        @Override
        void doWriteTo(BytesOut out) throws IOException {
            out.writeShort(mIndex);
        }
    }

    /**
     * Corresponds to target_type 0x47, 0x48, 0x49, 0x4a, and 0x4b: type_argument_target.
     */
    /* Not supported.
    static final class Target3 extends Target {
        private final int mOffset, mIndex;

        Target3(int type, int offset, int index) {
            super(type);
            mOffset = offset;
            mIndex = index;
        }

        @Override
        int doLength() {
            return 3;
        }

        @Override
        void doWriteTo(BytesOut out) throws IOException {
            out.writeShort(mOffset);
            out.writeByte(mIndex);
        }
    }
    */

    /**
     * Corresponds to target_type 0x40 and 0x41: localvar_target
     */
    /* Not supported.
    static final class Target4 extends Target {
        Target4(int type) {
            super(type);
        }

        @Override
        int doLength() {
            throw null;
        }

        @Override
        void doWriteTo(BytesOut out) throws IOException {
            throw null;
        }
    }
    */
}
