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

import java.io.Serializable;

import java.lang.invoke.*;

import java.util.*;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class InvokeTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(InvokeTest.class.getName());
    }

    @Test
    public void invokeInterface() throws Exception {
        // Define an interface, implement it, and then invoke it.

        Class iface;
        {
            ClassMaker cm = ClassMaker.begin().public_().interface_();
            cm.addMethod(void.class, "a", int.class).public_().abstract_();
            cm.addMethod(int.class, "b").public_().abstract_();
            cm.addMethod(float.class, "c").public_().abstract_();
            cm.addMethod(double.class, "d").public_().abstract_();
            cm.addMethod(long.class, "e").public_().abstract_();
            cm.addMethod(String.class, "f").public_().abstract_();
            cm.addMethod(Class.class, "clazz").public_().abstract_();
            iface = cm.finish();
        }

        Class impl;
        {
            ClassMaker cm = ClassMaker.begin().public_().implement(iface);

            MethodMaker mm = cm.addConstructor().public_();

            cm.addField(int.class, "state").protected_();

            mm = cm.addMethod(void.class, "a", int.class).public_().synchronized_();
            mm.field("state").set(mm.param(0));

            mm = cm.addMethod(int.class, "b").public_();
            mm.return_(mm.field("state"));

            mm = cm.addMethod(float.class, "c").public_();
            mm.return_(mm.field("state").cast(float.class));

            mm = cm.addMethod(double.class, "d").public_();
            mm.return_(mm.field("state"));

            mm = cm.addMethod(long.class, "e").public_();
            mm.return_(mm.field("state"));

            mm = cm.addMethod(String.class, "f").public_();
            mm.return_(mm.concat(mm.field("state")));

            mm = cm.addMethod(Class.class, "clazz").public_();
            mm.return_(mm.class_());

            impl = cm.finish();
        }

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var v1 = mm.var(iface).set(mm.new_(impl));

        v1.invoke("a", 100);

        var assertVar = mm.var(Assert.class);
        assertVar.invoke("assertEquals", 100, v1.invoke("b"));
        assertVar.invoke("assertEquals", 100f, v1.invoke("c"), 0.0f);
        assertVar.invoke("assertEquals", 100d, v1.invoke("d"), 0.0d);
        assertVar.invoke("assertEquals", 100L, v1.invoke("e"));
        assertVar.invoke("assertEquals", "100", v1.invoke("f"));
        assertVar.invoke("assertEquals", impl, v1.invoke("clazz"));

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void invokeDynamic() throws Exception {
        // Dynamically generate a method which adds two numbers. In practice, this is overkill.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        MethodHandle bootstrap = MethodHandles.lookup().findStatic
            (InvokeTest.class, "boot", MethodType.methodType
             (CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class));

        MethodHandleInfo info = MethodHandles.lookup().revealDirect(bootstrap);

        var assertVar = mm.var(Assert.class);

        {
            var v1 = mm.var(int.class).set(1);
            var v2 = mm.var(int.class).set(2);
            var v3 = mm.invokeDynamic
                (info, null, "intAdd",
                 MethodType.methodType(int.class, int.class, int.class), v1, v2);
            assertVar.invoke("assertEquals", 3, v3);
        }

        {
            var v1 = mm.var(double.class).set(1.1);
            var v2 = mm.var(double.class).set(2.1);
            var v3 = mm.invokeDynamic
                (info, null, "doubleAdd",
                 MethodType.methodType(double.class, double.class, double.class), v1, v2);
            assertVar.invoke("assertEquals", 3.2, v3, 0.0);
        }

        cm.finish().getMethod("run").invoke(null);
    }

    public static CallSite boot(MethodHandles.Lookup caller, String name, MethodType type)
        throws Exception
    {
        ClassMaker cm = ClassMaker.begin().public_().final_();
        MethodMaker mm = cm.addMethod(name, type).static_().public_();
        mm.return_(mm.param(0).add(mm.param(1)));
        Class<?> clazz = cm.finish();
        var mh = MethodHandles.lookup().findStatic(clazz, name, type);
        return new ConstantCallSite(mh);
    }

    @Test
    public void invokeDynamic2() throws Exception {
        // Dynamically generate a method which throws an exception. In practice, this is overkill.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        MethodHandle bootstrap = MethodHandles.lookup().findStatic
            (InvokeTest.class, "boot", MethodType.methodType
             (CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class,
              MethodHandle.class, MethodType.class));

        MethodHandleInfo info = MethodHandles.lookup().revealDirect(bootstrap);
        MethodType type = MethodType.methodType(void.class, String.class);

        Label start = mm.label().here();
        var v0 = mm.invokeDynamic
            (info, new Object[] {info, type}, // pass additional constants for code coverage
             "throwIt", type, "hello");
        assertNull(v0);
        mm.return_();
        Label end = mm.label().here();

        String expect = "hello" + bootstrap + type;

        var ex = mm.catch_(start, end, Exception.class);
        var msg = ex.invoke("getMessage");
        mm.var(Assert.class).invoke("assertEquals", expect, msg);
        mm.return_();

        cm.finish().getMethod("run").invoke(null);
    }

    public static CallSite boot(MethodHandles.Lookup caller, String name, MethodType type,
                                MethodHandle arg1, MethodType arg2)
        throws Exception
    {
        ClassMaker cm = ClassMaker.begin().public_().final_();
        MethodMaker mm = cm.addMethod(name, type).static_().public_();
        MethodHandleInfo info1 = caller.revealDirect(arg1);
        mm.new_(Exception.class, mm.concat(mm.param(0), info1, arg2)).throw_();
        Class<?> clazz = cm.finish();
        var mh = MethodHandles.lookup().findStatic(clazz, name, type);
        return new ConstantCallSite(mh);
    }

    @Test
    public void staticOrInstance() throws Exception {
        // Matching method might be static or instance.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var obj = mm.new_(InvokeTest.class);
        var v1 = obj.invoke("foo", 10);
        var v2 = obj.invoke("foo", 10L);

        mm.var(Assert.class).invoke("assertEquals", 11, v1);
        mm.var(Assert.class).invoke("assertEquals", 9L, v2);

        cm.finish().getMethod("run").invoke(null);
    }

    public static int foo(int arg) {
        return arg + 1;
    }

    public long foo(long arg) {
        return arg - 1;
    }

    @Test
    public void override() throws Exception {
        ClassMaker cm = ClassMaker.begin(null, InvokeTest.class).public_();

        {
            MethodMaker mm = cm.addConstructor(int.class).private_();
            mm.invokeSuperConstructor();
        }

        {
            MethodMaker mm = cm.addConstructor().private_();
            mm.invokeThisConstructor(10); // dummy value
        }

        {
            MethodMaker mm = cm.addMethod(int.class, "calc", int.class, int.class).protected_();
            var v1 = mm.invokeSuper("calc", mm.param(0), mm.param(1));
            var v2 = mm.var(v1).set(v1);
            mm.return_(v2.add(10));
        }

        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        var obj = mm.new_(cm.name());
        var result = obj.invoke("calc", 1, 2);
        mm.var(Assert.class).invoke("assertEquals", 13, result);

        cm.finish().getMethod("run").invoke(null);
    }

    protected int calc(int a, int b) {
        return a + b;
    }

    @Test
    public void invokePrimitive() throws Exception {
        // Invoking a primitive boxes it first.

        ClassMaker cm = ClassMaker.begin(null, InvokeTest.class).public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        var v1 = mm.var(int.class).set(99);
        mm.var(Assert.class).invoke("assertEquals", "99", v1.invoke("toString"));
        mm.var(Assert.class).invoke("assertEquals", 1, mm.var(Long.class).invoke("bitCount", 1));
        var v2 = mm.var(int.class);
        mm.var(Assert.class).invoke("assertEquals", 1, v2.invoke("bitCount", 1));

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void bigStack() throws Exception {
        ClassMaker cm = ClassMaker.begin(null, InvokeTest.class).public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var vars = new Variable[48];
        for (int i=0; i<vars.length; i++) {
            vars[i] = mm.var(long.class).set(i);
        }

        // First result is popped.
        mm.var(InvokeTest.class).invoke("big", (Object[]) vars);

        var result = mm.var(InvokeTest.class).invoke("big", (Object[]) vars);
        mm.var(Assert.class).invoke("assertEquals", 47, result);

        cm.finish().getMethod("run").invoke(null);
    }

    public static long big(long a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7,
                           long b0, long b1, long b2, long b3, long b4, long b5, long b6, long b7,
                           long c0, long c1, long c2, long c3, long c4, long c5, long c6, long c7,
                           long d0, long d1, long d2, long d3, long d4, long d5, long d6, long d7,
                           long e0, long e1, long e2, long e3, long e4, long e5, long e6, long e7,
                           long f0, long f1, long f2, long f3, long f4, long f5, long f6, long f7)
    {
        return f7;
    }

    @Test
    public void invokeInfer() throws Exception {
        ClassMaker cm = ClassMaker.begin(null, InvokeTest.class).public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var v1 = mm.var(int.class).set(1);
        var v2 = mm.var(long.class).set(2);

        {
            var result = mm.var(InvokeTest.class).invoke("methodA", v1, v2);
            mm.var(Assert.class).invoke("assertEquals", 1, result);
        }

        {
            var result = mm.var(InvokeTest.class).invoke("methodB", v1, v2);
            mm.var(Assert.class).invoke("assertEquals", 4, result);
        }

        try {
            mm.var(InvokeTest.class).invoke("methodA", 1L, 2L);
            fail();
        } catch (IllegalStateException e) {
            // No match.
        }

        try {
            mm.var(InvokeTest.class).invoke("methodA", 1, 2);
            fail();
        } catch (IllegalStateException e) {
            // No best match.
        }

        cm.finish().getMethod("run").invoke(null);
    }

    public static int methodA(int a, long b) {
        return 1;
    }

    public static int methodA(long a, int b) {
        return 2;
    }

    public static int methodB(long a, int b) {
        return 3;
    }

    public static int methodB(int a, long b) {
        return 4;
    }

    @Test
    public void invokeInfer2() throws Exception {
        ClassMaker cm = ClassMaker.begin(null, InvokeTest.class).public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var v1 = mm.new_(ArrayList.class);

        try {
            mm.var(InvokeTest.class).invoke("methodC", v1);
            fail();
        } catch (IllegalStateException e) {
            // No best match.
        }

        {
            var result = mm.var(InvokeTest.class).invoke("methodC", v1.cast(Collection.class));
            mm.var(Assert.class).invoke("assertEquals", 1, result);
        }

        cm.finish().getMethod("run").invoke(null);
    }

    public static int methodC(Collection a) {
        return 1;
    }

    public static int methodC(Serializable a) {
        return 2;
    }

    @Test
    public void invokeInfer3() throws Exception {
        ClassMaker cm = ClassMaker.begin(null, InvokeTest.class).public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var v1 = mm.new_(ArrayList.class);

        var result = mm.var(InvokeTest.class).invoke("methodD", v1);
        mm.var(Assert.class).invoke("assertEquals", 2, result);

        cm.finish().getMethod("run").invoke(null);
    }

    public static int methodD(AbstractCollection a) {
        return 1;
    }

    public static int methodD(AbstractList a) {
        return 2;
    }

    @Test
    public void invokeInfer4() throws Exception {
        ClassMaker cm = ClassMaker.begin(null, InvokeTest.class).public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var v1 = mm.new_(int[].class, 10);

        var result = mm.var(InvokeTest.class).invoke("methodE", v1);
        mm.var(Assert.class).invoke("assertEquals", 1, result);

        cm.finish().getMethod("run").invoke(null);
    }

    public static int methodE(int[] a) {
        return 1;
    }

    public static int methodE(Object a) {
        return 2;
    }

    @Test
    public void invokeInfer5() throws Exception {
        ClassMaker cm = ClassMaker.begin(null, InvokeTest.class).public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var v1 = mm.new_(ArrayList[].class, 10);

        var result = mm.var(InvokeTest.class).invoke("methodF", v1);
        mm.var(Assert.class).invoke("assertEquals", 2, result);

        cm.finish().getMethod("run").invoke(null);
    }

    public static int methodF(Object a) {
        return 0;
    }

    public static int methodF(AbstractCollection[] a) {
        return 1;
    }

    public static int methodF(AbstractList[] a) {
        return 2;
    }

    public static int methodF(Object[] a) {
        return 3;
    }

    public static int methodF(Collection[] a) {
        return 4;
    }

    @Test
    public void invokeHandle() throws Exception {
        // Test access to a private method using a MethodHandle.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(int.class, "tunnel").public_().static_();

        MethodHandle handle = MethodHandles.lookup()
            .findStatic(InvokeTest.class, "secret", MethodType.methodType(int.class, int.class));

        mm.return_(mm.invoke(int.class, handle, 10));

        assertEquals(100, cm.finish().getMethod("tunnel").invoke(null));
    }

    private static int secret(int a) {
        return a * a;
    }
}
