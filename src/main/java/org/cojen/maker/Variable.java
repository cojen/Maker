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

import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;

/**
 * Represents a variable bound to the body of a {@link MethodMaker method}.
 *
 * @author Brian S O'Neill
 * @see MethodMaker#var
 */
public interface Variable {
    /**
     * Returns the type of this variable, if bound to an existing class. Null is returned if
     * bound to a class which is being made.
     */
    Class<?> classType();

    /**
     * Returns the type of this variable, if bound to a class which is being made. Null is
     * returned if bound to an existing class.
     */
    ClassMaker makerType();

    /**
     * Returns the name of this variable, which is null if unnamed.
     */
    String name();

    /**
     * Optionally assign a variable name.
     *
     * @return this variable
     * @throws IllegalStateException if already named
     */
    Variable name(String name);

    /**
     * Define a signature for this named variable, which is a string for supporting generic
     * types. The components can be strings or types (class, ClassMaker, etc), which are
     * concatenated into a single string. Consult the JVM specification for the signature
     * syntax.
     *
     * @throws IllegalArgumentException if given an unsupported component
     * @throws IllegalStateException if this isn't a plain local variable
     * @return this
     */
    Variable signature(Object... components);

    /**
     * Add an annotation to this variable.
     *
     * @param annotationType name or class which refers to an annotation interface
     * @param visible true if annotation is visible at runtime
     * @throws IllegalArgumentException if the annotation type is unsupported
     * @throws IllegalStateException if this variable doesn't support annotations
     */
    AnnotationMaker addAnnotation(Object annotationType, boolean visible);

    /**
     * Assign a value of 0, false, or null to this variable, depending on its type.
     *
     * @return this variable
     * @throws IllegalStateException if this variable cannot be modified
     */
    Variable clear();

    /**
     * Assign a value to this variable, either from another variable or from a constant. A
     * constant value can be a primitive type (boxed or unboxed), {@code null}, a {@link
     * String}, a {@link Class}, an {@link Enum}, a {@link MethodType}, a {@link
     * MethodHandleInfo}, a {@link ConstantDesc}, or a {@link Constable}.
     *
     * <p>Note that a {@link MethodHandle} can be set with a {@code MethodHandleInfo}, which is
     * converted automatically at link time. Handling of {@code ConstantDesc} and {@code
     * Constable} is also treated specially &mdash; the actual type is determined by the
     * resolved constant.
     *
     * @param value a {@link Variable} or a constant
     * @return this variable
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable cannot be modified, or if it's not
     * compatible with the value type
     */
    Variable set(Object value);

    /**
     * Assign an exact object instance this variable, supported only when the class is built
     * dynamically instead of loaded from a file. At runtime, the object instance provided here
     * is exactly the same as referenced by the generated class. For simple constants, the
     * regular set method is preferred.
     *
     * @param value exact object instance to assign
     * @return this variable
     * @throws IllegalStateException if this variable cannot be modified, or if it's not
     * compatible with the value type
     */
    Variable setExact(Object value);

    /**
     * Return a new variable with the same type and value as this one.
     *
     * @return the result in a new variable, with the same type as this one
     */
    Variable get();

    /**
     * Conditional goto if this variable is true. The label doesn't need to be positioned yet.
     */
    void ifTrue(Label label);

    /**
     * Convenience method to generate conditional code if this variable is true.
     *
     * @param then called to generate the body of the "then" case
     */
    default void ifTrue(Runnable then) {
        Label endLabel = methodMaker().label();
        ifFalse(endLabel);
        then.run();
        endLabel.here();
    }

    /**
     * Convenience method to generate conditional code if this variable is true.
     *
     * @param then called to generate the body of the "then" case
     * @param else_ called to generate the body of the "else" case
     */
    default void ifTrue(Runnable then, Runnable else_) {
        MethodMaker mm = methodMaker();
        Label elseLabel = mm.label();
        ifFalse(elseLabel);
        then.run();
        Label endLabel = mm.label().goto_();
        elseLabel.here();
        else_.run();
        endLabel.here();
    }

    /**
     * Conditional goto if this variable is false. The label doesn't need to be positioned yet.
     */
    void ifFalse(Label label);

    /**
     * Convenience method to generate conditional code if this variable is false.
     *
     * @param then called to generate the body of the "then" case
     */
    default void ifFalse(Runnable then) {
        Label endLabel = methodMaker().label();
        ifTrue(endLabel);
        then.run();
        endLabel.here();
    }

