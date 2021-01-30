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
     * @param type constant type or method return type (can be null for void)
     * @param name constant or method name
     * @return the constant value, or the result of the method, which is null if void
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public default Variable invoke(Object type, String name) {
        return invoke(type, name, null);
    }

    /**
     * Invoke a dynamically generated method.
     *
     * @param returnType method return type (can be null for void)
     * @param name method name
     * @param types method parameter types (can be null if none)
     * @param values variables or constants
     * @return the result of the method, which is null if void
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this is a condy bootstrap
     */
    public Variable invoke(Object returnType, String name, Object[] types, Object... values);
}
