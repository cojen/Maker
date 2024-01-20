/*
 *  Copyright 2023 Cojen.org
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

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

/**
 * Generates more types of switch statements.
 *
 * @author Brian S O'Neill
 * @hidden
 */
public final class Switcher {
    static void switchString(MethodMaker mm, Variable condition,
                             Label defaultLabel, String[] keys, Label... labels)
    {
        if (condition.classType() != String.class) {
            throw new IllegalStateException("Not switching on a String type");
        }

        doSwitch(false, mm, condition, defaultLabel, keys, labels);
    }

    static void switchObject(MethodMaker mm, Variable condition,
                             Label defaultLabel, Object[] keys, Label... labels)
    {
        Class<?> type = condition.classType();
        if (type != null && type.isPrimitive()) {
            condition = condition.box();
        }

        doSwitch(true, mm, condition, defaultLabel, keys, labels);
    }

    static void switchExternal(MethodMaker mm, Variable condition,
                               Label defaultLabel, Object[] keys, Label... labels)
    {
        checkArgs(keys, labels);

        if (keys.length <= 2) {
            for (int i=0; i<keys.length; i++) {
                check(false, condition, keys[i], labels[i]);
            }
            defaultLabel.goto_();
            return;
        }

        var cases = new int[labels.length];
        for (int i=0; i<cases.length; i++) {
            cases[i] = i;
        }
        var indy = mm.var(Switcher.class).indy("ordinals", (Object[]) keys);
        var ordinalVar = indy.invoke(int.class, "_", null, condition);
        ordinalVar.switch_(defaultLabel, cases, labels);
    }

    /**
     * Bootstrap method which makes a method that accepts a single argument and returns a
     * zero-based ordinal value corresponding to one of the case keys. If none match, -1 is
     * returned.
     */
    public static CallSite ordinals(MethodHandles.Lookup lookup, String name, MethodType type,
                                    Object... keys)
    {
        if (type.returnType() != int.class || type.parameterCount() != 1) {
            throw new IllegalArgumentException();
        }

        MethodMaker mm = MethodMaker.begin(lookup, name, type);

        var labels = new Label[keys.length];
        for (int i=0; i<labels.length; i++) {
            labels[i] = mm.label();
        }

        Label defaultLabel = mm.label();

        switchObject(mm, mm.param(0), defaultLabel, keys, labels);

        for (int i=0; i<labels.length; i++) {
            labels[i].here();
            mm.return_(i);
        }
        defaultLabel.here();
        mm.return_(-1);

        return new ConstantCallSite(mm.finish());
    }

    private static void checkArgs(Object[] keys, Label... labels) {
        if (keys.length != labels.length) {
            throw new IllegalArgumentException("Number of cases and labels doesn't match");
        }
        for (Object key : keys) {
            Objects.requireNonNull(key, "Case cannot be null");
        }
    }

    @SuppressWarnings("unchecked")
    private static void doSwitch(boolean exact, MethodMaker mm, Variable condition,
                                 Label defaultLabel, Object[] keys, Label... labels)
    {
        checkArgs(keys, labels);

        if (keys.length <= 2) {
            for (int i=0; i<keys.length; i++) {
                check(exact, condition, keys[i], labels[i]);
            }
            defaultLabel.goto_();
            return;
        }

        var hashMatches = new HashMap<Integer, Object>();

        for (int i=0; i<keys.length; i++) {
            Object key = keys[i];
            var match = new Match(key, labels[i]);
            Integer hash = key.hashCode();
            Object matches = hashMatches.get(hash);

            if (matches == null) {
                hashMatches.put(hash, match);
            } else {
                ArrayList<Object> list;
                if (matches instanceof ArrayList) {
                    list = (ArrayList<Object>) matches;
                } else {
                    list = new ArrayList<>();
                    list.add(matches);
                    hashMatches.put(hash, list);
                }
                list.add(match);
            }
        }

        var hashCases = new int[hashMatches.size()];
        var hashLabels = new Label[hashCases.length];

        int i = 0;
        for (Integer hash : hashMatches.keySet()) {
            hashCases[i] = hash;
            hashLabels[i++] = mm.label();
        }

        condition.invoke("hashCode").switch_(defaultLabel, hashCases, hashLabels);

        i = 0;
        for (Object matches : hashMatches.values()) {
            hashLabels[i++].here();

            if (matches instanceof ArrayList list) {
                for (var match : list) {
                    ((Match) match).addCheck(exact, condition);
                }
            } else {
                ((Match) matches).addCheck(exact, condition);
            }

            mm.goto_(defaultLabel);
        }
    }

    private record Match(Object key, Label label) {
        void addCheck(boolean exact, Variable condition) {
            check(exact, condition, key, label);
        }
    }

    private static void check(boolean exact, Variable condition, Object key, Label label) {
        if (exact) {
            key = condition.methodMaker().var(Object.class).setExact(key);
        }
        condition.invoke("equals", key).ifTrue(label);
    }
}
