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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

/**
 * Allows new methods to be defined within a class.
 *
 * @author Brian S O'Neill
 * @see ClassMaker#addMethod
 */
public interface MethodMaker {
    /**
     * Begin defining a standalone method.
     *
     * @param lookup define the method using this lookup object
     * @param retType a class or name; can be null if method returns void
     * @param paramTypes classes or names; can be null if method accepts no parameters
     * @see #finish
     */
    public static MethodMaker begin(MethodHandles.Lookup lookup,
                                    Object retType, Object... paramTypes)
    {
        return begin(lookup, null, retType, paramTypes);
    }

    /**
     * Begin defining a standalone method.
     *
     * @param lookup define the method using this lookup object
     * @param type defines the return type and parameter types
     * @see #finish
     */
    public static MethodMaker begin(MethodHandles.Lookup lookup, MethodType type) {
        if (type == null) {
            type = MethodType.methodType(void.class);
        }
        return begin(lookup, type, type.returnType(), (Object[]) type.parameterArray());
    }

    private static MethodMaker begin(MethodHandles.Lookup lookup, MethodType type,
                                     Object retType, Object... paramTypes)
    {
        Class<?> lookupClass = lookup.lookupClass();
        String name = lookupClass.getName();
        name = name.substring(0, name.lastIndexOf('.') + 1) + "_";
        ClassLoader loader = lookupClass.getClassLoader();
        TheClassMaker cm = TheClassMaker.begin(true, name, loader, null, lookup);

        Type.Method method = cm.defineMethod(retType, "_", paramTypes);

        final MethodType mtype;
        if (type != null) {
            mtype = type;
        } else {
            Type[] ptypes = method.paramTypes();
            var pclasses = new Class[ptypes.length];
            for (int i=0; i<pclasses.length; i++) {
                pclasses[i] = classFor(ptypes[i]);
            }
            mtype = MethodType.methodType(classFor(method.returnType()), pclasses);
        }

        var mm = new TheMethodMaker(cm, method) {
            @Override
            public ClassMaker classMaker() {
                throw new IllegalStateException("Standalone method");
            }

            @Override
            public MethodHandle finish() {
                MethodHandles.Lookup lookup = mClassMaker.finishHidden();
                try {
                    return lookup.findStatic(lookup.lookupClass(), "_", mtype);
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        };

        mm.static_();
        cm.doAddMethod(mm);

        return mm;
    }

    private static Class<?> classFor(Type type) {
        Class<?> clazz = type.clazz();
        if (clazz == null) {
            throw new IllegalStateException("Unknown type: " + type.name());
        }
        return clazz;
    }

    /**
     * Returns the enclosing {@code ClassMaker} for this method.
     *
     * @throws IllegalStateException if this is a standalone method
     */
    public ClassMaker classMaker();

    /**
     * Switch this method to be public. Methods are package-private by default.
     *
     * @return this
     */
    public MethodMaker public_();

    /**
     * Switch this method to be private. Methods are package-private by default.
     *
     * @return this
     */
    public MethodMaker private_();

    /**
     * Switch this method to be protected. Methods are package-private by default.
     *
     * @return this
     */
    public MethodMaker protected_();

    /**
     * Switch this method to be static. Methods are non-static by default.
     *
     * @return this
     */
    public MethodMaker static_();

    /**
     * Switch this method to be final. Methods are non-final by default.
     *
     * @return this
     */
    public MethodMaker final_();

    /**
     * Switch this method to be synchronized. Methods are non-synchronized by default.
     *
     * @return this
     */
    public MethodMaker synchronized_();

    /**
     * Switch this method to be abstract. Methods are non-abstract by default.
     *
     * @return this
     */
    public MethodMaker abstract_();

    /**
     * Switch this method to strictfp mode. Methods are non-strict by default.
     *
     * @return this
     */
    public MethodMaker strictfp_();

    /**
     * Switch this method to be native. Methods are non-native by default.
     *
     * @return this
     */
    public MethodMaker native_();

    /**
     * Indicate that this method is synthetic. Methods are non-synthetic by default.
     *
     * @return this
     */
    public MethodMaker synthetic();

    /**
     * Indicate that this method is a bridge, which implements an inherited method exactly, but
     * it delegates to another method which returns a more specialized return type.
     *
     * @return this
     */
    public MethodMaker bridge();

    /**
     * Indicate that this method supports a variable number of arguments.
     *
     * @return this
     * @throws IllegalStateException if last parameter type isn't an array
     */
    public MethodMaker varargs();

    /**
     * Returns a variable which represents the enclosing class of this method.
     */
    public Variable class_();

    /**
     * Returns the variable which accesses the enclosing object of this method.
     *
     * @throws IllegalStateException if making a static method
     */
    public Variable this_();

    /**
     * Returns a variable which accesses a parameter of the method being built.
     *
     * @param index zero based index
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    public Variable param(int index);

    /**
     * Returns a new unitialized variable with the given type. Call {@link Variable#set set} to
     * initialize it immediately.
     *
     * @param type a class, class name, or a variable
     * @throws IllegalArgumentException if the type is unsupported
     */
    public Variable var(Object type);

    /**
     * Define a line number to represent the location of the next code instruction.
     */
    public void lineNum(int num);

    /**
     * Returns a new label, initially unpositioned. Call {@link Label#here here} to position it
     * immediately.
     */
    public Label label();

    /**
     * Generates an unconditional goto statement to the given label, which doesn't need to be
     * positioned yet.
     */
    public void goto_(Label label);

    /**
     * Generates a return void statement.
     */
    public void return_();

    /**
     * Generates a statement which returns a variable or a constant.
     *
     * @param value variable or constant
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public void return_(Object value);

    /**
     * Access a static or instance field in the enclosing object of this method.
     *
     * @param name field name
     * @throws IllegalStateException if field isn't found
     */
    public Field field(String name);

    /**
     * Invoke a static or instance method on the enclosing object of this method.
     *
     * @param name the method name
     * @param values variables or constants
     * @return the result of the method, which is null if void
     * @throws IllegalArgumentException if not given a variable or a constant
     * @see Variable#invoke
     */
    public Variable invoke(String name, Object... values);

    /**
     * Invoke a static or instance super class method on the enclosing object of this method.
     *
     * @param name the method name
     * @param values variables or constants
     * @return the result of the method, which is null if void
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public Variable invokeSuper(String name, Object... values);

    /**
     * Invoke a super class constructor method on the enclosing object of this method, from
     * within a constructor.
     *
     * @param values variables or constants
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public void invokeSuperConstructor(Object... values);

    /**
     * Invoke a this constructor method on the enclosing object of this method, from within a
     * constructor.
     *
     * @param values variables or constants
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public void invokeThisConstructor(Object... values);

    /**
     * Invoke a method via a {@code MethodHandle}, which only works when the class is built
     * dynamically instead of loaded from a file.
     *
     * @param handle runtime method handle
     * @param values variables or constants
     * @return the result of the method, which is null if void
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public Variable invoke(MethodHandle handle, Object... values);

    /**
     * Allocate a new object. If type is an ordinary object, a matching constructor is
     * invoked. If type is an array, no constructor is invoked, and the given values represent
     * array dimension sizes.
     *
     * @param type class name or {@code Class} instance
     * @param values variables or constants
     * @return the new object
     * @throws IllegalArgumentException if the type is unsupported, or if the constructor isn't
     * found
     */
    public Variable new_(Object type, Object... values);

    /**
     * Define an exception handler here, which catches exceptions between the given labels. Any
     * code prior to the handler must not flow into it directly.
     *
     * @param type exception type to catch; pass null to catch anything
     * @return a variable which references the exception instance
     */
    public Variable catch_(Label start, Label end, Object type);

    /**
     * Define a finally handler which is generated for every possible exit path between the
     * start label and here.
     *
     * @param handler called for each exit path to generate handler code
     */
    public void finally_(Label start, Runnable handler);

    /**
     * Concatenate variables and constants together into a new {@code String} in the same
     * matter as the Java concatenation operator. If no values are given, the returned variable
     * will refer to the empty string.
     *
     * @param values variables or constants
     * @return the result in a new {@code String} variable
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public Variable concat(Object... values);

    /**
     * Access a {@code VarHandle} via a pseudo field, which only works when the class is built
     * dynamically instead of loaded from a file. All of the coordinate values must be provided
     * up front, which are then used each time the {@code VarHandle} is accessed. Variable
     * coordinates are read each time the access field is used &mdash; they aren't fixed to the
     * initial value. In addition, the array of coordinates values isn't cloned, permitting
     * changes without needing to obtain a new access field.
     *
     * <p>A {@code VarHandle} can also be accessed by calling {@link VarHandle#toMethodHandle
     * toMethodHandle}, which is then passed to the {@link #invoke(MethodHandle, Object...)
     * invoke} method.
     *
     * @param handle runtime variable handle
     * @param values variables or constants for each coordinate
     * @return a pseudo field which accesses the variable
     * @throws IllegalArgumentException if not given a variable or a constant, or if the number
     * of values doesn't match the number of coordinates
     */
    public Field access(VarHandle handle, Object... values);

    /**
     * Append an instruction which does nothing, which can be useful for debugging.
     */
    public void nop();

    /**
     * Finishes the definition of a standalone method.
     *
     * @throws IllegalStateException if already finished or if not a standalone method
     */
    public MethodHandle finish();
}
