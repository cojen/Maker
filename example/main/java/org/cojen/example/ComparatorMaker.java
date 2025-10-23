/*
 *  Copyright 2020 Cojen.org
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

package org.cojen.example;

import java.lang.invoke.MethodHandles;

import java.lang.reflect.Method;

import java.util.Comparator;
import java.util.Objects;

import org.cojen.maker.ClassMaker;
import org.cojen.maker.Label;
import org.cojen.maker.MethodMaker;
import org.cojen.maker.Variable;


/**
 * Simple utility for making custom {@link Comparators} that rely on dynamically generated code.
 *
 * @author Brian S O'Neill
 */
public class ComparatorMaker<T> {
    /**
     * Begin the definition for a root object type.
     */
    public static <T> ComparatorMaker<T> begin(Class<T> type) {
        return new ComparatorMaker<T>(type);
    }

    /**
     * Add an order-by clause based on a method name chain, which is defined as a list of one
     * or more method names separated by '.' characters. If the chain does not return a {@link
     * Comparable} object when {@link #compare compare} is called on the returned comparator,
     * the order-by clause is ignored. Call {@link #using using} to specify a {@link
     * Comparator} to use instead.
     *
     * <p>If chain resolves to a primitive type, the ordering is the same as for its boxed
     * representation. Primitive booleans are ordered false low, true high. Floating point
     * primitives are ordered exactly the same way as {@link Float#compareTo(Float)
     * Float.compareTo} and {@link Double#compareTo(Double) Double.compareTo}.
     *
     * <p>As a convenience, clauses may lead with a '-' or '+' character prefix to specify sort
     * order. A prefix of '-' indicates that the clause is to be sorted in reverse
     * (descending). Ascending is the default, and so a prefix of '+' has no effect.
     *
     * @throws IllegalArgumentException when chain doesn't exist
     * @return this
     */
    public ComparatorMaker<T> orderBy(String chain) {
        int dot = chain.indexOf('.');
        String subChain;
        if (dot < 0) {
            subChain = null;
        } else {
            subChain = chain.substring(dot + 1);
            chain = chain.substring(0, dot);
        }

        boolean reverse = false;
        if (chain.length() > 0) {
            char prefix = chain.charAt(0);
            switch (prefix) {
            default:
                break;
            case '-':
                reverse = true;
                // Fall through
            case '+':
                chain = chain.substring(1);
            }
        }

        Method m;
        try {
            m = mClazz.getMethod(chain);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Method '" + chain + "' not found in '" +
                                               mClazz.getName() + '\'');
        }

        Class<?> type = m.getReturnType();

        if (type == null || type == void.class) {
            throw new IllegalArgumentException("Method '" + chain + "' returns void");
        }

        Clause clause = addClause(m);

        if (subChain != null) {
            clause.mUsing = begin(type).collate(mCollator).orderBy(subChain).finish();
        } else if (m.getReturnType() == String.class) {
            clause.mUsing = mCollator;
        }

        if (reverse) {
            clause.mFlags |= 1;
        }

