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
import java.lang.invoke.VarHandle;

import java.lang.ref.WeakReference;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Support for loading exact constants into generated classes.
 *
 * @author Brian S O'Neill
 * @hidden
 */
public abstract class ConstantsRegistry {
    private static final int ACCESS_MODE;

    static {
        ACCESS_MODE = Runtime.version().feature() >= 16
            ? /*MethodHandles.Lookup.ORIGINAL*/ 64 : MethodHandles.Lookup.PRIVATE;
    }

    private static WeakHashMap<ClassLoader, WeakReference<ConstantsRegistry>> cRegistries;

    private Map<Class, Object> mConstants;

    protected ConstantsRegistry() {
    }

    /**
     * Add an entry and return the slot assigned to it.
     */
    static int add(TheClassMaker cm, Object value) {
        Objects.requireNonNull(value);
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
     *
     * @param lookup can be null if class loader is a ClassInjector.Group.
     */
    static void finish(TheClassMaker cm, MethodHandles.Lookup lookup, Class clazz) {
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
                    // No need to be a WeakHashMap because ordinary classes cannot be unloaded
                    // until the class loader and all of its classes are unreferenced.
                    constants = new HashMap<>(4);
                    group.mConstants = constants;
                }
                constants.put(clazz, obj);
            }
        } else {
            ConstantsRegistry registry;
            synchronized (ConstantsRegistry.class) {
                if (cRegistries == null) {
                    cRegistries = new WeakHashMap<>(4);
                }
                WeakReference<ConstantsRegistry> registryRef = cRegistries.get(loader);
                if (registryRef == null || (registry = registryRef.get()) == null) {
                    registry = defineRegistry(lookup);
                    cRegistries.put(loader, new WeakReference<>(registry));
                }
            }
            synchronized (registry) {
                Map<Class, Object> constants = registry.mConstants;
                if (constants == null) {
                    // Use a WeakHashMap because some classes might be hidden and can be
                    // unloaded. A strong reference would prevent this.
                    constants = new WeakHashMap<>(4);
                    registry.mConstants = constants;
                }
                constants.put(clazz, obj);
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

        ConstantsRegistry registry = null;

        Object value;
        if (loader instanceof ClassInjector.Group) {
            var group = (ClassInjector.Group) loader;
            synchronized (group) {
                value = group.mConstants == null ? null : group.mConstants.get(clazz);
            }
        } else {
            WeakReference<ConstantsRegistry> registryRef;
            synchronized (ConstantsRegistry.class) {
                registryRef = cRegistries.get(loader);
            }
            if (registryRef == null || (registry = registryRef.get()) == null) {
                value = null;
            } else {
                synchronized (registry) {
                    value = registry.mConstants == null ? null : registry.mConstants.get(clazz);
                }
            }
        }

        if (value == null) {
            return null;
        }

        if (value instanceof Entries) {
            var entries = (Entries) value;
            synchronized (entries) {
                value = entries.mValues[slot];
                if (value == null) {
                    return null;
                }
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
            synchronized (registry) {
                registry.mConstants.remove(clazz);
                if (registry.mConstants.isEmpty()) {
                    registry.mConstants = null;
                }
            }
        }

        return value;
    }

    /**
     * Defines a ConstantsRegistry subclass in the class loader of the given lookup. The
     * returned instance is a singleton, strongly referenced to prevent premature GC.
     */
    private static ConstantsRegistry defineRegistry(MethodHandles.Lookup lookup) {
        String className = "registry";

        String pn = lookup.lookupClass().getPackageName();
        if (!pn.isEmpty()) {
            className = pn + '.' + className;
        }

        var cm = (TheClassMaker) ClassMaker.begin(className, lookup).synthetic();
        cm.extend(ConstantsRegistry.class);
        cm.addField(ConstantsRegistry.class, "_").static_().final_().synthetic();
        cm.addConstructor().private_().synthetic();
        MethodMaker mm = cm.addClinit();
        mm.field("_").set(mm.new_(cm));

        Class<?> clazz;
        if (cm.supportsHiddenClasses()) {
            // The hidden class must also be strongly referenced by the class loader.
            clazz = cm.finishHidden(true).lookupClass();
        } else {
            // Ordinary classes are always strongly referenced by the class loader.
            clazz = cm.finish();
        }

        VarHandle vh;
        try {
            vh = lookup.findStaticVarHandle(clazz, "_", ConstantsRegistry.class);
        } catch (Exception e) {
            throw TheClassMaker.toUnchecked(e);
        }

        return (ConstantsRegistry) vh.get();
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
