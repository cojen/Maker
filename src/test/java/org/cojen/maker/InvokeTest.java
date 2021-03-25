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

        var bootVar = mm.var(InvokeTest.class).indy("boot");
        var assertVar = mm.var(Assert.class);

        {
            var v1 = mm.var(int.class).set(1);
            var v2 = mm.var(int.class).set(2);
            var v3 = bootVar.invoke
                (int.class, "intAdd", new Object[] {int.class, int.class}, v1, v2);
            assertVar.invoke("assertEquals", 3, v3);
        }

        {
            var v1 = mm.var(double.class).set(1.1);
            var v2 = mm.var(double.class).set(2.1);
            var v3 = bootVar.invoke
                (double.class, "doubleAdd", new Object[] {double.class, double.class}, v1, v2);
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

        // Just a MethodHandle to anything, passed along for code coverage.
        MethodHandle handle = MethodHandles.lookup().findStatic
            (InvokeTest.class, "boot", MethodType.methodType
             (CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class,
              MethodHandle.class, MethodType.class));

        // Just a MethodType for anything, passed along for code coverage.
        MethodType type = MethodType.methodType(void.class, String.class);

        Label start = mm.label().here();
        var bootstrap = mm.var(InvokeTest.class).indy("boot", handle, type);
        var v0 = bootstrap.invoke(null, "throwIt", new Object[] {String.class}, "hello");
        assertNull(v0);
        mm.return_();
        Label end = mm.label().here();

        String expect = "hello" + handle + type;

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
    public void invokeDynamicComplexConstant() throws Exception {
        // Pass complex constants to the bootstrap method.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        Object a = "prefix";
        Object b = List.of("hello", "world");
        var bootstrap = mm.var(InvokeTest.class).indy("bootComplex", a, b);
        var v0 = bootstrap.invoke(String.class, "test", null);

        mm.var(Assert.class).invoke("assertEquals", "prefix[hello, world]", v0);

        cm.finish().getMethod("run").invoke(null);
    }

    public static CallSite bootComplex(MethodHandles.Lookup caller, String name, MethodType type,
                                       Object a, Object b)
        throws Exception
    {
        // Concat the object strings.
        ClassMaker cm = ClassMaker.begin().public_().final_();
        MethodMaker mm = cm.addMethod(name, type).static_().public_();
        mm.return_("" + a + b);
        Class<?> clazz = cm.finish();
        var mh = MethodHandles.lookup().findStatic(clazz, name, type);
        return new ConstantCallSite(mh);
    }

    @Test
    public void invokeDynamicTinyConstant() throws Exception {
        // Pass tiny primitive constants to the bootstrap method. The implementation is the
        // same as for complex constats, and so the class cannot be loaded from a file.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        byte a = 12;
        short b = 3456;
        char c = 'a';
        var bootstrap = mm.var(InvokeTest.class).indy("bootTiny", a, b, c);
        var v0 = bootstrap.invoke(String.class, "test", null);

        mm.var(Assert.class).invoke("assertEquals", "123456a", v0);

        cm.finish().getMethod("run").invoke(null);
    }

    public static CallSite bootTiny(MethodHandles.Lookup caller, String name, MethodType type,
                                    byte a, short b, char c)
        throws Exception
    {
        // Concat the params.
        ClassMaker cm = ClassMaker.begin().public_().final_();
        MethodMaker mm = cm.addMethod(name, type).static_().public_();
        mm.return_("" + a + b + c);
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
        ClassMaker cm = ClassMaker.begin(null).extend(InvokeTest.class).public_();

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
        var obj = mm.new_(cm);
        assertNull(obj.classType());
        assertEquals(cm, obj.makerType());
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

        ClassMaker cm = ClassMaker.begin(null).extend(InvokeTest.class).public_();
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
        ClassMaker cm = ClassMaker.begin(null).extend(InvokeTest.class).public_();
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
        ClassMaker cm = ClassMaker.begin(null).extend(InvokeTest.class).public_();
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
        ClassMaker cm = ClassMaker.begin(null).extend(InvokeTest.class).public_();
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
        ClassMaker cm = ClassMaker.begin(null).extend(InvokeTest.class).public_();
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
        ClassMaker cm = ClassMaker.begin(null).extend(InvokeTest.class).public_();
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
        ClassMaker cm = ClassMaker.begin(null).extend(InvokeTest.class).public_();
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

        mm.return_(mm.invoke(handle, 10));

        assertEquals(100, cm.finish().getMethod("tunnel").invoke(null));
    }

    private static int secret(int a) {
        return a * a;
    }

    @Test
    public void invokeHandleExternal() throws Exception {
        // DynamicConstantDesc added in Java 12.
        Assume.assumeTrue(Runtime.getRuntime().version().feature() >= 12);

        ClassMaker cm = ClassMaker.beginExternal(getClass().getName() + "Fake").public_();
        MethodMaker mm = cm.addMethod(int.class, "add").public_().static_();

        MethodHandle handle = MethodHandles.lookup()
            .findStatic(InvokeTest.class, "notSecret", MethodType.methodType(int.class, int.class));

        mm.return_(mm.invoke(handle, 10));

        assertEquals(20, cm.finish().getMethod("add").invoke(null));
    }

    public static int notSecret(int a) {
        return a + a;
    }

    @Test
    public void invokeHandleKind() throws Exception {
        // Test a few different kinds of method handles.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run", InvokeTest.class).public_().static_();
        var assertVar = mm.var(Assert.class);

        var lookup = MethodHandles.lookup();
        MethodHandle h1 = lookup.findSetter(InvokeTest.class, "mFoo", int.class);
        MethodHandle h2 = lookup.findGetter(InvokeTest.class, "mFoo", int.class);
        MethodHandle h3 = lookup.findStaticSetter(InvokeTest.class, "mBar", int.class);
        MethodHandle h4 = lookup.findStaticGetter(InvokeTest.class, "mBar", int.class);

        assertNull(mm.invoke(h1, mm.param(0), 10));
        assertVar.invoke("assertEquals", 10,  mm.invoke(h2, mm.param(0)));

        assertNull(mm.invoke(h3, 20));
        assertVar.invoke("assertEquals", 20,  mm.invoke(h4));

        // Test constants too. Note that a MethodHandle is set with a MethodHandleInfo.

        var c1 = mm.var(MethodHandle.class).set(lookup.revealDirect(h1));
        assertVar.invoke("assertEquals", "MethodHandle(InvokeTest,int)void", c1.invoke("toString"));

        var c2 = mm.var(MethodHandle.class).set(lookup.revealDirect(h2));
        assertVar.invoke("assertEquals", "MethodHandle(InvokeTest)int", c2.invoke("toString"));

        var c3 = mm.var(MethodHandle.class).set(lookup.revealDirect(h3));
        assertVar.invoke("assertEquals", "MethodHandle(int)void", c3.invoke("toString"));

        var c4 = mm.var(MethodHandle.class).set(lookup.revealDirect(h4));
        assertVar.invoke("assertEquals", "MethodHandle()int", c4.invoke("toString"));

        cm.finish().getMethod("run", InvokeTest.class).invoke(null, this);
    }

    public int mFoo;
    public static int mBar;

    @Test
    public void invokeSpecific() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        var result = mm.var(InvokeTest.class).invoke(int.class, "foo", null);
        mm.var(Assert.class).invoke("assertEquals", 10, result);
        mm.var(InvokeTest.class).invoke((Object) null, "foo", new Object[]{String.class}, "hello");
        try {
            cm.finish().getMethod("run").invoke(null);
            fail();
        } catch (Exception e) {
            assertEquals("hello", e.getCause().getMessage());
        }
    }

    public static void foo(String msg) throws Exception {
        throw new Exception(msg);
    }

    public static int foo() {
        return 10;
    }

    @Test
    public void invokeSpecific2() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        {
            var result = mm.var(InvokeTest.class).invoke
                (String.class, "foo2", new Object[]{int.class, long.class}, 1, 2);

            mm.var(Assert.class).invoke("assertEquals", "hello12", result);
        }

        {
            var result = mm.var(InvokeTest.class).invoke
                (String.class, "foo2", new Object[]{long.class, int.class}, 1, 2);

            mm.var(Assert.class).invoke("assertEquals", "world12", result);
        }

        cm.finish().getMethod("run").invoke(null);
    }

    public static String foo2(int a, long b) {
        return "hello" + a + b;
    }

    public static String foo2(long a, int b) {
        return "world" + a + b;
    }

    @Test
    public void invokeSpecific3() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_().extend(InvokeTest.class);

        {
            MethodMaker mm = cm.addMethod(long.class, "foo").public_().static_();
            mm.return_(Long.MAX_VALUE);
        }

        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        try {
            mm.invoke("foo");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().startsWith("No best"));
        }

        var result = mm.var(cm).invoke(long.class, "foo", new Object[0]);
        mm.var(Assert.class).invoke("assertEquals", Long.MAX_VALUE, result);

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void invokeSelfStatic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        MethodMaker mm = cm.addMethod(int.class, "foo", int.class).private_().static_();
        mm.return_(mm.param(0).add(1));

        mm = cm.addMethod(int.class, "foo", long.class).private_();
        mm.return_(mm.param(0).add(-1).cast(int.class));

        mm = cm.addMethod(int.class, "run", int.class).public_().static_();
        mm.return_(mm.invoke("foo", mm.param(0)));

        assertEquals(11, cm.finish().getMethod("run", int.class).invoke(null, 10));
    }

    @Test
    public void varargs() throws Throwable {
        ClassMaker cm = ClassMaker.begin().public_();

        try {
            MethodMaker mm = cm.addMethod(null, "wrong").varargs();
            fail();
        } catch (IllegalStateException e) {
        }

        MethodMaker mm = cm.addMethod(String.class, "eval", int.class, Object[].class)
            .public_().static_().varargs();
        mm.return_(mm.concat("eval", mm.param(0),
                             mm.var(Arrays.class).invoke("asList", mm.param(1))));

        MethodType type = MethodType.methodType(String.class, int.class, Object[].class);
        MethodHandle mh = MethodHandles.lookup().findStatic(cm.finish(), "eval", type);

        assertTrue(mh.isVarargsCollector());
        assertEquals("eval5[one, two, three]", mh.invoke(5, "one", "two", "three"));
    }

    @Test
    public void invokeVarargs() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var result = mm.var(InvokeTest.class).invoke("concat", "hello", "world", "!!!");
        mm.var(Assert.class).invoke("assertEquals", "helloworld!!!", result);

        cm.finish().getMethod("run").invoke(null);
    }

    public static String concat(String first, String... rest) {
        var b = new StringBuilder(first);
        for (String s : rest) {
            b.append(s);
        }
        return b.toString();
    }

    @Test
    public void invokeVarargsConvert() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var result = mm.var(InvokeTest.class).invoke("concat2", "hello", "world", 123);
        mm.var(Assert.class).invoke("assertEquals", "helloworld123", result);

        cm.finish().getMethod("run").invoke(null);
    }

    public static String concat2(String first, Object... rest) {
        var b = new StringBuilder(first);
        for (Object s : rest) {
            b.append(s);
        }
        return b.toString();
    }

    @Test
    public void invokeVarargsConvert2() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        {
            var result = mm.var(InvokeTest.class).invoke("sum");
            mm.var(Assert.class).invoke("assertEquals", 0, result);
        }

        {
            var result = mm.var(InvokeTest.class).invoke("sum", 1, 2L, (byte) 3);
            mm.var(Assert.class).invoke("assertEquals", 6, result);
        }

        cm.finish().getMethod("run").invoke(null);
    }

    public static long sum(long... numbers) {
        long sum = 0;
        for (long num : numbers) {
            sum += num;
        }
        return sum;
    }

    @Test
    public void invokeVarargsNot() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        try {
            mm.var(InvokeTest.class).invoke("useless", 1);
            fail();
        } catch (IllegalStateException e) {
        }

        var numbers = mm.var(long[].class).setExact(new long[] {1, 2});
        var result = mm.var(InvokeTest.class).invoke("sum", numbers);
        mm.var(Assert.class).invoke("assertEquals", 3, result);

        cm.finish().getMethod("run").invoke(null);
    }

    public static void useless(int a, int b, int... rest) {
    }

    @Test
    public void invokeVarargsOverload() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var result = mm.var(InvokeTest.class).invoke("combine", 3, 4);
        mm.var(Assert.class).invoke("assertEquals", 7, result);

        cm.finish().getMethod("run").invoke(null);
    }

    public static int combine(int a, int b) {
        return a + b;
    }

    public static int combine(int a, int... rest) {
        fail();
        return 0;
    }

    @Test
    public void invokeSelfVarargs() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        {
            MethodMaker mm = cm.addMethod(int.class, "add", int[].class)
                .private_().static_().varargs();
            var sum = mm.var(int.class).set(0);
            var len = mm.param(0).alength();
            Label start = mm.label().here();
            Label cont = mm.label();
            len.ifGt(0, cont);
            mm.return_(sum);
            cont.here();
            len.inc(-1);
            sum.set(sum.add(mm.param(0).aget(len)));
            mm.goto_(start);
        }

        {
            MethodMaker mm = cm.addMethod(null, "run").public_().static_();
            var result = mm.invoke("add", 1, 2, 3);
            mm.var(Assert.class).invoke("assertEquals", 6, result);
        }

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void instanceVarargs() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var instance = mm.var(InvokeTest.class).setExact(this);
        var result = instance.invoke("self", 1, 2);
        mm.var(Assert.class).invoke("assertEquals", self(1, 2), result);

        cm.finish().getMethod("run").invoke(null);
    }

    public String self(int... stuff) {
        return getClass().getName() + Arrays.toString(stuff);
    }

    @Test
    public void methodType() throws Exception {
        // Test MethodType as a constant.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(MethodType.class, "run").public_().static_();

        MethodType type = MethodType.methodType(String.class, int.class, Object[].class);
        var v1 = mm.var(MethodType.class).set(type);
        mm.return_(v1);

        assertEquals(type, cm.finish().getMethod("run").invoke(null));
    }

    @Test
    public void uncacheMethod() throws Exception {
        // Test that when an overloaded method is added, the previous cache entry doesn't
        // interfere with the new method from being found.

        ClassMaker cm = ClassMaker.begin().public_();
        cm.addMethod(null, "run", String.class).private_().static_();

        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        mm.invoke("run", "hello");

        try {
            mm.invoke("run", 100);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().startsWith("No matching"));
        }

        cm.addMethod(null, "run", int.class).private_().static_();

        mm.invoke("run", 100);

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void dynamicConstant() throws Exception {
        // Test passing a dynamic constant as a bootstrap arg.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var bootstrap = mm.var(InvokeTest.class);
        var map = bootstrap.condy("makeMap", "hello", "world").invoke(Map.class, "xxx");
        var result = bootstrap.indy("bootWithMap", map, "hello").invoke(String.class, "xxx");

        var assertVar = mm.var(Assert.class);
        assertVar.invoke("assertEquals", "world", result);

        cm.finish().getMethod("run").invoke(null);
    }

    public static Map<String, String> makeMap(MethodHandles.Lookup lookup, String name, Class type,
                                              String key, String value)
    {
        return Map.of(key, value);
    }

    public static CallSite bootWithMap(MethodHandles.Lookup caller, String name, MethodType type,
                                       Map<String, String> map, String key)
        throws Exception
    {
        ClassMaker cm = ClassMaker.begin().public_().final_();
        MethodMaker mm = cm.addMethod(name, type).static_().public_();
        mm.return_(map.get(key));
        Class<?> clazz = cm.finish();
        var mh = MethodHandles.lookup().findStatic(clazz, name, type);
        return new ConstantCallSite(mh);
    }

    @Test
    public void indyVarargs() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var bootstrap = mm.var(InvokeTest.class);
        var result = bootstrap.indy("bootVarargs", 10, "hello", "world").invoke(String.class, "x");

        var assertVar = mm.var(Assert.class);
        assertVar.invoke("assertEquals", "10[hello, world]", result);

        cm.finish().getMethod("run").invoke(null);
    }

    public static CallSite bootVarargs(MethodHandles.Lookup caller, String name, MethodType type,
                                       int num, String... strs)
    {
        MethodMaker mm = MethodMaker.begin(MethodHandles.lookup(), String.class, "_");
        mm.return_(num + Arrays.toString(strs));
        return new ConstantCallSite(mm.finish());
    }

    @Test
    public void signaturePolymorphic() throws Exception {
        MethodHandle mh = MethodHandles.lookup()
            .findStatic(InvokeTest.class, "secret", MethodType.methodType(int.class, int.class));

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        var mhVar = mm.var(MethodHandle.class).setExact(mh);
        var assertVar = mm.var(Assert.class);

        {
            var result = mhVar.invoke(int.class, "invokeExact", new Object[] {int.class}, 10);
            assertVar.invoke("assertEquals", 100, result);
        }

        {
            var result = mhVar.invoke("invoke", 20);
            assertVar.invoke("assertEquals", 400, result);
        }

        {
            var result = mhVar.invoke(int.class, "invokeExact", null, 30);
            assertVar.invoke("assertEquals", 900, result);
        }

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void methodHandleBootstrap() throws Exception {
        // Test passing a MethodHandle constant to an indy bootstrap method.

        ClassMaker cm = ClassMaker.begin().public_();

        {
            MethodMaker mm = cm.addMethod(String.class, "foo", int.class).static_();
            mm.return_(mm.var(String.class).invoke("valueOf", mm.param(0).add(10)));
        }

        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        var assertVar = mm.var(Assert.class);

        var mhVar = mm.var(cm).methodHandle(String.class, "foo", int.class);
        var bootstrap = mm.var(InvokeTest.class).indy("bootTest", mhVar);

        var result = bootstrap.invoke(String.class, "xxx", new Object[] {int.class}, 123);

        assertVar.invoke("assertEquals", "133", result);

        cm.finish().getMethod("run").invoke(null);
    }

    public static CallSite bootTest(MethodHandles.Lookup caller, String name, MethodType type,
                                    MethodHandle mh)
    {
        MethodMaker mm = MethodMaker.begin(caller, String.class, "_", int.class);
        var result = mm.var(MethodHandle.class).set(caller.revealDirect(mh))
            .invoke(String.class, "invokeExact", new Object[] {int.class}, mm.param(0));
        mm.return_(result);

        return new ConstantCallSite(mm.finish());
    }

    @Test
    public void methodHandleBootstrap2() throws Exception {
        // Test passing a MethodHandle newInvokeSpecial constant to an indy bootstrap method.

        ClassMaker cm = ClassMaker.begin().public_();

        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        var assertVar = mm.var(Assert.class);

        try {
            mm.var(ArrayList.class).methodHandle(null, ".new", int.class);
            fail();
        } catch (IllegalStateException e) {
        }

        var mhVar = mm.var(ArrayList.class).methodHandle(ArrayList.class, ".new", int.class);
        var bootstrap = mm.var(InvokeTest.class).indy("bootTest2", mhVar);

        var result = bootstrap.invoke(List.class, "xxx", new Object[] {int.class}, 10);

        assertVar.invoke("assertTrue", result.instanceOf(ArrayList.class));

        cm.finish().getMethod("run").invoke(null);
    }

    public static CallSite bootTest2(MethodHandles.Lookup caller, String name, MethodType type,
                                     MethodHandle mh)
    {
        MethodMaker mm = MethodMaker.begin(caller, List.class, name, int.class);
        var result = mm.var(MethodHandle.class).set(caller.revealDirect(mh))
            .invoke(ArrayList.class, "invokeExact", new Object[] {int.class}, mm.param(0));
        mm.return_(result);

        return new ConstantCallSite(mm.finish());
    }

    @Test
    public void invokeNew() throws Exception {
        // Test that ".new" can be used as a method name in place of calling the new_ method.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var v1 = mm.var(ArrayList.class)
            .invoke(ArrayList.class, ".new", new Object[] {int.class}, 10);

        try {
            mm.var(ArrayList.class).invoke((Object) null, ".new", new Object[] {int.class}, 10);
            fail();
        } catch (IllegalStateException e) {
        }

        var v2 = mm.var(String[].class).invoke(String[].class, ".new", null, 10);

        try {
            mm.var(int.class).invoke(".new", 10);
            fail();
        } catch (IllegalStateException e) {
        }

        var assertVar = mm.var(Assert.class);
        assertVar.invoke("assertTrue", v1.instanceOf(ArrayList.class));
        assertVar.invoke("assertTrue", v2.instanceOf(String[].class));

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void invokeInit() throws Exception {
        // Test that "<init>" cannot be called.
        ClassMaker cm = ClassMaker.begin().public_();
        cm.addConstructor();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        try {
            mm.invoke("<init>");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("<init>"));
        }
    }

    @Test
    public void invokeClinit() throws Exception {
        // Test that "<clinit>" cannot be called.
        ClassMaker cm = ClassMaker.begin().public_();
        cm.addClinit();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        try {
            mm.invoke("<clinit>");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("<clinit>"));
        }
    }

    @Test
    public void objectMethods() throws Exception {
        // Tests Java 16 toString generator using the ObjectMethods class. This test just
        // ensures that a complex bootstrap method can be invoked. If the format of the
        // generated string changes, then this test needs to be revised.
        Assume.assumeTrue(Runtime.getRuntime().version().feature() >= 16);

        var cm = ClassMaker.begin().public_();

        cm.addField(int.class, "foo");
        cm.addField(String.class, "bar");

        {
            var mm = cm.addConstructor().public_();
            mm.invokeSuperConstructor();
            mm.field("foo").set(10);
            mm.field("bar").set("hello");
        }

        {
            var mm = cm.addMethod(int.class, "getFoo").private_();
            mm.return_(mm.field("foo"));
        }

        {
            var mm = cm.addMethod(String.class, "toString").public_();

            var bootstrap = mm.var("java.lang.runtime.ObjectMethods")
                .indy("bootstrap", mm.class_(), "foo;bar",
                      //mm.field("foo").methodHandleGet(),
                      mm.var(cm).methodHandle(int.class, "getFoo", (Object[]) null),
                      mm.field("bar").methodHandleGet());

            mm.return_(bootstrap.invoke(String.class, "toString",
                                        new Object[] {cm}, mm.this_()));
        }

        Class<?> clazz = cm.finish();
        var obj = clazz.getConstructor().newInstance();

        assertEquals(clazz.getSimpleName() + "[foo=10, bar=hello]", obj.toString());
    }
}
