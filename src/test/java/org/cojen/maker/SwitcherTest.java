/*
 *  Copyright 2023 Cojen.org
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

import java.lang.reflect.InvocationTargetException;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class SwitcherTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(SwitcherTest.class.getName());
    }

    @Test
    public void invalid() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "t1", String.class);
        Label def = mm.label();

        try {
            mm.this_().switch_(def, new String[0]);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("String"));
        }

        try {
            mm.param(0).switch_(def, new String[0], def);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Number of cases"));
        }

        try {
            mm.param(0).switch_(def, new String[1], def);
            fail();
        } catch (NullPointerException e) {
        }

        try {
            mm.param(0).switch_(def, new String[10], new Label[10]);
            fail();
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void basic() throws Exception {
        // Note: the last three cases are there to test hash collisions.
        basic("apple", "banana", "cherry", "grape", "orange", "Ea", "FB", "G#");
    }

    @Test
    public void tiny() throws Exception {
        basic(new String[0]);
        basic("a");
        basic("a", "b");
    }

    private void basic(String... keys) throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(int.class, "map", String.class).public_().static_();

        Label[] labels = new Label[keys.length];
        for (int i=0; i<labels.length; i++) {
            labels[i] = mm.label();
        }

        Label notFound = mm.label();

        mm.param(0).switch_(notFound, keys, labels);

        for (int i=0; i<labels.length; i++) {
            labels[i].here();
            mm.return_(i + 100);
        }

        notFound.here();
        mm.return_(-1);

        var m = cm.finish().getMethod("map", String.class);

        for (int i=0; i<keys.length; i++) {
            assertEquals(i + 100, m.invoke(null, keys[i]));
        }

        assertEquals(-1, m.invoke(null, "xxxxxxxxx"));

        if (keys.length > 0) {
            try {
                m.invoke(null, (String) null);
                fail();
            } catch (InvocationTargetException e) {
                assertTrue(e.getCause() instanceof NullPointerException);
            }
        }
    }

    @Test
    public void field() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        cm.addField(String.class, "cond").private_().final_();

        MethodMaker mm = cm.addConstructor(String.class).public_();
        mm.invokeSuperConstructor();
        mm.field("cond").set(mm.param(0));

        mm = cm.addMethod(int.class, "test").public_();

        String[] keys = {"hello", "world"};
        Label[] labels = {mm.label(), mm.label()};
        Label notFound = mm.label();

        mm.field("cond").switch_(notFound, keys, labels);

        for (int i=0; i<labels.length; i++) {
            labels[i].here();
            mm.return_(i);
        }

        notFound.here();
        mm.return_(-1);

        Class<?> clazz = cm.finish();
        var ctor = clazz.getConstructor(String.class);
        var test = clazz.getMethod("test");

        assertEquals(0, test.invoke(ctor.newInstance("hello")));
        assertEquals(1, test.invoke(ctor.newInstance("world")));
        assertEquals(-1, test.invoke(ctor.newInstance("xxxxxxxxx")));
    }
}
