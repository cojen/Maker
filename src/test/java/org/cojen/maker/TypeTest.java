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

import java.lang.constant.ConstantDescs;

import java.io.Serializable;

import java.net.URL;
import java.net.URLClassLoader;

import java.util.ArrayList;
import java.util.List;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class TypeTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(TypeTest.class.getName());
    }

    @Test
    public void isAssignable() {
        // Test assignability of types currently being generated.

        {
            var cm = (TheClassMaker) ClassMaker.begin(null).extend(ArrayList.class);
            Type type = cm.type();

            assertFalse(type.isAssignableFrom(Type.from(int.class)));
            assertTrue(Type.from(ArrayList.class).isAssignableFrom(type));
            assertTrue(Type.from(Object.class).isAssignableFrom(type));
        }

        {
            var cm = (TheClassMaker) ClassMaker.begin(null)
                .implement(List.class).implement(Serializable.class);
            Type type = cm.type();

            assertTrue(Type.from(List.class).isAssignableFrom(type));
            assertTrue(Type.from(Serializable.class).isAssignableFrom(type));
            assertFalse(Type.from(String.class).isAssignableFrom(type));
        }

        {
            var cm1 = (TheClassMaker) ClassMaker.begin();
            Type type1 = cm1.type();

            var loader = new URLClassLoader(new URL[0]);
            Type type2 = Type.begin(loader, cm1, cm1.name());

            assertFalse(type1 == type2);
            assertTrue(type1.equals(type2));

            assertTrue(type1.isAssignableFrom(type2));
            assertTrue(type2.isAssignableFrom(type1));
        }
    }

    @Test
    public void findByString() {
        ClassLoader loader = getClass().getClassLoader();

        String[] prims = {
            "boolean", "Z", "byte", "B", "short", "S", "char", "C",
            "int", "I", "float", "F", "double", "D", "long", "J",
            "void", "V"
        };
        for (int i=0; i<prims.length; i+=2) {
            Type type = i < 10 ? Type.from(loader, prims[i]) : Type.from(loader, (Object) prims[i]);
            assertEquals(prims[i], type.name());
            assertTrue(type.isPrimitive());
            assertEquals(type, type.unbox());
            assertEquals(prims[i + 1], type.descriptor());
            assertEquals(type, type.box().unbox());

            verifyNonObject(type);

            // Find by descriptor.
            assertEquals(type, Type.from(loader, prims[i + 1]));
        }

        {
            Type type = Type.from(loader, "null");
            assertEquals("*null*", type.name());
            assertEquals("*null*", type.descriptor());
            assertFalse(type.isPrimitive());
            assertNull(type.unbox());
            assertEquals(type, type.box());
            assertNull(type.clazz());

            verifyNonObject(type);

            // Find by pseudo descriptor.
            assertEquals(type, Type.from(loader, ""));
        }

        {
            Type type = Type.from(loader, "abc.Foo[]");
            assertEquals("abc.Foo[]", type.name());
            assertEquals("[Labc/Foo;", type.descriptor());
            assertFalse(type.isPrimitive());
            assertNull(type.unbox());
            assertTrue(type.isArray());
            assertFalse(type.isInterface());
            assertNull(type.clazz());

            // Find by descriptor.
            assertEquals(type, Type.from(loader, "[Labc/Foo;"));
            assertEquals(type, Type.from(loader, "[Labc/Foo"));
        }

        {
            Type type = Type.from(loader, "java/lang.Void");
            assertEquals("java.lang.Void", type.name());
            assertEquals("Ljava/lang/Void;", type.descriptor());
            assertEquals(Type.from(void.class), type.unbox());
        }

        {
            Type type = Type.from(loader, "java.lang.FakeClass");
            assertEquals("java.lang.FakeClass", type.name());
            assertEquals("Ljava/lang/FakeClass;", type.descriptor());
            assertFalse(type.isInterface());
            type.toInterface();
        }

        {
            Type type = Type.from(loader, "Foo;");
            assertEquals("Foo", type.name());
            assertEquals("LFoo;", type.descriptor());
        }

        {
            Type type = Type.from(loader, "Labc/Foo");
            assertEquals("abc.Foo", type.name());
            assertEquals("Labc/Foo;", type.descriptor());
        }

        {
            Type type = Type.from(loader, "LFoo");
            assertEquals("LFoo", type.name());
            assertEquals("LLFoo;", type.descriptor());
        }

        {
            Type type = Type.from(loader, "java.util.List");
            assertEquals(List.class, type.clazz());
        }

        {
            Type type = Type.from(loader, "java.lang.String[]");
            assertEquals(String[].class, type.clazz());
            for (int i=0; i<2; i++) {
                assertEquals("java.lang.String[]", type.name());
            }
        }
    }

    private static void verifyNonObject(Type type) {
        assertFalse(type.isArray());
        assertNull(type.elementType());
        assertFalse(type.isInterface());

        assertNull(type.superType());
        assertNull(type.interfaces());
        assertTrue(type.fields().isEmpty());
        assertTrue(type.methods().isEmpty());

        try {
            type.defineField(false, type, "x");
            fail();
        } catch (IllegalStateException e) {
        }
        try {
            type.inventField(false, type, "x");
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            type.defineMethod(false, type, "x");
            fail();
        } catch (IllegalStateException e) {
        }
        try {
            type.inventMethod(false, type, "x");
            fail();
        } catch (IllegalStateException e) {
        }

        assertTrue(type.findMethods("x", new Type[0], 0, 0, null, null).isEmpty());

        assertTrue(type.isAssignableFrom(type));
        Type object = Type.from(Object.class);
        assertFalse(type.isAssignableFrom(object));
        
        assertEquals(type == Type.Null.THE, object.isAssignableFrom(type));
    }

    @Test
    public void setType() throws Exception {
        // Test that a variable of type Class can be be assigned by a Type instance. This
        // feature isn't actually used anywhere at the moment.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(Class[].class, "test").public_().static_();

        var c0 = mm.var(Class.class).set(Type.from(int.class));
        var c1 = mm.var(Class.class).set(Type.from(Integer.class));
        var c2 = mm.var(Class.class).set(Type.from(String.class));

        var result = mm.new_(Class[].class, 3);
        result.aset(0, c0);
        result.aset(1, c1);
        result.aset(2, c2);

        mm.return_(result);

        Class[] actual = (Class[]) cm.finish().getMethod("test").invoke(null);

        assertEquals(int.class, actual[0]);
        assertEquals(Integer.class, actual[1]);
        assertEquals(String.class, actual[2]);
    }

    @Test
    public void nonTypes() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        assertEquals(Type.Null.THE, Type.from(loader, (String) null));
        assertEquals(Type.Null.THE, Type.from(loader, (Object) null));
        assertEquals(Type.Null.THE, Type.from((Class) null));

        try {
            Type.from(loader, this);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Unknown"));
        }
    }

    @Test
    public void classDesc() throws Exception {
        // ClassDesc added in Java 12.
        Assume.assumeTrue(Runtime.getRuntime().version().feature() >= 12);

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(Object.class, "test").public_().static_();

        try {
            mm.var(ConstantDescs.CD_void).set(1);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().startsWith("Automatic conversion"));
        }

        var a = mm.var(ConstantDescs.CD_int).set(123);
        var b = mm.var(ConstantDescs.CD_Number).set(a);

        mm.return_(b);

        Object result = cm.finish().getMethod("test").invoke(null);

        assertEquals(123, result);
    }
}
