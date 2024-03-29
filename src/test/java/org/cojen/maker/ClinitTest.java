/*
 *  Copyright 2019 Cojen.org
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

import java.lang.invoke.MethodHandles;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class ClinitTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ClinitTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        cm.addField(String.class, "test1").static_().final_().public_();
        cm.addField(String.class, "test2").static_().public_();
        cm.addField(String.class, "test3").static_().public_();

        {
            MethodMaker mm = cm.addClinit();
            mm.field("test1").set("hello");
        }

        {
            MethodMaker mm = cm.addClinit();
        }

        {
            MethodMaker mm = cm.addClinit();
            mm.field("test2").set("hello!");
            mm.return_();
        }

        {
            MethodMaker mm = cm.addClinit();
            mm.field("test3").set("world");
        }

        var clazz = cm.finish();

        assertEquals("hello", clazz.getField("test1").get(null));
        assertEquals("hello!", clazz.getField("test2").get(null));
        assertEquals("world", clazz.getField("test3").get(null));
    }

    @Test
    public void varSharing() throws Exception {
        // Verifies that local variables cannot be shared between class initializers.

        ClassMaker cm = ClassMaker.begin().public_();

        cm.addField(int.class, "test1").static_().public_();
        cm.addField(int.class, "test2").static_().public_();

        Variable v1;
        {
            MethodMaker mm = cm.addClinit();
            v1 = mm.var(int.class).set(1);
            mm.field("test1").set(v1);
            v1.inc(1);
            mm.field("test1").set(v1);
        }

        {
            MethodMaker mm = cm.addClinit();
            try {
                mm.field("test2").set(v1);
                fail();
            } catch (IllegalStateException e) {                
            }
            v1 = mm.var(int.class).set(10);
            mm.field("test2").set(v1);
            v1.inc(1);
            mm.field("test2").set(v1);
        }

        var clazz = cm.finish();

        assertEquals(2, clazz.getField("test1").get(null));
        assertEquals(11, clazz.getField("test2").get(null));
    }

    @Test
    public void hidden() throws Exception {
        ClassMaker cm = ClassMaker.begin(null, MethodHandles.lookup()).public_();

        cm.addConstructor().public_();

        MethodMaker mm = cm.addClinit();
        mm.var(ClinitTest.class).field("value").set(1);

        var clazz = cm.finishHidden().lookupClass();

        synchronized (ClinitTest.class) {
            value = 0;
            clazz.getConstructor().newInstance();
            assertEquals(1, value);
        }
    }

    public static volatile int value;

    @Test
    public void exceptionHandlers() throws Exception {
        exceptionHandlers(false);
        exceptionHandlers(true);
    }

    private void exceptionHandlers(boolean multiple) throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        cm.addConstructor().public_();

        {
            MethodMaker mm = cm.addClinit();
            var start = mm.label().here();
            mm.nop();
            if (multiple) {
                mm.catch_(start, Throwable.class, exVar -> {
                    mm.new_(Exception.class, "a", exVar).throw_();
                });
            }
        }

        {
            MethodMaker mm = cm.addClinit();
            var start = mm.label().here();
            mm.new_(Exception.class, "b").throw_();
            mm.finally_(start, () -> {
                mm.new_(Exception.class, "c").throw_();
            });
        }

        var ctor = cm.finish().getConstructor();

        try {
            ctor.newInstance();
            fail();
        } catch (ExceptionInInitializerError e) {
            assertEquals("c", e.getCause().getMessage());
        }
    }
}
