/*
 *  Copyright 2021 Cojen.org
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

package org.cojen.maker.sub;

import java.lang.invoke.MethodHandles;

import org.cojen.maker.ClassMaker;
import org.cojen.maker.MethodMaker;

import org.cojen.maker.AccessTest;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Test access in a package different than the primary one.
 *
 * @author Brian S O'Neill
 */
public class SubAccessTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(SubAccessTest.class.getName());
    }

    @Test
    public void hasPackageAccess() throws Exception {
        ClassMaker cm = ClassMaker.begin(null, MethodHandles.lookup()).public_();
        MethodMaker mm = cm.addMethod(null, "run").public_().static_();
        var result = mm.var(SubAccessTest.class).invoke("foo", 10);
        mm.var(Assert.class).invoke("assertEquals", 11, result);
        Class<?> clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    // Must be package-private.
    static int foo(int a) {
        return a + 1;
    }

    @Test
    public void lookup2() throws Exception {
        synchronized (AccessTest.class) {
            AccessTest.doLookup2(MethodHandles.lookup());
            if (AccessTest.withoutEnsureInitialized()) {
                try {
                    AccessTest.doLookup2(MethodHandles.lookup());
                } finally {
                    AccessTest.restoreEnsureInitialized();
                }
            }
        }
    }
}
