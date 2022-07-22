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

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class WeakCacheTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(WeakCacheTest.class.getName());
    }

    @Test
    public void replace() {
        var cache = new WeakCache<String, String>();

        // Note that by using String literals, they won't get GC'd.
        assertNull(cache.put("a", "b"));
        assertEquals("b", cache.put("a", "c"));
        assertEquals("c", cache.get("a"));
    }

    @Test
    public void putCleanup() {
        var cache = new WeakCache<Integer, Value>();

        for (int i=0; i<10_000; i++) {
            cache.put(i, new Value());
            if (i % 1000 == 0) {
                System.gc();
            }
        }

        int found = 0;

        for (int i=0; i<10_000; i++) {
            if (cache.get(i) != null) {
                found++;
            }
        }

        assertTrue(found < 10_000);
    }

    @Test
    public void collision() {
        var cache = new WeakCache<String, Value>();

        var values = new Value[] {new Value(), new Value(), new Value(), new Value()};

        cache.put("Ea", values[0]);
        cache.put("FB", values[1]);

        assertEquals(values[0], cache.get("Ea"));
        assertEquals(values[1], cache.get("FB"));

        cache.put("Ea", values[2]);

        assertEquals(values[2], cache.get("Ea"));
        assertEquals(values[1], cache.get("FB"));

        cache.put("FB", values[3]);

        assertEquals(values[2], cache.get("Ea"));
        assertEquals(values[3], cache.get("FB"));
    }

    private static class Value {
    }
}
