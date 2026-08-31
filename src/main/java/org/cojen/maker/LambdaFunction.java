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

/**
 * Allows a lambda function to be defined. This interface extends {@link MethodMaker} for
 * adding code to the lambda function implementation. The first parameters to the method are
 * the captured values, and the rest are the actual function parameters.
 *
 * @author Brian S. O'Neill
 * @see ClassMaker#addLambdaFunction
 * @see MethodMaker#create
 */
public sealed interface LambdaFunction extends MethodMaker
    permits TheLambdaFunction, ExternalType.MMaker
{
    /**
     * Returns the function interface type.
     */
    Type functionType();

    /**
     * Adds a marker interface that the generated function should implement. The marker
     * interface shouldn't have any abstract methods.
     *
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    void addMarkerInterface(Object markerType);

    /**
     * Adds a descriptor for a bridge method that the generated function class should define.
     * A bridge method has a more specific signature than the primary function method.
     *
     * @param retType a class or name; can be null if the method returns void
     * @param paramTypes classes or names
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    void addBridgeMethod(Object retType, Object... paramTypes);
}
