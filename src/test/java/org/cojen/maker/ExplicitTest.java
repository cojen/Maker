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
        ClassMaker cm = basic(null, "org.cojen.maker.FakeClass", false, true);
        basic(cm, "org.cojen.maker.AnotherFakeClass", true, true);
    }

    private ClassMaker basic(ClassMaker cm, String name, boolean dynamic, boolean retry)
        throws Exception
    {
        if (cm == null) {
            cm = ClassMaker.beginExplicit(name, null);
        } else {
            cm = cm.another(name, null);
        }

        assertEquals(name, cm.name());

        cm.public_().addConstructor().public_();

        MethodMaker mm = cm.addMethod(String.class, "test").public_();
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

        if (!dynamic) {
            try {
                cm.another(name, null);
                fail();
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().startsWith("Already"));
            }

            // Duplicate name is allowed when a different internal ClassInjector is used.

            if (retry) {
                basic(null, name, false, false);
            }
        }

        return cm;
    }
}
