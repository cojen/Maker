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

import java.lang.invoke.MethodHandles;

import java.lang.reflect.InvocationTargetException;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class AccessTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(AccessTest.class.getName());
    }

    @Test
    public void noPackageAccess() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        mm.var(AccessTest.class).invoke("foo", 10);
        Class<?> clazz = cm.finish();
        try {
            clazz.getMethod("run").invoke(null);
            fail();
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof IllegalAccessError);
        }
    }

    @Test
    public void hasPackageAccess() throws Exception {
        ClassMaker cm = ClassMaker.begin(null, null, MethodHandles.lookup()).public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        var result = mm.var(AccessTest.class).invoke("foo", 10);
        mm.var(Assert.class).invoke("assertEquals", 11, result);
        Class<?> clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    // Must be package-private.
    static int foo(int a) {
        return a + 1;
    }
}
