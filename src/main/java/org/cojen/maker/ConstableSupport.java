/*
 *  Copyright 2021 Cojen.org
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

import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;

import java.lang.invoke.TypeDescriptor;

/**
 * Supports the java.lang.constant package, added in Java 12.
 *
 * @author Brian S O'Neill
 */
abstract class ConstableSupport {
    static final ConstableSupport THE;

    static {
        ConstableSupport instance = null;
        try {
            if (Constable.class != null) {
                instance = new Supported();
            }
        } catch (NoClassDefFoundError e) {
        }

        if (instance == null) {
            instance = new Unsupported();
        }

        THE = instance;
    }

    /**
     * Returns null if not supported.
     */
    abstract Type typeFrom(ClassLoader loader, Object type);

    /**
     * Returns null if not supported.
     */
    abstract DynamicConstantDesc descFrom(Object value);

    /**
     * Returns null if not supported.
     *
     * @param type optional type of value to expect
     */
    abstract ConstantPool.Constant tryAddDynamicConstant(TheMethodMaker mm,
                                                         Type type, Object value);

    /**
     * Returns null if not supported.
     */
    abstract TheMethodMaker.ConstantVar tryAddDynamicConstantVar(TheMethodMaker mm, Object value);

    private static class Supported extends ConstableSupport {
        @Override
        Type typeFrom(ClassLoader loader, Object type) {
            TypeDescriptor.OfField desc;
            if (type instanceof TypeDescriptor.OfField) {
                // This also handles ClassDesc, which extends TypeDescriptor.OfField.
                desc = (TypeDescriptor.OfField) type;
            } else {
                return null;
            }
            return Type.from(loader, desc.descriptorString());
        }

        @Override
        DynamicConstantDesc descFrom(Object value) {
            if (!(value instanceof Constable)) {
                return null;
            }
            var opt = ((Constable) value).describeConstable();
            if (opt.isEmpty()) {
                return null;
            }
            ConstantDesc desc = opt.get();
            if (!(desc instanceof DynamicConstantDesc)) {
                return null;
            }
            return (DynamicConstantDesc) desc;
        }

        @Override
        ConstantPool.Constant tryAddDynamicConstant(TheMethodMaker mm, Type type, Object value) {
            TheMethodMaker.ConstantVar cvar = tryAddDynamicConstantVar(mm, value);
            if (cvar == null) {
                return null;
            }
            if (type != null && !type.isAssignableFrom(cvar.type())) {
                throw new IllegalStateException
                    ("Automatic conversion disallowed: " +
                     cvar.type().name() + " to " + type.name());
            }
            return cvar.mConstant;
        }

        @Override
        TheMethodMaker.ConstantVar tryAddDynamicConstantVar(TheMethodMaker mm, Object value) {
            DynamicConstantDesc desc = descFrom(value);
            if (desc == null) {
                return null;
            }
            Type type = mm.mClassMaker.typeFrom(desc.constantType().descriptorString());
            DirectMethodHandleDesc mdesc = desc.bootstrapMethod();
            Variable constant = mm.var(mdesc.owner())
                .condy(mdesc.methodName(), (Object[]) desc.bootstrapArgs())
                .invoke(type, desc.constantName());
            return (TheMethodMaker.ConstantVar) constant;
        }
    }

    private static class Unsupported extends ConstableSupport {
        @Override
        Type typeFrom(ClassLoader loader, Object type) {
            return null;
        }

        @Override
        DynamicConstantDesc descFrom(Object value) {
            return null;
        }

        @Override
        ConstantPool.Constant tryAddDynamicConstant(TheMethodMaker mm, Type type, Object value) {
            return null;
        }

        @Override
        TheMethodMaker.ConstantVar tryAddDynamicConstantVar(TheMethodMaker mm, Object value) {
            return null;
        }
    }
}
