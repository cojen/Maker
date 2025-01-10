/*
 *  Copyright 2024 Cojen.org
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

import java.lang.invoke.MethodHandles;

import java.lang.reflect.InvocationTargetException;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class InstallTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(InstallTest.class.getName());
    }

    @Test
    public void usage() throws Exception {
        try {
            ClassMaker cm = ClassMaker.begin(null, MethodHandles.lookup());
            cm.installClass(String.class);
            fail();
        } catch (IllegalStateException e) {
        }

        Class<?> c1;
        {
            ClassMaker cm = ClassMaker.beginExplicit("a.Bee", null, "key1").public_();
            cm.addConstructor().public_();
            c1 = cm.finish();
        }

        Class<?> c2;
        {
            ClassMaker cm = ClassMaker.beginExplicit("a.Bee", null, "key2").public_();
            cm.addConstructor().public_();
            c2 = cm.finish();
        }

        {
            ClassMaker cm = ClassMaker.begin();

            assertTrue(cm.installClass(c1));
            assertFalse(cm.installClass(c1));

            try {
                cm.installClass(c2);
                fail();
            } catch (IllegalStateException e) {
            }
        }

        {
            ClassMaker cm = ClassMaker.begin(null, c1.getClassLoader()).public_();
            assertTrue(cm.installClass(String.class));
            assertTrue(cm.installClass(c1));

            try {
                cm.installClass(c2);
                fail();
            } catch (IllegalStateException e) {
            }
        }

        {
            ClassMaker cm = ClassMaker.begin(null, c1.getClassLoader(), "key1").public_();
            assertTrue(cm.installClass(String.class));
            assertTrue(cm.installClass(c1));

            try {
                cm.installClass(c2);
                fail();
            } catch (IllegalStateException e) {
            }
        }
    }

    @Test
    public void extend() throws Exception {
        // Test extending a class from a different ClassLoader.

        Class<?> base;
        {
            ClassMaker cm = ClassMaker.begin("a.Bee", null, "key1").public_();
            cm.addConstructor().public_();
            base = cm.finish();
        }

        // Doesn't work when installClass isn't called.
        {
            ClassMaker cm = ClassMaker.begin().public_().extend(base);
            cm.addConstructor().public_();
            try {
                cm.finish().getConstructor().newInstance();
                fail();
            } catch (NoClassDefFoundError e) {
            }
        }

        // Should work when installClass is called.
        {
            ClassMaker cm = ClassMaker.begin().public_().extend(base);
            cm.addConstructor().public_();
            cm.installClass(base);
            cm.finish().getConstructor().newInstance();
        }
    }

    @Test
    public void installPrimitive() throws Exception {
        ClassMaker cm = ClassMaker.begin();
        assertFalse(cm.installClass(int.class));
        assertFalse(cm.installClass(int[].class));
    }

    @Test
    public void installArray() throws Exception {
        Class<?> ref;
        {
            ClassMaker cm = ClassMaker.begin("a.Bee", null, "key2").public_();
            cm.addConstructor().public_();
            ref = cm.finish();
        }

        ref = ref.arrayType();

        // Doesn't work when installClass isn't called.
        {
            ClassMaker cm = ClassMaker.begin().public_();
            cm.addMethod(Class.class, "test").public_().static_().return_(ref);
            try {
                cm.finish().getMethod("test").invoke(null);
                fail();
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                assertTrue(cause instanceof NoClassDefFoundError);
            }
        }

        // Should work when installClass is called.
        {
            ClassMaker cm = ClassMaker.begin().public_();
            cm.addMethod(Class.class, "test").public_().static_().return_(ref);
            cm.installClass(ref);
            assertEquals(ref, cm.finish().getMethod("test").invoke(null));
        }
    }
}
