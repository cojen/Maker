/*
 *  Copyright 2019 Cojen.org
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
 * 
 *
 * @author Brian S O'Neill
 */
public class InvokeTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(InvokeTest.class.getName());
    }

    @Test
    public void invokeInterface() throws Exception {
        // Define an interface, implement it, and then invoke it.

        Class iface;
        {
            ClassMaker cm = ClassMaker.begin().public_().interface_();
            cm.addMethod(void.class, "a", int.class).public_().abstract_();
            cm.addMethod(int.class, "b").public_().abstract_();
            cm.addMethod(float.class, "c").public_().abstract_();
            cm.addMethod(double.class, "d").public_().abstract_();
            cm.addMethod(long.class, "e").public_().abstract_();
            cm.addMethod(String.class, "f").public_().abstract_();
            cm.addMethod(Class.class, "clazz").public_().abstract_();
            iface = cm.finish();
        }

        Class impl;
        {
            ClassMaker cm = ClassMaker.begin().public_().implement(iface);

            MethodMaker mm = cm.addConstructor().public_();

            cm.addField(int.class, "state").protected_();

            mm = cm.addMethod(void.class, "a", int.class).public_().synchronized_();
            mm.field("state").set(mm.param(0));

            mm = cm.addMethod(int.class, "b").public_();
            mm.return_(mm.field("state"));

            mm = cm.addMethod(float.class, "c").public_();
            mm.return_(mm.field("state").cast(float.class));

            mm = cm.addMethod(double.class, "d").public_();
            mm.return_(mm.field("state"));

            mm = cm.addMethod(long.class, "e").public_();
            mm.return_(mm.field("state"));

            mm = cm.addMethod(String.class, "f").public_();
            mm.return_(mm.concat(mm.field("state")));

            mm = cm.addMethod(Class.class, "clazz").public_();
            mm.return_(mm.class_());

            impl = cm.finish();
        }

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        var v1 = mm.var(iface).set(mm.new_(impl));

        v1.invoke("a", 100);

        var assertVar = mm.var(Assert.class);
        assertVar.invoke("assertEquals", 100, v1.invoke("b"));
        assertVar.invoke("assertEquals", 100f, v1.invoke("c"), 0.0f);
        assertVar.invoke("assertEquals", 100d, v1.invoke("d"), 0.0d);
        assertVar.invoke("assertEquals", 100L, v1.invoke("e"));
        assertVar.invoke("assertEquals", "100", v1.invoke("f"));
        assertVar.invoke("assertEquals", impl, v1.invoke("clazz"));

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void invokeDynamic() throws Exception {
        // Dynamically generate a method which adds two numbers. In practice, this is overkill.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        MethodHandle bootstrap = MethodHandles.lookup().findStatic
            (InvokeTest.class, "boot", MethodType.methodType
             (CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class));

        var assertVar = mm.var(Assert.class);

        {
            var v1 = mm.var(int.class).set(1);
            var v2 = mm.var(int.class).set(2);
            var v3 = mm.invokeDynamic
                (bootstrap, null, "intAdd",
                 MethodType.methodType(int.class, int.class, int.class), v1, v2);
            assertVar.invoke("assertEquals", 3, v3);
        }

        {
            var v1 = mm.var(double.class).set(1.1);
            var v2 = mm.var(double.class).set(2.1);
            var v3 = mm.invokeDynamic
                (bootstrap, null, "doubleAdd",
                 MethodType.methodType(double.class, double.class, double.class), v1, v2);
            assertVar.invoke("assertEquals", 3.2, v3, 0.0);
        }

        cm.finish().getMethod("run").invoke(null);
    }

    public static CallSite boot(MethodHandles.Lookup caller, String name, MethodType type)
        throws Exception
    {
        ClassMaker cm = ClassMaker.begin().public_().final_();
        MethodMaker mm = cm.addMethod(name, type).static_().public_();
        mm.return_(mm.param(0).add(mm.param(1)));
        Class<?> clazz = cm.finish();
        var mh = MethodHandles.lookup().findStatic(clazz, name, type);
        return new ConstantCallSite(mh);
    }

    @Test
    public void invokeDynamic2() throws Exception {
        // Dynamically generate a method which throws an exception. In practice, this is overkill.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();

        MethodHandle bootstrap = MethodHandles.lookup().findStatic
            (InvokeTest.class, "boot", MethodType.methodType
             (CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class,
              MethodHandle.class, MethodType.class));

        MethodHandleInfo info = MethodHandles.lookup().revealDirect(bootstrap);
        MethodType type = MethodType.methodType(void.class, String.class);

        Label start = mm.label().here();
        var v0 = mm.invokeDynamic
            (bootstrap, new Object[] {info, type}, // pass additional constants for code coverage
             "throwIt", type, "hello");
        assertNull(v0);
        mm.return_();
        Label end = mm.label().here();

        String expect = "hello" + bootstrap + type;

        var ex = mm.exceptionHandler(start, end, Exception.class);
        var msg = ex.invoke("getMessage");
        mm.var(Assert.class).invoke("assertEquals", expect, msg);
        mm.return_();

        cm.finish().getMethod("run").invoke(null);
    }

    public static CallSite boot(MethodHandles.Lookup caller, String name, MethodType type,
                                MethodHandle arg1, MethodType arg2)
        throws Exception
    {
        ClassMaker cm = ClassMaker.begin().public_().final_();
        MethodMaker mm = cm.addMethod(name, type).static_().public_();
        mm.new_(Exception.class, mm.concat(mm.param(0), arg1, arg2)).throw_();
        Class<?> clazz = cm.finish();
        var mh = MethodHandles.lookup().findStatic(clazz, name, type);
        return new ConstantCallSite(mh);
    }
}
