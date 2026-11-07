/*
 *  Copyright 2020 Cojen.org
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

import java.io.ByteArrayOutputStream;

import java.lang.invoke.MethodHandles;

import java.lang.reflect.Modifier;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class ExternalTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ExternalTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        ClassMaker cm = basic(null, "org.cojen.maker.FakeClass", true);
        basic(cm, "org.cojen.maker.AnotherFakeClass", false);
    }

    private ClassMaker basic(ClassMaker cm, String name, boolean dynamic) throws Exception {
        if (cm == null) {
            cm = ClassMaker.beginExternal(name);
        } else {
            cm = cm.another(name);
        }

        assertEquals(name, cm.name());

        cm.public_().addConstructor().public_();

        MethodMaker mm = cm.addMethod(String.class, "test").public_();

        try {
            mm.var(String.class).setExact("hello");
            fail();
        } catch (IllegalStateException e) {
        }

        mm.return_("hello");

        Class<?> clazz;
        if (dynamic) {
            clazz = cm.finish();
        } else {
            var out = new ByteArrayOutputStream();
            cm.finishTo(out);

            byte[] bytes = out.toByteArray();

            var loader = new ClassLoader() {
                {
                    defineClass(name, bytes, 0, bytes.length);
                }
            };

            clazz = loader.loadClass(name);
        }

        assertEquals(name, clazz.getName());

        var instance = clazz.getConstructor().newInstance();
        assertEquals("hello", clazz.getMethod("test").invoke(instance));

        return cm;
    }

    @Test
    public void extraModifiers() throws Exception {
        ClassMaker cm = ClassMaker.beginExternal("org.cojen.maker.Fake").abstract_().synthetic();
        cm.addMethod(void.class, "m2").abstract_().synthetic();
        cm.addMethod(void.class, "m3").native_();
        cm.addMethod(void.class, "m4").bridge();
        Class<?> clazz = cm.finish();

        int mods = clazz.getModifiers();
        assertTrue(Modifier.isAbstract(mods));
        assertTrue((mods & 0x1000) != 0);

        mods = clazz.getDeclaredMethod("m2").getModifiers();
        assertTrue(Modifier.isAbstract(mods));
        assertTrue((mods & 0x1000) != 0);

        mods = clazz.getDeclaredMethod("m3").getModifiers();
        assertTrue(Modifier.isNative(mods));

        mods = clazz.getDeclaredMethod("m4").getModifiers();
        assertTrue((mods & 0x40) != 0);
    }

    @Test
    public void finishAndLoad() throws Exception {
        if (finishAndLoadPassed) {
            return;
        }

        ClassMaker cm = ClassMaker.beginExternal("org.cojen.maker.Fake").public_();
        MethodMaker mm = cm.addMethod(String.class, "test", (Object[]) null).public_().static_();
        mm.return_("hello");
        byte[] bytes = cm.finishBytes();

        synchronized (ExternalTest.class) {
            Class<?> clazz;
            try {
                clazz = MethodHandles.lookup().defineClass(bytes);
            } catch (LinkageError e) {
                if (finishAndLoadPassed) {
                    // Was already defined earlier in the same ClassLoader.
                    return;
                }
                throw e;
            }

            assertEquals("org.cojen.maker.Fake", clazz.getName());
            Object result = clazz.getMethod("test").invoke(null);
            assertEquals("hello", result);

            finishAndLoadPassed = true;
        }
    }

    private static volatile boolean finishAndLoadPassed;

    /**
     * Writing a class with many constants to an OutputStream must produce the same bytes as
     * finishBytes, and the result must be loadable and usable. Short strings which are
     * interleaved with long, double, and int constants used to cause the stream buffer to
     * shrink below the size of a subsequent write, which threw an
     * ArrayIndexOutOfBoundsException.
     *
     * @author Claude
     */
    @Test
    public void finishToStreamManyConstants() throws Exception {
        // The failure depends on where the buffer happens to fill up, so try many sizes.
        for (int n = 1; n <= 150; n++) {
            String name = "org.cojen.maker.StreamFake" + n;

            byte[] expectBytes = streamFake(name, n).finishBytes();

            var out = new ByteArrayOutputStream();
            streamFake(name, n).finishTo(out);
            byte[] bytes = out.toByteArray();

            assertArrayEquals("n=" + n, expectBytes, bytes);

            // Verify that the class actually loads and computes the expected result.

            var loader = new ClassLoader() {
                {
                    defineClass(name, bytes, 0, bytes.length);
                }
            };

            Class<?> clazz = loader.loadClass(name);

            long expect = 0;
            double dexpect = 0;
            for (int i = 0; i < n; i++) {
                expect += 1_000_000_000_000L + i;
                dexpect += 0.5 + i;
                expect += 100_000 + i;
                expect += Integer.toString(i, 36).length();
            }
            expect += (long) dexpect;

            assertEquals("n=" + n, expect, (long) clazz.getMethod("run").invoke(null));
        }
    }

    /**
     * Makes a class with a static run method which uses many distinct constants. The strings
     * are short and unique, and they're interleaved with constants that require 4 and 8 byte
     * writes.
     *
     * @author Claude
     */
    private static ClassMaker streamFake(String name, int n) {
        ClassMaker cm = ClassMaker.beginExternal(name).public_();
        MethodMaker mm = cm.addMethod(long.class, "run").public_().static_();

        var sum = mm.var(long.class).set(0L);
        var dsum = mm.var(double.class).set(0.0);

        for (int i = 0; i < n; i++) {
            var str = mm.var(String.class).set(Integer.toString(i, 36));
            sum.set(sum.add(1_000_000_000_000L + i));
            dsum.set(dsum.add(0.5 + i));
            sum.set(sum.add(100_000 + i));
            sum.set(sum.add(str.invoke("length")));
        }

        sum.set(sum.add(dsum.cast(long.class)));
        mm.return_(sum);

        return cm;
    }
}
