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

import java.lang.invoke.*;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Tests for various illegal usage exceptions.
 *
 * @author Brian S O'Neill
 */
public class UsageTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(UsageTest.class.getName());
    }

    private ClassMaker mClassMaker;

    @Before
    public void setup() {
        mClassMaker = ClassMaker.begin();
    }

    @Test
    public void changeExtend() {
        mClassMaker.extend(UsageTest.class);
        try {
            mClassMaker.extend(UsageTest.class);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Super");
        }
    }

    @Test
    public void changeAutoExtend() {
        MethodMaker mm = mClassMaker.addConstructor();
        mm.invokeSuperConstructor();

        try {
            mClassMaker.extend(UsageTest.class);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Super");
        }
    }

    @Test
    public void changeStatic() {
        MethodMaker mm = mClassMaker.addMethod(null, "test", int.class);
        mm.param(0);
        try {
            mm.static_();
            fail();
        } catch (IllegalStateException e) {
            check(e, "parameters have been accessed");
        } 
    }

    @Test
    public void endReached() {
        MethodMaker mm = mClassMaker.addMethod(int.class, "test");
        try {
            mClassMaker.finish();
            fail();
        } catch (IllegalStateException e) {
            check(e, "End reached");
        }
    }

    @Test
    public void notStandalone() {
        MethodMaker mm = mClassMaker.addMethod(int.class, "test");
        try {
            mm.finish();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Not a standalone");
        }
    }

    @Test
    public void unpositioned() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        Label a = mm.label().goto_();
        assertEquals(mm, a.methodMaker());
        try {
            mClassMaker.finish();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Unpositioned");
        }
    }

    @Test
    public void noThis() {
        MethodMaker mm = mClassMaker.addMethod(null, "test").static_();
        try {
            mm.this_();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Not an instance");
        }
    }

    @Test
    public void illegalParam() {
        MethodMaker mm = mClassMaker.addMethod(null, "test").static_();
        try {
            mm.param(-1);
            fail();
        } catch (IndexOutOfBoundsException e) {
        }
        try {
            mm.param(10);
            fail();
        } catch (IndexOutOfBoundsException e) {
        }
    }

    @Test
    public void returnFail() {
        MethodMaker mm = mClassMaker.addMethod(int.class, "test").static_();
        try {
            mm.return_();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Must return a value");
        }
    }

    @Test
    public void returnFail2() {
        MethodMaker mm = mClassMaker.addMethod(void.class, "test").static_();
        try {
            mm.return_(3);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Cannot return a value");
        }
    }

    @Test
    public void returnFail3() {
        MethodMaker mm = mClassMaker.addMethod(int.class, "test").static_();
        try {
            mm.return_("hello");
            fail();
        } catch (IllegalStateException e) {
            check(e, "Automatic conversion");
        }
    }

    @Test
    public void wrongField() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.field("missing");
            fail();
        } catch (IllegalStateException e) {
            check(e, "Field not found");
        }
    }

    @Test
    public void notConstructor() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.invokeThisConstructor();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Not defining");
        }
    }

    @Test
    public void paramCount() throws Exception {
        MethodHandle handle = MethodHandles.lookup()
            .findStatic(UsageTest.class, "check",
                        MethodType.methodType(void.class, Exception.class, String.class));

        MethodMaker mm = mClassMaker.addMethod(null, "test");

        try {
            mm.invoke(handle, 3);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Wrong number");
        }
    }

    @Test
    public void paramCount2() throws Exception {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        var bootstrap = mm.var(UsageTest.class).indy("boot");

        try {
            bootstrap.invoke(void.class, "test", new Object[] {int.class}, 1, 2);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Mismatched");
        }
    }

    public static CallSite boot(MethodHandles.Lookup caller, String name, MethodType type) {
        throw null;
    }

    @Test
    public void dimensions() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.new_(int[].class);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "At least one");
        }
        try {
            mm.new_(long[].class, 10, 10);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Too many");
        }
    }

    @Test
    public void tooMuchCode() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        var a = mm.var(int.class);
        for (int i=0; i<100_000; i++) {
            a.inc(1);
        }
        try {
            mClassMaker.finish();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Code limit");
            check(e, "test");
        }
    }

    @Test
    public void tooManyFields() {
        for (int i=0; i<70_000; i++) {
            mClassMaker.addField(String.class, "f" + i);
        }
        try {
            mClassMaker.finish();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Field count");
        }
    }

    @Test
    public void unknownVar() {
        MethodMaker mm1 = mClassMaker.addMethod(null, "test1");
        MethodMaker mm2 = mClassMaker.addMethod(null, "test2");
        var a = mm1.var(int.class).set(0);
        var b = mm2.var(int.class).set(0);
        try {
            a.set(b);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Unknown variable");
        }
    }

    @Test
    public void storeNull() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(int.class).set(null);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Cannot store null");
        }
    }

    @Test
    public void unsupportedConstant() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(Object.class).set(new java.util.ArrayList());
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Unsupported constant");
        }
        try {
            mm.var(Object.class).set(new java.math.BigInteger("123"));
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Unsupported constant");
        }
    }

    @Test
    public void unsupportedConstantIndy() {
        mClassMaker = ClassMaker.beginExternal("foo");
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(UsageTest.class).indy("boot2", mm.var(String.class));
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Unsupported constant");
        }

        try {
            mm.var(UsageTest.class).indy("boot2", new java.util.ArrayList());
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Unsupported constant");
        }
    }

    public static CallSite boot2(MethodHandles.Lookup caller, String name, MethodType type,
                                 Object param)
    {
        return null;
    }

    @Test
    public void unmodifiable() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        var clazz = mm.class_();
        try {
            clazz.set(null);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Unmodifiable");
        }
        assertSame(clazz, mm.class_());
    }

    @Test
    public void nullMath() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(int.class).set(1).add(null);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Cannot 'add' by null");
        }
        try {
            mm.var(int.class).set(1).shr(null);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Cannot 'shift' by null");
        }
    }

    @Test
    public void wrongMath() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(double.class).set(1).shr(1);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Cannot 'shift' against a non-integer");
        }
        try {
            mm.var(String.class).set("hello").add(1);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Cannot 'add' against a non-numeric");
        }
        try {
            mm.var(String.class).set("hello").ushr(1);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Cannot 'shift' against a non-numeric");
        }
        try {
            mm.var(boolean.class).set(true).add(true);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Cannot 'add' against a non-numeric");
        }
        try {
            mm.var(boolean.class).set(true).shl(true);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Cannot 'shift' against a non-numeric");
        }
    }

    @Test
    public void unknownLabel() {
        MethodMaker mm1 = mClassMaker.addMethod(null, "test1");
        MethodMaker mm2 = mClassMaker.addMethod(null, "test2");
        try {
            mm1.goto_(null);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Label is null");
        }
        Label a = mm1.label().here();
        try {
            mm2.goto_(a);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Unknown label");
        }
        try {
            mm2.goto_(new Label() {
                public Label here() {return this;}
                public Label goto_() {return this;}
                public Label insert(Runnable body) {return null;}
                public MethodMaker methodMaker() {return null;}
            });
            fail();
        } catch (IllegalStateException e) {
            check(e, "Unknown label");
        }
        try {
            a.here();
            fail();
        } catch (IllegalStateException e) {
            // Cannot position again.
        }
    }

    @Test
    public void wrongComparison() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        Label a = mm.label().here();
        try {
            mm.var(int.class).ifEq(null, a);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Cannot compare");
        }
        try {
            mm.var(int.class).ifEq("hello", a);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Incomparable");
        }
        try {
            mm.var(String.class).gt(null);
            fail();
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void switchFail() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        Label a = mm.label().here();
        Label b = mm.label().here();
        try {
            mm.var(int.class).switch_(a, new int[]{1}, a, b);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Number of cases");
        }
        try {
            mm.var(int.class).switch_(a, new int[]{1, 1}, a, b);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Duplicate");
        }
    }

    @Test
    public void castFail() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(int.class).instanceOf(String.class);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Not an object");
        }
        try {
            mm.var(int.class).cast(String.class);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Unsupported");
        }
    }

    @Test
    public void arrayFail() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(int.class).set(1).alength();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Not an array");
        }
    }

    @Test
    public void throwFail() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(int.class).throw_();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Non-throwable");
        }

        try {
            mm.var(String.class).throw_();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Non-throwable");
        }

        try {
            mm.this_().throw_();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Non-throwable");
        }
    }

    @Test
    public void renameFail() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(int.class).name("foo").name("bar");
            fail();
        } catch (IllegalStateException e) {
            check(e, "Already named");
        }

        try {
            mm.class_().name("foo");
            fail();
        } catch (IllegalStateException e) {
            check(e, "Already named");
        }

        mClassMaker.addField(int.class, "foo");
        var field = mm.field("foo");
        try {
            field.name("bar");
            fail();
        } catch (IllegalStateException e) {
            check(e, "Already named");
        }
    }

    @Test
    public void signatureFail() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");

        try {
            mm.super_().signature("xxx");
            fail();
        } catch (IllegalStateException e) {
            check(e, "Cannot define a signature");
        }

        mClassMaker.addField(int.class, "foo");
        var field = mm.field("foo");
        try {
            field.signature("xxx");
            fail();
        } catch (IllegalStateException e) {
            check(e, "Cannot define a signature");
        }
    }

    @Test
    public void monitorFail() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(int.class).monitorEnter();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Not an object");
        }
    }

    @Test
    public void doubleFinish() {
        mClassMaker.finish();
        try {
            mClassMaker.finish();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Class definition");
        }
    }

    @Test
    public void doubleField() {
        mClassMaker.addField(int.class, "foo");
        try {
            mClassMaker.addField(String.class, "foo");
            fail();
        } catch (IllegalStateException e) {
            check(e, "Field is");
        }
    }

    @Test
    public void wrongMethod() {
        try {
            mClassMaker.addMethod(null, "<clinit>");
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Use the");
        }
        try {
            mClassMaker.addMethod(null, "<init>");
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Use the");
        }
    }

    @Test
    public void noAccess() throws Exception {
        noAccess(false);
    }

    @Test
    public void noAccessHidden() throws Exception {
        noAccess(true);
    }

    private void noAccess(boolean hidden) throws Exception {
        mClassMaker = ClassMaker.begin
            (null, MethodHandles.lookup().dropLookupMode(MethodHandles.Lookup.PACKAGE));

        try {
            if (hidden) {
                mClassMaker.finishHidden();
            } else {
                mClassMaker.finish();
            }
            fail();
        } catch (IllegalStateException e) {
            check(e, "java.lang.IllegalAccessException");
        }
    }

    @Test
    public void wrongPackage() throws Exception {
        mClassMaker = ClassMaker.begin("wrong.Place", MethodHandles.lookup());

        try {
            mClassMaker.finishHidden();
            fail();
        } catch (IllegalArgumentException e) {
            try {
                check(e, "wrong.Place");
            } catch (AssertionError e2) {
                check(e, "wrong/Place");
            }
        }
    }

    @Test
    public void verifyError() throws Exception {
        mClassMaker = ClassMaker.begin(null, MethodHandles.lookup());

        MethodMaker mm = mClassMaker.addMethod(int.class, "test").public_().static_();

        var v1 = mm.var(int.class);

        Label start = mm.label().here();
        //v1.set(1);
        Label done = mm.label().goto_();
        mm.catch_(start, Throwable.class, ex -> {
            v1.set(2);
        });

        done.here();
        mm.return_(v1); // v1 isn't guaranteed to have been assigned

        try {
            var clazz = mClassMaker.finishHidden().lookupClass();
            clazz.getMethod("test").invoke(null);
            fail();
        } catch (VerifyError e) {
        }
    }

    @Test
    public void unassigned() throws Exception {
        mClassMaker = ClassMaker.begin(null, MethodHandles.lookup());

        MethodMaker mm = mClassMaker.addMethod(int.class, "test").public_().static_();
        mm.return_(mm.var(int.class).name("foo"));

        if (!TheClassMaker.DEBUG) {
            try {
                mClassMaker.finishHidden();
                fail();
            } catch (IllegalStateException e) {
                check(e, "unassigned variable");
                check(e, "foo");
            }
        } else {
            try {
                mClassMaker.finishHidden();
                fail();
            } catch (VerifyError e) {
            }
        }
    }

    @Test
    public void exactConstants() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        mm.var(Object.class).setExact(new java.util.HashMap());
        try {
            mClassMaker.finishBytes();
        } catch (IllegalStateException e) {
            check(e, "Class has exact");
        }
    }

    @Test
    public void bigStringConstant() throws Exception {
        MethodMaker mm = mClassMaker.addMethod(String.class, "get").public_().static_();

        try {
            mm.return_(makeString(65536, 'a'));
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "65536");
        }

        try {
            mm.return_(makeString(40000, '\0'));
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "80000");
        }

        try {
            mm.return_(makeString(40000, '\u0100'));
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "80000");
        }

        try {
            mm.return_(makeString(40000, '\u1000'));
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "120000");
        }
    }

    @Test
    public void bigClassName() throws Exception {
        ClassMaker cm = ClassMaker.beginExternal(makeString(100_000, 'a'));
        try {
            cm.finishBytes();
            fail();
        } catch (IllegalStateException e) {
            check(e, "100000");
        }
    }

    @Test
    public void voidArray() throws Exception {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.new_(Type.from(void.class).asArray(), 10);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "void");
        }
    }

    static String makeString(int length, char c) {
        var b = new StringBuilder(length);
        for (int i=0; i<length; i++) {
            b.append(c);
        }
        return b.toString();
    }

    private static void check(Exception e, String message) {
        String actual = e.getMessage();
        assertTrue(message + "; " + actual, actual.contains(message));
    }
}
