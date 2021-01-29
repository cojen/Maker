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

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class NestTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(NestTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        // Add nest classes and verify private access.

        ClassMaker parent = ClassMaker.begin().public_();

        try {
            parent.addClass("A.B.C");
            fail();
        } catch (IllegalArgumentException e) {
        }

        ClassMaker child1 = parent.addClass(null).public_().static_();
        ClassMaker child2 = parent.addClass("Child2").private_();
        ClassMaker child3 = parent.addClass(null).public_().interface_();
        child2.addConstructor().private_();

        parent.addField(int.class, "f0").private_().static_();
        child1.addField(int.class, "f1").private_().static_();
        child2.addField(int.class, "f2").private_();

        // Parent can access the children.
        {
            MethodMaker mm = parent.addConstructor().public_();
            mm.invokeSuperConstructor();
            var f1 = mm.var(child1).field("f1");
            mm.var(Assert.class).invoke("assertEquals", 0, f1);
            var f2 = mm.new_(child2).field("f2");
            mm.var(Assert.class).invoke("assertEquals", 0, f2);
        }

        // Child can access the parent.
        {
            MethodMaker mm = child1.addMethod(null, "test1").public_().static_();
            var f0 = mm.var(parent).field("f0");
            mm.var(Assert.class).invoke("assertEquals", 0, f0);
        }

        // Parent to child to parent.
        {
            MethodMaker mm = child2.addMethod(null, "internal").private_();
            var f0 = mm.var(parent).field("f0");
            mm.var(Assert.class).invoke("assertEquals", 0, f0);
        }
        {
            MethodMaker mm = parent.addMethod(null, "test2").public_().static_();
            mm.new_(child2).invoke("internal");
        }

        Class<?> parentClass = parent.finish();
        Class<?> child1Class = child1.finish();
        Class<?> child2Class = child2.finish();
        Class<?> child3Class = child3.finish();

        parentClass.getConstructor().newInstance();
        child1Class.getMethod("test1").invoke(null);
        parentClass.getMethod("test2").invoke(null);
    }

    @Test
    public void levels() throws Exception {
        // The parent class defines the one nest. If defined wrong, a ClassFormatError is thrown.

        ClassMaker parent = ClassMaker.begin().public_();
        ClassMaker child1 = parent.addClass(null);
        ClassMaker child2 = child1.addClass(null);

        child1.finish();
        child2.finish();
        parent.finish();
    }

    @Test
    public void innerClasses() throws Exception {
        ClassMaker parent = ClassMaker.begin().public_();

        MethodMaker mm = parent.addMethod(null, "foo").public_().static_();

        ClassMaker inner1 = mm.addClass(null);
        inner1.addConstructor().invokeSuperConstructor();

        ClassMaker inner2 = mm.addClass("Inner");

        MethodMaker mm2 = inner2.addConstructor();
        mm2.invokeSuperConstructor();

        ClassMaker inner3 = mm2.addClass(null);
        inner3.addConstructor();

        ClassMaker inner4 = mm2.addClass("Inner");
        inner4.addConstructor();

        ClassMaker inner5 = mm2.addClass(null);
        inner5.addConstructor();

        mm.new_(inner1);
        mm.new_(inner2);

        mm2.new_(inner3);
        mm2.new_(inner4);
        mm2.new_(inner5);

        var clazz0 = parent.finish();
        var clazz1 = inner1.finish();
        var clazz2 = inner2.finish();
        var clazz3 = inner3.finish();
        var clazz4 = inner4.finish();
        var clazz5 = inner5.finish();

        clazz0.getMethod("foo").invoke(null);

        assertExists(clazz0.getDeclaredClasses(), clazz1, clazz2);
        assertExists(clazz1.getDeclaredClasses());
        assertExists(clazz2.getDeclaredClasses(), clazz3, clazz4, clazz5);

        assertNull(clazz0.getEnclosingClass());

        assertEquals(clazz0, clazz1.getEnclosingClass());
        assertEquals(clazz0, clazz2.getEnclosingClass());

        assertEquals(clazz2, clazz3.getEnclosingClass());
        assertEquals(clazz2, clazz4.getEnclosingClass());
        assertEquals(clazz2, clazz5.getEnclosingClass());

        assertEquals("foo", clazz1.getEnclosingMethod().getName());
        assertEquals("foo", clazz2.getEnclosingMethod().getName());

        assertEquals(0, clazz3.getEnclosingConstructor().getParameterCount());
        assertEquals(0, clazz4.getEnclosingConstructor().getParameterCount());
        assertEquals(0, clazz5.getEnclosingConstructor().getParameterCount());
    }

    private static void assertExists(Class<?>[] classes, Class<?> expect) {
        for (var c : classes) {
            if (c == expect) {
                return;
            }
        }
        fail("not found: " + expect);
    }

    private static void assertExists(Class<?>[] classes, Class<?>... expect) {
        for (var e : expect) {
            assertExists(classes, e);
        }
        assertEquals(expect.length, classes.length);
    }
}