        return this;
    }

    /**
     * Specifiy a {@code Comparator} to use on just the last {@link #orderBy order-by}
     * clause. This is good for comparing order-by clauses that are not {@link Comparable}, or
     * for applying special ordering rules. If no order-by clauses have been specified yet,
     * then the {@code Comparator} is applied to base type.
     *
     * <p>Any previously applied {@link #caseSensitive case-sensitive} or {@link #collate
     * collator} settings are overridden by the given {@code Comparator}. If the clause being
     * compared is primitive, then the boxed representation is passed to the {@code
     * Comparator}.
     *
     * @param c non-null {@code Comparator} to use on the last order-by clause
     * @return this
     */
    public ComparatorMaker<T> using(Comparator<?> c) {
        Objects.requireNonNull(c);
        Clause clause = mLastClause;
        clause.mUsing = c;
        return this;
    }

    /**
     * Toggle reverse-order option on just the last {@link #orderBy order-by} clause. By
     * default, order is ascending. If no order-by clauses have been specified yet, then
     * reverse order is applied to the base type.
     *
     * @return this
     */
    public ComparatorMaker<T> reverse() {
        mLastClause.mFlags ^= 1;
        return this;
    }

    /**
     * Set the order of comparisons against null as being high (the default) on just the last
     * {@link #orderBy order-by} clause. If no order-by clauses have been specified yet, then
     * null high order is applied to the base type. Note: {@code nullHigh().reverse()} is
     * equivalent to calling {@code reverse().nullLow()}.
     *
     * @return this
     */
    public ComparatorMaker<T> nullHigh() {
        Clause clause = mLastClause;
        clause.mFlags ^= (clause.mFlags & 1) << 1;
        return this;
    }

    /**
     * Set the order of comparisons against null as being low on just the last {@link #orderBy
     * order-by} clause. If no order-by clauses have been specified yet, then null low order is
     * applied to the base type. Note: {@code reverse().nullLow()} is equivalent to calling
     * {@code nullHigh().reverse()}.
     *
     * @return this
     */
    public ComparatorMaker<T> nullLow() {
        Clause clause = mLastClause;
        clause.mFlags ^= ((~clause.mFlags & 1)) << 1;
        return this;
    }

    /**
     * Set a default {@code Comparator} for ordering {@code Strings}, which is applied to the
     * current order-by class and all subsequent ones. Passing null for a collator will revert
     * to to using {@link String#compareTo(String) String.compareTo}.
     *
     * @param c {@code Comparator} to use for ordering all {@code Strings}
     * @return this
     */
    public ComparatorMaker<T> collate(Comparator<String> c) {
        mCollator = c;
        Method m = mLastClause.mOrderBy;
        if (m != null && m.getReturnType() == String.class) {
            mLastClause.mUsing = c;
        }
        return this;
    }

    /**
     * Returns a new {@code Comparator} instance which applies the clauses provided so far.
     */
    @SuppressWarnings("unchecked")
    public Comparator<T> finish() {
        // FIXME: cache it?

        ClassMaker cm = ClassMaker.begin(getClass().getName(), MethodHandles.lookup());
        cm.implement(Comparator.class);
        cm.addConstructor().public_();

        makeCompare(cm.addMethod(int.class, "compare", Object.class, Object.class).public_());

        try {
            return (Comparator<T>) cm.finishHidden().lookupClass().getConstructor().newInstance();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause == null) {
                cause = e;
            }
            throw new IllegalStateException(cause);
        }
    }

    private void makeCompare(MethodMaker mm) {
        Variable param0 = mm.param(0);
        Variable param1 = mm.param(1);

        Variable orderBy0 = param0;
        Variable orderBy1 = param1;

        Clause clause = mFirstClause;
        Class<?> clauseType = Object.class;

        while (true) {
            if ((clause.mFlags & 1) != 0) {
                // Reverse.
                var temp = orderBy0;
                orderBy0 = orderBy1;
                orderBy1 = temp;
                if (clause == mFirstClause) {
                    param0 = orderBy0;
                    param1 = orderBy1;
                }
            }

            Label nextLabel = null;

            if (!clauseType.isPrimitive()) {
                // Handle the case when orderBy0 and orderBy1 are the same or null.

                nextLabel = mm.label();
                orderBy0.ifEq(orderBy1, nextLabel);

                boolean nullHigh = (clause.mFlags & 2) == 0;
                Label cont = mm.label();
                orderBy0.ifNe(null, cont);
                mm.return_(nullHigh ? 1 : -1);
                cont.here();
                cont = mm.label();
                orderBy1.ifNe(null, cont);
                mm.return_(nullHigh ? -1 : 1);
                cont.here();
            }

            assignResult: {
                Variable result;

                if (clause.mUsing != null) {
                    var using = mm.var(Comparator.class).setExact(clause.mUsing);
                    result = using.invoke("compare", orderBy0, orderBy1);
                } else if (clauseType.isPrimitive()) {
                    result = orderBy0.invoke("compare", orderBy0, orderBy1);
                } else if (clause != mFirstClause) {
                    // Assume parameters are Comparable, or cast them at runtime.
                    if (!Comparable.class.isAssignableFrom(clauseType)) {
                        orderBy0 = orderBy0.cast(Comparable.class);
                        orderBy1 = orderBy1.cast(Comparable.class);
                    }
                    result = orderBy0.invoke("compareTo", orderBy1);
                } else {
                    break assignResult;
                }

                if (clause == mLastClause) {
                    mm.return_(result);
                    if (nextLabel != null) {
                        nextLabel.here();
                        mm.return_(0);
                    }
                    return;
                }

                Label cont = mm.label();
                result.ifEq(0, cont);
                mm.return_(result);
                cont.here();
            }

            if (nextLabel != null) {
                nextLabel.here();
            }

            boolean wasFirst = clause == mFirstClause;
            clause = clause.mNext;

            if (clause == null) {
                mm.return_(0);
                return;
            }

            if (wasFirst) {
                // Cast the parameters such that specific methods may be accessed.
                clauseType = mClazz;
                param0 = param0.cast(clauseType);
                param1 = param1.cast(clauseType);
            }

            Method orderBy = clause.mOrderBy;
            clauseType = orderBy.getReturnType();
            String methodName = orderBy.getName();
            var empty = new Object[0];
            orderBy0 = param0.invoke(clauseType, methodName, empty, empty);
            orderBy1 = param1.invoke(clauseType, methodName, empty, empty);
        }
    }

    private final Class<T> mClazz;

    private Clause mFirstClause;
    private Clause mLastClause;

    private Comparator<String> mCollator;

    private ComparatorMaker(Class<T> clazz) {
        mClazz = clazz;
        mFirstClause = mLastClause = new Clause();
    }

    private Clause addClause(Method orderBy) {
        Clause clause = new Clause();
        mLastClause.mNext = clause;
        mLastClause = clause;
        clause.mOrderBy = orderBy;
        return clause;
    }

    private static final class Clause {
        private Clause mNext;

        private Method mOrderBy;

        private Comparator<?> mUsing;

        // bit 0: reverse
        // bit 1: null low order
        private int mFlags;
    }
}
