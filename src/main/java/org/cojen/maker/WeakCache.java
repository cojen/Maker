/*
 *  Copyright 2021 Cojen.org
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;

/**
 * Simple cache of weakly referenced values.
 *
 * @author Brian S O'Neill
 */
class WeakCache<K, V> extends ReferenceQueue<Object> {
    private static final MethodHandle START_VIRTUAL_THREAD;

    static {
        MethodHandle mh;
        if (Runtime.version().feature() < 21) {
            mh = null;
        } else {
            try {
                var mt = MethodType.methodType(Thread.class, Runnable.class);
                mh = MethodHandles.lookup().findStatic(Thread.class, "startVirtualThread", mt);
            } catch (Throwable e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        START_VIRTUAL_THREAD = mh;
    }

    private Entry<K, V>[] mEntries;
    private int mSize;

    @SuppressWarnings({"unchecked"})
    public WeakCache() {
        // Initial capacity must be a power of 2.
        mEntries = new Entry[2];

        if (START_VIRTUAL_THREAD != null) {
            try {
                var t = (Thread) START_VIRTUAL_THREAD.invokeExact((Runnable) () -> {
                    try {
                        while (true) {
                            Object ref = remove();
                            synchronized (WeakCache.this) {
                                cleanup(ref);
                            }
                        }
                    } catch (InterruptedException e) {
                    }
                });
            } catch (Throwable e) {
            }
        }
    }

    /**
     * Can be called without explicit synchronization, but entries can appear to go missing.
     * Double check with synchronization.
     */
    public V get(K key) {
        Object ref = poll();
        if (ref != null) {
            synchronized (this) {
                cleanup(ref);
            }
        }

        var entries = mEntries;
        for (var e = entries[key.hashCode() & (entries.length - 1)]; e != null; e = e.mNext) {
            if (e.mKey.equals(key)) {
                return e.get();
            }
        }

        return null;
    }

    /**
     * @return replaced value, or null if none
     */
    @SuppressWarnings({"unchecked"})
    public synchronized V put(K key, V value) {
        Object ref = poll();
        if (ref != null) {
            cleanup(ref);
        }

        var entries = mEntries;
        int hash = key.hashCode();
        int index = hash & (entries.length - 1);

        for (Entry<K, V> e = entries[index], prev = null; e != null; e = e.mNext) {
            if (e.mKey.equals(key)) {
                V replaced = e.get();
                if (replaced != null) {
                    e.clear();
                }
                var newEntry = new Entry<K, V>(key, value, hash, this);
                if (prev == null) {
                    newEntry.mNext = e.mNext;
                } else {
                    prev.mNext = e.mNext;
                    newEntry.mNext = entries[index];
                }
                VarHandle.storeStoreFence(); // ensure that entry value is safely visible
                entries[index] = newEntry;
                return replaced;
            } else {
                prev = e;
            }
        }

        if (mSize >= entries.length) {
            // Rehash.
            var newEntries = new Entry[entries.length << 1];
            int size = 0;
            for (int i=entries.length; --i>=0 ;) {
                for (var existing = entries[i]; existing != null; ) {
                    var e = existing;
                    existing = existing.mNext;
                    if (e.get() != null) {
                        size++;
                        index = e.mHash & (newEntries.length - 1);
                        e.mNext = newEntries[index];
                        newEntries[index] = e;
                    }
                }
            }
            mEntries = entries = newEntries;
            mSize = size;
            index = hash & (entries.length - 1);
        }

        var newEntry = new Entry<K, V>(key, value, hash, this);
        newEntry.mNext = entries[index];
        VarHandle.storeStoreFence(); // ensure that entry value is safely visible
        entries[index] = newEntry;
        mSize++;

        return null;
    }

    /**
     * Caller must be synchronized.
     *
     * @param ref not null
     */
    @SuppressWarnings({"unchecked"})
    private void cleanup(Object ref) {
        var entries = mEntries;
        do {
            var cleared = (Entry<K, V>) ref;
            int ix = cleared.mHash & (entries.length - 1);
            for (Entry<K, V> e = entries[ix], prev = null; e != null; e = e.mNext) {
                if (e == cleared) {
                    if (prev == null) {
                        entries[ix] = e.mNext;
                    } else {
                        prev.mNext = e.mNext;
                    }
                    mSize--;
                    break;
                } else {
                    prev = e;
                }
            }
        } while ((ref = poll()) != null);
    }

    private static final class Entry<K, V> extends WeakReference<V> {
        final K mKey;
        final int mHash;

        Entry<K, V> mNext;

        Entry(K key, V value, int hash, WeakCache<K, V> cache) {
            super(value, cache);
            mKey = key;
            mHash = hash;
        }
    }
}
