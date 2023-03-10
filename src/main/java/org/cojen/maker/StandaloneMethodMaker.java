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

/**
 * 
 *
 * @author Brian S O'Neill
 */
final class StandaloneMethodMaker extends TheMethodMaker {
    private final String mName;
    private final MethodType mType;

    StandaloneMethodMaker(TheClassMaker classMaker, Type.Method method,
                          String name, MethodType type)
    {
        super(classMaker, method);
        mName = name;
        mType = type;
    }

    @Override
    public MethodHandle finish() {
        MethodHandles.Lookup lookup = mClassMaker.finishHidden();
        try {
            return lookup.findStatic(lookup.lookupClass(), mName, mType);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
