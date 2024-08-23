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
final class TheFieldMaker extends ClassMember implements FieldMaker, Typed {
    private final BaseType.Field mField;

    TheFieldMaker(TheClassMaker classMaker, BaseType.Field field) {
        super(classMaker, field.name(), field.type().descriptor());
        mField = field;
    }

    @Override
    public BaseType type() {
        return mField.type();
    }

    @Override
    public FieldMaker public_() {
        mModifiers = Modifiers.toPublic(mModifiers);
        return this;
    }

    @Override
    public FieldMaker private_() {
        mModifiers = Modifiers.toPrivate(mModifiers);
        mField.toPrivate();
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
        mField.toFinal();
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
    public FieldMaker enum_() {
        mModifiers = Modifiers.toEnum(mModifiers);
        return this;
    }

    @Override
    public FieldMaker signature(Object... components) {
        addSignature(components);
        return this;
    }

    @Override
    public FieldMaker init(Object value) {
        if (!Modifier.isStatic(mModifiers)) {
            throw new IllegalStateException("Not static");
        }

        ConstantPool.Constant constant;

        addConstant: {
            int ivalue;

            nonInt: {
                switch (mField.type().typeCode()) {
                case BaseType.T_BOOLEAN:
                    if (value instanceof Boolean b) {
                        ivalue = b ? 1 : 0;
                        break nonInt;
                    }
                    break;
                case BaseType.T_BYTE:
                    if (value instanceof Byte) {
                        ivalue = (byte) value;
                        break nonInt;
                    }
                    break;
                case BaseType.T_CHAR:
                    if (value instanceof Character) {
                        ivalue = (char) value;
                        break nonInt;
                    }
                    break;
                case BaseType.T_SHORT:
                    if (value instanceof Short) {
                        ivalue = (short) value;
                        break nonInt;
                    }
                    break;
                case BaseType.T_INT:
                    if (value instanceof Integer) {
                        ivalue = (int) value;
                        break nonInt;
                    }
                    break;
                case BaseType.T_FLOAT:
                    if (value instanceof Float) {
                        constant = mConstants.addFloat((float) value);
                        break addConstant;
                    }
                    break;
                case BaseType.T_LONG:
                    if (value instanceof Long || value instanceof Integer) {
                        constant = mConstants.addLong(((Number) value).longValue());
                        break addConstant;
                    }
                    break;
                case BaseType.T_DOUBLE:
                    if (value instanceof Double || value instanceof Integer) {
                        constant = mConstants.addDouble(((Number) value).doubleValue());
                        break addConstant;
                    }
                    break;
                default:
                    if (value == null) {
                        // Fields are null by default.
                        return this;
                    }
                    if (value instanceof String str &&
                        mField.type() == BaseType.from(String.class))
                    {
                        constant = mConstants.addString(str);
                        break addConstant;
                    }
                    break;
                }

                // Define a static initializer and perform a conversion.
                mClassMaker.addClinit().field(mField.name()).set(value);
                return this;
            }

            if (ivalue == 0) {
                // Fields are zero by default.
                return this;
            }

            constant = mConstants.addInteger(ivalue);
        }

        addAttribute(new Attribute.Constant(mConstants, "ConstantValue", constant));
        return this;
    }

    @Override
    public FieldMaker initExact(Object value) {
        if (!Modifier.isStatic(mModifiers)) {
            throw new IllegalStateException("Not static");
        }

        classMaker().addClinit().field(mField.name()).setExact(value);

        return this;
    }
}
