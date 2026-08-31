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
 */
public sealed interface LambdaFunction extends MethodMaker
    permits TheLambdaFunction, ExternalType.MMaker
{
    /**
     * Returns the function interface type.
     */
    Type functionType();
}
