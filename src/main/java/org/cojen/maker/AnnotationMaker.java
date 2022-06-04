/*
 *  Copyright 2021 Cojen.org
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
 * Defines the contents of an annotation.
 *
 * @author Brian S O'Neill
 * @see Maker#addAnnotation Maker.addAnnotation
 */
public interface AnnotationMaker {
    /**
     * Put a name-value pair into the annotation, where the value can be a primitive type, a
     * {@link String}, an {@link Enum}, a {@link Class}, an {@code AnnotationMaker}, or an array.
     *
     * @param name annotation element name
     * @param value annotation element value
     * @throws IllegalArgumentException if value is unsupported
     * @throws IllegalStateException if name is already in this annotation
     * @throws IllegalStateException if value is an incorrectly used {@code AnnotationMaker}
     */
    void put(String name, Object value);

    /**
     * Define a new annotation, which can be put into this annotation at most once.
     *
     * @param annotationType name or class which refers to an annotation interface
     * @throws IllegalArgumentException if the annotation type is unsupported
     */
    AnnotationMaker newAnnotation(Object annotationType);
}
