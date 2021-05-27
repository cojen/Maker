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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 
 *
 * @author Brian S O'Neill
 */
class ClassInjector extends ClassLoader {
    private static final Map<Object, ClassInjector> cInjectors = new ConcurrentHashMap<>();

    private final Map<String, Boolean> mReservedNames;
    private final WeakCache<String, Group> mPackageGroups;

    private ClassInjector(boolean explicit, ClassLoader parent) {
        super(parent);
        mReservedNames = explicit ? null : new WeakHashMap<>();
        mPackageGroups = new WeakCache<>();
    }

    static ClassInjector lookup(boolean explicit, ClassLoader parentLoader) {
        if (explicit) {
            return new ClassInjector(true, parentLoader);
        }

        final Object injectorKey = parentLoader;

        ClassInjector injector = cInjectors.get(injectorKey);
        if (injector == null) {
            injector = new ClassInjector(false, parentLoader);
            ClassInjector existing = cInjectors.putIfAbsent(injectorKey, injector);
            if (existing != null) {
                injector = existing;
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

    Class<?> define(String name, byte[] b) {
        Group group = findPackageGroup(name, true);

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

        for (int tryCount = 0; tryCount < 1000; tryCount++) {
            // Use '-' instead of '$' to prevent conflicts with inner class names.
            String mangled = className + '-' + rnd.nextInt(range);

            if (tryReserve(this, mangled, checkParent)) {
                return mangled;
            }

            if (range < 1_000_000_000) {
                range *= 10;
            }
        }

        throw new InternalError("Unable to create unique class name");
    }

    /**
     * @return false if the name is already taken
     */
    private static boolean tryReserve(ClassInjector self, String name, boolean checkParent) {
        ClassLoader parent;
        while ((parent = self.getParent()) instanceof ClassInjector) {
            self = (ClassInjector) parent;
        }

        synchronized (self.mReservedNames) {
            if (self.mReservedNames.put(name, Boolean.TRUE) != null) {
                return false;
            }
        }

        Group group = self.findPackageGroup(name, true);

        if (!group.isLoaded(name)) {
            if (!checkParent) {
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
    private class Group extends ClassLoader {
        Group() {
            super(ClassInjector.this);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return ClassInjector.this.loadClass(name);
        }

        Class<?> doLoadClass(String name) throws ClassNotFoundException {
            return super.loadClass(name);
        }

        Class<?> define(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }

        boolean isLoaded(String name) {
            return findLoadedClass(name) != null;
        }
    }
}
