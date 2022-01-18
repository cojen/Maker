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
 * Base interface for making classes, methods, and fields.
 *
 * @author Brian S O'Neill
 */
public interface Maker {
    /**
     * Switch this item to be public.
     *
     * @return this
     */
    public Maker public_();

    /**
     * Switch this item to be private.
     *
     * @return this
     */
    public Maker private_();

    /**
     * Switch this item to be protected.
     *
     * @return this
     */
    public Maker protected_();

    /**
     * Switch this item to be static.
     *
     * @return this
     */
    public Maker static_();

    /**
     * Switch this item to be final.
     *
     * @return this
     */
    public Maker final_();

    /**
     * Indicate that this item is synthetic.
     *
     * @return this
     */
    public Maker synthetic();

    /**
     * Returns the {@code ClassMaker} for this item, which can also be used as a type
     * specification.
     */
    public ClassMaker classMaker();

    /**
     * Add an annotation to this item.
     *
     * @param annotationType name or class which refers to an annotation interface
     * @param visible true if annotation is visible at runtime
     * @throws IllegalArgumentException if the annotation type is unsupported
     */
    public AnnotationMaker addAnnotation(Object annotationType, boolean visible);

    /**
     * Add a generic JVM attribute with an optional constant value. This is an advanced feature
     * and not typically used. Supported value types are: int, float, long, double, String,
     * Class, byte[], or an array of non-array values.
     */
    public void addAttribute(String name, Object value);
}
