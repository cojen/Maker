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

package org.cojen.maker;

import java.lang.invoke.MethodHandles;

import java.util.Arrays;
import java.util.WeakHashMap;

/**
 * Support for loading complex constants into generated classes.
 *
 * @author Brian S O'Neill
 * @hidden
 */
public class ConstantsRegistry {
    private static WeakHashMap<Object, Object> cEntries;

    private ConstantsRegistry() {
    }

    /**
     * Add an entry and return the slot assigned to it.
     */
    static int add(ClassMaker cm, Object value) {
        Entries entries;
        synchronized (ConstantsRegistry.class) {
            if (cEntries == null) {
                cEntries = new WeakHashMap<>();
            }
            Object obj = cEntries.get(cm);
            if (obj == null) {
                cEntries.put(cm, value);
                return 0;
            }
            if (obj instanceof Entries) {
                entries = (Entries) obj;
            } else {
                entries = new Entries(obj);
                cEntries.put(cm, entries);
            }
        }

        return entries.add(value);
    }

    /**
     * Called when the class definition is finished, to make the constants loadable.
     */
    static void finish(ClassMaker cm, Class clazz) {
        Object entries;
        synchronized (ConstantsRegistry.class) {
            entries = cEntries.remove(cm);
            if (entries != null) {
                if (entries instanceof Entries) {
                    ((Entries) entries).prune();
                }
                cEntries.put(clazz, entries);
            }
        }
    }

    /**
     * Removes the constant assigned to the given slot. This is a dynamic bootstrap method.
     *
     * @param name unused
     * @param type unused
     */
    public static Object remove(MethodHandles.Lookup lookup, String name, Class<?> type, int slot) {
        if ((lookup.lookupModes() & MethodHandles.Lookup.PRIVATE) == 0) {
            throw new IllegalStateException();
        }

        Class<?> clazz = lookup.lookupClass();

        Object value;
        synchronized (ConstantsRegistry.class) {
            value = cEntries == null ? null : cEntries.get(clazz);
        }

        if (value == null) {
            throw new IllegalStateException();
        }

        if (value instanceof Entries) {
            var entries = (Entries) value;
            synchronized (entries) {
                value = entries.mValues[slot];
                int size = entries.mSize;
                if (size > 1) {
                    entries.mValues[slot] = null;
                    entries.mSize = size - 1;
                    return value;
                }
            }
        }

        synchronized (ConstantsRegistry.class) {
            cEntries.remove(clazz);
            if (cEntries.isEmpty()) {
                cEntries = null;
            }
        }

        return value;
    }

    private static final class Entries {
        Object[] mValues;
        int mSize;

        Entries(Object first) {
            (mValues = new Object[4])[0] = first;
            mSize = 1;
        }

        int add(Object value) {
            int slot = mSize;
            if (slot >= mValues.length) {
                mValues = Arrays.copyOf
                    (mValues, (int) Math.min((long) Integer.MAX_VALUE, slot << 1));
            }
            mValues[slot] = value;
            mSize = slot + 1;
            return slot;
        }

        void prune() {
            if (mSize < mValues.length) {
                mValues = Arrays.copyOf(mValues, mSize);
            }
        }
    }
}
