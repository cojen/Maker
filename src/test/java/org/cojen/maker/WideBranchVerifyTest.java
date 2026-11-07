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

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Regression test: A method which is large enough to need a wide (more than 32767 byte) branch
 * can produce bytecode which fails verification.
 *
 * <p>Cause: when a branch turns out to need the wide form, TheMethodMaker.doFinish method
 * rebuilds the code and runs flow analysis a second time, but it passes the original "variable
 * usage" BitList to the second pass. The first pass already modified that BitList in place, so
 * the second pass starts out believing that variables assigned later along the main path are
 * already assigned. The StackMapTable frames then list those variables as live at branch
 * targets and exception handlers where the verifier has them as unassigned.
 *
 * @author Claude
 * @author Brian S. O'Neill
 */
public class WideBranchVerifyTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(WideBranchVerifyTest.class.getName());
    }

    /** Number of padding statements which is guaranteed to force a wide branch. */
    private static final int LARGE = 4000;
 
    /** Number of padding statements which is far too small to force a wide branch. */
    private static final int SMALL = 10;

    interface Builder {
        void build(MethodMaker mm, int padding);
    }
 
    /** Emits "n" statements of the form acc = acc * 31 + i. Each is about 9 bytes of code. */
    private static void pad(Variable acc, int n) {
        for (int i = 0; i < n; i++) {
            acc.set(acc.mul(31).add(i));
        }
    }

    private static void check(Builder builder, int padding) throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(int.class, "run", int.class).public_().static_();
        builder.build(mm, padding);
        // Invoking the method forces the class to be linked, and so verified.
        try {
            cm.finish().getMethod("run", int.class).invoke(null, 3);
        } catch (VerifyError e) {
            fail("verify error");
        }
    }

    @Test
    public void case1_small() throws Exception {
        check(this::case1, SMALL);
    }

    @Test
    public void case1_large() throws Exception {
        check(this::case1, LARGE);
    }

    @Test
    public void case2_small() throws Exception {
        check(this::case2, SMALL);
    }

    @Test
    public void case2_large() throws Exception {
        check(this::case2, LARGE);
    }
    @Test
    public void case3_small() throws Exception {
        check(this::case3, SMALL);
    }

    @Test
    public void case3_large() throws Exception {
        check(this::case3, LARGE);
    }

    // ---------------------------------------------------------------------------------------
    // Case 1: a conditional forward branch over a large block, where a variable is declared but
    // not yet assigned. Expected failure on affected builds:
    //
    //   VerifyError: Inconsistent stackmap frames at branch target ...
    // ---------------------------------------------------------------------------------------
    private void case1(MethodMaker mm, int padding) {
        var p = mm.param(0);
        var acc = mm.var(int.class).set(p);
        var v = mm.var(int.class); // declared, but not assigned yet
 
        Label elseL = mm.label(), endL = mm.label();
        p.add(1).ifGe(acc.sub(1), elseL); // becomes a wide branch when the padding is large
        pad(acc, padding);
        v.set(acc);
        mm.goto_(endL);
        elseL.here();
        v.set(p.add(5));
        endL.here();
 
        mm.return_(v.add(acc));
    }
 
    // ---------------------------------------------------------------------------------------
    // Case 2: a wide branch inside a try block, with an exception handler after it. Expected
    // failure on affected builds:
    //
    //   VerifyError: Stack map does not match the one at exception handler ...
    // ---------------------------------------------------------------------------------------
    private void case2(MethodMaker mm, int padding) {
        var p = mm.param(0);
        var acc = mm.var(int.class).set(p);
        var v = mm.var(int.class); // declared, but not assigned yet
 
        Label start = mm.label().here();
        Label elseL = mm.label(), endL = mm.label();
        p.sub(acc).ifGe(p, elseL);
        mm.goto_(endL);
        elseL.here();
        pad(acc, padding);
        endL.here();
        v.set(p.mul(acc.add(-2)));
        mm.catch_(start, ArithmeticException.class, ex -> v.set(p.sub(3).mul(p.add(1))));
 
        mm.return_(v.add(acc));
    }
 
    // ---------------------------------------------------------------------------------------
    // Case 3: a loop containing a switch, where the cases are large and a variable is only
    // assigned inside the cases. Expected failure on affected builds:
    //
    //   VerifyError: Instruction type does not match stack map
    // ---------------------------------------------------------------------------------------
    private void case3(MethodMaker mm, int padding) {
        var p = mm.param(0);
        var acc = mm.var(int.class).set(p);
        var v = mm.var(int.class); // declared, assigned in every case
        var count = mm.var(int.class).set(0);
 
        Label top = mm.label().here(), cont = mm.label(), end = mm.label();
        Label def = mm.label(), swEnd = mm.label();
        Label l0 = mm.label(), l1 = mm.label(), l2 = mm.label();
 
        acc.add(-1).mul(acc.sub(1)).rem(5).switch_(def, new int[] {-1048576, -1, 1}, l0, l1, l2);
 
        l0.here();
        pad(acc, padding * 2 / 3);
        mm.var(int.class).set(0).ifEq(0, end); // conditional break out of the loop
        v.set(1);
        mm.goto_(swEnd);
 
        l1.here();
        v.set(2);
        mm.goto_(swEnd);
 
        l2.here();
        v.set(3);
        // Falls through into the default case.
 
        def.here();
        pad(acc, padding / 3);
        v.set(4);
 
        swEnd.here();
        cont.here();
        count.inc(1);
        count.ifLt(2, top);
        end.here();
 
        mm.return_(acc);
    }
}
