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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.lang.reflect.Modifier;

import java.util.HashMap;

/**
 * Implements a class in which all of the inherited abstract methods do nothing. For any
 * methods which return a primitive value or an object, 0 or null is returned. Methods which
 * return an interface return a stub instance. If the interface type being returned is the same
 * as the enclosing type, then the same "this" instance is returned.
 *
 * @author Brian S. O'Neill
 */
final class StubMaker {
    private static final HashMap<Class, MethodHandle> CACHE = new HashMap<>();

    /**
     * Returns a new instance for the stub class.
     */
    @SuppressWarnings("unchecked")
    static <T> T newInstance(Class<T> clazz) {
        try {
            return (T) from(clazz).invoke();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a no-arg constructor for the stub class.
     */
    private static synchronized MethodHandle from(Class clazz) throws Exception {
        MethodHandle mh = CACHE.get(clazz);

        if (mh == null) {
            mh = make(clazz);
            CACHE.put(clazz, mh);
        }

        return mh;
    }

    private static MethodHandle make(Class clazz) throws Exception {
        ClassMaker cm = ClassMaker.begin().public_().final_().synthetic();

        if (clazz.isInterface()) {
            cm.implement(clazz);
        } else {
            cm.extend(clazz);
        }

        cm.addConstructor().private_();

        boolean hasBootstrap = false;

        for (BaseType.Method m : BaseType.from(clazz).allMethods().values()) {
            int mods = m.mFlags;
            if (Modifier.isStatic(mods) || !Modifier.isAbstract(mods)) {
                continue;
            }

            BaseType retType = m.returnType();
            Class retClass = retType.classType();

            MethodMaker mm = cm.addMethod(retType, m.name(), (Object[]) m.paramTypes()).public_();

            if (retType.isPrimitive()) {
                if (retClass != void.class) {
                    mm.return_(mm.var(retType).clear());
                }
            } else if (!retType.isInterface()) {
                mm.return_(null);
            } else if (retClass == clazz) {
                mm.return_(mm.this_());
            } else {
                if (!hasBootstrap) {
                    hasBootstrap = true;
                    MethodMaker boot = cm.addMethod
                        (MethodHandle.class, "$stub$",
                         MethodHandles.Lookup.class, String.class, Class.class, Class.class);
                    boot.private_().static_();
                    MethodHandle from = MethodHandles.lookup().findStatic
                        (StubMaker.class, "from",
                         MethodType.methodType(MethodHandle.class, Class.class));
                    boot.return_(boot.invoke(from, boot.param(3)));
                }

                var stubMh = mm.var(cm).condy("$stub$", retClass).invoke(MethodHandle.class, "_");
                mm.return_(stubMh.invoke(retType, "invoke", null));
            }
        }

        MethodHandles.Lookup lookup = cm.finishLookup();

        return lookup.findConstructor(lookup.lookupClass(), MethodType.methodType(void.class));
    }
}
