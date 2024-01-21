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
import java.lang.reflect.Method;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.Month;

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
            assertTrue(e.getMessage().contains("Case cannot be null"));
        }

        try {
            mm.param(0).switch_(def, new Object[] {mm.param(0)}, mm.label());
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Case isn't a constant"));
        }

        try {
            MethodMaker mm2 = cm.addMethod(int.class, "t2", String.class).public_().static_();
            mm2.param(0).switch_(mm2.label(), new Month[] {Month.JUNE}, mm.label());
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Not switching on an Enum"));
        }

        try {
            MethodMaker mm2 = cm.addMethod(int.class, "t2", DayOfWeek.class).public_().static_();
            mm2.param(0).switch_(mm2.label(), new Month[] {Month.JUNE}, mm.label());
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Enum case"));
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
        basic((Object[]) keys);
    }

    private void basic(Object... keys) throws Exception {
        basic(ClassMaker.begin(), keys);
    }

    private void basic(ClassMaker cm, Object... keys) throws Exception {
        cm.public_();

        Class<?> paramType = keys instanceof String[] ? String.class : Object.class;
        MethodMaker mm = cm.addMethod(int.class, "map", paramType).public_().static_();

        Label[] labels = new Label[keys.length];
        for (int i=0; i<labels.length; i++) {
            labels[i] = mm.label();
        }

        Label notFound = mm.label();

        if (keys instanceof String[] strKeys) {
            mm.param(0).switch_(notFound, strKeys, labels);
        } else {
            mm.param(0).switch_(notFound, keys, labels);
        }

        for (int i=0; i<labels.length; i++) {
            labels[i].here();
            mm.return_(i + 100);
        }

        notFound.here();
        mm.return_(-1);

        var m = cm.finish().getMethod("map", paramType);

        for (int i=0; i<keys.length; i++) {
            assertEquals(i + 100, m.invoke(null, keys[i]));
        }

        assertEquals(-1, m.invoke(null, "xxxxxxxxx"));

        try {
            m.invoke(null, (String) null);
            fail();
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof NullPointerException);
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

    @Test
    public void objects() throws Exception {
        basic(LocalTime.of(1, 1), Month.FEBRUARY);
        basic(LocalTime.of(1, 1), Month.FEBRUARY, LocalTime.of(3, 3, 3));
        basic(LocalTime.of(1, 1), LocalTime.of(2, 2, 2));
        basic(LocalTime.of(1, 1), LocalTime.of(2, 2, 2), this);
    }

    @Test
    public void external() throws Exception {
        try {
            basic(ClassMaker.beginExternal("Foo"), Month.JANUARY);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("external"));
        }
    }

    @Test
    public void primitive() throws Exception {
        primitive(123L, Long.MAX_VALUE - 10);
        primitive(123L, Long.MAX_VALUE - 10, -456L);
    }

    private void primitive(Long... keys) throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        MethodMaker mm = cm.addMethod(int.class, "map", long.class).public_().static_();

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

        var m = cm.finish().getMethod("map", long.class);

        for (int i=0; i<keys.length; i++) {
            assertEquals(i + 100, m.invoke(null, keys[i]));
        }

        assertEquals(-1, m.invoke(null, 999L));
    }

    @Test
    public void enums() throws Exception {
        doEnums();
    }

    @Test
    public void enumsNoSwitchBootstraps() throws Exception {
        Switcher.NO_SWITCH_BOOTSTRAPS = true;
        try {
            doEnums();
        } finally {
            Switcher.NO_SWITCH_BOOTSTRAPS = false;
        }
    }

    private void doEnums() throws Exception {
        doEnums(new Month[0]);
        doEnums(Month.FEBRUARY, Month.MARCH);
        doEnums(Month.FEBRUARY, Month.MARCH, Month.JULY);
    }

    private void doEnums(Month... keys) throws Exception {
        doEnums(ClassMaker.begin(), keys);
    }

    @Test
    public void enumsExternal() throws Exception {
        doEnumsExternal("_Enums$$$");
    }

    @Test
    public void enumsExternalNoSwitchBootstraps() throws Exception {
        Switcher.NO_SWITCH_BOOTSTRAPS = true;
        try {
            doEnumsExternal("_EnumsNoBoot$$$");
        } finally {
            Switcher.NO_SWITCH_BOOTSTRAPS = false;
        }
    }

    private void doEnumsExternal(String suffix) throws Exception {
        String base = SwitcherTest.class.getName() + suffix;

        doEnums(ClassMaker.beginExternal(base + 1), new Month[0]);
        doEnums(ClassMaker.beginExternal(base + 2), Month.FEBRUARY, Month.MARCH);
        doEnums(ClassMaker.beginExternal(base + 3), Month.FEBRUARY, Month.MARCH, Month.JULY);
    }

    private void doEnums(ClassMaker cm, Month... keys) throws Exception {
        cm.public_();

        MethodMaker mm = cm.addMethod(int.class, "map", Month.class).public_().static_();

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

        var m = cm.finish().getMethod("map", Month.class);

        for (int i=0; i<keys.length; i++) {
            assertEquals(i + 100, m.invoke(null, keys[i]));
        }

        assertEquals(-1, m.invoke(null, Month.JANUARY));

        try {
            m.invoke(null, (Month) null);
            fail();
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof NullPointerException);
        }
    }

    @Test
    public void enumsExternalNoSwitchBootstrapsRecycle() throws Exception {
        // Tests that only one inner class is generated per enum type.

        Class<?> clazz;

        Switcher.NO_SWITCH_BOOTSTRAPS = true;
        try {
            ClassMaker cm = ClassMaker.beginExternal(SwitcherTest.class.getName() + "_Recycle$$$");
            cm.public_();

            {
                MethodMaker mm = cm.addMethod(int.class, "m1", Month.class).public_().static_();
                Month[] keys = {Month.FEBRUARY, Month.MAY};
                Label[] labels = {mm.label(), mm.label()};
                Label notFound = mm.label();
                mm.param(0).switch_(notFound, keys, labels);
                for (int i=0; i<labels.length; i++) {
                    labels[i].here();
                    mm.return_(i + 100);
                }
                notFound.here();
                mm.return_(-1);
            }

            {
                MethodMaker mm = cm.addMethod(int.class, "m2", Month.class).public_().static_();
                Month[] keys = {Month.JUNE, Month.FEBRUARY};
                Label[] labels = {mm.label(), mm.label()};
                Label notFound = mm.label();
                mm.param(0).switch_(notFound, keys, labels);
                for (int i=0; i<labels.length; i++) {
                    labels[i].here();
                    mm.return_(i + 200);
                }
                notFound.here();
                mm.return_(-1);
            }

            {
                MethodMaker mm = cm.addMethod(int.class, "m3", DayOfWeek.class).public_().static_();
                DayOfWeek[] keys = {DayOfWeek.FRIDAY, DayOfWeek.MONDAY};
                Label[] labels = {mm.label(), mm.label()};
                Label notFound = mm.label();
                mm.param(0).switch_(notFound, keys, labels);
                for (int i=0; i<labels.length; i++) {
                    labels[i].here();
                    mm.return_(i + 300);
                }
                notFound.here();
                mm.return_(-1);
            }

            clazz = cm.finish();
        } finally {
            Switcher.NO_SWITCH_BOOTSTRAPS = false;
        }

        assertEquals(2, clazz.getDeclaredClasses().length);

        {
            Method m = clazz.getMethod("m1", Month.class);
            assertEquals(100, m.invoke(null, Month.FEBRUARY));
            assertEquals(101, m.invoke(null, Month.MAY));
            assertEquals(-1, m.invoke(null, Month.DECEMBER));
        }

        {
            Method m = clazz.getMethod("m2", Month.class);
            assertEquals(200, m.invoke(null, Month.JUNE));
            assertEquals(201, m.invoke(null, Month.FEBRUARY));
            assertEquals(-1, m.invoke(null, Month.DECEMBER));
        }

        {
            Method m = clazz.getMethod("m3", DayOfWeek.class);
            assertEquals(300, m.invoke(null, DayOfWeek.FRIDAY));
            assertEquals(301, m.invoke(null, DayOfWeek.MONDAY));
            assertEquals(-1, m.invoke(null, DayOfWeek.SUNDAY));
        }
    }
}
