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
import java.lang.invoke.MethodType;

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
        ClassMaker cm = ClassMaker.begin(null, MethodHandles.lookup()).public_();
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

    @Test
    public void noMethods() throws Exception {
        // Miscellaneous test.
        Class<?> clazz = ClassMaker.begin().public_().finish();
        try {
            clazz.getConstructor();
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
        }
    }

    @Test
    public void lookup() throws Exception {
        // Can call a private method when calling finishLookup.
        for (int i=0; i<2; i++) {
            ClassMaker cm = ClassMaker.begin("a.b.c.Dee");
            MethodMaker mm = cm.addMethod(null, "run").private_().static_();
            MethodHandles.Lookup lookup = cm.finishLookup();
            var clazz = lookup.lookupClass();
            lookup.findStatic(clazz, "run", MethodType.methodType(void.class));
        }
    }

    @Test
    public void lookup2() throws Exception {
        doLookup2(MethodHandles.lookup());
    }

    // Called by SubAccessTest.
    public static void doLookup2(MethodHandles.Lookup lookup) throws Exception {
        // Can call a private method when calling finishLookup.
        ClassMaker cm = ClassMaker.begin(lookup.lookupClass().getName(), lookup);
        MethodMaker mm = cm.addMethod(null, "run").private_().static_();
        lookup = cm.finishLookup();
        var clazz = lookup.lookupClass();
        lookup.findStatic(clazz, "run", MethodType.methodType(void.class));
    }

    @Test
    public void lookupExternal() throws Exception {
        // Can call a private method when calling finishLookup.
        ClassMaker cm = ClassMaker.beginExternal("a.b.c.Dee");
        MethodMaker mm = cm.addMethod(null, "run").private_().static_();
        MethodHandles.Lookup lookup = cm.finishLookup();
        var clazz = lookup.lookupClass();
        lookup.findStatic(clazz, "run", MethodType.methodType(void.class));
    }

    @Test
    public void denied() throws Exception {
        // Detect access to the hidden lookup class.

        ClassMaker cm = ClassMaker.begin();
        MethodMaker mm = cm.addMethod(null, "run").private_().static_();
        Class<?> clazz = cm.finishLookup().lookupClass();

        String packageName = clazz.getPackageName();
        ClassLoader loader = clazz.getClassLoader();
        Class<?> lookupClass = null;

        for (int i=0; i<10; i++) {
            try {
                lookupClass = loader.loadClass(packageName + ".lookup-" + i);
                break;
            } catch (ClassNotFoundException e) {
            }
        }

        assertNotNull(lookupClass);

        Object[] attempts = {null, loader, loader.getParent()};

        for (Object attempt : attempts) {
            try {
                lookupClass.getMethod("lookup", Object.class).invoke(null, attempt);
                fail();
            } catch (Exception e) {
                assertTrue(e.getCause() instanceof IllegalAccessError);
            }
        }
    }

    @Test
    public void accessPackage() throws Throwable {
        // Hidden class defined without a lookup can access the same package.

        Class<?> c1;
        {
            ClassMaker cm = ClassMaker.begin("foo.First");
            cm.addMethod(int.class, "test").static_().return_(123);
            c1 = cm.finish();
        }

        ClassMaker cm = ClassMaker.begin("foo.Second");
        MethodMaker mm = cm.addMethod(int.class, "test").static_().public_();
        mm.return_(mm.var(c1).invoke("test"));
        var lookup = cm.finishHidden();
        Class<?> c2 = lookup.lookupClass();
        var mh = lookup.findStatic(c2, "test", MethodType.methodType(int.class));

        assertEquals(123, mh.invoke());
    }
}