    /**
     * Convenience method to generate conditional code if this variable is false.
     *
     * @param then called to generate the body of the "then" case
     * @param else_ called to generate the body of the "else" case
     */
    default void ifFalse(Runnable then, Runnable else_) {
        MethodMaker mm = methodMaker();
        Label elseLabel = mm.label();
        ifTrue(elseLabel);
        then.run();
        Label endLabel = mm.label().goto_();
        elseLabel.here();
        else_.run();
        endLabel.here();
    }

    /**
     * Conditional goto if this variable is equal to another variable or constant. The label
     * doesn't need to be positioned yet.
     *
     * @param value a {@link Variable} or a constant
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    void ifEq(Object value, Label label);

    /**
     * Convenience method to generate conditional code if this variable is equal to another
     * variable or constant.
     *
     * @param value a {@link Variable} or a constant
     * @param then called to generate the body of the "then" case
     */
    default void ifEq(Object value, Runnable then) {
        Label endLabel = methodMaker().label();
        ifNe(value, endLabel);
        then.run();
        endLabel.here();
    }

    /**
     * Convenience method to generate conditional code if this variable is equal to another
     * variable or constant.
     *
     * @param value a {@link Variable} or a constant
     * @param then called to generate the body of the "then" case
     * @param else_ called to generate the body of the "else" case
     */
    default void ifEq(Object value, Runnable then, Runnable else_) {
        MethodMaker mm = methodMaker();
        Label elseLabel = mm.label();
        ifNe(value, elseLabel);
        then.run();
        Label endLabel = mm.label().goto_();
        elseLabel.here();
        else_.run();
        endLabel.here();
    }

    /**
     * Conditional goto if this variable is not equal to another variable or constant. The
     * label doesn't need to be positioned yet.
     *
     * @param value a {@link Variable} or a constant
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    void ifNe(Object value, Label label);

    /**
     * Convenience method to generate conditional code if this variable is not equal to another
     * variable or constant.
     *
     * @param value a {@link Variable} or a constant
     * @param then called to generate the body of the "then" case
     */
    default void ifNe(Object value, Runnable then) {
        Label endLabel = methodMaker().label();
        ifEq(value, endLabel);
        then.run();
        endLabel.here();
    }

    /**
     * Convenience method to generate conditional code if this variable is not equal to another
     * variable or constant.
     *
     * @param value a {@link Variable} or a constant
     * @param then called to generate the body of the "then" case
     * @param else_ called to generate the body of the "else" case
     */
    default void ifNe(Object value, Runnable then, Runnable else_) {
        MethodMaker mm = methodMaker();
        Label elseLabel = mm.label();
        ifEq(value, elseLabel);
        then.run();
        Label endLabel = mm.label().goto_();
        elseLabel.here();
        else_.run();
        endLabel.here();
    }

    /**
     * Conditional goto if this variable is less than another variable or constant. The label
     * doesn't need to be positioned yet.
     *
     * @param value a {@link Variable} or a constant
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    void ifLt(Object value, Label label);

    /**
     * Convenience method to generate conditional code if this variable is less than another
     * variable or constant.
     *
     * @param value a {@link Variable} or a constant
     * @param then called to generate the body of the "then" case
     */
    default void ifLt(Object value, Runnable then) {
        Label endLabel = methodMaker().label();
        ifGe(value, endLabel);
        then.run();
        endLabel.here();
    }

    /**
     * Convenience method to generate conditional code if this variable is less than another
     * variable or constant.
     *
     * @param value a {@link Variable} or a constant
     * @param then called to generate the body of the "then" case
     * @param else_ called to generate the body of the "else" case
     */
    default void ifLt(Object value, Runnable then, Runnable else_) {
        MethodMaker mm = methodMaker();
        Label elseLabel = mm.label();
        ifGe(value, elseLabel);
        then.run();
        Label endLabel = mm.label().goto_();
        elseLabel.here();
        else_.run();
        endLabel.here();
    }

    /**
     * Conditional goto if this variable is greater than or equal to another variable or
     * constant. The label doesn't need to be positioned yet.
     *
     * @param value a {@link Variable} or a constant
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    void ifGe(Object value, Label label);

    /**
     * Convenience method to generate conditional code if this variable is greater than or
     * equal to another variable or constant.
     *
     * @param value a {@link Variable} or a constant
     * @param then called to generate the body of the "then" case
     */
    default void ifGe(Object value, Runnable then) {
        Label endLabel = methodMaker().label();
        ifLt(value, endLabel);
        then.run();
        endLabel.here();
    }

