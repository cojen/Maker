/*
 *  Copyright (C) 2019 Cojen.org
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.lang.ref.WeakReference;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 
 *
 * @author Brian S O'Neill
 */
class ClassInjector extends ClassLoader {
    private static final WeakCache<Object, ClassInjector> cInjectors = new WeakCache<>();

    private final Map<String, Boolean> mReservedNames;
    private final WeakCache<String, Group> mPackageGroups;

    private ClassInjector(boolean explicit, ClassLoader parent) {
        super(parent);
        mReservedNames = explicit ? null : new WeakHashMap<>();
        mPackageGroups = new WeakCache<>();
    }

    static ClassInjector lookup(boolean explicit, ClassLoader parentLoader, Object key) {
        Objects.requireNonNull(parentLoader);

        if (explicit) {
            return new ClassInjector(true, parentLoader);
        }

        final Object injectorKey = createInjectorKey(parentLoader, key);

        ClassInjector injector = cInjectors.get(injectorKey);

        if (injector == null) {
            synchronized (cInjectors) {
                injector = cInjectors.get(injectorKey);
                if (injector == null) {
                    injector = new ClassInjector(false, parentLoader);
                    cInjectors.put(injectorKey, injector);
                }
            }
        }

        return injector;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        Group group = findPackageGroup(name, false);

        if (group != null) {
            try {
                return group.doLoadClass(name);
            } catch (ClassNotFoundException e) {
            }
        }

        return super.loadClass(name);
    }

    boolean isExplicit() {
        return mReservedNames == null;
    }

    /**
     * Returns the package group to be used for defining a class by the given name. Is weakly
     * referenced by the ClassInjector.
     */
    Group groupForClass(String name) {
        return findPackageGroup(name, true);
    }

    Class<?> define(Group group, String name, byte[] b) {
        try {
            return group.define(name, b);
        } catch (LinkageError e) {
            // Replace duplicate name definition with a better exception.
            try {
                loadClass(name);
                throw new IllegalStateException("Class already defined: " + name);
            } catch (ClassNotFoundException e2) {
            }
            throw e;
        } finally {
            unreserve(name);
        }
    }

    void unreserve(String name) {
        if (mReservedNames != null) {
            synchronized (mReservedNames) {
                mReservedNames.remove(name);
            }
        }
    }

    /**
     * @param className can be null
     * @return actual class name
     */
    String reserve(String className, boolean checkParent) {
        if (className == null) {
            className = ClassMaker.class.getName();
        }

        var rnd = ThreadLocalRandom.current();

        // Use a small identifier if possible, making it easier to read stack traces and
        // decompiled classes.
        int range = 10;

        while (true) {
            // Use '-' instead of '$' to prevent conflicts with inner class names.
            String mangled = className + '-' + rnd.nextInt(range);

            if (tryReserve(mangled, checkParent)) {
                return mangled;
            }

            if (range < 1_000_000_000) {
                range *= 10;
            }
        }
    }

    /**
     * @return false if the name is already taken
     */
    private boolean tryReserve(String name, boolean checkParent) {
        if (mReservedNames != null) {
            synchronized (mReservedNames) {
                if (mReservedNames.put(name, Boolean.TRUE) != null) {
                    return false;
                }
            }
        }

        Group group = findPackageGroup(name, true);

        if (!group.isLoaded(name)) {
            ClassLoader parent;
            if (!checkParent || (parent = getParent()) == null) {
                return true;
            } else {
                try {
                    parent.loadClass(name);
                } catch (ClassNotFoundException e) {
                    return true;
                }
            }
        }

        return false;
    }

    private static Object createInjectorKey(ClassLoader parentLoader, Object rest) {
        return new Key(parentLoader, rest);
    }

    private static class Key extends WeakReference<ClassLoader> {
        private final Object mRest;
        private final int mHash;

        Key(ClassLoader loader, Object rest) {
            super(loader);
            mRest = rest;
            int hash = loader.hashCode() * 31;
            if (rest != null) {
                hash += rest.hashCode();
            }
            mHash = hash;
        }

        @Override
        public int hashCode() {
            return mHash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof Key) {
                var other = (Key) obj;
                return Objects.equals(get(), other.get()) && Objects.equals(mRest, other.mRest);
            }
            return false;
        }
    }

    private Group findPackageGroup(String className, boolean create) {
        String packageName;
        {
            int ix = className.lastIndexOf('.');
            packageName = ix <= 0 ? "" : className.substring(0, ix);
        }

        Group group = mPackageGroups.get(packageName);
        if (group == null) {
            synchronized (mPackageGroups) {
                group = mPackageGroups.get(packageName);
                if (group == null && create) {
                    group = new Group();
                    mPackageGroups.put(packageName, group);
                }
            }
        }

        return group;
    }

    /**
     * A group is a loader for one package.
     */
    class Group extends ClassLoader {
        private volatile MethodHandles.Lookup mLookup;

        // Accessed by ConstantsRegistry.
        Map<Class, Object> mConstants;

        private Group() {
            // All group members are at the same level in the hierarchy as the ClassInjector
            // itself, and so the parent for all should be the same. This also ensures that the
            // ClassInjector instance isn't visible externally via the getParent method.
            super(ClassInjector.this.getParent());
        }

        /**
         * Returns a lookup object in the group's package.
         *
         * @param className used to extract the package name
         * @param hook when true, also define a "hook" method which invokes a named init method
         */
        MethodHandles.Lookup lookup(String className, boolean hook) {
            MethodHandles.Lookup lookup = mLookup;
            if (lookup == null) {
                lookup = makeLookup(className, hook);
            }
            return lookup;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return ClassInjector.this.loadClass(name);
        }

        private Class<?> doLoadClass(String name) throws ClassNotFoundException {
            return super.loadClass(name);
        }

        private Class<?> define(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }

        private boolean isLoaded(String name) {
            return findLoadedClass(name) != null;
        }

        private synchronized MethodHandles.Lookup makeLookup(String className, boolean hook) {
            MethodHandles.Lookup lookup = mLookup;
            if (lookup != null) {
                return lookup;
            }

            className = className.substring(0, className.lastIndexOf('.') + 1) + "lookup";
            var cm = new TheClassMaker(className, ClassInjector.this, this).public_().synthetic();

            var mt = MethodType.methodType(MethodHandles.Lookup.class, Object.class);

            MethodMaker mm = cm.addMethod("lookup", mt).public_().static_().synthetic();
            Label ok = mm.label();
            mm.var(Object.class).setExact(ClassInjector.this).ifEq(mm.param(0), ok);
            mm.new_(IllegalAccessError.class).throw_();
            ok.here();
            mm.return_(mm.var(MethodHandles.class).invoke("lookup"));

            if (hook) {
                mm = cm.addMethod(null, "hook", Class.class, String.class)
                    .public_().static_().synthetic();
                var initMethodVar = mm.param(0).invoke("getDeclaredMethod", mm.param(1));
                initMethodVar.invoke("invoke", (Object) null);
            }

            // Ideally, this should be a hidden class which can eventually be GC'd.
            // Unfortunately, this requires that a package-level lookup already exists.
            var clazz = cm.finish();

            try {
                var mh = MethodHandles.publicLookup().findStatic(clazz, "lookup", mt);
                lookup = (MethodHandles.Lookup) mh.invoke(ClassInjector.this);
            } catch (Throwable e) {
                throw TheClassMaker.toUnchecked(e);
            }

            mLookup = lookup;
            return lookup;
        }
    }
}
