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

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class BranchTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(BranchTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        var assertVar = mm.var(Assert.class);

        {
            var v1 = mm.var(boolean.class).set(true);
            Label l1 = mm.label();
            v1.ifTrue(l1);
            assertVar.invoke("fail");
            l1.here();
            v1 = v1.not();
            Label l2 = mm.label();
            v1.ifFalse(l2);
            assertVar.invoke("fail");
            l2.here();
        }

        {
            var v1 = mm.var(Boolean.class).set(true);
            Label l1 = mm.label();
            v1.ifTrue(l1);
            assertVar.invoke("fail");
            l1.here();
        }

        {
            var v1 = mm.var(String.class).set("hello");
            Label l1 = mm.label();
            v1.ifNe(null, l1);
            assertVar.invoke("fail");
            l1.here();
            v1.set(null);
            Label l2 = mm.label();
            v1.ifEq(null, l2);
            l2.here();
            var v2 = mm.var(String.class).set(null);
            Label l3 = mm.label();
            v1.ifEq(v2, l3);
            assertVar.invoke("fail");
            l3.here();
            v1.set("hello");
            v2.set("world");
            Label l4 = mm.label();
            v1.ifNe(v2, l4);
            assertVar.invoke("fail");
            l4.here();
        }

        {
            var v1 = mm.var(int.class).set(1);
            Label l1 = mm.label();
            v1.ifLt(1.1, l1);
            assertVar.invoke("fail");
            l1.here();
            Label l2 = mm.label();
            v1.ifGt(0, l2);
            assertVar.invoke("fail");
            l2.here();
            Label l3 = mm.label();
            v1.ifGe(0, l3);
            assertVar.invoke("fail");
            l3.here();
            Label l4 = mm.label();
            var v2 = mm.var(int.class).set(0);
            v2.ifLt(v1, l4);
            assertVar.invoke("fail");
            l4.here();
            Label l5 = mm.label();
            v2.ifLe(v1, l5);
            assertVar.invoke("fail");
            l5.here();
        }

        {
            var v1 = mm.var(long.class).set(1);
            Label l1 = mm.label();
            v1.ifLt(2, l1);
            assertVar.invoke("fail");
            l1.here();
            Label l2 = mm.label();
            v1.ifGt(0, l2);
            assertVar.invoke("fail");
            l2.here();
            Label l3 = mm.label();
            v1.ifGe(0, l3);
            assertVar.invoke("fail");
            l3.here();
            Label l4 = mm.label();
            var v2 = mm.var(long.class).set(0);
            v2.ifLt(v1, l4);
            assertVar.invoke("fail");
            l4.here();
            Label l5 = mm.label();
            v2.ifLe(v1, l5);
            assertVar.invoke("fail");
            l5.here();
        }

        {
            var v1 = mm.var(float.class).set(1);
            Label l1 = mm.label();
            v1.ifLt(1.1f, l1);
            assertVar.invoke("fail");
            l1.here();
            Label l2 = mm.label();
            v1.ifLe(1.0f, l2);
            assertVar.invoke("fail");
            l2.here();
            Label l3 = mm.label();
            v1.ifGe(v1, l3);
            assertVar.invoke("fail");
            l3.here();
            Label l4 = mm.label();
            v1.ifGt(0.9f, l4);
            assertVar.invoke("fail");
            l4.here();

            var v2 = mm.var(float.class).set(0.0f/0.0f);

            Label l5 = mm.label();
            v1.ifGt(v2, l5);
            Label l6 = mm.label();
            mm.goto_(l6);
            l5.here();
            assertVar.invoke("fail");
            l6.here();

            Label l7 = mm.label();
            v1.ifLe(v2, l7);
            Label l8 = mm.label();
            mm.goto_(l8);
            l7.here();
            assertVar.invoke("fail");
            l8.here();
        }

        {
            var v1 = mm.var(double.class).set(1);
            Label l1 = mm.label();
            v1.ifLt(1.1, l1);
            assertVar.invoke("fail");
            l1.here();
            Label l2 = mm.label();
            v1.ifLe(1.0, l2);
            assertVar.invoke("fail");
            l2.here();
            Label l3 = mm.label();
            v1.ifGe(v1, l3);
            assertVar.invoke("fail");
            l3.here();
            Label l4 = mm.label();
            v1.ifGt(0.9, l4);
            assertVar.invoke("fail");
            l4.here();

            var v2 = mm.var(double.class).set(0.0/0.0);

            Label l5 = mm.label();
            v1.ifGt(v2, l5);
            Label l6 = mm.label();
            mm.goto_(l6);
            l5.here();
            assertVar.invoke("fail");
            l6.here();

            Label l7 = mm.label();
            v1.ifLe(v2, l7);
            Label l8 = mm.label();
            mm.goto_(l8);
            l7.here();
            assertVar.invoke("fail");
            l8.here();
        }

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void someSwitches() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        var assertVar = mm.var(Assert.class);

        var v1 = mm.var(int.class).set(5);

        {
            Label def = mm.label();
            v1.switch_(def, new int[0]);
            def.here();
        }

        // tableswitch
        {
            Label def = mm.label();
            int[] cases = {0,1,2,3,4,5,7,6};
            Label[] labels = new Label[cases.length];
            for (int i=0; i<labels.length; i++) {
                labels[i] = mm.label();
            }
            v1.switch_(def, cases, labels);
            for (int i=0; i<labels.length; i++) {
                labels[i].here();
                if (i != 5) {
                    assertVar.invoke("fail");
                }
                mm.goto_(def);
            }

            def.here();
        }

        // lookupswitch
        {
            Label def = mm.label();
            int[] cases = {10,5,0};
            Label[] labels = new Label[cases.length];
            for (int i=0; i<labels.length; i++) {
                labels[i] = mm.label();
            }
            v1.switch_(def, cases, labels);
            for (int i=0; i<labels.length; i++) {
                labels[i].here();
                if (i != 1) {
                    assertVar.invoke("fail");
                }
                mm.goto_(def);
            }

            def.here();
        }

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void multiLabel() throws Exception {
        // Test use of multiple labels at same address.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        {
            var v1 = mm.var(int.class).set(0);
            Label L1 = mm.label().here();
            Label L2 = mm.label().here();
            v1.inc(1);
            v1.ifEq(1, L1);
            v1.ifEq(2, L2);
            mm.var(Assert.class).invoke("assertEquals", 3, v1);
        }

        {
            var v1 = mm.var(int.class).set(0);
            Label L1 = mm.label().here();
            Label L2 = mm.label();
            Label L3 = mm.label();
            v1.inc(1);
            v1.ifEq(10, L2);
            v1.ifEq(20, L3);
            mm.goto_(L1);
            L2.here();
            L3.here();
            mm.var(Assert.class).invoke("assertEquals", 10, v1);
        }

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }
}
