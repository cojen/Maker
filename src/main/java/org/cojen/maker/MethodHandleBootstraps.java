/*
 *  Copyright 2023 Cojen.org
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static java.lang.invoke.MethodHandleInfo.*;

/**
 * Bootstrap methods for supporting method invocation on hidden classes. Ideally, these methods
 * would exist in the java.lang.invoke.ConstantBootstraps class, since the various methods for
 * obtaining VarHandles have a similar pattern.
 *
 * @author Brian S. O'Neill
 * @hidden
 */
public class MethodHandleBootstraps {
    /**
     * Condy bootstrap method for finding a MethodHandle.
     *
     * @param name the method name to find
     * @param type expected to be {@code Class<MethodHandle>}
     * @param kind see MethodHandleInfo.REF_*
     * @param declaringClass the class in which the method is declared
     * @param returnType return type of the method to find
     * @param paramTypes parameter types of the method to find
     */
    public static MethodHandle methodHandle(MethodHandles.Lookup lookup,
                                            String name, Class<MethodHandle> type,
                                            int kind, Class<?> declaringClass,
                                            Class<?> returnType, Class<?>... paramTypes)
        throws IllegalAccessException, NoSuchFieldException, NoSuchMethodException
    {
        switch (kind) {
            /* Unused
        case REF_getField:
            return lookup.findGetter(declaringClass, name, returnType);

        case REF_getStatic:
            return lookup.findStaticGetter(declaringClass, name, returnType);

        case REF_putField:
            return lookup.findSetter(declaringClass, name, returnType);

        case REF_putStatic:
            return lookup.findStaticSetter(declaringClass, name, returnType);
            */

        case REF_invokeVirtual: case REF_invokeInterface:
            return lookup.findVirtual(declaringClass, name, mt(returnType, paramTypes));

        case REF_invokeStatic:
            return lookup.findStatic(declaringClass, name, mt(returnType, paramTypes));

            /* Unused
        case REF_invokeSpecial:
            return lookup.findSpecial
                (declaringClass, name, mt(returnType, paramTypes), declaringClass);
            */

        case REF_newInvokeSpecial:
            return lookup.findConstructor(declaringClass, mt(returnType, paramTypes));
        }

        throw new IllegalArgumentException("" + kind);
    }

    private static MethodType mt(Class<?> returnType, Class<?>[] paramTypes) {
        return MethodType.methodType(returnType, paramTypes);
    }
}
