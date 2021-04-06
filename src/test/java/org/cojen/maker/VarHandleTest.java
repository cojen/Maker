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

import java.lang.invoke.*;

import java.nio.ByteOrder;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class VarHandleTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(VarHandleTest.class.getName());
    }

    @Test
    public void arrayAccess() throws Exception {
        VarHandle vh = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var bytes = mm.var(byte[].class).setExact("hello world".getBytes("UTF-8"));
        var result = mm.access(vh, bytes, 4).get();

        int expect = ('o' << 24) | (' ' << 16) | ('w' << 8) | 'o';

        var assertVar = mm.var(Assert.class);
        assertVar.invoke("assertEquals", expect, result);

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void accessExternal() throws Exception {
        // DynamicConstantDesc added in Java 12.
        Assume.assumeTrue(Runtime.getRuntime().version().feature() >= 12);

        VarHandle vh = MethodHandles.arrayElementVarHandle(int[].class);

        ClassMaker cm = ClassMaker.beginExternal(getClass().getName() + "Fake").public_();
        MethodMaker mm = cm.addMethod(int.class, "run", int[].class).public_().static_();

        mm.return_(mm.access(vh, mm.param(0), 1).get());

        Object result = cm.finish().getMethod("run", int[].class).invoke(null, new int[] {5, 6, 7});

        assertEquals(6, result);
    }

    @Test
    public void privateAccess() throws Exception {
        VarHandle vh = MethodHandles.lookup()
            .findStaticVarHandle(VarHandleTest.class, "hiddenStr", String.class);

        hiddenStr = "hello";

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        mm.access(vh).set("world");

        cm.finish().getMethod("run").invoke(null);

        assertEquals("world", hiddenStr);
    }

    private static String hiddenStr;

    @Test
    public void compareAndSet() throws Exception {
        VarHandle vh = MethodHandles.lookup()
            .findStaticVarHandle(VarHandleTest.class, "hiddenInt", int.class);

        hiddenInt = 10;

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        Field field = mm.access(vh);
        assertNull(field.name());

        var assertVar = mm.var(Assert.class);
        assertVar.invoke("assertFalse", (field.compareAndSet(1, 11)));
        assertVar.invoke("assertTrue", (field.compareAndSet(10, 11)));

        cm.finish().getMethod("run").invoke(null);

        assertEquals(11, hiddenInt);
    }

    private static int hiddenInt;

    @Test
    public void getAndSet() throws Exception {
        VarHandle vh = MethodHandles.lookup()
            .findStaticVarHandle(VarHandleTest.class, "hiddenInt", int.class);

        hiddenInt = 10;

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        Field field = mm.access(vh);

        var assertVar = mm.var(Assert.class);
        assertVar.invoke("assertEquals", 10, field.getAndSet(11));

        cm.finish().getMethod("run").invoke(null);

        assertEquals(11, hiddenInt);
    }

    @Test
    public void setExact() throws Exception {
        VarHandle vh1 = MethodHandles.lookup()
            .findStaticVarHandle(VarHandleTest.class, "hiddenObj", Object.class);
        VarHandle vh2 = MethodHandles.lookup()
            .findStaticVarHandle(VarHandleTest.class, "hiddenArray", Object[].class);
        VarHandle vh3 = MethodHandles.arrayElementVarHandle(Object[].class);

        hiddenObj = null;
        hiddenArray = new Object[2];

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var access1 = mm.access(vh1);
        access1.setExact(this);
        var assertVar = mm.var(Assert.class);
        assertVar.invoke("assertEquals", VarHandleTest.class, access1.invoke("getClass"));
        
        var array = mm.access(vh2);
        mm.access(vh3, array, 1).setExact(this);

        cm.finish().getMethod("run").invoke(null);

        assertEquals(this, hiddenObj);
        assertEquals(this, hiddenArray[1]);
    }

    private static Object hiddenObj;
    private static Object[] hiddenArray;

    @Test
    public void arrayAtomics() throws Exception {
        VarHandle vh = MethodHandles.arrayElementVarHandle(int[].class);

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var assertVar = mm.var(Assert.class);
        var array = mm.new_(int[].class, 10);
        var access = mm.access(vh, array, 1);

        assertVar.invoke("assertEquals", true, access.compareAndSet(0, 5));
        assertVar.invoke("assertEquals", 5, access.getAndSet(6));
        assertVar.invoke("assertEquals", 6, access.compareAndExchange(6, 7));
        assertVar.invoke("assertEquals", 7, access.get());

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void arrayFill() throws Exception {
        VarHandle vh = MethodHandles.arrayElementVarHandle(int[].class);

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(int[].class, "run").public_().static_();

        var assertVar = mm.var(Assert.class);

        var array = mm.new_(int[].class, 10);
        var ix = mm.var(int.class).set(0);
        var access = mm.access(vh, array, ix);
        Label start = mm.label().here();
        Label end = mm.label();
        ix.ifGe(array.alength(), end);
        access.set(ix.add(100));
        ix.inc(1);
        mm.goto_(start);

        end.here();
        mm.return_(array);

        int[] result = (int[]) cm.finish().getMethod("run").invoke(null);

        assertEquals(10, result.length);
        for (int i=0; i<result.length; i++) {
            assertEquals(i + 100, result[i]);
        }
    }

    @Test
    public void changeCoordinate() throws Exception {
        VarHandle vh = MethodHandles.arrayElementVarHandle(int[].class);

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(int[].class, "run").public_().static_();

        var assertVar = mm.var(Assert.class);

        var array = mm.new_(int[].class, 2);
        var coords = new Object[] {array, 0};
        var access = mm.access(vh, coords);
        access.set(10);
        coords[1] = 1;
        access.set(20);
        mm.return_(array);

        int[] result = (int[]) cm.finish().getMethod("run").invoke(null);

        assertArrayEquals(new int[] {10, 20}, result);
    }

    @Test
    public void signaturePolymorphic() throws Exception {
        VarHandle vh = MethodHandles.lookup()
            .findStaticVarHandle(VarHandleTest.class, "hiddenInt", int.class);

        hiddenInt = 10;

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        var vhVar = mm.var(VarHandle.class).setExact(vh);
        var assertVar = mm.var(Assert.class);

        {
            var result = vhVar.invoke(int.class, "compareAndExchange",
                                      new Object[] {int.class, int.class}, 10, 11);
            assertVar.invoke("assertEquals", 10, result);
        }

        {
            var result = vhVar.invoke("compareAndExchange", 11, 12);
            assertVar.invoke("assertEquals", 11, result);
        }

        {
            var result = vhVar.invoke(int.class, "compareAndExchange", null, 12, 13);
            assertVar.invoke("assertEquals", 12, result);
        }

        cm.finish().getMethod("run").invoke(null);

        assertEquals(13, hiddenInt);
    }

    @Test
    public void toMethodHandle() throws Exception {
        VarHandle vh = MethodHandles.lookup()
            .findStaticVarHandle(VarHandleTest.class, "hiddenStr", String.class);

        hiddenStr = "hello";

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var access = mm.access(vh);

        var mh1 = access.methodHandleGet();
        var mh2 = access.methodHandleSet();

        // Must be a new instance each time and not a copy.
        assertTrue(mh1 != access.methodHandleGet());
        assertTrue(mh2 != access.methodHandleSet());
        assertTrue(access.varHandle() != mh1);

        var value = mh1.invoke(String.class, "invokeExact", null);
        var concat = mm.concat(value, "world");
        mh2.invoke(void.class, "invokeExact", new Object[] {String.class}, concat);

        cm.finish().getMethod("run").invoke(null);

        assertEquals("helloworld", hiddenStr);
    }

    @Test
    public void meta() throws Exception {
        // Test layering of signature polymorphic methods.

        ClassMaker cm = ClassMaker.begin().public_();
        {
            cm.addConstructor().public_();

            cm.addField(int.class, "test");

            MethodMaker mm = cm.addMethod(MethodHandle.class, "run").public_();

            mm.field("test").set(100);

            var vh = mm.field("test").varHandle();

            // Obtain a MethodHandle instance with a specific signature.
            var mh = vh.methodHandle(int.class, "get", cm);

            var result = mh.invoke(int.class, "invoke", null, vh, mm.this_());

            var assertVar = mm.var(Assert.class);
            assertVar.invoke("assertEquals", 100, result);

            mm.return_(mh);
        }

        var clazz = cm.finish();
        var obj = clazz.getConstructor().newInstance();
        var mh = (MethodHandle) clazz.getMethod("run").invoke(obj);

        assertEquals(MethodType.methodType(int.class, VarHandle.class, clazz), mh.type());
    }
}
