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
import java.lang.invoke.VarHandle;

/**
 * Represents a field accessible by the body of a {@link MethodMaker method}. Note that the
 * most commonly used features are inherited from the {@link Variable} interface.
 *
 * @author Brian S O'Neill
 * @see MethodMaker#field
 */
public interface Field extends Variable {
    /**
     * Fields cannot be renamed, and so this method always throws an {@link
     * IllegalStateException}.
     */
    @Override
    default Field name(String name) {
        throw new IllegalStateException("Already named");
    }

    /**
     * Fields cannot have a signature defined within the body of a method, and so this method
     * always throws an {@link IllegalStateException}.
     */
    @Override
    default Field signature(Object... components) {
        throw new IllegalStateException("Cannot define a signature");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Field set(Object value);

    /**
     * Access the field using plain mode, ignoring any volatile declaration.
     *
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#get
     */
    Variable getPlain();

    /**
     * Set the field using plain mode, ignoring any volatile declaration.
     *
     * @see VarHandle#set
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    void setPlain(Object value);

    /**
     * Access the field using opaque mode.
     *
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getOpaque
     */
    Variable getOpaque();

    /**
     * Set the field using opaque mode.
     *
     * @see VarHandle#setOpaque
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    void setOpaque(Object value);

    /**
     * Access the field using acquire mode.
     *
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAcquire
     */
    Variable getAcquire();

    /**
     * Set the field using release mode.
     *
     * @see VarHandle#setRelease
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    void setRelease(Object value);

    /**
     * Access the field as if it was declared volatile.
     *
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getVolatile
     */
    Variable getVolatile();

    /**
     * Set the field as if it was declared volatile.
     *
     * @see VarHandle#setVolatile
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    void setVolatile(Object value);

    /**
     * @return the result in a new boolean variable
     * @see VarHandle#compareAndSet
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable compareAndSet(Object expectedValue, Object newValue);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#compareAndExchange
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable compareAndExchange(Object expectedValue, Object newValue);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#compareAndExchangeAcquire
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable compareAndExchangeAcquire(Object expectedValue, Object newValue);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#compareAndExchangeRelease
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable compareAndExchangeRelease(Object expectedValue, Object newValue);

    /**
     * @return the result in a new boolean variable
     * @see VarHandle#weakCompareAndSetPlain
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable weakCompareAndSetPlain(Object expectedValue, Object newValue);

    /**
     * @return the result in a new boolean variable
     * @see VarHandle#weakCompareAndSet
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable weakCompareAndSet(Object expectedValue, Object newValue);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#weakCompareAndSetAcquire
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable weakCompareAndSetAcquire(Object expectedValue, Object newValue);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#weakCompareAndSetRelease
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable weakCompareAndSetRelease(Object expectedValue, Object newValue);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndSet
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable getAndSet(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndSetAcquire
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable getAndSetAcquire(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndSetRelease
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable getAndSetRelease(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndAdd
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable getAndAdd(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndAddAcquire
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable getAndAddAcquire(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndAddRelease
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable getAndAddRelease(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseOr
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable getAndBitwiseOr(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseOrAcquire
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable getAndBitwiseOrAcquire(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseOrRelease
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable getAndBitwiseOrRelease(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseAnd
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable getAndBitwiseAnd(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseAndAcquire
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable getAndBitwiseAndAcquire(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseAndRelease
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable getAndBitwiseAndRelease(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseXor
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable getAndBitwiseXor(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseXorAcquire
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable getAndBitwiseXorAcquire(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseXorRelease
     * @see <a href="package-summary.html#types-and-values-heading">Types and Values</a>
     */
    Variable getAndBitwiseXorRelease(Object value);

    /**
     * Returns a {@link VarHandle} variable which accesses the field. If this is an ordinary
     * field, the variable is actually a constant, and so it can be supplied as an argument to
     * a {@link Bootstrap bootstrap} method or be used by another method in the same class. For
     * non-static fields, the first {@code VarHandle} coordinate is the object instance which
     * owns the field. Static fields have no coordinates.
     */
    Variable varHandle();

    /**
     * Returns a {@link MethodHandle} variable for setting the field value. If this is an
     * ordinary field, the variable is actually a constant, and so it can be supplied as an
     * argument to a {@link Bootstrap bootstrap} method or be used by another method in the
     * same class. For non-static fields, the {@code MethodHandle} accepts two arguments: the
     * object instance which owns the field, and the value to set. For static fields, the only
     * argument is the value to set.
     */
    Variable methodHandleSet();

    /**
     * Returns a {@link MethodHandle} variable for getting the field value. If this is an
     * ordinary field, the variable is actually a constant, and so it can be supplied as an
     * argument to a {@link Bootstrap bootstrap} method or be used by another method in the
     * same class. For non-static fields, the {@code MethodHandle} accepts one argument: the
     * object instance which owns the field. For static fields, there are no arguments.
     */
    Variable methodHandleGet();
}
