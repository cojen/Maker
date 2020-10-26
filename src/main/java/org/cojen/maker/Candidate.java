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

/**
 * Partial-order comparator for selecting the best method to bind to.
 *
 * @author Brian S O'Neill
 */
final class Candidate {
    /**
     * Compares method which have the same number of parameters, which are known to be all
     * possible candidates to bind to. For a method to be strictly "better" than another, all
     * parameter types must be better or equal based on conversion cost.
     *
     * @param params actual types supplied to the invoke method
     * @return -1 if if method a is better, 1 if b is better, or 0 if neither is strictly better
     */
    public static int compare(Type[] params, Type.Method a, Type.Method b) {
        Type[] aParams = a.paramTypes();
        Type[] bParams = b.paramTypes();

        int best = 0;

        for (int i=0; i<params.length; i++) {
            int cmp = compare(params[i], aParams[i], bParams[i]);
            if (best == 0) {
                best = cmp;
            } else if (cmp != 0 && best != cmp) {
                return 0;
            }
        }

        return best;
    }

    public static int compare(Type param, Type aParam, Type bParam) {
        int aCost = param.canConvertTo(aParam);
        int bCost = param.canConvertTo(bParam);

        if (aCost != bCost) {
            return aCost < bCost ? -1 : 1;
        }

        if (aCost != 0) {
            return 0;
        }

        if (param.equals(aParam)) {
            return param.equals(bParam) ? 0 : -1;
        } else if (param.equals(bParam)) {
            return 1;
        }

        // Both a and b are supertypes of the actual param.

        if (param.isArray()) {
            if (aParam.isArray()) {
                if (bParam.isArray()) {
                    return compare(root(param), root(aParam), root(bParam));
                } else {
                    return -1;
                }
            } else {
                if (bParam.isArray()) {
                    return 1;
                } else {
                    return 0;
                }
            }
        }

        int aSpec = specialization(aParam);
        int bSpec = specialization(bParam);

        return Integer.compare(bSpec, aSpec);
    }

    private static Type root(Type type) {
        while (true) {
            Type next = type.elementType();
            if (next == null) {
                return type;
            }
            type = next;
        }
    }

    private static int specialization(Type type) {
        int spec = 0;
        while ((type = type.superType()) != null) {
            spec++;
        }
        return spec;
    }
}
