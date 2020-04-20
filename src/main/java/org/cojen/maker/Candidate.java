/*
 *  Copyright (C) 2019 Cojen.org
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

import java.util.Arrays;
import java.util.Comparator;

/**
 * Compares candidate invocation methods by best match.
 *
 * @author Brian S O'Neill
 */
final class Candidate implements Comparator<Type.Method> {
    static final Candidate THE = new Candidate();

    /**
     * Assumes methods have the same name and number of parameters. Comparison ordering rules:
     *
     * 1. Primitive parameters before object parameters.
     * 2. Narrow primitives before wide primitives.
     * 3. Subclass parameters before superclass parameters.
     * 4. Non-array parameters first.
     * 5. Lexicographical parameter class name.
     * 6. Return type comparison.
     * 7. Static before non-static method.
     */
    @Override
    public int compare(Type.Method a, Type.Method b) {
        int result = Arrays.compare(a.paramTypes(), b.paramTypes(), Candidate::compareTypes);
        if (result != 0) {
            return result;
        }
        result = compareTypes(a.returnType(), b.returnType());
        if (result != 0) {
            return result;
        }
        if (a.isStatic() != b.isStatic()) {
            return a.isStatic() ? -1 : 1;
        }
        return 0;
    }

    private static int compareTypes(Type thisType, Type otherType) {
        if (thisType.equals(otherType)) {
            return 0;
        }

        int result = Integer.compare(thisType.typeCode(), otherType.typeCode());
        if (result != 0) {
            return result;
        }

        if (otherType.isAssignableFrom(thisType)) {
            return -1;
        }
        if (thisType.isAssignableFrom(otherType)) {
            return 1;
        }

        if (!thisType.isArray()) {
            if (otherType.isArray()) {
                return -1;
            }
        } else {
            if (!otherType.isArray()) {
                return 1;
            }
            return compareTypes(thisType.elementType(), otherType.elementType());
        }

        return thisType.name().compareTo(otherType.name());
    }
}
