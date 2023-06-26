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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generates more types of switch statements.
 *
 * @author Brian S O'Neill
 */
final class Switcher {
    @SuppressWarnings("unchecked")
    static void switchString(MethodMaker mm, Variable condition,
                             Label defaultLabel, String[] keys, Label... labels)
    {
        if (condition.classType() != String.class) {
            throw new IllegalStateException("Not switching on a String type");
        }

        if (keys.length != labels.length) {
            throw new IllegalArgumentException("Number of cases and labels doesn't match");
        }

        if (keys.length <= 2) {
            for (int i=0; i<keys.length; i++) {
                condition.invoke("equals", Objects.requireNonNull(keys[i])).ifTrue(labels[i]);
            }
            defaultLabel.goto_();
            return;
        }

        var hashMatches = new HashMap<Integer, Object>();

        for (int i=0; i<keys.length; i++) {
            String key = Objects.requireNonNull(keys[i]);
            var match = new StringMatch(key, labels[i]);
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
                    ((StringMatch) match).addCheck(condition);
                }
            } else {
                ((StringMatch) matches).addCheck(condition);
            }

            mm.goto_(defaultLabel);
        }
    }

    private record StringMatch(String key, Label label) {
        void addCheck(Variable condition) {
            condition.invoke("equals", key).ifTrue(label);
        }
    }

    static void switchEnum(TheMethodMaker mm, Variable condition,
                           Label defaultLabel, Enum[] keys, Label... labels)
    {
        Class<?> type = condition.classType();

        if (type == null || !Enum.class.isAssignableFrom(type)) {
            throw new IllegalStateException("Not switching on an Enum type");
        }

        if (keys.length != labels.length) {
            throw new IllegalArgumentException("Number of cases and labels doesn't match");
        }

        if (keys.length == 0) {
            defaultLabel.goto_();
            return;
        }

        for (Enum key : keys) {
            if (type != key.getClass()) {
                throw new IllegalArgumentException("Mismatched enum types");
            }
        }

        var mapVar = mm.var(enumSwitchMap(mm.mClassMaker.nestHost(), type)).field("_");

        var cases = new int[keys.length];
        for (int i=0; i<cases.length; i++) {
            cases[i] = keys[i].ordinal() + 1;
        }

        mapVar.aget(condition.invoke("ordinal")).switch_(defaultLabel, cases, labels);
    }

    /**
     * Returns an inner class with a switch map field named "_".
     */
    private static ClassMaker enumSwitchMap(TheClassMaker nestHost, Class<?> type) {
        Map<Class<?>, ClassMaker> maps = nestHost.mEnumSwitchMaps;
        if (maps == null) {
            nestHost.mEnumSwitchMaps = maps = new HashMap<>(4);
        }

        ClassMaker cm = maps.get(type);

        if (cm != null) {
            return cm;
        }

        // Generate a static final int[] field which maps enum ordinals to switch cases. Enum
        // ordinals are zero based, and the switch cases are one based. Any enums which aren't
        // available at runtime map to zero.

        Enum[] enums;
        try {
            enums = (Enum[]) type.getMethod("values").invoke(null);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid enum type: " + type, e);
        }

        cm = nestHost.addInnerClass(null).static_().synthetic();

        String fieldName = "_";
        cm.addField(int[].class, fieldName).private_().static_().final_().synthetic();

        MethodMaker mm = cm.addClinit();

        var arrayVar = mm.field(fieldName);
        arrayVar.set(mm.new_(int[].class, mm.var(type).invoke("values").alength()));

        var enumVar = mm.var(type);
        var ordinalVar = mm.var(int.class);

        int num = 0;
        for (Enum e : enums) {
            Label next = mm.label();

            Label tryStart = mm.label().here();
            ordinalVar.set(enumVar.field(e.name()).invoke("ordinal"));
            arrayVar.aset(ordinalVar, ++num);
            Label tryEnd = mm.label().here();
            next.goto_();
            mm.catch_(tryStart, tryEnd, NoSuchFieldError.class); // ignore

            next.here();
        }

        // FIXME: Must finish when class is finished. Ugh. Won't work for external classes
        // unless I ditch the standard inner class design. I can use Java 21 API (if
        // available), or else ???
        cm.finish();

        maps.put(type, cm);

        return cm;
    }
}
