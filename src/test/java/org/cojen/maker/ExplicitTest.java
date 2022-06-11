/*
 *  Copyright 2022 Cojen.org
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
public class ExplicitTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ExplicitTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        try {
            ClassMaker.beginExplicit(null, null, null);
            fail();
        } catch (NullPointerException e) {
        }

        ClassMaker cm1 = ClassMaker.beginExplicit("org.cojen.maker.E1", null, null);
        ClassMaker cm2 = ClassMaker.beginExplicit("org.cojen.maker.E1", null, null);

        assertEquals("org.cojen.maker.E1", cm1.finish().getName());

        ClassMaker cm3 = ClassMaker.beginExplicit("org.cojen.maker.E1", null, null);

        try {
            cm2.finish();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("already defined"));
        }
 
        try {
            cm3.finish();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("already defined"));
        }

        ClassMaker cm4 = ClassMaker.beginExplicit("org.cojen.maker.E2", null, null);
        assertEquals("org.cojen.maker.E2", cm4.finish().getName());

        var key = new Object();

        ClassMaker cm5 = ClassMaker.beginExplicit("org.cojen.maker.E1", null, key);
        assertEquals("org.cojen.maker.E1", cm5.finish().getName());

        var loader = new ClassLoader() { };

        ClassMaker cm6 = ClassMaker.beginExplicit("org.cojen.maker.E1", loader, null);
        assertEquals("org.cojen.maker.E1", cm6.finish().getName());

        try {
            ClassMaker cm7 = ClassMaker.beginExplicit("org.cojen.maker.E1", loader, null);
            cm7.finish();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("already defined"));
        }

        ClassMaker cm8 = ClassMaker.beginExplicit("org.cojen.maker.E1", loader, key);
        assertEquals("org.cojen.maker.E1", cm8.finish().getName());

        try {
            cm8.another(null);
            fail();
        } catch (NullPointerException e) {
        }

        try {
            ClassMaker cm9 = cm8.another("org.cojen.maker.E1");
            cm9.finish();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("already defined"));
        }

        ClassMaker cm10 = cm8.another("org.cojen.maker.E2");
        assertEquals("org.cojen.maker.E2", cm10.finish().getName());
    }
}
