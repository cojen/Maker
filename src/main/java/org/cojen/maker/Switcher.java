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
import java.util.Objects;

/**
 * Generates more types of switch statements.
 *
 * @author Brian S O'Neill
 */
final class Switcher {
    @SuppressWarnings("unchecked")
    public static void switchString(MethodMaker mm, Variable condition,
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
                    list = new ArrayList<Object>();
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
                    ((StringMatch) match).addCheck(mm, condition);
                }
            } else {
                ((StringMatch) matches).addCheck(mm, condition);
            }

            mm.goto_(defaultLabel);
        }
    }

    private static record StringMatch(String key, Label label) {
        void addCheck(MethodMaker mm, Variable condition) {
            condition.invoke("equals", key).ifTrue(label);
        }
    }
}
