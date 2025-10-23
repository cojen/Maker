/*
 *  Copyright 2025 Cojen.org
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

/**
 * Represents a method {@link MethodMaker#param parameter}. Note that the most commonly used
 * features are inherited from the {@link Variable} interface.
 *
 * @author Brian S. O'Neill
 */
public interface Parameter extends Variable {
    /**
     * Optionally assign a parameter name.
     *
     * @return this parameter
     * @throws IllegalStateException if already named
     */
    @Override
    Parameter name(String name);

    /**
     * Define a signature for this named parameter, which is a string for supporting generic
     * types. The components can be strings or types (class, ClassMaker, etc.), which are
     * concatenated into a single string. Consult the JVM specification for the signature
     * syntax.
     *
     * @throws IllegalArgumentException if given an unsupported component
     * @return this
     */
    @Override
    Parameter signature(Object... components);

    /**
     * Indicate that this parameter is final.
     *
     * @return this
     */
    Parameter final_();

    /**
     * Indicate that this parameter is synthetic, which means that it wasn't implicitly or
     * explicitly declared.
     *
     * @return this
     */
    Parameter synthetic();

    /**
     * Indicate that this parameter is mandated, which means that the compiler has implicitly
     * declared it by necessity.
     *
     * @return this
     */
    Parameter mandated();
}
