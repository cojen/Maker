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

import java.lang.invoke.VarHandle;

import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;

/**
 * Simple cache of weakly referenced values.
 *
 * @author Brian S O'Neill
 */
class WeakCache<K, V> extends ReferenceQueue<Object> {
    private Entry<K, V>[] mEntries;
    private int mSize;

    @SuppressWarnings({"unchecked"})
    public WeakCache() {
        // Initial capacity must be a power of 2.
        mEntries = new Entry[2];
    }

    /**
     * Can be called without explicit synchronization, but entries can appear to go missing.
     * Double check with synchronization.
     */
    public V get(K key) {
        Object obj = poll();
        if (obj != null) {
            synchronized (this) {
                cleanup(obj);
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
        Object obj = poll();
        if (obj != null) {
            cleanup(obj);
        }

        var entries = mEntries;
        int hash = key.hashCode();
        int index = hash & (entries.length - 1);

        for (Entry<K, V> e = entries[index], prev = null; e != null; e = e.mNext) {
            if (e.mKey.equals(key)) {
                V replaced = e.get();
                e.clear();
                var newEntry = new Entry<K, V>(key, value, hash, this);
                if (prev == null) {
                    newEntry.mNext = e.mNext;
                } else {
                    prev.mNext = e.mNext;
                }
                VarHandle.storeStoreFence(); // ensure that entry value is safely visible
                entries[index] = newEntry;
                return replaced;
            } else {
                prev = e;
            }
        }

        if (mSize >= mEntries.length) {
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
     * @param obj not null
     */
    @SuppressWarnings({"unchecked"})
    private void cleanup(Object obj) {
        var entries = mEntries;
        do {
            var cleared = (Entry<K, V>) obj;
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
        } while ((obj = poll()) != null);
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
