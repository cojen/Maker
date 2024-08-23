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

import java.lang.invoke.MethodHandles;

import java.lang.ref.WeakReference;

import java.util.WeakHashMap;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class InjectorTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(InjectorTest.class.getName());
    }

    @Test
    public void unloading() throws Exception {
        // Test that classes get unloaded, and that classes which need to be in the same
        // package have access to package-private elements.

        var classes = new WeakHashMap<Class, Boolean>();

        Class<?> parent;

        for (int q=0; q<100; q++) {
            String parentGroup = "foo" + q;

            parent = Object.class;
            Class<?> clazz = null;

            for (int i=25; --i>=0; ) {
                int group = i / 10;
                String name = parentGroup + ".bar" + group + ".Thing";

                ClassMaker cm = ClassMaker.begin(name).extend(parent);

                if (i == 0 || ((i - 1) / 10) != group) {
                    cm.public_();
                }

                MethodMaker mm = cm.addConstructor().public_();
                mm.invokeSuperConstructor();

                clazz = cm.finish();
                cm = null; // help GC
                classes.put(clazz, true);

                ClassLoader loader = clazz.getClassLoader();
                do {
                    assertFalse(loader instanceof ClassInjector);
                } while ((loader = loader.getParent()) != null);

                parent = clazz;
            }

            Object obj = clazz.getConstructor().newInstance();
            clazz = null; // help GC
            obj = null; // help GC
        }

        parent = null; // help GC
        BaseType.clearCaches();

        for (int i=0; i<10; i++) {
            // More reliable check than calling isEmpty due to apparent race conditions when
            // cleared references are enqueued.
            if (!classes.entrySet().iterator().hasNext()) {
                return;
            }
            System.gc();
        }

        fail();
    }

    @Test
    public void group() throws Exception {
        // Verify that the ClassInjector.Group is strongly referenced.

        ClassMaker cm = ClassMaker.begin("a.b.c.Dee").public_();

        var ref = new WeakReference<>(cm.classLoader());

        System.gc();

        cm.addConstructor().public_();
        var obj = cm.finish().getConstructor().newInstance();
        cm = null; // help GC

        System.gc();

        assertEquals(ref.get(), obj.getClass().getClassLoader());

        obj = null; // help GC

        for (int i=0; i<10; i++) {
            if (ref.get() == null) {
                return;
            }
            System.gc();
        }

        fail();
    }

    @Test
    public void lookup() throws Exception {
        // Verify the ClassLoader when using MethodHandles.Lookup.

        var lookup = MethodHandles.lookup();
        ClassMaker cm = ClassMaker.begin(null, lookup);
        assertEquals(getClass().getClassLoader(), cm.classLoader());
        cm.addConstructor();
        var obj = cm.finish().getDeclaredConstructor().newInstance();
        assertEquals(getClass().getClassLoader(), obj.getClass().getClassLoader());
    }

    @Test
    public void separateKeys() throws Exception {
        // Same parent loader but distinct child loaders.

        final var k1 = new Object();
        final Class<?> c1;
        {
            ClassMaker cm = ClassMaker.begin(null, null, k1).public_();
            cm.addConstructor().public_();
            c1 = cm.finish();
        }

        final var k2 = new Object();
        final Class<?> c2;
        {
            ClassMaker cm = ClassMaker.begin(null, null, k2).public_();
            cm.addConstructor().public_();
            c2 = cm.finish();
        }

        var o1 = c1.getConstructor().newInstance();
        var o2 = c2.getConstructor().newInstance();

        assertNotEquals(o1.getClass().getClassLoader(), o2.getClass().getClassLoader());

        assertEquals(o1.getClass().getClassLoader().getParent(),
                     o2.getClass().getClassLoader().getParent());
    }
}
