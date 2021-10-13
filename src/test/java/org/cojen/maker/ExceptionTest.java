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

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class ExceptionTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ExceptionTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        basic(false);
    }

    @Test
    public void basicWithFiller() throws Exception {
        basic(true);
    }

    private void basic(boolean filler) throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        Label L1 = mm.label().here();
        if (filler) {
            var v1 = mm.var(long.class).set(0);
            for (int i=0; i<20; i++) {
                v1.inc(100);
            }
            mm.var(Assert.class).invoke("assertEquals", 2_000, v1);
        }
        mm.new_(Exception.class, "message").throw_();
        Label L2 = mm.label().here();

        mm.var(System.class).field("out").invoke("println", "dead");
        Label L3 = mm.label().here();

        var ex = mm.catch_(L1, L2, Exception.class);
        mm.var(Assert.class).invoke("assertEquals", "message", ex.invoke("getMessage"));
        mm.return_();

        mm.catch_(L1, L2, (Object) null).throw_();

        mm.catch_(L2, L3, (Object) null).throw_();

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void monitor() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_().implement(Runnable.class);
        cm.addConstructor().public_();

        MethodMaker mm = cm.addMethod(null, "run").public_();

        mm.this_().monitorEnter();
        Label L1 = mm.label().here();
        mm.var(System.class).invoke("getProperties");
        Label L2 = mm.label().here();
        mm.this_().monitorExit();
        mm.return_();
        var ex = mm.catch_(L1, L2, (Object) null);
        mm.this_().monitorExit();
        ex.throw_();

        var clazz = cm.finish();
        var obj = clazz.getConstructor().newInstance();
        clazz.getMethod("run").invoke(obj);
    }

    @Test
    public void tiny() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_().implement(Runnable.class);
        MethodMaker mm = cm.addMethod(void.class, "run").public_().static_();

        Label L1 = mm.label().here();
        mm.return_();
        Label L2 = mm.label().here();
        var ex = mm.catch_(L1, L2, Exception.class);
        mm.return_();

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void empty() throws Exception {
        // Labels are flipped compared to the tiny test, thus doing nothing.

        ClassMaker cm = ClassMaker.begin().public_().implement(Runnable.class);
        MethodMaker mm = cm.addMethod(void.class, "run").public_().static_();

        Label L1 = mm.label().here();
        mm.return_();
        Label L2 = mm.label().here();
        var ex = mm.catch_(L2, L1, Exception.class);
        mm.return_();

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void flowIntoHandler() throws Exception {
        // Detect direct flow into an exception handler.

        ClassMaker cm = ClassMaker.begin().public_().implement(Runnable.class);
        MethodMaker mm = cm.addMethod(void.class, "run").public_().static_();

        Label L1 = mm.label().here();
        mm.nop();
        Label L2 = mm.label().here();
        var ex = mm.catch_(L1, L2, Exception.class);
        mm.return_();

        try {
            cm.finish();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("handler"));
        }
    }

    @Test
    public void declare() throws Exception {
        // Test the exception declaration.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(void.class, "run").public_()
            .throws_(NullPointerException.class).throws_(InterruptedException.class);

        var clazz = cm.finish();
        Class<?>[] types = clazz.getMethod("run").getExceptionTypes();

        assertEquals(2, types.length);

        if (types[0] == NullPointerException.class) {
            assertEquals(InterruptedException.class, types[1]);
        } else if (types[0] == InterruptedException.class) {
            assertEquals(NullPointerException.class, types[1]);
        } else {
            fail();
        }
    }

    @Test
    public void throwSelf() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_().extend(InterruptedException.class);
        cm.addConstructor();
        MethodMaker mm = cm.addMethod(int.class, "run").public_().static_();
        mm.new_(cm).throw_();
        var clazz = cm.finish();
        try {
            clazz.getMethod("run").invoke(null);
        } catch (Exception e) {
            assertEquals(clazz, e.getCause().getClass());
        }
    }

    @Test
    public void unpositioned() throws Exception {
        ClassMaker cm = ClassMaker.begin();
        MethodMaker mm = cm.addMethod(null, "run");
        Label start = mm.label();
        mm.var(System.class).field("out").invoke("println", "hello");
        try {
            mm.catch_(start, Exception.class, ex -> {});
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("positioned"));
        }
    }
}
