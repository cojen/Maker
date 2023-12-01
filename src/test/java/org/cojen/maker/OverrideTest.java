/*
 *  Copyright 2022 Cojen.org
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

import java.util.AbstractList;
import java.util.Comparator;
import java.util.Set;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class OverrideTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(OverrideTest.class.getName());
    }

    @Test
    public void basic() {
        ClassMaker cm = ClassMaker.begin().extend(TestClass.class).implement(java.util.List.class);

        cm.addMethod(String.class, "toString").override();
        
        try {
            cm.addMethod(Object.class, "toString").override();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Not overriding"));
        }

        try {
            cm.addMethod(int.class, "remove", int.class).override();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Not overriding"));
        }

        try {
            cm.addMethod(String.class, "remove", int.class).override();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Not overriding"));
        }

        try {
            cm.addMethod(void.class, "xxx").override();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Not overriding"));
        }

        cm.addMethod(Object.class, "remove", int.class).override();

        try {
            cm.addMethod(boolean.class, "isEmpty").private_().override();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Not defining a virtual"));
        }

        try {
            cm.addMethod(boolean.class, "isEmpty").static_().override();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Not defining a virtual"));
        }

        try {
            cm.addMethod(Class.class, "getClass").override();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("final method"));
        }

        try {
            cm.addMethod(void.class, "a").override();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Not overriding"));
        }

        try {
            cm.addMethod(void.class, "b").override();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Not overriding"));
        }
    }

    public static class TestClass {
        private void a() {
        }

        public static void b() {
        }
    }

    @Test
    public void extendUnfinished() {
        ClassMaker cm1 = ClassMaker.begin();
        ClassMaker cm2 = ClassMaker.begin().extend(cm1);
        ClassMaker cm3 = ClassMaker.begin().extend(cm2);

        cm1.addMethod(null, "test");

        cm2.addMethod(null, "test").override();

        cm3.addMethod(null, "test").override();

        try {
            cm3.addMethod(null, "xxx").override();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Not overriding"));
        }

        cm1.finish();
        cm2.finish();
        cm3.finish();
    }

    @Test
    public void shouldBeAbstract() throws Exception {
        ClassMaker cm = ClassMaker.begin().extend(AbstractList.class);

        Set<String> unimplemented = cm.unimplementedMethods();
        assertEquals("[int size(), java.lang.Object get(int)]", unimplemented.toString());

        cm.addMethod(Object.class, "get", int.class).override().public_().return_(null);
        unimplemented = cm.unimplementedMethods();
        assertEquals("[int size()]", unimplemented.toString());

        cm.addMethod(int.class, "size").override().public_().return_(0);
        unimplemented = cm.unimplementedMethods();
        assertTrue(unimplemented.isEmpty());

        cm.implement(Comparator.class);
        unimplemented = cm.unimplementedMethods();
        assertEquals("[int compare(java.lang.Object, java.lang.Object)]", unimplemented.toString());

        cm.addMethod(int.class, "compare", Object.class, Object.class)
            .override().public_().return_(0);
        unimplemented = cm.unimplementedMethods();
        assertTrue(unimplemented.isEmpty());
    }
}
