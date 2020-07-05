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

import java.io.OutputStream;
import java.io.IOException;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.security.ProtectionDomain;

import java.util.Objects;

/**
 * Allows new classes to be defined dynamically. {@code ClassMaker} instances and all objects
 * that interact with them aren't thread-safe.
 *
 * @author Brian S O'Neill
 */
public interface ClassMaker {
    /**
     * Begin defining a class with an automatically assigned name.
     */
    public static ClassMaker begin() {
        return begin(null, (String) null, null, null);
    }

    /**
     * Begin defining a class with the given name. The actual name will have a suffix applied
     * to ensure uniqueness.
     *
     * @param className fully qualified class name; pass null to use default
     */
    public static ClassMaker begin(String className) {
        return begin(className, (String) null, null, null);
    }

    /**
     * Begin defining a class with the given name. The actual name will have a suffix applied
     * to ensure uniqueness.
     *
     * @param className fully qualified class name; pass null to use default
     * @param superClass Class or String; pass null to use Object.
     */
    public static ClassMaker begin(String className, Object superClass) {
        return begin(className, superClass, null, null);
    }

    /**
     * Begin defining a class with the given name. The actual name will have a suffix applied
     * to ensure uniqueness.
     *
     * @param className fully qualified class name; pass null to use default
     * @param superClass Class or String; pass null to use Object.
     * @param parentLoader parent class loader; pass null to use default
     */
    public static ClassMaker begin(String className, Object superClass,
                                   ClassLoader parentLoader)
    {
        return begin(className, superClass, parentLoader, null);
    }

    /**
     * Begin defining a class with the given name. The actual name will have a suffix applied
     * to ensure uniqueness.
     *
     * @param className fully qualified class name; pass null to use default
     * @param superClass Class or String; pass null to use Object.
     * @param parentLoader parent class loader; pass null to use default
     * @param domain to define class in; pass null to use default
     */
    public static ClassMaker begin(String className, Object superClass,
                                   ClassLoader parentLoader, ProtectionDomain domain)
    {
        return new TheClassMaker(className, superClass, parentLoader, domain, null);
    }

    /**
     * Begin defining a class with the given name. The actual name will have a suffix applied
     * to ensure uniqueness.
     *
     * @param className fully qualified class name; pass null to use default
     * @param superClass Class or String; pass null to use Object.
     * @param lookup finish loading the class using this lookup object
     */
    public static ClassMaker begin(String className, Object superClass,
                                   MethodHandles.Lookup lookup)
    {
        Objects.requireNonNull(lookup);
        ClassLoader loader = lookup.lookupClass().getClassLoader();
        return new TheClassMaker(className, superClass, loader, null, lookup);
    }

    /**
     * Begin defining another class with the same loader, domain, and lookup as this one. The
     * actual class name will have a suffix applied to ensure uniqueness.
     *
     * @param className fully qualified class name; pass null to use default
     * @param superClass Class or String; pass null to use Object.
     * @see addClass
     */
    public ClassMaker another(String className, Object superClass);

    /**
     * Switch this class to be public. Classes are package-private by default.
     *
     * @return this
     */
    public ClassMaker public_();

    /**
     * Switch this class to be final. Classes are non-final by default.
     *
     * @return this
     */
    public ClassMaker final_();

    /**
     * Switch this class to be an interface. Classes are classes by default.
     *
     * @return this
     */
    public ClassMaker interface_();

    /**
     * Switch this class to be abstract. Classes are non-abstract by default.
     *
     * @return this
     */
    public ClassMaker abstract_();

    /**
     * Indicate that this class is synthetic. Classes are non-synthetic by default.
     *
     * @return this
     */
    public ClassMaker synthetic();

    /**
     * Add an interface that this class implements.
     *
     * @param iface Class or String
     * @return this
     */
    public ClassMaker implement(Object iface);

    /**
     * Add a field to the class.
     *
     * @param type a class or name
     * @throws IllegalStateException if field is already defined
     */
    public FieldMaker addField(Object type, String name);
 
    /**
     * Add a method to this class.
     *
     * @param retType a class or name; can be null if method returns void
     * @param paramTypes classes or names; can be null if method accepts no parameters
     */
    public MethodMaker addMethod(Object retType, String name, Object... paramTypes);

    /**
     * Add a method to this class.
     *
     * @param type defines the return type and parameter types
     */
    public default MethodMaker addMethod(String name, MethodType type) {
        return addMethod(type.returnType(), name, (Object[]) type.parameterArray());
    }

    /**
     * Add a constructor to this class.
     *
     * @param paramTypes classes or names; can be null if constructor accepts no parameters
     */
    public MethodMaker addConstructor(Object... paramTypes);

    /**
     * Add a constructor to this class.
     *
     * @param type defines the parameter types
     */
    public default MethodMaker addConstructor(MethodType type) {
        return addConstructor((Object[]) type.parameterArray());
    }

    /**
     * Add code to the static initializer of this class. Multiple initializers can be added,
     * and they're all stitched together when the class definition is finished. Returning from
     * one initializer only breaks out of the local scope.
     */
    public MethodMaker addClinit();

    /**
     * Add a nested class to this class.
     *
     * @param className simple class name; pass null to use default
     * @param superClass Class or String; pass null to use Object.
     * @throws IllegalArgumentException if not given a simple class name
     * @see another
     */
    public ClassMaker addClass(String className, Object superClass);

    /**
     * Set the source file of this class file by adding a source file attribute.
     *
     * @return this
     */
    public ClassMaker sourceFile(String fileName);

    /**
     * Returns the name of the class being made.
     */
    public String name();

    /**
     * Finishes the definition of the new class.
     *
     * @throws IllegalStateException if already finished or if the definition is broken
     */
    public Class<?> finish();

    /**
     * Finishes the definition of a new hidden class, using the lookup passed to the begin
     * method. Hidden classes are automatically unloaded when no longer referenced, even if the
     * class loader still is.
     *
     * <p>This feature is only fully supported in Java 15. Hidden classes created with earlier
     * versions don't support all the lookup features.
     *
     * @return the lookup for the class; call {@code lookupClass} to obtain the actual class
     * @throws IllegalStateException if already finished, or if the definition is broken, or if
     * no lookup object was passed to the begin method
     */
    public MethodHandles.Lookup finishHidden();

    /**
     * Finishes the definition of the new class and writes it to a stream.
     *
     * @throws IllegalStateException if already finished or if the definition is broken
     */
    public void finishTo(OutputStream out) throws IOException;
}
