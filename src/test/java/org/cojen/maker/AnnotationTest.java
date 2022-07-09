/*
 *  Copyright 2021 Cojen.org
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

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import java.math.RoundingMode;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class AnnotationTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(AnnotationTest.class.getName());
    }

    @Test
    public void basic() {
        ClassMaker cm = ClassMaker.begin().public_();

        cm.addAnnotation(Ann0.class, true);

        AnnotationMaker am = cm.addAnnotation(Ann1.class, true);

        am.put("name", "MyName");
        am.put("prim0", false);
        am.put("prim1", true);
        am.put("prim2", (byte) 123);
        am.put("prim3", (short) 12345);
        am.put("prim4", 12345678);
        am.put("prim5", 1L << 60);
        am.put("prim6", 123.456f);
        am.put("prim7", 123.456789d);
        am.put("prim8", 'A');
        am.put("array1", new String[] {"hello", "world"});
        am.put("array2", new int[] {10, 20, 30});
        am.put("class1", java.util.List.class);
        am.put("class2", cm);
        am.put("mode", RoundingMode.UP);

        try {
            am.put("bogus", this);
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            am.put("name", "OtherName");
            fail();
        } catch (IllegalStateException e) {
            // Duplicate.
        }

        var clazz = cm.finish();

        Annotation[] anns = clazz.getAnnotations();
        assertEquals(2, anns.length);
        assertTrue(anns[0] instanceof Ann0);
        var ann = (Ann1) anns[1];

        assertEquals("MyName", ann.name());
        assertEquals(false, ann.prim0());
        assertEquals(true, ann.prim1());
        assertEquals(123, ann.prim2());
        assertEquals(12345, ann.prim3());
        assertEquals(12345678, ann.prim4());
        assertEquals(1L << 60, ann.prim5());
        assertTrue(123.456f == ann.prim6());
        assertTrue(123.456789d == ann.prim7());
        assertEquals('A', ann.prim8());
        assertArrayEquals(new String[] {"hello", "world"}, ann.array1());
        assertArrayEquals(new int[] {10, 20, 30}, ann.array2());
        assertEquals(java.util.List.class, ann.class1());
        assertEquals(clazz, ann.class2());
        assertEquals(RoundingMode.UP, ann.mode());
    }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Ann0 {
    }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Ann1 {
        String name();
        boolean prim0();
        boolean prim1();
        byte prim2();
        short prim3();
        int prim4();
        long prim5();
        float prim6();
        double prim7();
        char prim8();
        String[] array1();
        int[] array2();
        Class class1();
        Class class2();
        RoundingMode mode();
    }

    @Test
    public void tooManyElements() {
        ClassMaker cm = ClassMaker.begin().public_();
        AnnotationMaker am = cm.addAnnotation(Ann1.class, true);
        try {
            for (int i=0; i<65536; i++) {
                am.put("name-" + i, i);
            }
            fail();
        } catch (IllegalStateException e) {
        }
    }

    @Test
    public void tooLargeArray() {
        ClassMaker cm = ClassMaker.begin().public_();
        AnnotationMaker am = cm.addAnnotation(Ann1.class, true);
        try {
            am.put("array2", new int[65536]);
            fail();
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void nestedAnnotation() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(void.class, "test").public_().static_();

        AnnotationMaker am2 = mm.addAnnotation(Ann2.class, true);

        try {
            am2.put("sub", am2);
            fail();
        } catch (IllegalStateException e) {
            // Cannot put self.
        }

        AnnotationMaker am1 = am2.newAnnotation(Ann1.class);

        am2.put("sub", am1);

        am1.put("name", "yo");
        am1.put("prim0", true);
        am1.put("prim1", false);
        am1.put("prim2", (byte) -1);
        am1.put("prim3", (short) -2);
        am1.put("prim4", -3);
        am1.put("prim5", -4L);
        am1.put("prim6", -5f);
        am1.put("prim7", -6d);
        am1.put("prim8", 'Z');
        am1.put("array1", new String[0]);
        am1.put("array2", new int[0]);
        am1.put("class1", String.class);
        am1.put("class2", cm);
        am1.put("mode", RoundingMode.DOWN);

        var clazz = cm.finish();

        var method = clazz.getMethod("test");
        Annotation[] anns = method.getAnnotations();
        assertEquals(1, anns.length);
        var ann2 = (Ann2) anns[0];
        var ann1 = ann2.sub();

        assertEquals("yo", ann1.name());
        assertEquals(true, ann1.prim0());
        assertEquals(false, ann1.prim1());
        assertEquals(-1, ann1.prim2());
        assertEquals(-2, ann1.prim3());
        assertEquals(-3, ann1.prim4());
        assertEquals(-4, ann1.prim5());
        assertTrue(-5f == ann1.prim6());
        assertTrue(-6d == ann1.prim7());
        assertEquals('Z', ann1.prim8());
        assertArrayEquals(new String[0], ann1.array1());
        assertArrayEquals(new int[0], ann1.array2());
        assertEquals(String.class, ann1.class1());
        assertEquals(clazz, ann1.class2());
        assertEquals(RoundingMode.DOWN, ann1.mode());
    }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Ann2 {
        Ann1 sub();
    }

    @Test
    public void nestedArray() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        FieldMaker fm = cm.addField(int.class, "test").public_().static_();

        AnnotationMaker am4 = fm.addAnnotation(Ann4.class, true);
        AnnotationMaker[] am3 = {am4.newAnnotation(Ann3.class), am4.newAnnotation(Ann3.class)};
        am4.put("array", am3);
        am3[0].put("name", "A");
        am3[1].put("name", "B");

        var clazz = cm.finish();

        var field = clazz.getField("test");
        Annotation[] anns = field.getAnnotations();
        assertEquals(1, anns.length);
        var ann4 = (Ann4) anns[0];
        var ann3 = ann4.array();
        assertEquals(2, ann3.length);
        assertEquals("A", ann3[0].name());
        assertEquals("B", ann3[1].name());
    }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Ann3 {
        String name();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Ann4 {
        Ann3[] array();
    }

    @Test
    public void invisible() {
        ClassMaker cm = ClassMaker.begin().public_();
        cm.addAnnotation(Ann5.class, false);
        AnnotationMaker am = cm.addAnnotation(Ann3.class, true);
        am.put("name", "bob");

        var clazz = cm.finish();

        Annotation[] anns = clazz.getAnnotations();
        assertEquals(1, anns.length);
        var ann = (Ann3) anns[0];
        assertEquals("bob", ann.name());
    }

    @Retention(RetentionPolicy.CLASS)
    public static @interface Ann5 {
    }

    @Test
    public void parameterAnnotations() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        MethodMaker mm = cm.addMethod(null, "t1", String.class, long.class).public_();

        try {
            mm.this_().addAnnotation(Ann3.class, true);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(int.class).addAnnotation(Ann3.class, true);
            fail();
        } catch (IllegalStateException e) {
        }

        mm.param(0).addAnnotation(Ann3.class, true).put("name", "bob");
        mm.param(0).addAnnotation(Ann5.class, false);
        mm.param(1).addAnnotation(Ann0.class, true);
        mm.param(1).addAnnotation(Ann3.class, true).put("name", "alice");

        mm = cm.addMethod(null, "t2", String.class, long.class).public_().static_();
        mm.param(1).addAnnotation(Ann3.class, true).put("name", "yo");

        var clazz = cm.finish();

        {
            Method m = clazz.getMethod("t1", String.class, long.class);
            Parameter[] params = m.getParameters();
            assertEquals(2, params.length);

            Annotation[] anns = params[0].getAnnotations();
            assertEquals(1, anns.length);
            assertEquals("bob", ((Ann3) anns[0]).name());

            anns = params[1].getAnnotations();
            assertEquals(2, anns.length);
            assertTrue(anns[0] instanceof Ann0);
            assertEquals("alice", ((Ann3) anns[1]).name());
        }

        {
            Method m = clazz.getMethod("t2", String.class, long.class);
            Parameter[] params = m.getParameters();
            assertEquals(2, params.length);

            Annotation[] anns = params[0].getAnnotations();
            assertEquals(0, anns.length);

            anns = params[1].getAnnotations();
            assertEquals(1, anns.length);
            assertEquals("yo", ((Ann3) anns[0]).name());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void makeAnnotation() throws Exception {
        for (int i = 1; i <= 2; i++) {
            Class annClass;
            {
                ClassMaker cm = ClassMaker.begin().public_().annotation();
                if (i == 2) {
                    cm = cm.annotation(); // okay to call again
                }
                cm.addAnnotation(Retention.class, true)
                    .put("value", RetentionPolicy.RUNTIME);
                cm.addAnnotation(Target.class, true)
                    .put("value", new ElementType[] {ElementType.METHOD});
                annClass = cm.finish();
            }

            ClassMaker cm = ClassMaker.begin().public_();

            MethodMaker mm = cm.addMethod(null, "test").public_().static_();
            mm.return_();
            mm.addAnnotation(annClass, true);

            var clazz = cm.finish();

            assertNotNull(clazz.getMethod("test").getAnnotation(annClass));
        }
    }
}
