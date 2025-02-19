/*
 *  Copyright 2024 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.maker;

import java.io.IOException;

import java.lang.annotation.*;

import java.lang.reflect.*;

import java.math.RoundingMode;

import java.util.*;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class TypeAnnotationsTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(TypeAnnotationsTest.class.getName());
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    public @interface Ann {
        String message() default "";

        int value() default 0;

        Class<?> clazz() default Object.class;

        RoundingMode mode() default RoundingMode.UNNECESSARY;

        String[] array() default {""};

        Sub sub() default @Sub;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Sub {
        int state() default 0;
    }

    @Test
    public void extendClass() {
        ClassMaker cm = ClassMaker.begin().public_();

        Type ext = Type.from(ArrayList.class);

        try {
            ext.addAnnotation(Ann.class, true);
            fail();
        } catch (IllegalStateException e) {
        }

        ext = ext.annotatable();

        AnnotationMaker am = ext.addAnnotation(Ann.class, true);
        am.put("message", "hello");

        try {
            am.put("message", "xxx");
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            am.put("value", this);
            fail();
        } catch (IllegalArgumentException e) {
        }

        cm.extend(ext);

        try {
            ext.addAnnotation(Ann.class, true);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("frozen"));
        }

        // Test using the annotatable type for other things. It just invokes the various
        // wrapper methods.
        {
            MethodMaker mm = cm.addMethod(null, "extra");
            mm.field("modCount");
            mm.invoke("size");
            mm.new_(ext);
        }

        Class<?> clazz = cm.finish();

        AnnotatedType at = clazz.getAnnotatedSuperclass();

        assertEquals(ArrayList.class, at.getType());

        Ann ann = at.getAnnotation(Ann.class);
        assertEquals("hello", ann.message());
    }

    @Test
    public void implementInterfaces() {
        ClassMaker cm = ClassMaker.begin().public_();

        Type iface1 = Type.from(List.class).annotatable();
        iface1.addAnnotation(Ann.class, true).put("value", 123);

        Type iface2 = Type.from(Map.class).annotatable();
        iface2.addAnnotation(Ann.class, true).put("clazz", String.class);

        cm.implement(iface1).implement(iface2);

        Class<?> clazz = cm.finish();

        AnnotatedType[] types = clazz.getAnnotatedInterfaces();

        assertEquals(2, types.length);

        assertEquals(List.class, types[0].getType());
        Ann ann = types[0].getAnnotation(Ann.class);
        assertEquals(123, ann.value());

        assertEquals(Map.class, types[1].getType());
        ann = types[1].getAnnotation(Ann.class);
        assertEquals(String.class, ann.clazz());
    }

    @Test
    public void fieldAnnotation() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        Type type = Type.from(String.class).annotatable();
        AnnotationMaker am = type.addAnnotation(Ann.class, true);
        am.put("mode", RoundingMode.UP);
        am.put("clazz", Type.from(List.class));

        cm.addField(type, "foo").public_().static_();

        Class<?> clazz = cm.finish();

        AnnotatedType at = clazz.getField("foo").getAnnotatedType();

        assertEquals(String.class, at.getType());

        Ann ann = at.getAnnotation(Ann.class);
        assertEquals(RoundingMode.UP, ann.mode());
        assertEquals(List.class, ann.clazz());
    }

    @Test
    public void recordComponent() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        Type type = Type.from(String.class).annotatable();
        AnnotationMaker am = type.addAnnotation(Ann.class, true);
        am.put("array", new String[] {"hello", "world"});

        cm.addField(type, "foo").public_().static_();

        cm.asRecord();

        Class<?> clazz = cm.finish();

        assertTrue(clazz.isRecord());

        RecordComponent[] components = clazz.getRecordComponents();
        assertEquals(1, components.length);
        RecordComponent comp = components[0];
        AnnotatedType at = comp.getAnnotatedType();

        assertEquals(String.class, at.getType());

        Ann ann = at.getAnnotation(Ann.class);
        assertArrayEquals(new String[] {"hello", "world"}, ann.array());
    }

    @Test
    public void returnTypeAndParams() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        Type t1 = Type.from(String.class).annotatable();
        AnnotationMaker am1 = t1.addAnnotation(Ann.class, true);

        Type t2 = Type.from(int.class).annotatable();
        AnnotationMaker am = t2.addAnnotation(Ann.class, true);
        AnnotationMaker sub = am.newAnnotation(Sub.class);
        sub.put("state", 123);
        am.put("sub", sub);

        try {
            am1.put("sub", sub);
            fail();
        } catch (IllegalStateException e) {
            // Sub annotation doesn't belong to am1.
        }

        MethodMaker mm = cm.addMethod(t1, "test", t1, t2, float.class).public_().static_();
        mm.return_(mm.concat(mm.param(0), mm.param(1), mm.param(2)));

        Class<?> clazz = cm.finish();

        Method m = clazz.getMethod("test", String.class, int.class, float.class);

        AnnotatedType retType = m.getAnnotatedReturnType();
        assertEquals(String.class, retType.getType());
        assertNotNull(retType.getAnnotation(Ann.class));

        AnnotatedType[] paramAnns = m.getAnnotatedParameterTypes();
        assertEquals(3, paramAnns.length);

        assertEquals(String.class, paramAnns[0].getType());
        assertNotNull(paramAnns[0].getAnnotation(Ann.class));

        assertEquals(int.class, paramAnns[1].getType());
        Ann ann = paramAnns[1].getAnnotation(Ann.class);
        assertEquals(123, ann.sub().state());

        assertEquals(float.class, paramAnns[2].getType());
        assertEquals(0, paramAnns[2].getAnnotations().length);
        assertEquals(0, paramAnns[2].getDeclaredAnnotations().length);

        try {
            am.put("x", "y");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("frozen"));
        }

        try {
            am.newAnnotation(Sub.class);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("frozen"));
        }
    }

    @Test
    public void throwsException() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        Type t1 = Type.from(IllegalStateException.class).annotatable();
        t1.addAnnotation(Ann.class, true);

        Type t2 = Type.from(IOException.class).annotatable();
        t2.addAnnotation(Ann.class, true).put("message", "hello");

        MethodMaker mm = cm.addMethod(null, "test").public_().static_();
        mm.throws_(t1).throws_(t2);

        Class<?> clazz = cm.finish();

        Method m = clazz.getMethod("test");

        AnnotatedType[] exAnns = m.getAnnotatedExceptionTypes();
        assertEquals(2, exAnns.length);

        assertEquals(IllegalStateException.class, exAnns[0].getType());
        assertNotNull(exAnns[0].getAnnotation(Ann.class));

        assertEquals(IOException.class, exAnns[1].getType());
        Ann ann = exAnns[1].getAnnotation(Ann.class);
        assertEquals("hello", ann.message());
    }

    @Test
    public void receiverType() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        MethodMaker mm = cm.addMethod(Object.class, "test", int.class).public_();

        Type t1 = cm.type().annotatable();
        t1.addAnnotation(Ann.class, true).put("value", 999);

        mm.receiverType(t1);

        mm.return_(mm.this_());

        try {
            mm.receiverType(t1);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Parameters have been accessed"));
        }

        try {
            cm.addMethod(null, "foo").static_().receiverType(t1);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Not an instance method"));
        }

        Class<?> clazz = cm.finish();

        Method m = clazz.getMethod("test", int.class);

        AnnotatedType at = m.getAnnotatedReceiverType();

        assertEquals(clazz, at.getType());

        Ann ann = at.getAnnotation(Ann.class);
        assertEquals(999, ann.value());
    }

    @Test
    public void ctorReceiverType() throws Exception {
        ClassMaker outer = ClassMaker.begin().public_();

        ClassMaker inner = outer.addInnerClass("Inner");

        MethodMaker ctor = inner.addConstructor(outer).public_();

        Type t1 = outer.type().annotatable();
        t1.addAnnotation(Ann.class, true).put("value", 555);

        ctor.receiverType(t1);

        ctor.invokeSuperConstructor();

        Class<?> outerClass = outer.finish();
        Class<?> innerClass = inner.finish();

        Constructor c = innerClass.getConstructor(outerClass);

        AnnotatedType at = c.getAnnotatedReceiverType();

        assertEquals(outerClass, at.getType());

        Ann ann = at.getAnnotation(Ann.class);
        assertEquals(555, ann.value());
    }

    @Test
    public void arrays() throws Exception {
        Type type = Type.from(int.class).annotatable();
        type.addAnnotation(Ann.class, true).put("message", "hi");
        type = type.asArray();
        type.addAnnotation(Ann.class, true).put("value", 123);
        type = type.asArray();
        type.addAnnotation(Sub.class, true).put("state", -10);

        ClassMaker cm = ClassMaker.begin().public_();

        MethodMaker mm = cm.addMethod(type, "test").public_().static_();
        mm.return_(null);

        Class<?> clazz = cm.finish();

        Method m = clazz.getMethod("test");

        AnnotatedType at = m.getAnnotatedReturnType();
        assertEquals(int[][].class, at.getType());
        Sub sub = at.getAnnotation(Sub.class);
        assertEquals(-10, sub.state());

        at = ((AnnotatedArrayType) at).getAnnotatedGenericComponentType();
        assertEquals(int[].class, at.getType());
        Ann ann = at.getAnnotation(Ann.class);
        assertEquals(123, ann.value());

        at = ((AnnotatedArrayType) at).getAnnotatedGenericComponentType();
        assertEquals(int.class, at.getType());
        ann = at.getAnnotation(Ann.class);
        assertEquals("hi", ann.message());
    }

    @Test
    public void equalsAndHash() {
        // The Type hashCode and equals methods must ignore annotations.

        Type t1 = Type.from(String.class);
        Type t2 = t1.annotatable();

        assertEquals(t1, t2);
        assertEquals(t2, t1);
        assertEquals(t1.hashCode(), t2.hashCode());

        t2.addAnnotation(Ann.class, true);

        assertEquals(t1, t2);
        assertEquals(t2, t1);
        assertEquals(t1.hashCode(), t2.hashCode());

        Type t3 = t2.annotatable();
        t3.addAnnotation(Sub.class, true);

        assertEquals(t1, t3);
        assertEquals(t2, t3);
        assertEquals(t3, t1);
        assertEquals(t3, t2);
        assertEquals(t2.hashCode(), t3.hashCode());
    }

    @Test
    public void boxUnbox() throws Exception {
        assertNull(Type.from(String.class).annotatable().unbox());

        Type t1 = Type.from(int.class).annotatable();
        t1.addAnnotation(Ann.class, true).put("message", "hello");
        t1 = t1.box();

        Type t2 = Type.from(Long.class).annotatable();
        t2.addAnnotation(Ann.class, true).put("value", 123);
        t2 = t2.unbox();

        {
            ClassMaker cm = ClassMaker.begin().public_();

            MethodMaker mm = cm.addMethod(String.class, "test", t1, t2).public_().static_();
            mm.return_(mm.concat(mm.param(0), mm.param(1)));

            Class<?> clazz = cm.finish();

            Method m = clazz.getMethod("test", Integer.class, long.class);
        
            AnnotatedType[] paramAnns = m.getAnnotatedParameterTypes();
            assertEquals(2, paramAnns.length);

            assertEquals(Integer.class, paramAnns[0].getType());
            assertEquals("hello", paramAnns[0].getAnnotation(Ann.class).message());

            assertEquals(long.class, paramAnns[1].getType());
            assertEquals(123, paramAnns[1].getAnnotation(Ann.class).value());

            assertEquals("98", m.invoke(null, 9, 8L));
        }

        assertSame(t1, t1.box());

        t2 = t2.box();

        try {
            t2.addAnnotation(Sub.class, true);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("frozen"));
        }

        t2 = t2.annotatable();
        t2.addAnnotation(Sub.class, true);

        {
            ClassMaker cm = ClassMaker.begin().public_();

            MethodMaker mm = cm.addMethod(Long.class, "test", t2).public_().static_();
            mm.return_(mm.param(0));

            Class<?> clazz = cm.finish();

            Method m = clazz.getMethod("test", Long.class);
        
            AnnotatedType[] paramAnns = m.getAnnotatedParameterTypes();
            assertEquals(1, paramAnns.length);

            assertEquals(Long.class, paramAnns[0].getType());
            assertEquals(123, paramAnns[0].getAnnotation(Ann.class).value());
            assertNotNull(paramAnns[0].getAnnotation(Sub.class));

            assertEquals(10L, m.invoke(null, 10L));
        }
    }

    @Test
    public void usingVariable() throws Exception {
        // Test using a variable as a type carrier.

        ClassMaker cm = ClassMaker.begin().public_();

        var ctor = cm.addConstructor().public_();
        var subVar = ctor.var(Sub.class);
        ctor.invokeSuperConstructor();

        Type t1 = Type.from(String.class).annotatable();
        AnnotationMaker am1 = t1.addAnnotation(Ann.class, true);

        Type t2 = Type.from(int.class).annotatable();
        AnnotationMaker am = t2.addAnnotation(Ann.class, true);
        AnnotationMaker sub = am.newAnnotation(subVar); // <-- using a variable
        sub.put("state", 123);
        am.put("sub", sub);

        MethodMaker mm = cm.addMethod(null, "test", t2).public_().static_();

        {
            Class<?> clazz = cm.finish();

            Method m = clazz.getMethod("test", int.class);

            AnnotatedType[] paramAnns = m.getAnnotatedParameterTypes();
            assertEquals(1, paramAnns.length);

            assertEquals(int.class, paramAnns[0].getType());
            Ann ann = paramAnns[0].getAnnotation(Ann.class);
            assertEquals(123, ann.sub().state());
        }

        // The type is usable for making another class.

        cm = ClassMaker.begin().public_();
        mm = cm.addMethod(null, "test2", t2).public_().static_();

        {
            Class<?> clazz = cm.finish();

            Method m = clazz.getMethod("test2", int.class);

            AnnotatedType[] paramAnns = m.getAnnotatedParameterTypes();
            assertEquals(1, paramAnns.length);

            assertEquals(int.class, paramAnns[0].getType());
            Ann ann = paramAnns[0].getAnnotation(Ann.class);
            assertEquals(123, ann.sub().state());
        }
    }

    @Test
    public void misc() {
        assertTrue(Type.from(List.class).annotatable().isInterface());

        var at = (AnnotatableType) Type.from(ArrayList.class).annotatable();
        assertFalse(at.isInterface());
        at.toInterface();
        assertFalse(at.isInterface());
        assertNull(at.makerType());
        at.resetInherited();

        var field = at.defineField(0, at, "xxx");

        assertSame(field, at.defineField(0, at, "xxx"));
        assertSame(field, at.inventField(0, at, "xxx"));

        try {
            at.defineField(BaseType.FLAG_STATIC, at, "xxx");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("field exists"));
        }

        var method = at.defineMethod(0, at, "xxx");

        assertSame(method, at.defineMethod(0, at, "xxx"));
        assertSame(method, at.inventMethod(0, at, "xxx"));

        try {
            at.defineMethod(BaseType.FLAG_STATIC, at, "xxx");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("method exists"));
        }
    }
}
