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

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class FinallyTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(FinallyTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        basic(false, null, 0);
    }

    @Test
    public void basicReturn() throws Exception {
        basic(true, null, 0);
    }

    @Test
    public void basicReturnValue() throws Exception {
        basic(true, int.class, 0);
    }

    @Test
    public void basicInside() throws Exception {
        basic(false, null, 1);
    }

    @Test
    public void basicInsideAndReturn() throws Exception {
        basic(true, null, 2);
    }

    @Test
    public void basicInsideAndReturnValue() throws Exception {
        basic(true, int.class, 3);
    }

    private void basic(boolean doReturn, Class returnType, int insideCount) throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(returnType, "run").public_().static_();

        cm.addField(int.class, "counter").public_().static_();
        var counter = mm.field("counter");

        Label start = mm.label().here();

        if (insideCount >= 1) {
            Label ok = mm.label();
            counter.ifEq(0, ok);
            mm.var(Assert.class).invoke("fail");
            ok.here();
        }

        counter.inc(10);

        if (insideCount >= 2) {
            Label ok = mm.label();
            counter.ifEq(10, ok);
            mm.var(Assert.class).invoke("fail");
            ok.here();
        }

        if (doReturn) {
            if (returnType == null) {
                mm.return_();
            } else {
                if (insideCount >= 3) {
                    Label cont = mm.label();
                    counter.ifEq(10, cont);
                    mm.return_(-1);
                    cont.here();
                }
                mm.return_(100);
            }
        }

        mm.finally_(start, () -> {
            counter.inc(5);
        });

        var clazz = cm.finish();
        Object result = clazz.getMethod("run").invoke(null);

        assertEquals(15, clazz.getField("counter").get(null));

        if (doReturn && returnType != null) {
            assertEquals(100, result);
        }
    }

    @Test
    public void basicGoto() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        cm.addField(int.class, "counter").public_().static_();
        var counter = mm.field("counter");

        Label start = mm.label().here();

        counter.inc(10);
        Label cont = mm.label().goto_();

        mm.finally_(start, () -> {
            counter.inc(5);
        });

        cont.here();

        var clazz = cm.finish();
        Object result = clazz.getMethod("run").invoke(null);

        assertEquals(15, clazz.getField("counter").get(null));
    }

    @Test
    public void unpositioned() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        Label start = mm.label();
        try {
            mm.finally_(start, () -> {});
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("unpositioned"));
        }
    }

    @Test
    public void releaseLock() throws Exception {
        // Verify that a lock object is released when code runs normally or with an exception.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(int.class, "run", Lock.class, int.class).public_().static_();

        var lock = mm.param(0);
        var value = mm.param(1);

        Label ok = mm.label();
        lock.ifNe(null, ok);
        mm.new_(Exception.class, "no lock").throw_();
        ok.here();

        lock.invoke("lock");
        Label start = mm.label().here();
        Label cont = mm.label();

        ok = mm.label();
        value.ifNe(0, ok);
        mm.new_(Exception.class, "zero").throw_();
        ok.here();
        value.inc(1);

        mm.goto_(cont);

        mm.finally_(start, () -> lock.invoke("unlock"));

        cont.here();
        mm.return_(value);

        var clazz = cm.finish();
        var method = clazz.getMethod("run", Lock.class, int.class);

        var lockInstance = new ReentrantLock();

        Object result = method.invoke(null, lockInstance, 10);
        assertEquals(11, result);
        assertFalse(lockInstance.isLocked());

        try {
            method.invoke(null, null, 10);
            fail();
        } catch (Exception e) {
            assertEquals("no lock", e.getCause().getMessage());
        }

        try {
            method.invoke(null, lockInstance, 0);
            fail();
        } catch (Exception e) {
            assertEquals("zero", e.getCause().getMessage());
        }
        assertFalse(lockInstance.isLocked());
    }

    @Test
    public void switchTest() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        cm.addField(int.class, "counter").public_().static_();
        MethodMaker mm = cm.addMethod(int.class, "run", int.class).public_().static_();

        var counter = mm.field("counter");
        counter.set(mm.param(0));

        Label start = mm.label().here();

        Label defaultLabel = mm.label();
        int[] cases = new int[3];
        Label[] labels = new Label[cases.length];
        for (int i=0; i<cases.length; i++) {
            cases[i] = i;
            labels[i] = mm.label();
        }

        counter.switch_(defaultLabel, cases, labels);

        labels[0].here();
        counter.set(10);
        mm.goto_(defaultLabel);

        labels[1].here();
        mm.new_(Exception.class, "wrong").throw_();

        mm.finally_(start, () -> {
            counter.set(counter.neg());
        });

        labels[2].here();
        counter.set(20);

        defaultLabel.here();
        mm.return_(counter);

        var clazz = cm.finish();
        var method = clazz.getMethod("run", int.class);
        var field = clazz.getField("counter");

        assertEquals(1, method.invoke(null, -1));
        assertEquals(1, field.get(null));

        assertEquals(-10, method.invoke(null, 0));
        assertEquals(-10, field.get(null));

        try {
            method.invoke(null, 1);
            fail();
        } catch (Exception e) {
           assertEquals("wrong", e.getCause().getMessage());
        }
        assertEquals(-1, field.get(null));

        assertEquals(20, method.invoke(null, 2));
        assertEquals(20, field.get(null));
    }
}
