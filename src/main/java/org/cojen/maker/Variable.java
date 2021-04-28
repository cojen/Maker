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
 * Represents a variable bound to a body of a {@link MethodMaker method}.
 *
 * @author Brian S O'Neill
 * @see MethodMaker#var
 */
public interface Variable {
    /**
     * Returns the type of this variable, if bound to an existing class. Null is returned if
     * bound to a class which is being made.
     */
    public Class classType();

    /**
     * Returns the type of this variable, if bound to a class which is being made. Null is
     * returned if bound to an existing class.
     */
    public ClassMaker makerType();

    /**
     * Returns the name of this variable, which is null if unnamed.
     */
    public String name();

    /**
     * Optionally assign a variable name.
     *
     * @return this variable
     * @throws IllegalStateException if already named
     */
    public Variable name(String name);

    /**
     * Assign a value to this variable, either from another variable or from a constant. A
     * constant value can be a primitive type (boxed or unboxed), {@code null}, a {@code
     * String}, a {@code Class}, an {@code Enum}, a {@code MethodType}, a {@code
     * MethodHandleInfo}, a {@code ConstantDesc}, or a {@code Constable}.
     *
     * <p>Note that a {@code MethodHandle} can be set with a {@code MethodHandleInfo}, which is
     * converted automatically at link time. Handling of {@code ConstantDesc} and {@code
     * Constable} is also treated specially &mdash; the actual type is determined by the
     * resolved constant.
     *
     * @param value a {@code Variable} or a constant
     * @return this variable
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable cannot be modified, or if it's not
     * compatible with the variable type
     */
    public Variable set(Object value);

    /**
     * Assign an exact object instance this variable, supported only when the class is built
     * dynamically instead of loaded from a file. At runtime, the object instance provided here
     * is exactly the same as referenced by the generated class. For simple constants, the
     * regular set method is preferred.
     *
     * @param value exact object instance to assign
     * @return this variable
     * @throws IllegalStateException if this variable cannot be modified, or if it's not
     * compatible with the variable type
     */
    public Variable setExact(Object value);

    /**
     * Return a new variable with the same type and value as this one.
     *
     * @return the result in a new variable, with the same type as this one
     */
    public Variable get();

    /**
     * Conditional goto if this variable is true. The label doesn't need to be positioned yet.
     */
    public void ifTrue(Label label);

    /**
     * Conditional goto if this variable is false. The label doesn't need to be positioned yet.
     */
    public void ifFalse(Label label);

    /**
     * Conditional goto if this variable is equal to another variable or constant. The label
     * doesn't need to be positioned yet.
     *
     * @param value other {@code Variable} or a constant
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public void ifEq(Object value, Label label);

    /**
     * Conditional goto if this variable is not equal to another variable or constant. The
     * label doesn't need to be positioned yet.
     *
     * @param value other {@code Variable} or a constant
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public void ifNe(Object value, Label label);

    /**
     * Conditional goto if this variable is less than another variable or constant. The label
     * doesn't need to be positioned yet.
     *
     * @param value other {@code Variable} or a constant
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public void ifLt(Object value, Label label);

    /**
     * Conditional goto if this variable is greater than or equal to another variable or
     * constant. The label doesn't need to be positioned yet.
     *
     * @param value other {@code Variable} or a constant
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public void ifGe(Object value, Label label);

    /**
     * Conditional goto if this variable is greater than another variable or constant. The
     * label doesn't need to be positioned yet.
     *
     * @param value other {@code Variable} or a constant
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public void ifGt(Object value, Label label);

    /**
     * Conditional goto if this variable is less than or equal to another variable or
     * constant. The label doesn't need to be positioned yet.
     *
     * @param value other {@code Variable} or a constant
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    public void ifLe(Object value, Label label);

    /**
     * Generates a switch statement against this {@code int} or {@code Integer} variable. None
     * of the labels need to be positioned yet.
     *
     * @param defaultLabel required
     * @throws IllegalArgumentException if the number of cases and labels don't match
     * @throws IllegalStateException if this type cannot be automatically cast to an int
     * or if the number of cases and labels don't match
     */
    public void switch_(Label defaultLabel, int[] cases, Label... labels);

