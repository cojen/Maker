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

        var bootstrap = mm.var(CondyTest.class).condy("boot");
        var assertVar = mm.var(Assert.class);

        var v1 = bootstrap.invoke(String.class, "hello");
        assertVar.invoke("assertEquals", "hello-world", v1);

        var v2 = bootstrap.invoke(int.class, "dummy");
        assertVar.invoke("assertEquals", 123, v2);

        var v3 = bootstrap.invoke(long.class, "dummy");
        assertVar.invoke("assertEquals", Long.MAX_VALUE, v3);

        bootstrap = mm.var(CondyTest.class).condy("boot", java.util.Map.class, 999);

        try {
            bootstrap.invoke(String.class, "dummy", new Object[]{Object.class}, (Object[]) null);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("no parameters"));
        }

        var v4 = bootstrap.invoke(String.class, "dummy");
        assertVar.invoke("assertEquals", "java.util.Map999", v4);

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
        mm.invoke("add", mm.var(byte[].class).setConstant(const0));

        var const1 = "hello";
        mm.invoke("add", mm.var(String.class).setConstant(const1));

        var const2 = Long.valueOf(8675309);
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
        mm.field("test").setConstant(const0);

        Class<?> clazz = hidden ? cm.finishHidden().lookupClass() : cm.finish();

        assertTrue(const0 == clazz.getField("test").get(null));
    }

    @Test
    public void mismatch() {
        ClassMaker cm = ClassMaker.begin(null);
        MethodMaker mm = cm.addMethod(null, "test");
        try {
            mm.var(String.class).setConstant(new ArrayList());
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
            v1.setConstant("world");
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
        mm.field("test").setConstant(const0);

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
}
