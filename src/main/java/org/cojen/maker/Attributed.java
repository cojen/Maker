/*
 *  Copyright 2022 Cojen.org
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
 * Defines an entity which can have attributes.
 *
 * @author Brian S O'Neill
 */
public interface Attributed {
    /**
     * Add a generic JVM attribute with an optional constant value. Supported value types are:
     * int, float, long, double, String, Class, byte[], or an array of non-array values.
     */
    public void addAttribute(String name, Object value);
}
