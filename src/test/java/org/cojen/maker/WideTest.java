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
public class WideTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(WideTest.class.getName());
    }

    @Test
    public void wideJump() throws Exception {
        // Test goto_w opcode.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        var v1 = mm.var(int.class).set(0);

        Label L1 = mm.label().here();
        Label L2 = mm.label();

        v1.ifEq(0, L2);

        for (int i=0; i<10_000; i++) {
            v1.inc(1000);
        }

        //mm.var(System.class).field("out").invoke("println", v1);

        L2.here();
        v1.inc(1);
        v1.ifLt(20_000_000, L1);

        mm.var(Assert.class).invoke("assertEquals", 20_000_003, v1);

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }
}
