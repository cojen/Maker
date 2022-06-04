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

import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import static java.lang.invoke.MethodHandleInfo.*;

/**
 * Supports the java.lang.constant package, added in Java 12.
 *
 * @author Brian S O'Neill
 */
abstract class ConstableSupport {
    /**
     * Returns null if not supported.
     *
     * @param value expected to be a ClassDesc
     */
    static String toTypeDescriptor(Object value) {
        if (value instanceof ClassDesc) {
            return ((ClassDesc) value).descriptorString();
        }
        return null;
    }

    /**
     * Returns null if not supported.
     *
     * @param value expected to be a custom Constable or ConstantDesc
     */
    static TheMethodMaker.ConstantVar toConstantVar(TheMethodMaker mm, Object value) {
        Type type;
        ConstantPool.Constant constant;

        if (value instanceof MethodTypeDesc) {
            type = Type.from(MethodType.class);
            constant = mm.mConstants.addMethodType(((MethodTypeDesc) value).descriptorString());
        } else if (value instanceof DirectMethodHandleDesc) {
            type = Type.from(MethodHandle.class);
            constant = addMethodHandle(mm, (DirectMethodHandleDesc) value);
        } else if (value instanceof DynamicConstantDesc) {
            var dcd = (DynamicConstantDesc) value;
            type = mm.mClassMaker.typeFrom(dcd.constantType().descriptorString());

            DirectMethodHandleDesc bootDesc = dcd.bootstrapMethod();
            ConstantPool.C_MethodHandle bootHandle = addMethodHandle(mm, bootDesc);

            ConstantDesc[] bootArgs = dcd.bootstrapArgs();
            var bootConstants = new ConstantPool.Constant[bootArgs.length];

            ConstantPool cp = mm.mConstants;

            for (int i=0; i<bootArgs.length; i++) {
                bootConstants[i] = mm.addLoadableConstant(null, bootArgs[i]);
            }

            constant = cp.addDynamicConstant
                (mm.mClassMaker.addBootstrapMethod(bootHandle, bootConstants),
                 dcd.constantName(), type);
        } else if (value instanceof ClassDesc) {
            type = Type.from(Class.class);
            constant = mm.addLoadableConstant
                (type, mm.mClassMaker.typeFrom(((ClassDesc) value).descriptorString()));
        } else if (value instanceof Constable) {
            var opt = ((Constable) value).describeConstable();
            if (opt.isEmpty()) {
                return null;
            }
            return toConstantVar(mm, opt.get());
        } else {
            return null;
        }

        return mm.new ConstantVar(type, constant);
    }

    private static ConstantPool.C_MethodHandle addMethodHandle(TheMethodMaker mm,
                                                               DirectMethodHandleDesc mdesc)
    {
        final ConstantPool cp = mm.mConstants;
        final TheClassMaker cm = mm.mClassMaker;

        final int refKind = mdesc.refKind();
        final Type owner = cm.typeFrom(mdesc.owner().descriptorString());
        final MethodTypeDesc mtype = mdesc.invocationType();
        final String name = mdesc.methodName();

        final ConstantPool.C_MemberRef ref;

        switch (refKind) {
        default:
            throw new AssertionError();

        case REF_getField: case REF_getStatic:
            Type type = cm.typeFrom(mtype.returnType().descriptorString());
            ref = cp.addField(owner.inventField(refKind == REF_getStatic, type, name));
            break;

        case REF_putField: case REF_putStatic:
            type = cm.typeFrom(mtype.parameterType(0).descriptorString());
            ref = cp.addField(owner.inventField(refKind == REF_putStatic, type, name));
            break;

        case REF_invokeVirtual: case REF_newInvokeSpecial:
        case REF_invokeStatic: case REF_invokeSpecial: case REF_invokeInterface:
            Type ret;
            int drop;
            if (mdesc.kind() == DirectMethodHandleDesc.Kind.CONSTRUCTOR) {
                ret = Type.VOID;
                drop = 0;
            } else {
                ret = cm.typeFrom(mtype.returnType().descriptorString());
                drop = refKind == REF_invokeStatic ? 0 : 1;
            }
            Type[] params = new Type[mtype.parameterCount() - drop];
            for (int i=0; i<params.length; i++) {
                params[i] = cm.typeFrom(mtype.parameterType(i + drop).descriptorString());
            }
            ref = cp.addMethod(owner.inventMethod
                               (refKind == REF_invokeStatic, ret, name, params));
            break;
        }

        return cp.addMethodHandle(refKind, ref);
    }

    static boolean isConstantDesc(Object value) {
        return value instanceof ConstantDesc;
    }

    /**
     * Returns the type of the constant if isConstantDesc, or null if not supported.
     *
     * @param value expected to be ConstantDesc
     */
    static Type toConstantDescType(TheMethodMaker mm, Object value) {
        if (value instanceof ConstantDesc) {
            if (value instanceof MethodTypeDesc) {
                return Type.from(MethodType.class);
            } else if (value instanceof DirectMethodHandleDesc) {
                return Type.from(MethodHandle.class);
            } else if (value instanceof DynamicConstantDesc) {
                var dcd = (DynamicConstantDesc) value;
                return mm.mClassMaker.typeFrom(dcd.constantType().descriptorString());
            } else if (value instanceof ClassDesc) {
                return Type.from(Class.class);
            }
        }
            
        return null;
    }

    static boolean isDynamicConstant(Object value) {
        if (value instanceof DynamicConstantDesc) {
            return true;
        } else if (value instanceof Constable) {
            var opt = ((Constable) value).describeConstable();
            return opt.isPresent() && opt.get() instanceof DynamicConstantDesc;
        } else {
            return false;
        }
    }
}
