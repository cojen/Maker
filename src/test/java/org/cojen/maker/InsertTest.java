/*
 *  Copyright 2024 Cojen.org
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

import java.lang.invoke.MethodHandles;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class InsertTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(InsertTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(String.class, "test", String.class).public_().static_();
        var strVar = mm.param(0);

        var L1 = mm.label();

        try {
            L1.insert(() -> {});
            fail();
        } catch (IllegalStateException e) {
            // Unpositioned.
        }

        L1.here();
        strVar.set(mm.concat(strVar, "a"));
        var L2 = mm.label().here();
        // . L1 a L2

        L2.insert(() -> {
            strVar.set(mm.concat(strVar, "b"));
        });
        // . L1 a L2 b Lx

        Label L3 = L1.insert(() -> {
            strVar.set(mm.concat(strVar, "c"));
        });
        // . L1 c L3 a L2 b Lx

        L1.insert(() -> {
            strVar.set(mm.concat(strVar, "d"));
        });
        // . L1 d Lx c L3 a L2 b Lx

        L3.insert(() -> {
            strVar.set(mm.concat(strVar, "e"));
        });
        // . L1 d Lx c L3 e Lx a L2 b Lx

        mm.return_(strVar);

        Object result = cm.finish().getMethod("test", String.class).invoke(null, ".");
        assertEquals(".dceab", result);
    }

    @Test
    public void falseFinish() throws Throwable {
        // Disallow finishing the class within the inserted body.

        MethodMaker mm = MethodMaker.begin
            (MethodHandles.lookup(), String.class, "test", String.class).public_();
        var strVar = mm.param(0);

        var L1 = mm.label().here();
        strVar.set(mm.concat(strVar, "a"));
        var L2 = mm.label().here();

        try {
            L2.insert(() -> {
                mm.return_(strVar);
                mm.finish();
            });
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            L1.insert(() -> {
                mm.finish();
            });
            fail();
        } catch (IllegalStateException e) {
        }

        var result = (String) mm.finish().invokeExact("x");
        assertEquals("xa", result);
    }
}
