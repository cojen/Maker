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

import java.lang.reflect.InvocationTargetException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generates more types of switch statements.
 *
 * @author Brian S O'Neill
 * @hidden
 */
public final class Switcher {
    // Accessed by tests.
    static boolean NO_SWITCH_BOOTSTRAPS;

    static void switchString(MethodMaker mm, Variable condition,
                             Label defaultLabel, String[] keys, Label... labels)
    {
        if (condition.classType() != String.class) {
            throw new IllegalStateException("Not switching on a String type");
        }

        checkArgs(keys, labels);

        doSwitchObject(false, false, mm, condition, defaultLabel, keys, labels);
    }

    static void switchEnum(boolean external, MethodMaker mm, Variable condition,
                           Label defaultLabel, Enum<?>[] keys, Label... labels)
    {
        Class<?> type = condition.classType();
        if (type == null || !type.isEnum()) {
            throw new IllegalStateException("Not switching on an Enum type");
        }

        checkArgs(keys, labels);

        if (keys.length == 0) {
            mm.var(Objects.class).invoke("requireNonNull", condition);
            defaultLabel.goto_();
            return;
        }

        for (Enum<?> key : keys) {
            if (key.getClass() != type) {
                throw new IllegalArgumentException("Enum case doesn't match the condition type");
            }
        }

        if (NO_SWITCH_BOOTSTRAPS || Runtime.version().feature() < 21) {
            if (!external) {
                doSwitchObject(true, false, mm, condition, defaultLabel, keys, labels);
                return;
            }

            Class<?> mapperClass = ordinalMapper(mm, type);

            var ordinalCases = new int[keys.length];
            for (int i=0; i<ordinalCases.length; i++) {
                ordinalCases[i] = keys[i].ordinal() + 1;
            }

            mm.var(mapperClass).field("_").aget(condition.invoke("ordinal"))
                .switch_(defaultLabel, ordinalCases, labels);

            return;
        }

        Variable bootstraps;
        try {
            bootstraps = mm.var(Class.forName("java.lang.runtime.SwitchBootstraps"));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }

        var keyNames = new String[keys.length];
        for (int i=0; i<keys.length; i++) {
            keyNames[i] = keys[i].name();
        }

        var cases = new int[1 + labels.length];
        for (int i=0; i<cases.length; i++) {
            cases[i] = i - 1;
        }

        var newLabels = new Label[1 + labels.length];
        newLabels[0] = mm.label(); // null case
        System.arraycopy(labels, 0, newLabels, 1, labels.length);
        labels = newLabels;

        var indexVar = bootstraps.indy
            ("enumSwitch", (Object[]) keyNames).invoke(int.class, "_", null, condition, 0);

        indexVar.switch_(defaultLabel, cases, labels);

        // Implement the null case.
        labels[0].here();
        mm.new_(NullPointerException.class).throw_();
    }

    static void switchObject(MethodMaker mm, Variable condition,
                             Label defaultLabel, Object[] keys, Label... labels)
    {
        Class<?> type = condition.classType();
        if (type != null && type.isPrimitive()) {
            condition = condition.box();
        }

        boolean hasConstantVars = checkArgs(keys, labels);

        doSwitchObject(true, hasConstantVars, mm, condition, defaultLabel, keys, labels);
    }

    /**
     * @return true if any keys are ConstantVars
     */
    private static boolean checkArgs(Object[] keys, Label... labels) {
        if (keys.length != labels.length) {
            throw new IllegalArgumentException("Number of cases and labels doesn't match");
        }
        boolean isConstantVar = false;
        for (Object key : keys) {
            Objects.requireNonNull(key, "Case cannot be null");
            isConstantVar |= key instanceof TheMethodMaker.ConstantVar;
        }
        return isConstantVar;
    }

    @SuppressWarnings("unchecked")
    private static void doSwitchObject(boolean exact, boolean hasConstantVars,
                                       MethodMaker mm, Variable condition,
                                       Label defaultLabel, Object[] keys, Label... labels)
    {
        if (keys.length <= 2) {
            mm.var(Objects.class).invoke("requireNonNull", condition);
            for (int i=0; i<keys.length; i++) {
                check(exact, condition, keys[i], labels[i]);
            }
            defaultLabel.goto_();
            return;
        }

        if (hasConstantVars) {
            // Cannot immediately calculate the hash codes of the constants if they're
            // dynamically generated. For this reason, dynamically generate another switch
            // statement which maps to an ordinal value, and switch on that.
            var cases = new int[labels.length];
            for (int i=0; i<cases.length; i++) {
                cases[i] = i;
            }
            var indy = mm.var(Switcher.class).indy("ordinals", (Object[]) keys);
            var ordinalVar = indy.invoke(int.class, "_", null, condition);
            ordinalVar.switch_(defaultLabel, cases, labels);
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
        MethodMaker mm = condition.methodMaker();

        Variable keyVar;

        if (!exact) {
            keyVar = mm.var(key.getClass()).set(key);
        } else if (key instanceof Variable) {
            if (!(key instanceof TheMethodMaker.ConstantVar cv)) {
                throw new IllegalArgumentException("Case isn't a constant");
            }
            keyVar = ((TheMethodMaker) mm).var(Object.class).setConstant(cv);
        } else {
            keyVar = mm.var(Object.class).setExact(key);
        }

        keyVar.invoke("equals", condition).ifTrue(label);
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

    /**
     * Returns a class with a static final int[] field named "_" which maps actual runtime
     * ordinal values to switch ordinals. The first valid switch ordinal is 1.
     */
    private static Class<?> ordinalMapper(MethodMaker mm, Class<?> enumType) {
        var enclosing = (TheClassMaker) mm.classMaker();

        Map<Class<?>, Class<?>> mappers = enclosing.mEnumMappers;
        if (mappers == null) {
            enclosing.mEnumMappers = mappers = new HashMap<>();
        } else {
            Class<?> mapper = mappers.get(enumType);
            if (mapper != null) {
                return mapper;
            }
        }

        Enum[] enumValues;
        try {
            enumValues = (Enum[]) enumType.getMethod("values").invoke(null);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException | NoSuchMethodException | ClassCastException e) {
            throw new IllegalStateException(e);
        }

        ClassMaker cm = mm.addInnerClass(null).private_().static_();
        cm.addField(int[].class, "_").private_().static_().final_();
        MethodMaker clinit = cm.addClinit();

        var enumVar = clinit.var(enumType);

        var mapperVar = clinit.new_(int[].class, enumVar.invoke("values").alength());
        clinit.field("_").set(mapperVar);

        var indexVar = clinit.var(int.class);

        for (int i=0; i<enumValues.length; i++) {
            Enum e = enumValues[i];
            Label tryStart = clinit.label().here();
            indexVar.set(enumVar.field(e.name()).invoke("ordinal"));
            mapperVar.aset(indexVar, i + 1);
            clinit.catch_(tryStart, NoSuchFieldError.class, exVar -> {});
        }

        Class<?> mapper = cm.finish();

        mappers.put(enumType, mapper);

        return mapper;
    }
}
