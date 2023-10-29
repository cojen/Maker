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

import java.lang.invoke.*;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class HiddenTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(HiddenTest.class.getName());
    }

    public static class Base {
        public int add(int x) {
            return x + 1;
        }

        public final int addOne(int x) {
            return x + 1;
        }
    }

    public static interface Iface {
        public int apply(int x);

        public default int negate(int x) {
            return -x;
        }
    }

    @Before
    public void setup() {
        ClassMaker cm = ClassMaker.begin().public_().extend(Base.class).implement(Iface.class);

        cm.addField(int.class, "state").public_().static_();
        cm.addField(int.class, "value").public_();

        MethodMaker mm = cm.addConstructor(int.class).public_();
        mm.field("value").set(mm.param(0));
        mm.invokeSuperConstructor();

        mm = cm.addMethod(int.class, "getState").public_().static_();
        mm.return_(mm.field("state"));

        mm = cm.addMethod(null, "setState", int.class).public_().static_();
        mm.field("state").set(mm.param(0));

        mm = cm.addMethod(int.class, "add", int.class).public_().override();
        mm.return_(mm.param(0).add(10));

        mm = cm.addMethod(int.class, "apply", int.class).public_().override();
        mm.return_(mm.param(0).neg());

        mm = cm.addMethod(int.class, "getValue").public_();
        mm.return_(mm.field("value"));

        mm = cm.addMethod(null, "setValue", int.class).public_();
        mm.field("value").set(mm.param(0));

        mHidden = cm.finishHidden().lookupClass();
    }

    private Class mHidden;

    @Test
    public void access() throws Exception {
        // Test that fields, constructors, and methods of a hidden class can be accessed.

        ClassMaker cm = ClassMaker.begin().public_();

        MethodMaker mm = cm.addMethod(null, "test").public_().static_();

        var hVar = mm.var(mHidden);

        // Test static field access.

        var stateField = hVar.field("state");

        makeAssertTrue(stateField.eq(0));
        makeAssertTrue(stateField.getVolatile().eq(0));
        makeAssertTrue(stateField.varHandle().invoke(int.class, "get", null).eq(0));
        makeAssertTrue(stateField.methodHandleGet().invoke(int.class, "invokeExact", null).eq(0));

        stateField.set(1);
        makeAssertTrue(stateField.eq(1));
        stateField.setVolatile(2);
        makeAssertTrue(stateField.eq(2));
        stateField.varHandle().invoke(void.class, "set", null, 3);
        makeAssertTrue(stateField.eq(3));
        stateField.methodHandleSet().invoke(void.class, "invokeExact", null, 4);
        makeAssertTrue(stateField.eq(4));

        // Test MethodHandle access.

        makeAssertTrue(hVar.methodHandle(int.class, "getState")
                       .invoke(int.class, "invokeExact", null).eq(4));

        var hInstanceVar = hVar.methodHandle(null, ".new", int.class).invoke("invoke", 10);

        makeAssertTrue(hVar.methodHandle(int.class, "add", int.class)
                       .invoke("invoke", hInstanceVar, 10).cast(int.class).eq(20));

        makeAssertTrue(hVar.methodHandle(int.class, "negate", int.class)
                       .invoke("invoke", hInstanceVar, 10).cast(int.class).eq(-10));

        // Test static method access.

        hVar.invoke("setState", 11);
        makeAssertTrue(hVar.invoke("getState").eq(11));

        // Test constructor.

        hInstanceVar = mm.new_(hVar, 10);
        makeAssertTrue(hInstanceVar.instanceOf(Base.class));

        hInstanceVar = hVar.invoke((Object) null, ".new", null, 123);
        makeAssertTrue(hInstanceVar.instanceOf(Base.class));

        // Test instance field access.

        var valueField = hInstanceVar.field("value");

        makeAssertTrue(valueField.eq(123));
        makeAssertTrue(valueField.getVolatile().eq(123));
        makeAssertTrue(valueField.varHandle().invoke
                       (int.class, "get", new Object[] {Object.class}, hInstanceVar).eq(123));
        makeAssertTrue(valueField.methodHandleGet().invoke
                       (int.class, "invoke", new Object[] {Object.class}, hInstanceVar).eq(123));

        valueField.set(1);
        makeAssertTrue(valueField.eq(1));
        valueField.setVolatile(2);
        makeAssertTrue(valueField.eq(2));
        valueField.varHandle().invoke
            (void.class, "set", new Object[] {Object.class, int.class}, hInstanceVar, 3);
        makeAssertTrue(valueField.eq(3));
        valueField.methodHandleSet().invoke
            (void.class, "invoke",  new Object[] {Object.class, int.class}, hInstanceVar, 4);
        makeAssertTrue(valueField.eq(4));

        // Test instance method access.

        hInstanceVar.invoke("setValue", 555);
        makeAssertTrue(hInstanceVar.invoke("getValue").eq(555));
        makeAssertTrue(hInstanceVar.methodHandle(int.class, "getValue").invoke
                       (int.class, "invoke", new Object[] {Object.class}, hInstanceVar).eq(555));

        // Test inherited method access.

        makeAssertTrue(hInstanceVar.invoke("addOne", 1).eq(2));
        makeAssertTrue(hInstanceVar.invoke("negate", 1).eq(-1));

        // FIXME: Dynamic constants are duplicated, even when they're the same.

        Class<?> clazz = cm.finish();
        clazz.getMethod("test").invoke(null);
    }

    private static void makeAssertTrue(Variable v) {
        v.methodMaker().var(Assert.class).invoke("assertTrue", v);
    }
}
