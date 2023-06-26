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
    public void invalidString() throws Exception {
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
    public void basicString() throws Exception {
        // Note: the last three cases are there to test hash collisions.
        basicString("apple", "banana", "cherry", "grape", "orange", "Ea", "FB", "G#");
    }

    @Test
    public void tinyString() throws Exception {
        basicString(new String[0]);
        basicString("a");
        basicString("a", "b");
    }

    private void basicString(String... keys) throws Exception {
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
    public void invalidEnum() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "t1", Enum.class, Thread.State.class);
        Label def = mm.label();

        try {
            mm.this_().switch_(def, new Enum[0]);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Not switching on an Enum"));
        }

        try {
            mm.var(String.class).set("").switch_(def, new Enum[0]);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Not switching on an Enum"));
        }

        try {
            mm.param(0).switch_(def, new Enum[0], def);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Number of cases"));
        }

        try {
            mm.param(0).switch_(def, new Enum[1], def);
            fail();
        } catch (NullPointerException e) {
        }

        try {
            mm.param(0).switch_(def, new Enum[10], new Label[10]);
            fail();
        } catch (NullPointerException e) {
        }

        Label label1 = mm.label();
        Label label2 = mm.label();

        try {
            mm.param(0).switch_
                (def, new Enum[] {StackWalker.Option.SHOW_HIDDEN_FRAMES, Thread.State.RUNNABLE},
                 label1, label2);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Mismatched"));
        }

        try {
            mm.param(0).switch_(def, new Enum[]{Thread.State.RUNNABLE}, label1);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Mismatched"));
        }
    }

    @Test
    public void basicEnum() throws Exception {
        basicEnum(new Thread.State[0]);
        basicEnum(Thread.State.RUNNABLE, Thread.State.BLOCKED);
    }

    private void basicEnum(Thread.State... keys) throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(int.class, "map", Thread.State.class).public_().static_();

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

        var m = cm.finish().getMethod("map", Thread.State.class);

        for (int i=0; i<keys.length; i++) {
            assertEquals(i + 100, m.invoke(null, keys[i]));
        }

        assertEquals(-1, m.invoke(null, Thread.State.TIMED_WAITING));

        if (keys.length > 0) {
            try {
                m.invoke(null, (Thread.State) null);
                fail();
            } catch (InvocationTargetException e) {
                assertTrue(e.getCause() instanceof NullPointerException);
            }
        }
    }
}
