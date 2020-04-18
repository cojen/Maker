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
 * Represents a label bound to a body of {@link MethodMaker method}.
 *
 * @author Brian S O'Neill
 */
public interface Label {
    /**
     * Sets the position of the label at the location of next code instruction.
     *
     * @return this label
     * @throws IllegalStateException if label is already positioned
     */
    public Label here();
}
