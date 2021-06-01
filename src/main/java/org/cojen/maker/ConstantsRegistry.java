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
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Support for loading exact constants into generated classes.
 *
 * @author Brian S O'Neill
 * @hidden
 */
public class ConstantsRegistry {
    private static final int ACCESS_MODE;

    static {
        ACCESS_MODE = Runtime.version().feature() >= 16
            ? /*MethodHandles.Lookup.ORIGINAL*/ 64 : MethodHandles.Lookup.PRIVATE;
    }

    private static Map<Class, Object> cConstants;

    private ConstantsRegistry() {
    }

    /**
     * Add an entry and return the slot assigned to it.
     */
    static int add(TheClassMaker cm, Object value) {
        Object obj = cm.mExactConstants;
        if (obj == null) {
            cm.mExactConstants = value;
            return 0;
        }
        Entries entries;
        if (obj instanceof Entries) {
            entries = (Entries) obj;
        } else {
            entries = new Entries(obj);
            cm.mExactConstants = entries;
        }
        return entries.add(value);
    }

    /**
     * Called when the class definition is finished, to make the constants loadable.
     */
    static void finish(TheClassMaker cm, Class clazz) {
        Object obj = cm.mExactConstants;
        if (obj == null) {
            return;
        }

        if (obj instanceof Entries) {
            ((Entries) obj).prune();
        }

        ClassLoader loader = clazz.getClassLoader();

        if (loader instanceof ClassInjector.Group) {
            var group = (ClassInjector.Group) loader;            
            synchronized (group) {
                Map<Class, Object> constants = group.mConstants;
                if (constants == null) {
                    constants = new HashMap<>();
                    group.mConstants = constants;
                }
                constants.put(clazz, obj);
            }
        } else {
            synchronized (ConstantsRegistry.class) {
                if (cConstants == null) {
                    cConstants = new WeakHashMap<>();
                }
                cConstants.put(clazz, obj);
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
        if ((lookup.lookupModes() & ACCESS_MODE) == 0) {
            throw new IllegalStateException();
        }

        Class<?> clazz = lookup.lookupClass();
        ClassLoader loader = clazz.getClassLoader();

        Object value;
        if (loader instanceof ClassInjector.Group) {
            var group = (ClassInjector.Group) loader;
            synchronized (group) {
                value = group.mConstants == null ? null : group.mConstants.get(clazz);
            }
        } else {
            synchronized (ConstantsRegistry.class) {
                value = cConstants == null ? null : cConstants.get(clazz);
            }
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

        if (loader instanceof ClassInjector.Group) {
            var group = (ClassInjector.Group) loader;
            synchronized (group) {
                group.mConstants.remove(clazz);
                if (group.mConstants.isEmpty()) {
                    group.mConstants = null;
                }
            }
        } else {
            synchronized (ConstantsRegistry.class) {
                cConstants.remove(clazz);
                if (cConstants.isEmpty()) {
                    cConstants = null;
                }
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
                    (mValues, (int) Math.min(Integer.MAX_VALUE, ((long) slot) << 1));
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
