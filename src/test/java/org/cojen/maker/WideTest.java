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

import java.util.ArrayList;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class WideTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(WideTest.class.getName());
    }

    @Test
    public void wideJump() throws Exception {
        wideJump(false);
    }

    @Test
    public void wideJump2() throws Exception {
        wideJump(true);
    }

    private void wideJump(boolean alt) throws Exception {
        // Test the goto_w opcode.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        var v1 = mm.var(int.class).set(0);

        Label L1 = mm.label().here();
        Label L2 = mm.label();

        v1.ifEq(0, L2);

        for (int i=0; i<10_000; i++) {
            v1.inc(1000);
        }

        L2.here();
        v1.inc(1);
        if (!alt) {
            v1.ifLt(20_000_000, L1);
        } else {
            Label L3 = mm.label();
            v1.ifGe(20_000_000, L3);
            mm.goto_(L1);
            L3.here();
        }

        mm.var(Assert.class).invoke("assertEquals", 20_000_003, v1);

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void wideConstant() throws Exception {
        // Test the ldc_w opcode.

        ClassMaker cm = ClassMaker.begin(null, ArrayList.class).public_();
        cm.addConstructor().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_();

        for (int i=0; i<1000; i++) {
            mm.invoke("add", 100_000 + i);
        }

        var clazz = cm.finish();
        var obj = (ArrayList) clazz.getConstructor().newInstance();
        clazz.getMethod("run").invoke(obj);

        for (int i=0; i<1000; i++) {
            assertEquals(100_000 + i, obj.get(i));
        }
    }

    @Test
    public void wideVar() throws Exception {
        // Test the wide load and store opcodes.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        Variable prev = null;
        for (int i=0; i<300; i++) {
            Variable var = mm.var(int.class);
            if (i == 0) {
                var.set(1);
            } else {
                prev.inc(1);
                var.set(prev);
            }
            prev = var;
        }

        mm.var(Assert.class).invoke("assertEquals", 300, prev);

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }
}
