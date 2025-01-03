/*
 *  Copyright 2020 Cojen.org
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

/**
 * Represents an invoke dynamic bootstrap method which is bound to a {@link MethodMaker
 * method}.
 *
 * @author Brian S O'Neill
 * @see Variable#indy indy
 * @see Variable#condy condy
 */
public interface Bootstrap {
    /**
     * Invoke a dynamically generated constant, or a dynamically generated method that has no
     * parameters.
     *
     * @param type constant type or method return type; can be null for void
     * @param name constant or method name
     * @return the constant value, or the result of the method, which is null if it's void
     * @throws IllegalArgumentException if the type is unsupported
     */
    default Variable invoke(Object type, String name) {
        return invoke(type, name, null);
    }

    /**
     * Invoke a dynamically generated method.
     *
     * @param returnType method return type; can be null for void
     * @param name method name
     * @param types method parameter types; the entire array or individual elements can be null
     * to infer the actual type from the corresponding value
     * @param values variables or constants
     * @return the result of the method, which is null if it's void
     * @throws IllegalArgumentException if a type is unsupported, or if a value isn't a
     * variable or a supported constant
     * @throws IllegalStateException if this is a condy bootstrap
     */
    Variable invoke(Object returnType, String name, Object[] types, Object... values);

    /**
     * @hidden
     */
    default Variable invoke(Object returnType, String name, Object[] types) {
        return invoke(returnType, name, types, BaseType.NO_ARGS);
    }
}