    /**
     * Convenience method to generate conditional code if this variable is greater than or
     * equal to another variable or constant.
     *
     * @param value a {@link Variable} or a constant
     * @param then called to generate the body of the "then" case
     * @param else_ called to generate the body of the "else" case
     */
    default void ifGe(Object value, Runnable then, Runnable else_) {
        MethodMaker mm = methodMaker();
        Label elseLabel = mm.label();
        ifLt(value, elseLabel);
        then.run();
        Label endLabel = mm.label().goto_();
        elseLabel.here();
        else_.run();
        endLabel.here();
    }

    /**
     * Conditional goto if this variable is greater than another variable or constant. The
     * label doesn't need to be positioned yet.
     *
     * @param value a {@link Variable} or a constant
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    void ifGt(Object value, Label label);

    /**
     * Convenience method to generate conditional code if this variable is greater than another
     * variable or constant.
     *
     * @param value a {@link Variable} or a constant
     * @param then called to generate the body of the "then" case
     */
    default void ifGt(Object value, Runnable then) {
        Label endLabel = methodMaker().label();
        ifLe(value, endLabel);
        then.run();
        endLabel.here();
    }

    /**
     * Convenience method to generate conditional code if this variable is greater than another
     * variable or constant.
     *
     * @param value a {@link Variable} or a constant
     * @param then called to generate the body of the "then" case
     * @param else_ called to generate the body of the "else" case
     */
    default void ifGt(Object value, Runnable then, Runnable else_) {
        MethodMaker mm = methodMaker();
        Label elseLabel = mm.label();
        ifLe(value, elseLabel);
        then.run();
        Label endLabel = mm.label().goto_();
        elseLabel.here();
        else_.run();
        endLabel.here();
    }

    /**
     * Conditional goto if this variable is less than or equal to another variable or
     * constant. The label doesn't need to be positioned yet.
     *
     * @param value a {@link Variable} or a constant
     * @throws IllegalArgumentException if not given a variable or a constant
     */
    void ifLe(Object value, Label label);

    /**
     * Convenience method to generate conditional code if this variable is less than or equal
     * to another variable or constant.
     *
     * @param value a {@link Variable} or a constant
     * @param then called to generate the body of the "then" case
     */
    default void ifLe(Object value, Runnable then) {
        Label endLabel = methodMaker().label();
        ifGt(value, endLabel);
        then.run();
        endLabel.here();
    }

    /**
     * Convenience method to generate conditional code if this variable is less than or equal
     * to another variable or constant.
     *
     * @param value a {@link Variable} or a constant
     * @param then called to generate the body of the "then" case
     * @param else_ called to generate the body of the "else" case
     */
    default void ifLe(Object value, Runnable then, Runnable else_) {
        MethodMaker mm = methodMaker();
        Label elseLabel = mm.label();
        ifGt(value, elseLabel);
        then.run();
        Label endLabel = mm.label().goto_();
        elseLabel.here();
        else_.run();
        endLabel.here();
    }

    /**
     * Generates a switch statement against this {@code int} or non-null {@code Integer}
     * variable. None of the labels need to be positioned yet.
     *
     * @param defaultLabel required
     * @throws IllegalArgumentException if the number of cases and labels don't match
     * @throws IllegalStateException if this type cannot be automatically cast to an int
     */
    void switch_(Label defaultLabel, int[] cases, Label... labels);

    /**
     * Generates a switch statement against this non-null {@code String} variable. None of the
     * labels need to be positioned yet.
     *
     * @param defaultLabel required
     * @throws IllegalArgumentException if the number of cases and labels don't match
     * @throws IllegalStateException if this type isn't a String
     */
    void switch_(Label defaultLabel, String[] cases, Label... labels);

    /**
     * Generates a switch statement against this non-null variable, of any type. None of the
     * labels need to be positioned yet.
     *
     * @param defaultLabel required
     * @throws IllegalArgumentException if the number of cases and labels don't match
     * @throws IllegalStateException if the class being made is {@link ClassMaker#beginExternal
     * external}
     */
    void switch_(Label defaultLabel, Object[] cases, Label... labels);

