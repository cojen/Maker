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

import java.lang.invoke.*;

import java.lang.reflect.Modifier;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class FieldTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(FieldTest.class.getName());
    }

    @Test
    public void cases() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_().sourceFile("FieldTest").final_();
        cm.addConstructor().public_().invokeSuperConstructor();

        cm.addField(String.class, "str").private_().static_().init("hello");
        cm.addField(int.class, "num1").private_();
        cm.addField(double.class, "num2").volatile_();

        FieldMaker fm = cm.addField(float.class, "num3");
        assertEquals(cm, fm.classMaker());
        assertEquals("num3", fm.name());

        try {
            fm.init(10.0f);
            fail();
        } catch (IllegalStateException e) {
        }

        fm.static_().final_().init(10.1f);

        cm.addField(double.class, "num4").static_().init(10.2);
        cm.addField(int.class, "num5").static_().init(10);
        cm.addField(long.class, "num6").static_().init(Long.MAX_VALUE);

        MethodMaker mm = cm.addMethod(null, "run").public_().final_();
        assertEquals(cm, mm.classMaker());

        var strVar = mm.field("str");
        assertEquals(mm, strVar.methodMaker());
        var num1Var = mm.field("num1");
        var num2Var = mm.field("num2");

        assertEquals(String.class, strVar.classType());
        assertNull(strVar.makerType());
        assertEquals(int.class, num1Var.classType());
        assertNull(num1Var.makerType());
        assertEquals(double.class, num2Var.classType());
        assertEquals("num2", num2Var.name());

        var assertVar = mm.var(Assert.class);
        assertVar.invoke("assertEquals", "hello", strVar);
        assertVar.invoke("assertEquals", 0L, num1Var);
        assertVar.invoke("assertEquals", 0.0, num2Var, 0);

        strVar.set("world");
        assertVar.invoke("assertEquals", "world", strVar.get());
        strVar.setPlain("plain world");
        assertVar.invoke("assertEquals", "plain world", strVar.getPlain());
        strVar.setOpaque("opaque world");
        assertVar.invoke("assertEquals", "opaque world", strVar.getOpaque());
        strVar.setRelease("acq/rel world");
        assertVar.invoke("assertEquals", "acq/rel world", strVar.getAcquire());
        strVar.setVolatile("volatile world");
        assertVar.invoke("assertEquals", "volatile world", strVar.getVolatile());

        assertVar.invoke("assertTrue", num1Var.compareAndSet(0, 10));
        assertVar.invoke("assertFalse", num1Var.compareAndSet(0, 20));
        assertVar.invoke("assertTrue", num1Var.weakCompareAndSet(10, 20));
        assertVar.invoke("assertTrue", num1Var.weakCompareAndSetPlain(20, 10));
        assertVar.invoke("assertTrue", num1Var.weakCompareAndSetAcquire(10, 15));
        assertVar.invoke("assertTrue", num1Var.weakCompareAndSetRelease(15, 20));
        assertVar.invoke("assertEquals", 20, num1Var.compareAndExchange(15, 30));
        assertVar.invoke("assertEquals", 20, num1Var.compareAndExchange(20, 30));
        assertVar.invoke("assertEquals", 30, num1Var.compareAndExchangeAcquire(30, 40));
        assertVar.invoke("assertEquals", 40, num1Var.compareAndExchangeRelease(40, 50));

        assertVar.invoke("assertEquals", 0.0, num2Var.getAndSet(1.2), 0.0);
        assertVar.invoke("assertEquals", 1.2, num2Var.getAndSetAcquire(1.4), 0.0);
        assertVar.invoke("assertEquals", 1.4, num2Var.getAndSetRelease(-1.6), 0.0);

        assertVar.invoke("assertEquals", 50, num1Var.getAndAdd(1));
        assertVar.invoke("assertEquals", 51, num1Var.getAndAddAcquire(1));
        assertVar.invoke("assertEquals", 52, num1Var.getAndAddRelease(1));
        assertVar.invoke("assertEquals", 53, num1Var);

        num1Var.set(0b1000);
        assertVar.invoke("assertEquals", 0b1000, num1Var.getAndBitwiseOr(0b001));
        assertVar.invoke("assertEquals", 0b1001, num1Var.getAndBitwiseOrAcquire(0b010));
        assertVar.invoke("assertEquals", 0b1011, num1Var.getAndBitwiseOrRelease(0b100));
        assertVar.invoke("assertEquals", 0b1111, num1Var.getAndBitwiseAnd(~0b001));
        assertVar.invoke("assertEquals", 0b1110, num1Var.getAndBitwiseAndAcquire(~0b010));
        assertVar.invoke("assertEquals", 0b1100, num1Var.getAndBitwiseAndRelease(~0b100));
        assertVar.invoke("assertEquals", 0b1000, num1Var.getAndBitwiseXor(0b1000));
        assertVar.invoke("assertEquals", 0b0000, num1Var.getAndBitwiseXorAcquire(0b1001));
        assertVar.invoke("assertEquals", 0b1001, num1Var.getAndBitwiseXorRelease(0b1001));
        assertVar.invoke("assertEquals", 0, num1Var);

        assertVar.invoke("assertEquals", 10.1f, mm.field("num3"), 0.0f);
        assertVar.invoke("assertEquals", 10.2d, mm.field("num4"), 0.0f);
        assertVar.invoke("assertEquals", 10, mm.field("num5"));
        assertVar.invoke("assertEquals", 10, mm.field("num5").getVolatile());
        assertVar.invoke("assertEquals", Long.MAX_VALUE, mm.field("num6"));

        num1Var.inc(10);
        assertVar.invoke("assertEquals", 10, num1Var.getVolatile());
        num1Var.setVolatile(20);
        assertVar.invoke("assertEquals", 20, num1Var.getPlain());

        assertVar.invoke("assertFalse", strVar.compareAndSet("yo", "yo!"));
        assertVar.invoke("assertEquals", "volatile world", strVar.getAndSet("yo!"));
        assertVar.invoke("assertEquals", "yo!", strVar);

        mm.return_();

        var clazz = cm.finish();
        var obj = clazz.getConstructor().newInstance();
        clazz.getMethod("run").invoke(obj);
    }

    @Test
    public void chain() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        var v1 = mm.new_(FieldTest.class);
        var v2 = mm.new_(FieldTest.class);
        var v3 = mm.new_(FieldTest.class);
        v1.field("next").set(v2);
        v1.field("next").field("next").set(v3);
        mm.var(Assert.class).invoke("assertEquals", v3, v1.field("next").field("next"));

        var clazz = cm.finish().getMethod("run").invoke(null);
    }

    public FieldTest next;

    @Test
    public void initConversions() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        var assertVar = mm.var(Assert.class);

        cm.addField(int.class, "f1").private_().static_().final_().init(1.0d);
        assertVar.invoke("assertEquals", 1, mm.field("f1"));

        cm.addField(float.class, "f2").private_().static_().final_().init(2.0d);
        assertVar.invoke("assertEquals", 2.0f, mm.field("f2"), 0);

        cm.addField(long.class, "f3").private_().static_().final_().init(3);
        assertVar.invoke("assertEquals", 3L, mm.field("f3"));

        cm.addField(double.class, "f4").private_().static_().final_().init(4);
        assertVar.invoke("assertEquals", 4.0d, mm.field("f4"), 0);

        cm.addField(long.class, "f5").private_().static_().final_().init(5.0d);
        assertVar.invoke("assertEquals", 5L, mm.field("f5"));

        cm.addField(double.class, "f6").private_().static_().final_().init((byte) 6);
        assertVar.invoke("assertEquals", 6.0d, mm.field("f6"), 0);

        cm.addField(String.class, "f7").private_().static_().final_().init(null);
        assertVar.invoke("assertEquals", null, mm.field("f7"));

        cm.addField(Integer.class, "f8").private_().static_().final_().init(null);
        assertVar.invoke("assertEquals", null, mm.field("f8"));

        cm.addField(boolean.class, "f9").private_().static_().final_().init(false);
        assertVar.invoke("assertEquals", false, mm.field("f9"));

        cm.addField(boolean.class, "f10").private_().static_().final_().init(true);
        assertVar.invoke("assertEquals", true, mm.field("f10"));

        cm.addField(byte.class, "f11").private_().static_().final_().init((byte) 11);
        assertVar.invoke("assertEquals", 11, mm.field("f11"));

        cm.addField(byte.class, "f12").private_().static_().final_().init(12);
        assertVar.invoke("assertEquals", 12, mm.field("f12"));

        cm.addField(char.class, "f13").private_().static_().final_().init((char) 13);
        assertVar.invoke("assertEquals", 13, mm.field("f13"));

        cm.addField(short.class, "f14").private_().static_().final_().init((short) 14);
        assertVar.invoke("assertEquals", 14, mm.field("f14"));

        cm.addField(short.class, "f15").private_().static_().final_().init(15);
        assertVar.invoke("assertEquals", 15, mm.field("f15"));

        cm.addField(Number.class, "f16").private_().static_().final_().init(16);
        assertVar.invoke("assertEquals", 16, mm.field("f16"));

        cm.addField(Class.class, "f17").private_().static_().final_().init(String.class);
        assertVar.invoke("assertEquals", String.class, mm.field("f17"));

        try {
            cm.addField(boolean.class, "f18").private_().static_().final_().init(17);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("conversion"));
        }

        try {
            cm.addField(char.class, "f19").private_().static_().final_().init("18");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("conversion"));
        }

        try {
            cm.addField(Integer.class, "f20").private_().static_().final_().init("19");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("conversion"));
        }

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void extraModifiers() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        cm.addField(int.class, "f1").public_().transient_();
        cm.addField(int.class, "f2").synthetic();
        cm.addField(int.class, "f3").transient_().volatile_();

        var clazz = cm.finish();

        {
            int mods = clazz.getField("f1").getModifiers();
            assertTrue(Modifier.isPublic(mods));
            assertTrue(Modifier.isTransient(mods));
            assertFalse(Modifier.isFinal(mods));
        }

        {
            int mods = clazz.getDeclaredField("f2").getModifiers();
            assertEquals(0x1000, mods);
        }

        {
            int mods = clazz.getDeclaredField("f3").getModifiers();
            assertTrue(Modifier.isTransient(mods));
            assertTrue(Modifier.isVolatile(mods));
        }
    }

    @Test
    public void methodHandles() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        cm.addField(int.class, "f1").static_();
        cm.addField(String.class, "f2").static_().private_();

        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        var assertVar = mm.var(Assert.class);

        mm.field("f1").set(10);
        var mh1 = mm.field("f1").methodHandleGet();
        var result = mh1.invoke(int.class, "invokeExact", null);
        assertVar.invoke("assertEquals", 10, result);

        var mh2 = mm.field("f2").methodHandleSet();
        mh2.invoke(void.class, "invokeExact", new Object[] {String.class}, "hello"); 
        assertVar.invoke("assertEquals", "hello", mm.field("f2").get());

        try {
            mh2.set(null);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Unmodifiable"));
        }

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void methodHandlesNonStatic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        cm.addConstructor().public_();

        cm.addField(int.class, "f1");
        cm.addField(String.class, "f2").private_();

        MethodMaker mm = cm.addMethod(null, "run").public_();
        var assertVar = mm.var(Assert.class);

        mm.field("f1").set(10);
        var mh1 = mm.field("f1").methodHandleGet();
        var result = mh1.invoke(int.class, "invokeExact", new Object[] {cm}, mm.this_());
        assertVar.invoke("assertEquals", 10, result);

        var mh2 = mm.field("f2").methodHandleSet();
        mh2.invoke(void.class, "invokeExact", new Object[]{cm, String.class}, mm.this_(), "hello"); 
        assertVar.invoke("assertEquals", "hello", mm.field("f2").get());

        try {
            mh2.set(null);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Unmodifiable"));
        }

        var clazz = cm.finish();
        var instance = clazz.getConstructor().newInstance();
        clazz.getMethod("run").invoke(instance);
    }

    @Test
    public void methodHandlesBootstrap() throws Exception {
        // Test passing the field MethodHandle to an indy bootstrap method.

        ClassMaker cm = ClassMaker.begin().public_();

        cm.addField(int.class, "f1").static_().private_();

        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        var assertVar = mm.var(Assert.class);

        var bootstrap = mm.var(FieldTest.class).indy("bootTest", mm.field("f1").methodHandleSet());
        bootstrap.invoke(void.class, "123");

        assertVar.invoke("assertEquals", 123, mm.field("f1").get());

        cm.finish().getMethod("run").invoke(null);
    }

    public static CallSite bootTest(MethodHandles.Lookup caller, String name, MethodType type,
                                    MethodHandle fieldHandle)
    {
        MethodMaker mm = MethodMaker.begin(MethodHandles.lookup(), void.class, "_");
        mm.invoke(fieldHandle, Integer.parseInt(name));
        return new ConstantCallSite(mm.finish());
    }

    @Test
    public void varHandles() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        cm.addField(int.class, "f1").static_();
        cm.addField(String.class, "f2").static_().private_();

        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        var assertVar = mm.var(Assert.class);

        mm.field("f1").set(10);
        var vh1 = mm.field("f1").varHandle();
        var result = vh1.invoke(int.class, "getOpaque", null);
        assertVar.invoke("assertEquals", 10, result);

        var vh2 = mm.field("f2").varHandle();
        vh2.invoke(void.class, "setRelease", new Object[] {String.class}, "hello"); 
        assertVar.invoke("assertEquals", "hello", mm.field("f2").get());

        try {
            vh2.set(null);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Unmodifiable"));
        }

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void varHandlesNonStatic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        cm.addConstructor().public_();

        cm.addField(int.class, "f1");
        cm.addField(String.class, "f2").private_();

        MethodMaker mm = cm.addMethod(null, "run").public_();
        var assertVar = mm.var(Assert.class);

        mm.field("f1").set(10);
        var vh1 = mm.field("f1").varHandle();
        var result = vh1.invoke(int.class, "getOpaque", new Object[] {cm}, mm.this_());
        assertVar.invoke("assertEquals", 10, result);

        var vh2 = mm.field("f2").varHandle();
        vh2.invoke(void.class, "setRelease", new Object[] {cm, String.class}, mm.this_(), "hello"); 
        assertVar.invoke("assertEquals", "hello", mm.field("f2").get());

        try {
            vh2.set(null);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Unmodifiable"));
        }

        var clazz = cm.finish();
        var instance = clazz.getConstructor().newInstance();
        clazz.getMethod("run").invoke(instance);
    }

    @Test
    public void varHandlesBootstrap() throws Exception {
        // Test passing the field VarHandle to an indy bootstrap method.

        ClassMaker cm = ClassMaker.begin().public_();

        cm.addField(int.class, "f1").static_().private_();

        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        var assertVar = mm.var(Assert.class);

        var bootstrap = mm.var(FieldTest.class).indy("bootTest", mm.field("f1").varHandle());
        bootstrap.invoke(void.class, "123");

        assertVar.invoke("assertEquals", -123, mm.field("f1").get());

        cm.finish().getMethod("run").invoke(null);
    }

    public static CallSite bootTest(MethodHandles.Lookup caller, String name, MethodType type,
                                    VarHandle fieldHandle)
    {
        MethodMaker mm = MethodMaker.begin(MethodHandles.lookup(), void.class, "_");
        mm.access(fieldHandle).setVolatile(-Integer.parseInt(name));
        return new ConstantCallSite(mm.finish());
    }

    @Test
    public void superField() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_().extend(FieldTest.class);
        cm.addConstructor().public_();

        MethodMaker mm = cm.addMethod(null, "run").public_();
        assertNull(mm.super_().name());

        try {
            mm.super_().name("foo");
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.super_().set(null);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.super_().setExact(this);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.super_().inc(1);
            fail();
        } catch (IllegalStateException e) {
        }

        Field field = mm.super_().field("next");
        // Actual instance should be the same as 'this'.
        field.set(mm.super_());

        var assertVar = mm.var(Assert.class);
        assertVar.invoke("assertEquals", mm.this_(), mm.field("next"));

        var clazz = cm.finish();
        var instance = clazz.getConstructor().newInstance();
        clazz.getMethod("run").invoke(instance);
    }
}
