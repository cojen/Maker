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

import java.lang.reflect.Modifier;

/**
 * 
 *
 * @author Brian S O'Neill
 */
final class TheFieldMaker extends ClassMember implements FieldMaker {
    Type.Field mField;

    TheFieldMaker(ConstantPool cp, Type.Field field) {
        super(cp, cp.addUTF8(field.name()), cp.addUTF8(field.type().descriptor()));
        mField = field;
    }

    @Override
    public FieldMaker public_() {
        mModifiers = Modifiers.toPublic(mModifiers);
        return this;
    }

    @Override
    public FieldMaker private_() {
        mModifiers = Modifiers.toPrivate(mModifiers);
        return this;
    }

    @Override
    public FieldMaker protected_() {
        mModifiers = Modifiers.toProtected(mModifiers);
        return this;
    }

    @Override
    public FieldMaker static_() {
        mModifiers = Modifiers.toStatic(mModifiers);
        mField.toStatic();
        return this;
    }

    @Override
    public FieldMaker final_() {
        mModifiers = Modifiers.toFinal(mModifiers);
        return this;
    }

    @Override
    public FieldMaker volatile_() {
        mModifiers = Modifiers.toVolatile(mModifiers);
        return this;
    }

    @Override
    public FieldMaker transient_() {
        mModifiers = Modifiers.toTransient(mModifiers);
        return this;
    }

    @Override
    public FieldMaker synthetic() {
        mModifiers = Modifiers.toSynthetic(mModifiers);
        return this;
    }

    @Override
    public FieldMaker init(int value) {
        init(mConstants.addInteger(value));
        return this;
    }

    @Override
    public FieldMaker init(float value) {
        init(mConstants.addFloat(value));
        return this;
    }

    @Override
    public FieldMaker init(long value) {
        init(mConstants.addLong(value));
        return this;
    }

    @Override
    public FieldMaker init(double value) {
        init(mConstants.addDouble(value));
        return this;
    }

    @Override
    public FieldMaker init(String value) {
        init(mConstants.addString(value));
        return this;
    }

    private void init(ConstantPool.Constant constant) {
        if (!Modifier.isStatic(mModifiers)) {
            throw new IllegalStateException("Not static");
        }
        addAttribute(new Attribute.Constant(mConstants, constant));
    }
}
