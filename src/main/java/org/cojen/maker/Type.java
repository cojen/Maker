/*
 *  Copyright 2024 Cojen.org
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
 * Describes a class which is being made, or describes an existing class or primitive type.
 *
 * @author Brian S. O'Neill
 */
public interface Type {
    /**
     * Return a Type corresponding to the given Class.
     */
    static Type from(Class<?> clazz) {
        return BaseType.from(clazz);
    }

    /**
     * Return a Type corresponding to the given class name or type descriptor.
     *
     * @param str class name or type descriptor
     */
    static Type from(String str) {
        return BaseType.from(null, str);
    }

    /**
     * Return a Type corresponding to the given class name or type descriptor, as found by the
     * given ClassLoader.
     *
     * @param str class name or type descriptor
     * @param loader can be null to use the bootstrap ClassLoader
     */
    static Type from(String str, ClassLoader loader) {
        return BaseType.from(loader, str);
    }

    /**
     * Return a Type corresponding to the given object parameter.
     *
     * @param obj Class, String, ClassMaker, Variable, Field, FieldMaker, or ClassDesc
     */
    static Type from(Object obj) {
        return BaseType.from(null, obj);
    }

    /**
     * Return a Type corresponding to the given object parameter, as found by the given
     * ClassLoader.
     *
     * @param obj Class, String, ClassMaker, Variable, Field, FieldMaker, or ClassDesc
     * @param loader can be null to use the bootstrap ClassLoader
     */
    static Type from(Object obj, ClassLoader loader) {
        return BaseType.from(loader, obj);
    }

    /**
     * Returns the name of this type in Java syntax.
     */
    String name();

    /**
     * Returns a descriptor string for this type.
     */
    String descriptor();

    /**
     * Returns the Class corresponding to this type, or else null if this type represents a
     * class which is being made.
     */
    Class<?> classType();

    /**
     * Returns true if this type is an int, boolean, double, etc.
     */
    boolean isPrimitive();

    /**
     * Returns true if this type is an array, an interface, or a class.
     */
    boolean isObject();

    /**
     * Returns true if this type is known to be an interface.
     */
    boolean isInterface();

    /**
     * Returns true if this type is an array.
     */
    boolean isArray();

    /**
     * Returns the element type of this array type, or else null if this type isn't an array.
     */
    Type elementType();

    /**
     * Returns the dimensions of this array type, or else 0 if this type isn't an array.
     */
    int dimensions();

    /**
     * Returns this type as an array, or else adds a dimension if this type is already an
     * array. If this type is annotatable, then the returned instance will also be annotatable,
     * but it won't initially have any annotations.
     */
    Type asArray();

    /**
     * Returns an object type for a primitive type, or else returns this type. If this type is
     * annotatable, then the returned instance will also be annotatable, and it will have a
     * copy of all the annotations added so far.
     */
    Type box();

    /**
     * Returns a primitive type for an object type, or else returns null if not applicable. If
     * this type is annotatable, then the returned instance will also be annotatable, and it
     * will have a copy of all the annotations added so far.
     */
    Type unbox();

    /**
     * Returns a new type instance which supports annotations. If this type is already
     * annotatable, then the new instance will have a copy of all the annotations added so far.
     *
     * <p>Note: The {@code hashCode} and {@code equals} methods ignore annotations because
     * annotations don't affect linkage rules.
     */
    Type annotatable();

    /**
     * Add an annotation to this type, if it's annotatable. Once this type has been used, it
     * becomes {@link freeze frozen} and no more annotations can be added. Instead, call the
     * {@link annotatable annotatable} method to obtain a new instance.
     *
     * @param annotationType name or class which refers to an annotation interface
     * @param visible true if annotation is visible at runtime
     * @throws IllegalStateException if this type isn't annotatable or it's frozen
     * @throws IllegalArgumentException if the annotation type is unsupported
     */
    AnnotationMaker addAnnotation(Object annotationType, boolean visible);

    /**
     * Prevent adding more annotations to this type.
     */
    void freeze();

    /**
     * Returns this type without any annotations.
     */
    Type unannotated();
}
