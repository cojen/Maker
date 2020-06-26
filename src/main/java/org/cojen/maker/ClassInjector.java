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

import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Principal;
import java.security.ProtectionDomain;

/**
 * 
 *
 * @author Brian S O'Neill
 */
class ClassInjector extends ClassLoader {
    private static final Map<Object, ClassInjector> cInjectors = new ConcurrentHashMap<>();

    private final Map<String, Boolean> mReservedNames = new WeakHashMap<>();
    private final ProtectionDomain mDomain;

    private ClassInjector(ClassLoader parent, ProtectionDomain domain) {
        super(parent);
        mDomain = prepareDomain(domain, this);
    }

    private static ProtectionDomain prepareDomain(ProtectionDomain domain, ClassLoader loader) {
        if (domain == null) {
            return null;
        }

        return new ProtectionDomain(domain.getCodeSource(),
                                    domain.getPermissions(),
                                    loader,
                                    domain.getPrincipals());
    }

    static ClassInjector lookup(ClassLoader parentLoader, ProtectionDomain domain) {
        final Object injectorKey = createInjectorKey(parentLoader, domain);

        ClassInjector injector = cInjectors.get(injectorKey);
        if (injector == null) {
            injector = new ClassInjector(parentLoader, domain);
            ClassInjector existing = cInjectors.putIfAbsent(injectorKey, injector);
            if (existing != null) {
                injector = existing;
            }
        }

        return injector;
    }

    ProtectionDomain domain() {
        return mDomain;
    }

    Class<?> define(String name, byte[] b) {
        try {
            if (mDomain == null) {
                return defineClass(name, b, 0, b.length);
            } else {
                return defineClass(name, b, 0, b.length, mDomain);
            }
        } catch (LinkageError e) {
            // Replace duplicate name definition with a better exception.
            try {
                loadClass(name);
                throw new IllegalStateException("Class already defined: " + name);
            } catch (ClassNotFoundException e2) {
            }
            throw e;
        } finally {
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

        if (self.findLoadedClass(name) == null) {
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

    private static Object createInjectorKey(ClassLoader parentLoader, ProtectionDomain domain) {
        if (domain == null) {
            return parentLoader;
        }

        // ProtectionDomain doesn't have an equals method, so break it apart and add the
        // elements to a composite key.

        Object csKey = domain.getCodeSource();
        Object permsKey = null;
        Object principalsKey = null;

        PermissionCollection pc = domain.getPermissions();
        if (pc != null) {
            List<Permission> permList = Collections.list(pc.elements());
            if (permList.size() == 1) {
                permsKey = permList.get(0);
            } else if (permList.size() > 1) {
                permsKey = new HashSet<Permission>(permList);
            }
        }

        Principal[] principals = domain.getPrincipals();
        if (principals != null && principals.length > 0) {
            if (principals.length == 1) {
                principalsKey = principals[0];
            } else {
                Set<Principal> principalSet = new HashSet<>(principals.length);
                for (Principal principal : principals) {
                    principalSet.add(principal);
                }
                principalsKey = principalSet;
            }
        }

        return new Key(parentLoader, csKey, permsKey, principalsKey);
    }

    private static class Key {
        private final Object[] mComposite;
        private final int mHash;

        Key(Object... composite) {
            mComposite = composite;
            mHash = Arrays.deepHashCode(composite);
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
                return Arrays.deepEquals(mComposite, ((Key) obj).mComposite);
            }
            return false;
        }
    }
}