    /**
     * Add this variable with another variable or a constant, and assign the result back to
     * this variable.
     *
     * @param value a {@code Variable} or a constant
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    public void inc(Object value);

    /**
     * Add this variable with another variable or a constant, and assign the result to a new
     * variable.
     *
     * @param value a {@code Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    public Variable add(Object value);

    /**
     * Subtract this variable with another variable or a constant, and assign the result to a
     * new variable.
     *
     * @param value a {@code Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    public Variable sub(Object value);

    /**
     * Multiply this variable with another variable or a constant, and assign the result to a
     * new variable.
     *
     * @param value a {@code Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    public Variable mul(Object value);

    /**
     * Divide this variable with another variable or a constant, and assign the result to a
     * new variable.
     *
     * @param value a {@code Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    public Variable div(Object value);

    /**
     * Compute the division remainder of this variable with another variable or a constant,
     * and assign the result to a new variable.
     *
     * @param value a {@code Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    public Variable rem(Object value);

    /**
     * Determine if this variable is equal to another variable or constant, and assign
     * the result to a new boolean variable.
     *
     * @param value other {@code Variable} or a constant
     * @return the result in a new boolean variable
     * @throws IllegalArgumentException if not given a variable or a constant
     * @see #ifEq
     */
    public Variable eq(Object value);

    /**
     * Determine if this variable is not equal to another variable or constant, and assign the
     * result to a new boolean variable.
     *
     * @param value other {@code Variable} or a constant
     * @return the result in a new boolean variable
     * @throws IllegalArgumentException if not given a variable or a constant
     * @see #ifNe
     */
    public Variable ne(Object value);

    /**
     * Determine if this variable is less than to another variable or constant, and assign the
     * result to a new boolean variable.
     *
     * @param value other {@code Variable} or a constant
     * @return the result in a new boolean variable
     * @throws IllegalArgumentException if not given a variable or a constant
     * @see #ifLt
     */
    public Variable lt(Object value);

    /**
     * Determine if this variable is greater than or equal to another variable or constant, and
     * assign the result to a new boolean variable.
     *
     * @param value other {@code Variable} or a constant
     * @return the result in a new boolean variable
     * @throws IllegalArgumentException if not given a variable or a constant
     * @see #ifGe
     */
    public Variable ge(Object value);

    /**
     * Determine if this variable is greater than to another variable or constant, and assign
     * the result to a new boolean variable.
     *
     * @param value other {@code Variable} or a constant
     * @return the result in a new boolean variable
     * @throws IllegalArgumentException if not given a variable or a constant
     * @see #ifGt
     */
    public Variable gt(Object value);

    /**
     * Determine if this variable is less than or equal to another variable or constant, and
     * assign the result to a new boolean variable.
     *
     * @param value other {@code Variable} or a constant
     * @return the result in a new boolean variable
     * @throws IllegalArgumentException if not given a variable or a constant
     * @see #ifLe
     */
    public Variable le(Object value);

    /**
     * Determine if this variable is an instance of the given class or interface, and assign
     * the result to a new boolean variable.
     *
     * @param type class or interface
     * @return the result in a new boolean variable
     */
    public Variable instanceOf(Object type);

    /**
     * Cast this variable to another type, and assign the result to a new variable. If the
     * variable represents a primitive type, a conversion might be applied.
     *
     * @param type class or interface
     * @return the result in a new variable
     */
    public Variable cast(Object type);

    /**
     * Compute the logical negation of this boolean variable, and assign the result to a new
     * variable.
     *
     * @return the result in a new variable, with the same type as this one
     */
    public Variable not();

    /**
     * Compute the bitwise and of this variable with another variable or a constant, and assign
     * the result to a new variable.
     *
     * @param value a {@code Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    public Variable and(Object value);

    /**
     * Compute the bitwise or of this variable with another variable or a constant, and assign
     * the result to a new variable.
     *
     * @param value a {@code Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    public Variable or(Object value);

    /**
     * Compute the bitwise xor of this variable with another variable or a constant, and assign
     * the result to a new variable.
     *
     * @param value a {@code Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    public Variable xor(Object value);

    /**
     * Compute the bitwise left shift of this variable with another variable or a constant, and
     * assign the result to a new variable.
     *
     * @param value a {@code Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    public Variable shl(Object value);

    /**
     * Compute the bitwise right shift of this variable with another variable or a constant,
     * and assign the result to a new variable.
     *
     * @param value a {@code Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    public Variable shr(Object value);

    /**
     * Compute the bitwise unsigned right shift of this variable with another variable or a
     * constant, and assign the result to a new variable.
     *
     * @param value a {@code Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    public Variable ushr(Object value);

    /**
     * Negate the value of this variable and assign the result to a new variable.
     *
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalStateException if this variable doesn't support the operation
     */
    public Variable neg();

