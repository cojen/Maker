/*
 *  Copyright 2026 Cojen.org
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

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import static java.lang.invoke.MethodHandleInfo.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
final class TheLambdaFunction extends TheMethodMaker implements LambdaFunction {
    private final BaseType mFunctionType;
    private final BaseType.Method mFunctionMethod;
    private final MethodTypeDesc mInterfaceMethodType;
    private final MethodTypeDesc mDynamicMethodType;

    private ConstantVar mImplMethodHandle;

    /**
     * @param method must not be registered with the BaseType
     */
    TheLambdaFunction(TheClassMaker classMaker, BaseType.Method implMethod,
                      BaseType functionType, BaseType.Method functionMethod)
    {
        super(classMaker, classMaker.mConstants.addLateUTF8(implMethod.name()), implMethod);

        mFunctionType = functionType;
        mFunctionMethod = functionMethod;

        mInterfaceMethodType = MethodTypeDesc.ofDescriptor(functionMethod.descriptor());

        ClassDesc dynamicRetType = ClassDesc.ofDescriptor(implMethod.returnType().descriptor());

        var dynamicParamTypes = new ClassDesc[functionMethod.paramTypes().length];
        BaseType[] implParamTypes = implMethod.paramTypes();
        int numCaptures = implParamTypes.length - dynamicParamTypes.length;

        for (int i=0; i<dynamicParamTypes.length; i++) {
            BaseType paramType = implParamTypes[numCaptures + i];
            dynamicParamTypes[i] = ClassDesc.ofDescriptor(paramType.descriptor());
        }

        mDynamicMethodType = MethodTypeDesc.of(dynamicRetType, dynamicParamTypes);
    }

    @Override
    public BaseType type() {
        return mFunctionType;
    }

    Variable doCreate(TheMethodMaker mm, Object... values) {
        return mm.var(LambdaMetafactory.class)
            .indy("metafactory", mInterfaceMethodType, implMethodHandle(), mDynamicMethodType)
            .invoke(mFunctionType, mFunctionMethod.name(), null, values);
    }

    /**
     * Returns a Variable with a MethodHandle constant.
     */
    private ConstantVar implMethodHandle() {
        ConstantVar hvar = mImplMethodHandle;

        if (hvar == null) {
            ConstantPool cp = mConstants;
            ConstantPool.C_Method ref = cp.addMethod(mClassMaker.type(), mMethod, mName);
            ConstantPool.Constant mhConstant = cp.addMethodHandle(REF_invokeStatic, ref);
            hvar = new ConstantVar(BaseType.from(MethodHandle.class), mhConstant);
            mImplMethodHandle = hvar;
        }

        return hvar;
    }
}
