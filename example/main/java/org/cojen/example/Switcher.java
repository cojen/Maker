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

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.util.ArrayList;
import java.util.HashMap;

import org.cojen.maker.ClassMaker;
import org.cojen.maker.Label;
import org.cojen.maker.MethodMaker;
import org.cojen.maker.Variable;

/**
 * Example utility which generates switch statements for any kind of hashable key. The
 * generated class can only be dynamically loaded instead of loaded from a file.
 *
 * <p>For classes to be loaded externally, the 'ordinals' bootstrap method can be used
 * alongside a normal switch instruction. External classes require that all case keys be
 * Constable because they get passed as invokedynamic bootstrap arguments.
 *
 * @author Brian S O'Neill
 */
public class Switcher {

    /**
     * @params args one or more conditions to evaluate
     */
    public static void main(String[] args) throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "check", String.class).public_().static_();

        // Note: the last two cases are there to test hash collisions.
        String[] caseKeys = {"apple", "banana", "cherry", "grape", "orange", "Ea", "FB"};
        Label[] labels = new Label[caseKeys.length];
        for (int i=0; i<labels.length; i++) {
            labels[i] = mm.label();
        }

        var out = mm.var(System.class).field("out");

        Label defaultLabel = mm.label();
        switchExact(mm, mm.param(0), defaultLabel, caseKeys, labels);

        for (int i=0; i<labels.length; i++) {
            labels[i].here();
            out.invoke("println", mm.concat("Slot found for ", mm.param(0), ": ", i));
            mm.return_();
        }

        defaultLabel.here();
        out.invoke("println", mm.concat("Not found: ", mm.param(0)));

        Class<?> clazz = cm.finish();
        var method = clazz.getMethod("check", String.class);

        for (String arg : args) {
            method.invoke(null, arg);
        }

        // Now use the 'ordinals' bootstrap method.

        cm = ClassMaker.beginExternal("org.cojen.example.Foo").public_();
        mm = cm.addMethod(int.class, "match", String.class).public_().static_();

        var bootstrap = mm.var(Switcher.class).indy("ordinals", (Object[]) caseKeys);
        mm.return_(bootstrap.invoke(int.class, "_", new Object[] {Object.class}, mm.param(0)));

        clazz = cm.finish();
        method = clazz.getMethod("match", String.class);

        for (String arg : args) {
            System.out.println(method.invoke(null, arg));
        }
    }

    /**
     * @param condition the variable condition to evaluate at runtime
     * @param defaultLabel required
     * @param caseKeys dynamically loaded constants
     */
    @SuppressWarnings("unchecked")
    public static void switchExact(MethodMaker mm, Variable condition,
                                   Label defaultLabel, Object[] caseKeys, Label... labels)
    {
        if (caseKeys.length != labels.length) {
            throw new IllegalArgumentException();
        }

        var hashMatches = new HashMap<Integer, Object>();

        for (int i=0; i<caseKeys.length; i++) {
            Object caseKey = caseKeys[i];
            Integer hash = caseKey.hashCode();
            var match = new Match(caseKey, labels[i]);

            Object matches = hashMatches.get(hash);

            if (matches == null) {
                hashMatches.put(hash, match);
            } else if (matches instanceof ArrayList) {
                ((ArrayList) matches).add(match);
            } else {
                var list = new ArrayList<Object>();
                list.add(matches);
                list.add(match);
                hashMatches.put(hash, list);
            }
        }

        var hashCases = new int[hashMatches.size()];
        var hashLabels = new Label[hashCases.length];

        int i = 0;
        for (Integer hash : hashMatches.keySet()) {
            hashCases[i] = hash;
            hashLabels[i++] = mm.label();
        }

        condition.ifEq(null, defaultLabel);
        condition.invoke("hashCode").switch_(defaultLabel, hashCases, hashLabels);

        i = 0;
        for (Object matches : hashMatches.values()) {
            hashLabels[i++].here();

            if (matches instanceof ArrayList) {
                for (var match : ((ArrayList) matches)) {
                    ((Match) match).addCheck(mm, condition);
                }
            } else {
                ((Match) matches).addCheck(mm, condition);
            }

            mm.goto_(defaultLabel);
        }
    }

    /**
     * Bootstrap method which makes a method that accepts a single argument and returns a
     * zero-based ordinal value corresponding to one of the case keys. If none match, -1 is
     * returned.
     */
    public static CallSite ordinals(MethodHandles.Lookup lookup, String name, MethodType type,
                                    Object... caseKeys)
    {
        if (type.returnType() != int.class || type.parameterCount() != 1) {
            throw new IllegalArgumentException();
        }

        MethodMaker mm = MethodMaker.begin(lookup, name, type);

        var labels = new Label[caseKeys.length];
        for (int i=0; i<labels.length; i++) {
            labels[i] = mm.label();
        }

        Label defaultLabel = mm.label();

        switchExact(mm, mm.param(0), defaultLabel, caseKeys, labels);

        for (int i=0; i<labels.length; i++) {
            labels[i].here();
            mm.return_(i);
        }
        defaultLabel.here();
        mm.return_(-1);

        return new ConstantCallSite(mm.finish());
    }

    private static class Match {
        final Object key;
        final Label label;

        Match(Object key, Label label) {
            this.key = key;
            this.label = label;
        }

        void addCheck(MethodMaker mm, Variable condition) {
            mm.var(key.getClass()).setExact(key).invoke("equals", condition).ifEq(true, label);
        }
    }
}
