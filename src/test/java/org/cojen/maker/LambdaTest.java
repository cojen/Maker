/*
 *  Copyright 2026 Cojen.org
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

import java.io.Serializable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import java.util.List;
import java.util.RandomAccess;

import java.util.function.Function;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class LambdaTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(LambdaTest.class.getName());
    }

    @Test
    public void broken() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        try {
            cm.addLambdaFunction(String.class, null, null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Not an interface"));
        }

        try {
            cm.addLambdaFunction(List.class, null, null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("More than one abstract"));
        }

        try {
            cm.addLambdaFunction(Serializable.class, null, null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No abstract"));
        }

        try {
            cm.addLambdaFunction(Function.class, null, null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Too few parameter types"));
        }
    }

    @Test
    public void basic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        LambdaFunction lf = cm.addLambdaFunction(Function.class, String.class, null, String.class);
        lf.return_(lf.concat(lf.param(0), "world"));

        MethodMaker mm = cm.addMethod(Object.class, "test", Function.class).static_();
        mm.return_(mm.param(0).invoke("apply", "hello"));

        mm = cm.addMethod(Object.class, "test").public_().static_();
        var function = mm.create(lf);
        mm.return_(mm.invoke("test", function));

        Class<?> clazz = cm.finish();

        assertEquals("helloworld", clazz.getMethod("test").invoke(null));
    }

    @Test
    public void captures() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        cm.addConstructor().public_();

        LambdaFunction lf = cm.addLambdaFunction
            (Function.class, String.class, "captures", cm.type(), int.class, null);
        lf.return_(lf.concat(lf.param(2), lf.param(1), lf.param(0)));

        assertEquals(Type.from(Function.class), lf.functionType());

        MethodMaker mm = cm.addMethod(Object.class, "test", Function.class).static_();
        mm.return_(mm.param(0).invoke("apply", "hello"));

        mm = cm.addMethod(Object.class, "test1").public_();
        var function = mm.create(lf, mm.this_(), 123);
        mm.return_(mm.invoke("test", function));

        mm = cm.addMethod(Object.class, "test2").public_();
        function = mm.create(lf, mm.var(cm).clear(), 0);
        mm.return_(mm.invoke("test", function));

        Class<?> clazz = cm.finish();
        Constructor ctor = clazz.getConstructor();
        Object instance = ctor.newInstance();

        {
            var result = (String) clazz.getMethod("test1").invoke(instance);
            assertTrue(result.startsWith("hello123org.cojen.maker.ClassMaker"));
        }

        {
            var result = clazz.getMethod("test2").invoke(instance);
            assertEquals("hello0null", result);
        }
    }

    @Test
    public void marker() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        LambdaFunction lf = cm.addLambdaFunction(Function.class, Object.class, null, Object.class);

        try {
            lf.addMarkerInterface(String.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Not an interface"));
        }

        lf.addMarkerInterface(RandomAccess.class);

        lf.return_(lf.param(0));

        MethodMaker mm = cm.addMethod(Object.class, "test", Function.class).static_();
        mm.return_(mm.param(0).invoke("apply", "hello"));

        mm = cm.addMethod(Object.class, "test").public_().static_();
        var function = mm.create(lf);
        mm.var(Assert.class).invoke("assertTrue", function.instanceOf(RandomAccess.class));
        mm.return_(mm.invoke("test", function));

        Class<?> clazz = cm.finish();

        assertEquals("hello", clazz.getMethod("test").invoke(null));
    }

    @Test
    public void bridges() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        LambdaFunction lf = cm.addLambdaFunction(Function.class, String.class, null, String.class);
        lf.return_(lf.concat(lf.param(0), "world"));

        lf.addBridgeMethod(String.class, String.class);
        lf.addBridgeMethod(String.class, Object.class);
        lf.addBridgeMethod(Object.class, String.class);

        MethodMaker mm = cm.addMethod(Object.class, "test").public_().static_();
        var function = mm.create(lf);
        mm.return_(function);

        Class<?> clazz = cm.finish();

        Object result = clazz.getMethod("test").invoke(null);

        assertTrue(result instanceof Function);

        Method[] methods = result.getClass().getDeclaredMethods();

        assertEquals(4, methods.length);

        int numBridges = 0;

        for (Method m : methods) {
            if (m.isBridge()) {
                numBridges++;
            }
        }

        assertEquals(3, numBridges);
    }
}
