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

import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;

import java.lang.invoke.*;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Vector;

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

        var bootstrap = mm.var(CondyTest.class).condy("boot");
        var assertVar = mm.var(Assert.class);

        var v1 = bootstrap.invoke(String.class, "hello");
        assertVar.invoke("assertEquals", "hello-world", v1);

        var v2 = bootstrap.invoke(int.class, "dummy");
        assertVar.invoke("assertEquals", 123, v2);

        var v3 = bootstrap.invoke(long.class, "dummy");
        assertVar.invoke("assertEquals", Long.MAX_VALUE, v3);

        // Duplicate use of a constant. Only one constant pool entry should be generated,
        // although there's no convenient way to verify this.
        var v4 = bootstrap.invoke(long.class, "dummy");
        assertVar.invoke("assertEquals", Long.MAX_VALUE, v4);

        bootstrap = mm.var(CondyTest.class).condy("boot", java.util.Map.class, 999);

        try {
            bootstrap.invoke(String.class, "dummy", new Object[]{Object.class}, (Object[]) null);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("no parameters"));
        }

        try {
            bootstrap.invoke(String.class, "dummy", new Object[0], "bogus");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("no parameters"));
        }

        try {
            bootstrap.invoke(null, "_", null, (Object[]) null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equals("Unsupported constant type: null"));
        }

        var v5 = bootstrap.invoke(String.class, "dummy");
        assertVar.invoke("assertEquals", "java.util.Map999", v5);

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

        var v0 = mm.var(CondyTest.class);
        var assertVar = mm.var(Assert.class);

        Class[] types = {boolean.class, byte.class, char.class, short.class,
                         int.class, float.class, long.class, double.class};

        for (Class type : types) {
            var v1 = v0.condy("boot", type).invoke(String.class, "dummy");
            assertVar.invoke("assertEquals", type.toString(), v1);
        }

        cm.finish().getMethod("run").invoke(null);
    }

    public static Object boot(MethodHandles.Lookup lookup, String name, Class type, Class arg1)
        throws Exception
    {
        return String.valueOf(arg1);
    }

    @Test
    public void bootstrapWithPrimitiveValue() throws Exception {
        // Test passing a special constant (a short) to a bootstrap method. As of Java 12, it
        // will use Constable and DynamicConstantDesc. For Java 11, the ConstantsRegistry is
        // used instead.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        var v0 = mm.var(CondyTest.class);
        var assertVar = mm.var(Assert.class);

        var v1 = v0.condy("boot_s", (short) 12345).invoke(String.class, "dummy");
        assertVar.invoke("assertEquals", "12345", v1);

        cm.finish().getMethod("run").invoke(null);
    }

    public static Object boot_s(MethodHandles.Lookup lookup, String name, Class type, short arg)
        throws Exception
    {
        return String.valueOf(arg);
    }

    @Test
    public void bootstrapWithNull() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        var bootstrap = mm.var(CondyTest.class).condy("bootx", (Object) null);
        var v1 = bootstrap.invoke(String.class, "dummy");
        var assertVar = mm.var(Assert.class);
        assertVar.invoke("assertEquals", "null", v1);

        cm.finish().getMethod("run").invoke(null);
    }

    public static Object bootx(MethodHandles.Lookup lookup, String name, Class type, String arg1)
        throws Exception
    {
        return String.valueOf(arg1);
    }

    @Test
    public void bootstrapWithEnum() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        var bootstrap = mm.var(CondyTest.class).condy("boot", Thread.State.BLOCKED);
        var v1 = bootstrap.invoke(String.class, "dummy");
        var assertVar = mm.var(Assert.class);
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
        ClassMaker cm = ClassMaker.begin(null).extend(ArrayList.class).public_();
        cm.addConstructor().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_();

        var const0 = new byte[] {1,2,3};
        mm.invoke("add", mm.var(byte[].class).setExact(const0));

        var const1 = "hello";
        mm.invoke("add", mm.var(String.class).setExact(const1));

        var const2 = Long.valueOf(8675309);
        mm.invoke("add", mm.var(Long.class).setExact(const2));

        var const3 = this;
        mm.invoke("add", mm.var(CondyTest.class).setExact(const3));

        var const4 = System.getProperties();
        mm.invoke("add", mm.var(Object.class).setExact(const4));

        mm.invoke("add", "hello");
        mm.invoke("set", 5, mm.var(Object.class).setExact(null));

        cm.addField(byte[].class, "test").private_();
        mm.field("test").setExact(const0);
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

    @Test
    public void lazyInit() throws Exception {
        lazyInit(false);
    }

    @Test
    public void lazyInitHidden() throws Exception {
        lazyInit(true);
    }

    private void lazyInit(boolean hidden) throws Exception {
        // The static initializer isn't run immediately when classes are generated, allowing
        // complex constants to be handed off correctly.

        ClassMaker cm = ClassMaker.begin(null, MethodHandles.lookup())
            .extend(ArrayList.class).public_();
        cm.addField(byte[].class, "test").public_().static_().final_();

        MethodMaker mm = cm.addClinit();
        var const0 = new byte[] {1,2,3};
        mm.field("test").setExact(const0);

        Class<?> clazz = hidden ? cm.finishHidden().lookupClass() : cm.finish();

        assertTrue(const0 == clazz.getField("test").get(null));
    }

    @Test
    public void mismatch() {
        ClassMaker cm = ClassMaker.begin(null);
        MethodMaker mm = cm.addMethod(null, "test");
        try {
            mm.var(String.class).setExact(new ArrayList());
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().startsWith("Mismatched"));
        }
    }

    @Test
    public void unmodifiable() {
        ClassMaker cm = ClassMaker.begin(null);
        MethodMaker mm = cm.addMethod(null, "test");
        var bootstrap = mm.var(CondyTest.class).condy("boot");

        var v1 = bootstrap.invoke(String.class, "hello");

        try {
            v1.set(null);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().startsWith("Unmodifiable"));
        }

        try {
            v1.setExact("world");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().startsWith("Unmodifiable"));
        }

        try {
            v1.inc(1);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().startsWith("Unmodifiable"));
        }
    }

    @Test
    public void sneaky() throws Exception {
        // Try to steal a complex constant, to verify some of the security features. When given
        // enough access, the constant can be stolen. This isn't a feature, but just an
        // artifact of the current implementation.

        ClassMaker cm = ClassMaker.begin(null, MethodHandles.lookup());
        cm.addField(byte[].class, "test").public_().static_().final_();

        MethodMaker mm = cm.addClinit();
        var const0 = new byte[] {1,2,3};
        mm.field("test").setExact(const0);

        var lookup = cm.finishHidden();
        var clazz = lookup.lookupClass();

        // Class hasn't been initialized yet. Try to steal via the backdoor.

        try {
            ConstantsRegistry.remove(MethodHandles.lookup(), "_", null, 0);
            fail();
        } catch (IllegalStateException e) {
            // Wrong lookup, so not found.
        }

        try {
            ConstantsRegistry.remove(MethodHandles.lookup().in(clazz), "_", null, 0);
            fail();
        } catch (IllegalStateException e) {
            // Doesn't have private access.
        }

        try {
            ConstantsRegistry.remove
                (lookup.dropLookupMode(MethodHandles.Lookup.PRIVATE), "_", null, 0);
            fail();
        } catch (IllegalStateException e) {
            // Doesn't have private access.
        }

        if (Runtime.getRuntime().version().feature() < 15) {
            // Hidden classes aren't really supported, and so private lookup isn't available.
            return;
        }

        // Works when given full permission.
        Object const1 = ConstantsRegistry.remove(lookup, "_", null, 0);
        assertEquals(const0, const1);

        try {
            clazz.getField("test").get(null);
            fail();
        } catch (BootstrapMethodError e) {
            // Stolen!
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }


    @Test
    public void classDesc() throws Exception {
        // ClassDesc added in Java 12.
        Assume.assumeTrue(Runtime.getRuntime().version().feature() >= 12);

        ClassDesc cd0 = ClassDesc.of("java.lang.String").arrayType();
        ClassDesc cd1 = ClassDesc.ofDescriptor("I");
        ClassDesc cd2 = ClassDesc.ofDescriptor("I").arrayType();

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(Class[].class, "test").public_().static_();

        var c0 = mm.var(Class.class).set(cd0);
        var c1 = mm.var(Class.class).set(cd1);
        var c2 = mm.var(Class.class).set(cd2);

        var result = mm.new_(Class[].class, 3);
        result.aset(0, c0);
        result.aset(1, c1);
        result.aset(2, c2);

        mm.return_(result);

        Class[] actual = (Class[]) cm.finish().getMethod("test").invoke(null);

        assertEquals(String[].class, actual[0]);
        assertEquals(int.class, actual[1]);
        assertEquals(int[].class, actual[2]);
    }

    @Test
    public void methodTypeDesc() throws Exception {
        // MethodTypeDesc added in Java 12.
        Assume.assumeTrue(Runtime.getRuntime().version().feature() >= 12);

        MethodTypeDesc mtd0 = MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)I");

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(MethodType[].class, "test").public_().static_();

        var c0 = mm.var(MethodType.class).set(mtd0);

        var result = mm.new_(MethodType[].class, 1);
        result.aset(0, c0);

        mm.return_(result);

        MethodType[] actual = (MethodType[]) cm.finish().getMethod("test").invoke(null);

        MethodType mt = MethodType.methodType(int.class, String.class);
        assertEquals(mt, actual[0]);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void methodHandleDesc() throws Throwable {
        // MethodHandleDesc added in Java 12.
        Assume.assumeTrue(Runtime.getRuntime().version().feature() >= 12);

        ClassDesc cd0 = ClassDesc.of(Vector.class.getName());
        ClassDesc cd1 = ClassDesc.ofDescriptor("I");
        ClassDesc cd2 = ClassDesc.of(Object.class.getName());
        ClassDesc cd3 = ClassDesc.of(CondyTest.class.getName());
        ClassDesc cd4 = ClassDesc.of(SomeClass.class.getName());

        MethodTypeDesc mtd0 = MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Object;");

        DirectMethodHandleDesc mhd0 = MethodHandleDesc.ofConstructor(cd0, cd1);
        DirectMethodHandleDesc mhd1 = MethodHandleDesc.ofMethod
            (DirectMethodHandleDesc.Kind.VIRTUAL, cd0, "get", mtd0);

        DirectMethodHandleDesc mhd2 = MethodHandleDesc.ofField
            (DirectMethodHandleDesc.Kind.STATIC_GETTER, cd3, "test_field", cd1);
        DirectMethodHandleDesc mhd3 = MethodHandleDesc.ofField
            (DirectMethodHandleDesc.Kind.STATIC_SETTER, cd3, "test_field", cd1);

        DirectMethodHandleDesc mhd4 = MethodHandleDesc.ofField
            (DirectMethodHandleDesc.Kind.GETTER, cd4, "some_field", cd1);
        DirectMethodHandleDesc mhd5 = MethodHandleDesc.ofField
            (DirectMethodHandleDesc.Kind.SETTER, cd4, "some_field", cd1);

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(MethodHandle[].class, "test").public_().static_();

        var c0 = mm.var(MethodHandle.class).set(mhd0);
        var c1 = mm.var(MethodHandle.class).set(mhd1);
        var c2 = mm.var(MethodHandle.class).set(mhd2);
        var c3 = mm.var(MethodHandle.class).set(mhd3);
        var c4 = mm.var(MethodHandle.class).set(mhd4);
        var c5 = mm.var(MethodHandle.class).set(mhd5);

        var result = mm.new_(MethodHandle[].class, 6);
        result.aset(0, c0);
        result.aset(1, c1);
        result.aset(2, c2);
        result.aset(3, c3);
        result.aset(4, c4);
        result.aset(5, c5);

        mm.return_(result);

        MethodHandle[] handles = (MethodHandle[]) cm.finish().getMethod("test").invoke(null);

        Vector obj0 = (Vector) handles[0].invoke(10);
        assertEquals(10, obj0.capacity());
        obj0.add("hello");

        assertEquals("hello", handles[1].invoke(obj0, 0));

        test_field = 123;
        assertEquals(123, (int) handles[2].invoke());

        handles[3].invoke(999);
        assertEquals(999, test_field);

        var sc = new SomeClass();
        sc.some_field = 10;

        assertEquals(10, (int) handles[4].invoke(sc));

        handles[5].invoke(sc, 321);
        assertEquals(321, sc.some_field);
    }

    public static int test_field;

    public static class SomeClass {
        public int some_field;
    }

    @Test
    public void dynamicConstantDesc() throws Exception {
        // DynamicConstantDesc added in Java 12.
        Assume.assumeTrue(Runtime.getRuntime().version().feature() >= 12);

        ClassDesc cd0 = ClassDesc.of(CondyTest.class.getName());
        ClassDesc cd1 = ClassDesc.of(MethodHandles.Lookup.class.getName());
        ClassDesc cd2 = ClassDesc.of(String.class.getName());
        ClassDesc cd3 = ClassDesc.of(Class.class.getName());
        ClassDesc cd4 = ClassDesc.ofDescriptor("I");

        MethodTypeDesc mtd0 = MethodTypeDesc.of(cd2, cd1, cd2, cd3, cd4, cd3);

        DirectMethodHandleDesc boot = MethodHandleDesc.ofMethod
            (DirectMethodHandleDesc.Kind.STATIC, cd0, "dynBoot", mtd0);

        DynamicConstantDesc dcd0 = DynamicConstantDesc.of(boot, 10, cd2);

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(Object[].class, "test").public_().static_();

        var c0 = mm.var(String.class).set(dcd0);

        var result = mm.new_(Object[].class, 1);
        result.aset(0, c0);

        mm.return_(result);

        Object[] actual = (Object[]) cm.finish().getMethod("test").invoke(null);

        assertEquals("hello-10:String", actual[0]);
    }

    public static String dynBoot(MethodHandles.Lookup lookup, String name, Class type,
                                 int arg, Class someClass)
    {
        return "hello-" + arg + ":" + someClass.getSimpleName();
    }

    @Test
    public void dynamicConstantAssign() throws Exception {
        // DynamicConstantDesc added in Java 12.
        Assume.assumeTrue(Runtime.getRuntime().version().feature() >= 12);

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(Object.class, "run").static_().public_();

        var v = mm.var(Object.class);

        try {
            v.set(new MagicConstant(-1));
            fail();
        } catch (IllegalArgumentException e) {
            // Unsupported magic constant.
        }

        v.set(new MagicConstant(100));

        mm.return_(v);

        Object result = cm.finish().getMethod("run").invoke(null);

        assertTrue(result instanceof MagicConstant);
        assertEquals(100, ((MagicConstant) result).value);
    }

    public static class MagicBooter {
        // Hide inside another class to prevent linkage errors when running pre Java 12.
        public static MagicConstant boot_magic(MethodHandles.Lookup lookup, String name, Class type,
                                               int value)
            throws Exception
        {
            return new MagicConstant(value);
        }
    }

    public static class MagicConstant implements Constable {
        final int value;

        MagicConstant(int value) {
            this.value = value;
        }

        @Override
        public Optional<? extends ConstantDesc> describeConstable() {
            if (value < 0) {
                return Optional.empty();
            }

            DynamicConstantDesc desc = DynamicConstantDesc
                .of(MethodHandleDesc.of
                    (DirectMethodHandleDesc.Kind.STATIC,
                     MagicBooter.class.describeConstable().get(),
                     "boot_magic",
                     "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;" +
                     "Ljava/lang/Class;I)Lorg/cojen/maker/CondyTest$MagicConstant;"),
                    value);

            return Optional.of(desc);
        }

        @Override
        public String toString() {
            return "magic-" + value;
        }
    }

    @Test
    public void dynamicConstantAssignForBootstrap() throws Exception {
        // DynamicConstantDesc added in Java 12.
        Assume.assumeTrue(Runtime.getRuntime().version().feature() >= 12);

        // Pass a dynamic constant to a bootstrap method.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(Object.class, "run").static_().public_();

        var c = new MagicConstant(123);
        var bootstrap = mm.var(CondyTest.class).condy("boot_magic2", c);
        var v = bootstrap.invoke(String.class, "hello ");
        mm.return_(v);
        
        Object result = cm.finish().getMethod("run").invoke(null);
        assertEquals("hello magic-123", result);
    }

    public static String boot_magic2(MethodHandles.Lookup lookup, String name, Class type,
                                     Object magic)
        throws Exception
    {
        return name + magic.toString();
    }

    @Test
    public void constantVarHandle() throws Exception {
        // DynamicConstantDesc added in Java 12.
        Assume.assumeTrue(Runtime.getRuntime().version().feature() >= 12);

        // Set a VarHandle variable with a constant, which uses a DynamicConstantDesc.

        VarHandle vh = MethodHandles.arrayElementVarHandle(int[].class);

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(int.class, "get", int[].class, int.class).public_().static_();

        var vhVar = mm.var(VarHandle.class).set(vh);
        mm.return_(vhVar.invoke(int.class, "getVolatile",
                                new Object[] {int[].class, int.class}, mm.param(0), mm.param(1)));

        Object result = cm.finish()
            .getMethod("get", int[].class, int.class).invoke(null, new int[] {1, 2, 3}, 1);

        assertEquals(2, result);
    }

    @Test
    public void bootWithVarHandle() throws Exception {
        // A VarHandle lookup isn't lost when building a class dynamically, even though it's
        // Constable.

        hidden = 1234;

        VarHandle vh = MethodHandles.lookup()
            .findStaticVarHandle(CondyTest.class, "hidden", int.class);

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(String.class, "run").static_().public_();

        var bootstrap = mm.var(CondyTest.class).condy("bootWithVarHandle", vh);
        var v = bootstrap.invoke(String.class, "hello ");
        mm.return_(v);

        Object result = cm.finish().getMethod("run").invoke(null);
        assertEquals("hello 1234", result);
    }

    private static int hidden;

    public static String bootWithVarHandle(MethodHandles.Lookup lookup, String name, Class type,
                                           VarHandle vh)
        throws Exception
    {
        return name + (int) vh.get();
    }

    @Test
    public void bootWithVarHandleDesc() throws Exception {
        // Test that a VarHandle can be passed to a bootstrap method as a ConstantDesc.

        notHidden = 9999;

        VarHandle vh = MethodHandles.lookup()
            .findStaticVarHandle(CondyTest.class, "notHidden", int.class);
        ConstantDesc desc = vh.describeConstable().get();

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(String.class, "run").static_().public_();

        var bootstrap = mm.var(CondyTest.class).condy("bootWithVarHandle", desc);
        var v = bootstrap.invoke(String.class, "hello ");
        mm.return_(v);

        Object result = cm.finish().getMethod("run").invoke(null);
        assertEquals("hello 9999", result);
    }

    public static int notHidden;

    @Test
    public void specialBootArgs() throws Exception {
        // Test various special bootstrap argument types which resolve differently at runtime.
        // This is an extension to the bootWithVarHandleDesc test.

        var lookup = MethodHandles.lookup();

        var mt = MethodType.methodType(String.class, int.class);
        var mh = lookup.findStatic(CondyTest.class, "someMethod", mt);

        ClassDesc a = String.class.describeConstable().get();
        MethodTypeDesc b = mt.describeConstable().get();
        MethodHandleInfo c = lookup.revealDirect(mh);
        MethodHandleDesc d = mh.describeConstable().get();

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(String.class, "run").static_().public_();

        var bootstrap = mm.var(CondyTest.class).condy("bootSpecials", a, b, c, d);
        var v = bootstrap.invoke(String.class, "hello");
        mm.return_(v);

        Object result = cm.finish().getMethod("run").invoke(null);
        String expect = "hello:String:(int)String:MethodHandle(int)String:MethodHandle(int)String";
        assertEquals(expect, result);
    }

    public static String someMethod(int param) {
        return "" + param;
    }

    public static String bootSpecials(MethodHandles.Lookup lookup, String name, Class type,
                                      Class a, TypeDescriptor b, MethodHandle c, Constable d)
        throws Exception
    {
        return name + ":" + a.getSimpleName() + ":" + b + ":" + c + ":" + d;
    }

    @Test
    public void selfBoot() throws Exception {
        // The class being made defines a private bootstrap method in itself.

        ClassMaker cm = ClassMaker.begin().public_();

        // Note that the bootstrap method can be private.
        MethodMaker boot = cm.addMethod
            (String.class, "boot", MethodHandles.Lookup.class, String.class, Class.class, int.class)
            .static_().private_();

        boot.return_(boot.concat("hello-", boot.param(1), '-', boot.param(3)));

        MethodMaker mm = cm.addMethod(Object.class, "run").static_().public_();
        var v0 = mm.var(cm).condy("boot", 999).invoke(String.class, "qwerty");
        mm.return_(v0);

        Object result = cm.finish().getMethod("run").invoke(null);
        assertEquals("hello-qwerty-999", result);
    }
}