    /**
     * Add this variable with another variable or a constant, and assign the result back to
     * this variable.
     *
     * @param value a {@link Variable} or a constant
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    void inc(Object value);

    /**
     * Add this variable with another variable or a constant, and assign the result to a new
     * variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    Variable add(Object value);

    /**
     * Subtract this variable with another variable or a constant, and assign the result to a
     * new variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    Variable sub(Object value);

    /**
     * Multiply this variable with another variable or a constant, and assign the result to a
     * new variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    Variable mul(Object value);

    /**
     * Divide this variable with another variable or a constant, and assign the result to a
     * new variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    Variable div(Object value);

    /**
     * Compute the division remainder of this variable with another variable or a constant,
     * and assign the result to a new variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    Variable rem(Object value);

    /**
     * Determine if this variable is equal to another variable or constant, and assign
     * the result to a new boolean variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new boolean variable
     * @throws IllegalArgumentException if not given a variable or a constant
     * @see #ifEq
     */
    Variable eq(Object value);

    /**
     * Determine if this variable is not equal to another variable or constant, and assign the
     * result to a new boolean variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new boolean variable
     * @throws IllegalArgumentException if not given a variable or a constant
     * @see #ifNe
     */
    Variable ne(Object value);

    /**
     * Determine if this variable is less than another variable or constant, and assign the
     * result to a new boolean variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new boolean variable
     * @throws IllegalArgumentException if not given a variable or a constant
     * @see #ifLt
     */
    Variable lt(Object value);

    /**
     * Determine if this variable is greater than or equal to another variable or constant, and
     * assign the result to a new boolean variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new boolean variable
     * @throws IllegalArgumentException if not given a variable or a constant
     * @see #ifGe
     */
    Variable ge(Object value);

    /**
     * Determine if this variable is greater than another variable or constant, and assign
     * the result to a new boolean variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new boolean variable
     * @throws IllegalArgumentException if not given a variable or a constant
     * @see #ifGt
     */
    Variable gt(Object value);

    /**
     * Determine if this variable is less than or equal to another variable or constant, and
     * assign the result to a new boolean variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new boolean variable
     * @throws IllegalArgumentException if not given a variable or a constant
     * @see #ifLe
     */
    Variable le(Object value);

    /**
     * Determine if this variable is an instance of the given class or interface, and assign
     * the result to a new boolean variable.
     *
     * @param type class or interface
     * @return the result in a new boolean variable
     */
    Variable instanceOf(Object type);

    /**
     * Cast this variable to another type, and assign the result to a new variable. If the
     * variable represents a primitive type, a conversion might be applied.
     *
     * @param type class or interface
     * @return the result in a new variable
     */
    Variable cast(Object type);

    /**
     * Compute the logical negation of this boolean variable, and assign the result to a new
     * variable.
     *
     * @return the result in a new variable, with the same type as this one
     */
    Variable not();

    /**
     * Compute the bitwise and of this variable with another variable or a constant, and assign
     * the result to a new variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    Variable and(Object value);

    /**
     * Compute the bitwise or of this variable with another variable or a constant, and assign
     * the result to a new variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    Variable or(Object value);

    /**
     * Compute the bitwise xor of this variable with another variable or a constant, and assign
     * the result to a new variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    Variable xor(Object value);

    /**
     * Compute the bitwise left shift of this variable with another variable or a constant, and
     * assign the result to a new variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    Variable shl(Object value);

    /**
     * Compute the bitwise right shift of this variable with another variable or a constant,
     * and assign the result to a new variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    Variable shr(Object value);

    /**
     * Compute the bitwise unsigned right shift of this variable with another variable or a
     * constant, and assign the result to a new variable.
     *
     * @param value a {@link Variable} or a constant
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if this variable doesn't support the operation, or if
     * value is incompatible
     */
    Variable ushr(Object value);

    /**
     * Negate the value of this variable and assign the result to a new variable.
     *
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalStateException if this variable doesn't support the operation
     */
    Variable neg();

    /**
     * Compute the bitwise complement of this variable and assign the result to a new variable.
     *
     * @return the result in a new variable, with the same type as this one
     * @throws IllegalStateException if this variable doesn't support the operation
     */
    Variable com();

    /**
     * Box this primitive variable into its object peer. If not a primitive type, then this is
     * equivalent to calling {@link #get get}.
     *
     * @return the result in a new variable
     */
    Variable box();

    /**
     * Unbox this object variable into its primitive peer. If already a primitive type, then
     * this is equivalent to calling {@link #get get}.
     *
     * @return the result in a new variable
     * @throws IllegalStateException if this variable cannot be unboxed
     */
    Variable unbox();

    /**
     * Access the length of this array.
     *
     * @return the result in a new int variable
     * @throws IllegalStateException if not an array type
     */
    Variable alength();

