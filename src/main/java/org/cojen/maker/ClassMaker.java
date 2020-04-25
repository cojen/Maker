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

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.security.ProtectionDomain;

/**
 * Allows new classes to be defined dynamically.
 *
 * @author Brian S O'Neill
 */
public interface ClassMaker {
    /**
     * Begin defining a class with an automatically assigned name
     */
    public static ClassMaker begin() {
        return begin(null, (String) null, null, null);
    }

    /**
     * @param className fully qualified class name; pass null to use default
     */
    public static ClassMaker begin(String className) {
        return begin(className, (String) null, null, null);
    }

    /**
     * @param className fully qualified class name; pass null to use default
     * @param superClass super class; pass null to use Object.
     */
    public static ClassMaker begin(String className, Class superClass) {
        String superClassName = superClass == null ? (String) null : superClass.getName();
        return begin(className, superClassName, null, null);
    }

    /**
     * @param className fully qualified class name; pass null to use default
     * @param superClassName fully qualified super class name; pass null to use Object.
     */
    public static ClassMaker begin(String className, String superClassName) {
        return begin(className, superClassName, null, null);
    }

    // TODO: Define a static hashtable (with weak refs) to all ClassMakers which have the same
    // parent loader and protection domain. If necessary, also define a "finishLater" method
    // which returns a Supplier<Class>. This allows classes being made to refer to other ones
    // being made, perhaps in a cycle.

    /**
     * @param className fully qualified class name; pass null to use default
     * @param superClassName fully qualified super class name; pass null to use Object.
     * @param parentLoader parent class loader; pass null to use default
     */
    public static ClassMaker begin(String className, String superClassName,
                                   ClassLoader parentLoader)
    {
        return begin(className, superClassName, parentLoader, null);
    }

    /**
     * @param className fully qualified class name; pass null to use default
     * @param superClassName fully qualified super class name; pass null to use Object.
     * @param parentLoader parent class loader; pass null to use default
     * @param domain to define class in; pass null to use default
     */
    public static ClassMaker begin(String className, String superClassName,
                                   ClassLoader parentLoader, ProtectionDomain domain)
    {
        return new TheClassMaker(className, superClassName, parentLoader, domain);
    }

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
     * Add an interface that this class implements.
     *
     * @return this
     */
    public ClassMaker implement(String interfaceName);

    /**
     * Add an interface that this class implements.
     *
     * @return this
     */
    public default ClassMaker implement(Class iface) {
        return implement(iface.getName());
    }

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
     * @throws IllegalStateException if definition is broken
     */
    public Class<?> finish();

    /**
     * Finishes the definition of a new hidden class, using the loader and protection domain of
     * the given lookup. Hidden classes are automatically unloaded when no longer referenced,
     * even if the class loader still is.
     *
     * <p>This feature is only fully supported in Java 15. Hidden classes created with earlier
     * versions don't support all the lookup features.
     *
     * @param lookup can pass null to use caller lookup
     * @return the lookup for the class; call lookupClass to obtain the actual class
     * @throws IllegalStateException if definition is broken
     */
    public MethodHandles.Lookup finishHidden(MethodHandles.Lookup lookup)
        throws IllegalAccessException;

    /**
     * Finishes the definition of the new class and writes it to a stream.
     *
     * @throws IllegalStateException if definition is broken
     */
    public default void finishTo(OutputStream out) throws IOException {
        if (!(out instanceof DataOutput)) {
            out = new DataOutputStream(out);
        }
        finishTo((DataOutput) out);
    }

    /**
     * Finishes the definition of the new class and writes it to a stream.
     *
     * @throws IllegalStateException if definition is broken
     */
    public void finishTo(DataOutput dout) throws IOException;
}
