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

        Class<?> clazz = cm.finish();

        AnnotatedType at = clazz.getAnnotatedSuperclass();

        assertEquals(ArrayList.class, at.getType());

        var ann = (Ann) at.getAnnotation(Ann.class);
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
        var ann = (Ann) types[0].getAnnotation(Ann.class);
        assertEquals(123, ann.value());

        assertEquals(Map.class, types[1].getType());
        ann = (Ann) types[1].getAnnotation(Ann.class);
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

        var ann = (Ann) at.getAnnotation(Ann.class);
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

        var ann = (Ann) at.getAnnotation(Ann.class);
        assertArrayEquals(new String[] {"hello", "world"}, ann.array());
    }
}