    /**
     * Access an element from this array.
     *
     * @param index a {@link Variable} or a constant
     * @return the result in a new variable
     * @throws IllegalStateException if not an array type
     */
    Variable aget(Object index);

    /**
     * Set an element into this array.
     *
     * @param index a {@link Variable} or a constant
     * @param value a {@link Variable} or a constant
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if not an array type, or if type doesn't match
     * @see #set
     */
    void aset(Object index, Object value);

    /**
     * Access a static or instance field from the object referred to by this variable.
     *
     * @param name field name
     * @throws IllegalStateException if field isn't found
     */
    Field field(String name);

    /**
     * Invoke a static or instance method on the object referenced by this variable.
     *
     * @param name method name
     * @param values {@link Variable Variables} or constants
     * @return the result of the method, which is null if void
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if method isn't found
     */
    Variable invoke(String name, Object... values);

    /**
     * @hidden
     */
    default Variable invoke(String name) {
        return invoke(name, Type.NO_ARGS);
    }

    /**
     * Invoke a static or instance method on the object referenced by this variable.
     *
     * @param returnType method return type
     * @param name method name; can be {@code ".new"} to construct an instance of this variable
     * type, and returnType can be null
     * @param types method parameter types; the entire array or individual elements can be null
     * to infer the actual type from the corresponding value
     * @param values {@link Variable Variables} or constants
     * @return the result of the method, which is null if void
     * @throws IllegalArgumentException if not given a variable or a constant
     * @throws IllegalStateException if method isn't found
     */
    Variable invoke(Object returnType, String name, Object[] types, Object... values);

    /**
     * @hidden
     */
    default Variable invoke(Object returnType, String name, Object[] types) {
        return invoke(returnType, name, types, Type.NO_ARGS);
    }

    /**
     * Returns a {@link MethodHandle} variable which can invoke a static or instance method on
     * the object referenced by this variable. The returned variable is actually a constant,
     * and so it can be supplied as an argument to a {@link Bootstrap bootstrap} method or be
     * used by another method in the same class.
     *
     * @param returnType method return type
     * @param name method name; can be {@code ".new"} to construct an instance of this variable
     * type, and returnType can be null
     * @param types method parameter types; can be null if none
     * @throws IllegalArgumentException if not given a supported type object
     * @throws IllegalStateException if method isn't found
     */
    Variable methodHandle(Object returnType, String name, Object... types);

    /**
     * @hidden
     */
    default Variable methodHandle(Object returnType, String name) {
        return methodHandle(returnType, name, Type.NO_ARGS);
    }

    /**
     * Specify a static bootstrap method for dynamically generating methods, as found in the
     * class type of this variable.
     *
     * @param name bootstrap method name
     * @param args constants which are passed to the bootstrap method, not including the
     * first three standard arguments: {@code (Lookup caller, String name, MethodType type)}
     * @see java.lang.invoke
     */
    Bootstrap indy(String name, Object... args);

    /**
     * @hidden
     */
    default Bootstrap indy(String name) {
        return indy(name, Type.NO_ARGS);
    }

    /**
     * Specify a static bootstrap method for dynamically generating constants, as found in the
     * class type of this variable. The variable returned by the bootstrap method cannot be
     * modified. Since it is a constant, it can be supplied as an argument to another bootstrap
     * method or be used by another method in the same class.
     *
     * @param name bootstrap method name
     * @param args constants which are passed to the bootstrap method, not including the
     * first three standard arguments: {@code (Lookup caller, String name, Class type)}
     * @see java.lang.invoke
     */
    Bootstrap condy(String name, Object... args);

    /**
     * @hidden
     */
    default Bootstrap condy(String name) {
        return condy(name, Type.NO_ARGS);
    }

    /**
     * Throw the exception object referred to by this variable.
     *
     * @throws IllegalStateException if not an exception type
     */
    void throw_();

    /**
     * Enter a synchronized block on this variable.
     *
     * @throws IllegalStateException if this variable isn't an object type
     */
    void monitorEnter();

    /**
     * Exit a synchronized block on this variable.
     *
     * @throws IllegalStateException if this variable isn't an object type
     */
    void monitorExit();

    /**
     * Convenience method for defining a synchronized block on this variable.
     *
     * @param body called to generate the body of the synchronized block
     * @throws IllegalStateException if this variable isn't an object type
     */
    void synchronized_(Runnable body);

    /**
     * Returns the {@code MethodMaker} that this variable belongs to.
     */
    MethodMaker methodMaker();
}
