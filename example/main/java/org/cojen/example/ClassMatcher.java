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

package org.cojen.example;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cojen.maker.Label;
import org.cojen.maker.MethodMaker;

/**
 * A ClassMatcher is a CallSite which accepts a Class parameter and returns an int, based on a
 * set of matching classes passed to the constructor. If a match is found, the returned int
 * matches the array index of these cases. Otherwise, -1 is returned.
 *
 * <p>For a case to match, the Class.isAssignableFrom method must return true. The
 * implementation doesn't call this method except when building the internal switch statement,
 * which matches against specific Class instances. As new Classes are encountered, the switch
 * statement grows to cover more cases.
 *
 * @author Brian S O'Neill
 */
public class ClassMatcher extends MutableCallSite {
    /**
     * Simple test program.
     */
    public static void main(String[] args) throws Throwable {
        Class[] cases = {
            java.util.Vector.class, java.util.List.class, java.util.Collection.class,
            Exception.class, Throwable.class, String.class
        };

        var matcher = new ClassMatcher(MethodHandles.lookup(), cases);

        Class[] tests = {
            java.util.Set.class, ArrayList.class, java.util.Vector.class, java.util.Stack.class,
            java.util.Map.class, java.util.HashSet.class, Arrays.class,
            null, String.class, Object.class,
            NullPointerException.class, java.io.IOException.class, java.util.Set.class, Error.class
        };

        for (Class test : tests) {
            int result = (int) matcher.dynamicInvoker().invokeExact(test);
            String match = result < 0 ? "???" : cases[result].getName();
            System.out.println((test == null ? "null" : test.getName()) + " -> " + match);
        }
    }

    private final Class[] mCases;

    private Class[] mKnownClasses;
    private int[] mKnownSlots;
    private int mKnownCount;

    public ClassMatcher(MethodHandles.Lookup lookup, Class... cases) {
        super(MethodType.methodType(int.class, Class.class));
        makeDelegator(lookup);
        mCases = cases.clone();
    }

    @Override
    public final void setTarget(MethodHandle target) {
        throw new UnsupportedOperationException();
    }

    /**
     * Is called by generated code.
     *
     * @return the delegator (usually a switch statement)
     */
    @SuppressWarnings("unchecked")
    public synchronized MethodHandle newCase(MethodHandles.Lookup lookup, Class clazz) {
        for (int i=0; i<mKnownCount; i++) {
            if (mKnownClasses[i] == clazz) {
                // Case already exists.
                return getTarget();
            }
        }

        int slot = -1;

        if (clazz != null) for (int i=0; i<mCases.length; i++) {
            Class c = mCases[i];
            if (c != null && c.isAssignableFrom(clazz)) {
                slot = i;
                break;
            }
        }

        if (mKnownClasses == null) {
            mKnownClasses = new Class[2];
            mKnownSlots = new int[2];
        } else if (mKnownClasses.length >= mKnownCount) {
            int newCapacity = mKnownClasses.length << 1;
            mKnownClasses = Arrays.copyOf(mKnownClasses, newCapacity);
            mKnownSlots = Arrays.copyOf(mKnownSlots, newCapacity);
        }

        mKnownClasses[mKnownCount] = clazz;
        mKnownSlots[mKnownCount] = slot;
        mKnownCount++;

        return makeDelegator(lookup);
    }

    /**
     * Makes the delegator and sets it as the current target.
     *
     * @return the delegator (usually a switch statement)
     */
    @SuppressWarnings("unchecked")
    private synchronized MethodHandle makeDelegator(MethodHandles.Lookup lookup) {
        MethodMaker mm = MethodMaker.begin(lookup, int.class, "matcher", Class.class);
        var classVar = mm.param(0);

        Label noMatch = mm.label();

        if (mKnownCount <= 4) {
            // Make a simple if-else chain.

            for (int ix=0; ix<mKnownCount; ix++) {
                Label next = mm.label();
                classVar.ifNe(mKnownClasses[ix], next);
                mm.return_(mKnownSlots[ix]);
                next.here();
            }

            classVar.ifNe(null, noMatch);
            mm.return_(-1);
        } else {
            // Make a switch statement on the Class hash code.

            Label notNull = mm.label();
            classVar.ifNe(null, notNull);
            mm.return_(-1);
            notNull.here();

            // Maps Class hash codes to one or more known array indexes.
            Map<Integer, Object> map = new HashMap<>(mKnownCount);

            for (int i=0; i<mKnownCount; i++) {
                Integer key = mKnownClasses[i].hashCode();
                Object matches = map.get(key);
                if (matches == null) {
                    map.put(key, i);
                } else {
                    List<Integer> matchList;
                    if (matches instanceof List) {
                        matchList = (List<Integer>) matches;
                    } else {
                        matchList = new ArrayList<>();
                        matchList.add((Integer) matches);
                        map.put(key, matchList);
                    }
                    matchList.add(i);
                }
            }

            var cases = new int[map.size()];
            var labels = new Label[cases.length];

            int num = 0;
            for (int hash : map.keySet()) {
                cases[num] = hash;
                labels[num++] = mm.label();
            }

            classVar.invoke("hashCode").switch_(noMatch, cases, labels);

            num = 0;
            for (var e : map.entrySet()) {
                labels[num++].here();

                int hash = e.getKey();
                Object matches = e.getValue();

                int ix;
                if (matches instanceof Integer) {
                    ix = (int) matches;
                } else {
                    var list = (List<Integer>) matches;
                    for (int i=0;; ) {
                        ix = list.get(i++);
                        if (i == list.size()) {
                            break;
                        }
                        Label next = mm.label();
                        classVar.ifNe(mKnownClasses[ix], next);
                        mm.return_(mKnownSlots[ix]);
                        next.here();
                    }
                }

                classVar.ifNe(mKnownClasses[ix], noMatch);
                mm.return_(mKnownSlots[ix]);
            }
        }

        noMatch.here();

        // The default case is handled by a separate method, which is almost never
        // executed. This helps with inlining by keeping the core switch code small.

        MethodMaker defMaker = mm.classMaker().addMethod(int.class, "default", Class.class);
        defMaker.static_().private_();
        makeDefault(defMaker);

        mm.return_(mm.invoke("default", classVar));

        var mh = mm.finish();
        super.setTarget(mh);
        return mh;
    }

    private void makeDefault(MethodMaker mm) {
        var classVar = mm.param(0);
        var matcherVar = mm.var(ClassMatcher.class).setExact(this);
        var lookupVar = mm.var(MethodHandles.class).invoke("lookup");
        var newCaseVar = matcherVar.invoke("newCase", lookupVar, classVar);
        mm.return_(newCaseVar.invoke(int.class, "invokeExact", null, classVar));
    }
}
