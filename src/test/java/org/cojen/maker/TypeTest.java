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

import java.io.IOException;
import java.io.Serializable;

import java.net.URL;
import java.net.URLClassLoader;

import java.util.*;

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
            BaseType type = cm.type();

            assertFalse(type.isAssignableFrom(BaseType.from(int.class)));
            assertTrue(BaseType.from(ArrayList.class).isAssignableFrom(type));
            assertTrue(BaseType.from(Object.class).isAssignableFrom(type));
        }

        {
            var cm = (TheClassMaker) ClassMaker.begin(null)
                .implement(List.class).implement(Serializable.class);
            BaseType type = cm.type();

            assertTrue(BaseType.from(List.class).isAssignableFrom(type));
            assertTrue(BaseType.from(Serializable.class).isAssignableFrom(type));
            assertFalse(BaseType.from(String.class).isAssignableFrom(type));
        }

        {
            var cm1 = (TheClassMaker) ClassMaker.begin();
            BaseType type1 = cm1.type();

            var loader = new URLClassLoader(new URL[0]);
            BaseType type2 = BaseType.begin(loader, cm1, cm1.name());

            assertNotSame(type1, type2);
            assertEquals(type1, type2);

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
            Type type = i < 10 ? Type.from(prims[i], loader) : Type.from((Object) prims[i], loader);
            assertEquals(prims[i], type.name());
            assertTrue(type.isPrimitive());
            assertEquals(type, type.unbox());
            assertEquals(prims[i + 1], type.descriptor());
            assertEquals(type, type.box().unbox());

            verifyNonObject((BaseType) type);

            // Find by descriptor.
            assertEquals(type, Type.from(prims[i + 1]));
        }

        {
            BaseType type = BaseType.Null.THE;
            assertEquals("java.lang.Object", type.name());
            assertEquals("Ljava/lang/Object;", type.descriptor());
            assertFalse(type.isPrimitive());
            assertNull(type.unbox());
            assertEquals(type, type.box());
            assertNull(type.classType());
            assertFalse(type.isInterface());
            assertFalse(type.isArray());
            assertNull(type.elementType());
            assertEquals(BaseType.SM_NULL, type.stackMapCode());
            assertFalse(type.isAssignableFrom(BaseType.from(Object.class)));
        }

        try {
            Type.from("");
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            Type.from("", loader);
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            Type.from(this);
            fail();
        } catch (IllegalArgumentException e) {
        }

        {
            BaseType type = BaseType.from(loader, "abc.Foo[]");
            assertEquals("abc.Foo[]", type.name());
            assertEquals("[Labc/Foo;", type.descriptor());
            assertFalse(type.isPrimitive());
            assertNull(type.unbox());
            assertTrue(type.isArray());
            assertFalse(type.isInterface());
            assertNull(type.classType());

            // Find by descriptor.
            assertEquals(type, BaseType.from(loader, "[Labc/Foo;"));
            assertEquals(type, BaseType.from(loader, "[Labc/Foo"));
        }

        {
            Type type = Type.from("java/lang.Void", loader);
            assertEquals("java.lang.Void", type.name());
            assertEquals("Ljava/lang/Void;", type.descriptor());
            assertEquals(Type.from(void.class), type.unbox());
        }

        {
            BaseType type = BaseType.from(loader, "java.lang.FakeClass");
            assertEquals("java.lang.FakeClass", type.name());
            assertEquals("Ljava/lang/FakeClass;", type.descriptor());
            assertFalse(type.isInterface());
            type.toInterface();
        }

        {
            BaseType type = BaseType.from(loader, "Foo;");
            assertEquals("Foo", type.name());
            assertEquals("LFoo;", type.descriptor());
        }

        {
            BaseType type = BaseType.from(loader, "Labc/Foo");
            assertEquals("abc.Foo", type.name());
            assertEquals("Labc/Foo;", type.descriptor());
        }

        {
            BaseType type = BaseType.from(loader, "LFoo");
            assertEquals("LFoo", type.name());
            assertEquals("LLFoo;", type.descriptor());
        }

        {
            BaseType type = BaseType.from(loader, "java.util.List");
            assertEquals(List.class, type.classType());
        }

        {
            BaseType type = BaseType.from(loader, "java.lang.String[]");
            assertEquals(String[].class, type.classType());
            for (int i=0; i<2; i++) {
                assertEquals("java.lang.String[]", type.name());
            }
        }
    }

    private static void verifyNonObject(BaseType type) {
        assertFalse(type.isArray());
        assertNull(type.elementType());
        assertFalse(type.isInterface());

        assertNull(type.superType());
        assertNull(type.interfaces());
        assertTrue(type.fields().isEmpty());
        assertTrue(type.methods().isEmpty());

        try {
            type.defineField(0, type, "x");
            fail();
        } catch (IllegalStateException e) {
        }
        try {
            type.inventField(0, type, "x");
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            type.defineMethod(0, type, "x");
            fail();
        } catch (IllegalStateException e) {
        }
        try {
            type.inventMethod(0, type, "x");
            fail();
        } catch (IllegalStateException e) {
        }

        assertTrue(type.findMethods("x", new BaseType[0], 0, 0, null, null).isEmpty());

        assertTrue(type.isAssignableFrom(type));
        BaseType object = BaseType.from(Object.class);
        assertFalse(type.isAssignableFrom(object));
        
        assertFalse(object.isAssignableFrom(type));
    }

    @Test
    public void setType() throws Exception {
        // Test that a variable of type Class can be be assigned by a Type instance.

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

        try {
            Type.from((String) null, loader);
            fail();
        } catch (NullPointerException e) {
        }

        try {
            Type.from((Object) null, loader);
            fail();
        } catch (NullPointerException e) {
        }

        try {
            Type.from((Class) null);
            fail();
        } catch (NullPointerException e) {
        }

        try {
            Type.from(this, loader);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Unknown"));
        }
    }

    @Test
    public void classDesc() throws Exception {
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

    @Test
    public void voidType() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(void.class, "test", void.class).public_().static_();

        var v = mm.param(0);

        try {
            v.invoke("getClass");
            cm.finish();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().startsWith("Unsupported"));
            assertTrue(e.getMessage().contains("test"));
        }

        cm = ClassMaker.begin().public_();
        mm = cm.addMethod(v, "test").public_().static_();
        var v2 = mm.var(v);

        try {
            v2.cast(int.class);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().startsWith("Unsupported"));
        }

        mm.return_(v2);

        cm.finish().getMethod("test").invoke(null);

        cm = ClassMaker.begin().public_();
        mm = cm.addMethod(v, "test").public_().static_();
        var v3 = mm.var(int.class);

        try {
            mm.return_(v3);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().startsWith("Cannot return"));
        }
    }

    @Test
    public void commonCatchType() throws Exception {
        BaseType objType = BaseType.from(Object.class);

        BaseType[] types = {
            BaseType.from(RuntimeException.class),
            BaseType.from(NullPointerException.class),
            BaseType.from(IllegalArgumentException.class),
            BaseType.from(IllegalFormatException.class),
            BaseType.from(IOException.class),
            BaseType.from(Exception.class),
            BaseType.from(Error.class),
        };

        var map = new HashMap<BaseType, List<BaseType>>();

        for (int i=0; i<types.length; i++) {
            for (int j=0; j<types.length; j++) {
                map.clear();

                map.put(types[i], null);
                map.put(types[j], null);

                BaseType common = BaseType.commonCatchType(map);

                assertNotEquals(common, objType);

                assertTrue(common.isAssignableFrom(types[i]));
                assertTrue(common.isAssignableFrom(types[j]));

                if (map.size() == 1) {
                    assertTrue(common == types[i] || common == types[j]);
                }
            }
        }

        for (int i=0; i<types.length; i++) {
            for (int j=0; j<types.length; j++) {
                if (j == i) {
                    continue;
                }

                for (int k=0; k<types.length; k++) {
                    if (k == j || k == i) {
                        continue;
                    }

                    map.clear();

                    map.put(types[i], null);
                    map.put(types[j], null);
                    map.put(types[k], null);

                    BaseType common = BaseType.commonCatchType(map);

                    assertNotEquals(common, objType);

                    assertTrue(common.isAssignableFrom(types[i]));
                    assertTrue(common.isAssignableFrom(types[j]));
                    assertTrue(common.isAssignableFrom(types[k]));

                    if (map.size() == 1) {
                        assertTrue(common == types[i] || common == types[j] || common == types[k]);
                    } else {
                        assertEquals(3, map.size());
                    }
                }
            }
        }
    }
}
