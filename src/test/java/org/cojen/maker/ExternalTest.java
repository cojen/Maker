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
}
