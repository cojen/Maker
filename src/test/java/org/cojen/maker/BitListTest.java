/*
 *  Copyright 2026 Cojen.org
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
 * @author Brian S. O'Neill
 */
public class BitListTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(BitListTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        var list = new BitList(0);
        assertFalse(list.get(100));
        list.set(100);
        assertTrue(list.get(100));
        assertFalse(list.get(0));
        list.set(0);
        assertTrue(list.get(0));

        var copy = list.clone();
        assertEquals(list, copy);
        assertEquals(copy, copy);
        assertNotEquals(copy, this);

        copy.set(1000);
        assertTrue(copy.get(1000));
        assertFalse(list.get(1000));
        assertNotEquals(list, copy);

        copy.set(10);
        assertTrue(copy.get(10));
        assertFalse(list.get(10));
        assertNotEquals(list, copy);
    }

    @Test
    public void negative() throws Exception {
        var list = new BitList(10);
        assertFalse(list.get(-1));
        assertFalse(list.get(-10));

        try {
            list.get(-11);
            fail();
        } catch (IndexOutOfBoundsException e) {
        }

        try {
            list.set(-11);
            fail();
        } catch (IndexOutOfBoundsException e) {
        }

        list.set(-5);
        assertTrue(list.get(-5));
        assertFalse(list.get(0));
    }

    @Test
    public void and() throws Exception {
        try {
            new BitList(-10).and(new BitList(0));
            fail();
        } catch (IllegalArgumentException e) {
        }

        var small = new BitList(5);
        small.set(-1);
        small.set(10);
        small.set(20);

        var large = new BitList(5);
        large.set(-2);
        large.set(10);
        large.set(1000);

        var smallCopy = small.clone();

        small.and(large);
        assertFalse(small.get(-1));
        assertFalse(small.get(-2));
        assertTrue(small.get(10));
        assertFalse(small.get(20));
        assertFalse(small.get(1000));

        small = smallCopy;

        large.and(small);
        assertFalse(large.get(-1));
        assertFalse(large.get(-2));
        assertTrue(large.get(10));
        assertFalse(large.get(20));
        assertFalse(large.get(1000));
    }
}
