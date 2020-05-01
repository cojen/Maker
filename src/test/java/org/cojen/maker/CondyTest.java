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

import java.util.ArrayList;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Tests for dynamic constants.
 *
 * @author Brian S O'Neill
 */
public class CondyTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(CondyTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        MethodHandle bootstrap = MethodHandles.lookup().findStatic
            (CondyTest.class, "boot", MethodType.methodType
             (Object.class, MethodHandles.Lookup.class, String.class, Class.class));

        MethodHandleInfo bootInfo = MethodHandles.lookup().revealDirect(bootstrap);

        var assertVar = mm.var(Assert.class);

        var v1 = mm.var(String.class);
        v1.setDynamic(bootInfo, null, "hello");
        assertVar.invoke("assertEquals", "hello-world", v1);

        var v2 = mm.var(int.class);
        v2.setDynamic(bootInfo, null, "dummy");
        assertVar.invoke("assertEquals", 123, v2);

        var v3 = mm.var(long.class);
        v3.setDynamic(bootInfo, null, "dummy");
        assertVar.invoke("assertEquals", Long.MAX_VALUE, v3);

        bootstrap = MethodHandles.lookup().findStatic
            (CondyTest.class, "boot", MethodType.methodType
             (String.class, MethodHandles.Lookup.class, String.class, Class.class,
              Class.class, int.class));

        bootInfo = MethodHandles.lookup().revealDirect(bootstrap);

        var v4 = mm.var(String.class);
        v4.setDynamic(bootInfo, new Object[] {java.util.Map.class, 999}, "dummy");
        assertVar.invoke("assertEquals", "java.util.Map999", v4);

        bootstrap = MethodHandles.lookup().findStatic
            (CondyTest.class, "boot", MethodType.methodType
             (Object.class, MethodHandles.Lookup.class, String.class, Class.class));

        bootInfo = MethodHandles.lookup().revealDirect(bootstrap);

        cm.addField(String.class, "test").private_().static_();
        mm.field("test").setDynamic(bootInfo, null, "the-field");
        assertVar.invoke("assertEquals", "the-field-world", mm.field("test"));

        cm.finish().getMethod("run").invoke(null);
    }

    public static Object boot(MethodHandles.Lookup lookup, String name, Class type)
        throws Exception
    {
        if (type == int.class) {
            return 123;
        } else if (type == long.class) {
            return Long.MAX_VALUE;
        } else {
            return name + "-world";
        }
    }

    public static String boot(MethodHandles.Lookup lookup, String name, Class type,
                              Class arg1, int arg2)
        throws Exception
    {
        return arg1.getName() + arg2;
    }

    @Test
    public void bootstrapWithPrimitive() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        MethodHandle bootstrap = MethodHandles.lookup().findStatic
            (CondyTest.class, "boot", MethodType.methodType
             (String.class, MethodHandles.Lookup.class, String.class, Class.class, Class.class));

        MethodHandleInfo bootInfo = MethodHandles.lookup().revealDirect(bootstrap);

        var assertVar = mm.var(Assert.class);

        Class[] types = {boolean.class, byte.class, char.class, short.class,
                         int.class, float.class, long.class, double.class};

        for (Class type : types) {
            var v1 = mm.var(String.class);
            v1.setDynamic(bootInfo, new Object[] {type}, "dummy");
            assertVar.invoke("assertEquals", type.toString(), v1);
        }

        cm.finish().getMethod("run").invoke(null);
    }

    public static String boot(MethodHandles.Lookup lookup, String name, Class type, Class arg1)
        throws Exception
    {
        return String.valueOf(arg1);
    }

    @Test
    public void bootstrapWithNull() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        MethodHandle bootstrap = MethodHandles.lookup().findStatic
            (CondyTest.class, "boot", MethodType.methodType
             (String.class, MethodHandles.Lookup.class, String.class, Class.class, Class.class));

        MethodHandleInfo bootInfo = MethodHandles.lookup().revealDirect(bootstrap);

        var assertVar = mm.var(Assert.class);

        var v1 = mm.var(String.class);
        v1.setDynamic(bootInfo, new Object[] {null}, "dummy");
        assertVar.invoke("assertEquals", "null", v1);

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void bootstrapWithEnum() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        MethodHandle bootstrap = MethodHandles.lookup().findStatic
            (CondyTest.class, "boot", MethodType.methodType
             (String.class, MethodHandles.Lookup.class, String.class, Class.class, Enum.class));

        MethodHandleInfo bootInfo = MethodHandles.lookup().revealDirect(bootstrap);

        var assertVar = mm.var(Assert.class);

        var v1 = mm.var(String.class);
        v1.setDynamic(bootInfo, new Object[] {Thread.State.BLOCKED}, "dummy");
        assertVar.invoke("assertEquals", "BLOCKED", v1);

        cm.finish().getMethod("run").invoke(null);
    }

    public static String boot(MethodHandles.Lookup lookup, String name, Class type, Enum arg1)
        throws Exception
    {
        return String.valueOf(arg1);
    }

    @Test
    public void basicEnum() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        var assertVar = mm.var(Assert.class);

        var v1 = mm.var(Thread.State.class).set(Thread.State.NEW);
        var v2 = v1.invoke("toString");
        assertVar.invoke("assertEquals", "NEW", v2);

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void complexConstants() throws Exception {
        ClassMaker cm = ClassMaker.begin(null, ArrayList.class).public_();
        cm.addConstructor().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_();

        var const0 = new byte[] {1,2,3};
        mm.invoke("add", mm.var(byte[].class).setConstant(const0));

        var const1 = "hello";
        mm.invoke("add", mm.var(String.class).setConstant(const1));

        var const2 = new Long(8675309);
        mm.invoke("add", mm.var(Long.class).setConstant(const2));

        var const3 = this;
        mm.invoke("add", mm.var(CondyTest.class).setConstant(const3));

        var const4 = System.getProperties();
        mm.invoke("add", mm.var(Object.class).setConstant(const4));

        mm.invoke("add", "hello");
        mm.invoke("set", 5, mm.var(Object.class).setConstant(null));

        cm.addField(byte[].class, "test").private_();
        mm.field("test").setConstant(const0);
        mm.invoke("add", mm.field("test").get());

        var clazz = cm.finish();
        var instance = (ArrayList<Object>) clazz.getConstructor().newInstance();
        clazz.getMethod("run").invoke(instance);

        assertTrue(const0 == instance.get(0));
        assertTrue(const1 == instance.get(1));
        assertTrue(const2 == instance.get(2));
        assertTrue(const3 == instance.get(3));
        assertTrue(const4 == instance.get(4));
        assertEquals(null, instance.get(5));
        assertTrue(const0 == instance.get(6));
    }
}