    /**
     * Compute the bitwise complement of this variable and assign the result to a new variable.
     *
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalStateException if this variable doesn't support the operation
     */
    public Variable com();

    /**
     * Box this primitive variable into its object peer. If not a primitive type, then this is
     * equivalent to calling {@link #get get}.
     *
     * @return the result in a new variable
     */
    public Variable box();

    /**
     * Unbox this object variable into its primitive peer. If already a primitive type, then
     * this is equivalent to calling {@link #get get}.
     *
     * @return the result in a new variable
     * @throws IllegalStateException if cannot be unboxed
     */
    public Variable unbox();

    /**
     * Access the length of this array.
     *
     * @return the result in a new int variable
     * @throws IllegalStateException if not an array type
     */
    public Variable alength();

    /**
     * Access an element from this array.
     *
     * @param index {@code Variable} or constant
     * @return the result in a new variable
     * @throws IllegalStateException if not an array type
     */
    public Variable aget(Object index);

    /**
     * Set an element into this array.
     *
     * @param index {@code Variable} or constant
     * @param value {@code Variable} or constant to assign
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if not an array type, or if type doesn't match
     */
    public void aset(Object index, Object value);

    /**
     * Access a static or instance field from the object referred to by this variable.
     *
     * @param name field name
     * @throws IllegalStateException if field isn't found
     */
    public Field field(String name);

    /**
     * Invoke a static or instance method on the object referenced by this variable.
     *
     * @param name method name
     * @param values {@code Variables} or constants
     * @return the result of the method, which is null if void
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if method isn't found
     */
    public Variable invoke(String name, Object... values);

    /**
     * Invoke a static or instance method on the object referenced by this variable.
     *
     * @param returnType method return type
     * @param name method name; can be {@code ".new"} to construct an instance of this variable
     * type
     * @param types method parameter types; can be null to infer from the values
     * @param values {@code Variables} or constants
     * @return the result of the method, which is null if void
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if method isn't found
     */
    public Variable invoke(Object returnType, String name, Object[] types, Object... values);

    /**
     * Returns a {@code MethodHandle} variable which can invoke a static or instance method on
     * the object referenced by this variable. The returned variable is actually a constant,
     * and so it can be supplied as an argument to a {@link Bootstrap bootstrap} method.
     *
     * @param returnType method return type
     * @param name method name; can be {@code ".new"} to construct an instance of this variable
     * type
     * @param types method parameter types; can be null if none
     * @throws IllegalArgumentException if not given a supported type object
     * @throws IllegalStateException if method isn't found
     */
    public Variable methodHandle(Object returnType, String name, Object... types);

    /**
     * Specify a static bootstrap method for dynamically generating methods, as found in the
     * class type of this variable.
     *
     * @param name bootstrap method name
     * @param args constants which are passed to the bootstrap method, not including the
     * first three standard arguments: {@code (Lookup caller, String name, MethodType type)}
     * @see java.lang.invoke
     */
    public Bootstrap indy(String name, Object... args);

    /**
     * Specify a static bootstrap method for dynamically generating constants, as found in the
     * class type of this variable. The variable returned by the bootstrap method cannot be
     * modified. Since it is a constant, it can be supplied as an argument to another bootstrap
     * method.
     *
     * @param name bootstrap method name
     * @param args constants which are passed to the bootstrap method, not including the
     * first three standard arguments: {@code (Lookup caller, String name, Class type)}
     * @see java.lang.invoke
     */
    public Bootstrap condy(String name, Object... args);

    /**
     * Throw the exception object referred to by this variable.
     *
     * @throws IllegalStateException if not an exception type
     */
    public void throw_();

    /**
     * Enter a synchronized block on this variable.
     *
     * @throws IllegalStateException if this variable isn't an object type
     */
    public void monitorEnter();

    /**
     * Exit a synchronized block on this variable.
     *
     * @throws IllegalStateException if this variable isn't an object type
     */
    public void monitorExit();
}
