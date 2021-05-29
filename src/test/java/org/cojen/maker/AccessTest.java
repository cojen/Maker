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

import java.util.Collections;

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
        doLookup("a.b.c.Dee"); 
        if (withoutEnsureInitialized()) {
            try {
                doLookup("w.x.y.Zee");
            } finally {
                restoreEnsureInitialized();
            }
        }
    }

    private void doLookup(String name) throws Exception {
        // Can call a private method when calling finishLookup.
        for (int i=0; i<2; i++) {
            ClassMaker cm = ClassMaker.begin(name);
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
    public void lookup3() throws Exception {
        doLookup3("a.b.c.Dee");
        if (withoutEnsureInitialized()) {
            try {
                doLookup3("w.x.y.Zee");
            } finally {
                restoreEnsureInitialized();
            }
        }
    }

    private void doLookup3(String name) throws Exception {
        // Can call a private method when calling finishLookup.
        ClassMaker cm = ClassMaker.beginExternal(name);
        MethodMaker mm = cm.addMethod(null, "run").private_().static_();
        MethodHandles.Lookup lookup = cm.finishLookup();
        var clazz = lookup.lookupClass();
        lookup.findStatic(clazz, "run", MethodType.methodType(void.class));
    }

    // Called by SubAccessTest.
    public static boolean withoutEnsureInitialized() {
        if (TheClassMaker.cEnsureInitialized == null) {
            return false;
        } else {
            // Don't use the ensureInitialized method, even if available. It became available
            // in Java 15.
            TheClassMaker.cNoEnsureInitialized = true;
            TheClassMaker.cEnsureInitialized = null;
            return true;
        }
    }

    // Called by SubAccessTest.
    public static void restoreEnsureInitialized() {
        // This forces the method to be looked up again.
        TheClassMaker.cNoEnsureInitialized = false;
    }
}
