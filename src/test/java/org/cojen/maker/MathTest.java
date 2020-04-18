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
import java.util.List;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class MathTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(MathTest.class.getName());
    }

    @Test
    public void cases() throws Exception {
        // Generates a class which reads a set of math operations and saves the results.

        ClassMaker cm = ClassMaker.begin(null, ArrayList.class).public_().sourceFile("MathTest");

        cm.addConstructor().public_().invokeSuperConstructor();

        MethodMaker mm = cm.addMethod(null, "run", Op[].class).public_();

        // Need early declarations for branching back.
        var opVar = mm.var(Op.class).set(null);
        var intVar = mm.var(int.class).set(0);
        var longVar = mm.var(long.class).set(0);
        var floatVar = mm.var(float.class).set(0);
        var doubleVar = mm.var(double.class).set(0);

        List<Op> ops = new ArrayList<>();
        List<Label> opLabels = new ArrayList<>();

        var loopIndex = mm.var(int.class).set(0);
        Label loopStart = mm.label().here();
        Label loopBody = mm.label();
        loopIndex.ifLt(mm.param(0).alength(), loopBody);
        mm.return_();
        loopBody.here();

        Label toSwitch = mm.label();
        mm.goto_(toSwitch);
        Label contLabel = mm.label();

        {
            ops.add(new Op(OP_IADD, 3).init(mm, opLabels, opVar));
            intVar.set(mm.var(int.class).set(1).add(2));
            mm.invoke("add", intVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_ISUB, 1).init(mm, opLabels, opVar));
            intVar.set(mm.var(int.class).set(3).sub(2));
            mm.invoke("add", intVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_IMUL, 6).init(mm, opLabels, opVar));
            intVar.set(mm.var(int.class).set(3).mul(2));
            mm.invoke("add", intVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_IDIV, 5).init(mm, opLabels, opVar));
            intVar.set(mm.var(int.class).set(10).div(2));
            mm.invoke("add", intVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_IREM, 1).init(mm, opLabels, opVar));
            intVar.set(mm.var(int.class).set(10).rem(3));
            mm.invoke("add", intVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_INEG, -3).init(mm, opLabels, opVar));
            intVar.set(mm.var(int.class).set(3).neg());
            mm.invoke("add", intVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_ISHL, 40).init(mm, opLabels, opVar));
            intVar.set(mm.var(int.class).set(10).shl(2));
            mm.invoke("add", intVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_ISHR, 10).init(mm, opLabels, opVar));
            intVar.set(mm.var(int.class).set(40).shr(2));
            mm.invoke("add", intVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_IUSHR, 1).init(mm, opLabels, opVar));
            intVar.set(mm.var(int.class).set(-1).ushr(31));
            mm.invoke("add", intVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_IAND, 1).init(mm, opLabels, opVar));
            intVar.set(mm.var(int.class).set(3).and(1));
            mm.invoke("add", intVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_IOR, 5).init(mm, opLabels, opVar));
            intVar.set(mm.var(int.class).set(1).or(4));
            mm.invoke("add", intVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_IXOR, 0).init(mm, opLabels, opVar));
            intVar.set(mm.var(int.class).set(10).xor(10));
            mm.invoke("add", intVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_LADD, 30L).init(mm, opLabels, opVar));
            longVar.set(mm.var(long.class).set(10).add(20));
            mm.invoke("add", longVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_LSUB, 10L).init(mm, opLabels, opVar));
            longVar.set(mm.var(long.class).set(30).sub(20));
            mm.invoke("add", longVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_LMUL, 600L).init(mm, opLabels, opVar));
            longVar.set(mm.var(long.class).set(30).mul(20));
            mm.invoke("add", longVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_LDIV, 50L).init(mm, opLabels, opVar));
            longVar.set(mm.var(long.class).set(100).div(2));
            mm.invoke("add", longVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_LREM, 1L).init(mm, opLabels, opVar));
            longVar.set(mm.var(long.class).set(10).rem(3));
            mm.invoke("add", longVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_LNEG, -30L).init(mm, opLabels, opVar));
            longVar.set(mm.var(long.class).set(30).neg());
            mm.invoke("add", longVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_LSHL, 40L).init(mm, opLabels, opVar));
            longVar.set(mm.var(long.class).set(10).shl(2));
            mm.invoke("add", longVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_LSHR, 10L).init(mm, opLabels, opVar));
            longVar.set(mm.var(long.class).set(40).shr(2));
            mm.invoke("add", longVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_LUSHR, 1L).init(mm, opLabels, opVar));
            longVar.set(mm.var(long.class).set(-1).ushr(63));
            mm.invoke("add", longVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_LAND, 1L).init(mm, opLabels, opVar));
            longVar.set(mm.var(long.class).set(3).and(1));
            mm.invoke("add", longVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_LOR, 5L).init(mm, opLabels, opVar));
            longVar.set(mm.var(long.class).set(1).or(4));
            mm.invoke("add", longVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_LXOR, 6L).init(mm, opLabels, opVar));
            longVar.set(mm.var(long.class).set(7).xor(1));
            mm.invoke("add", longVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_FADD, 3f).init(mm, opLabels, opVar));
            floatVar.set(mm.var(float.class).set(1).add(2));
            mm.invoke("add", floatVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_FSUB, 1f).init(mm, opLabels, opVar));
            floatVar.set(mm.var(float.class).set(3).sub(2));
            mm.invoke("add", floatVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_FMUL, 6f).init(mm, opLabels, opVar));
            floatVar.set(mm.var(float.class).set(3).mul(2));
            mm.invoke("add", floatVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_FDIV, 5f).init(mm, opLabels, opVar));
            floatVar.set(mm.var(float.class).set(10).div(2));
            mm.invoke("add", floatVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_FREM, 1f).init(mm, opLabels, opVar));
            floatVar.set(mm.var(float.class).set(10).rem(3));
            mm.invoke("add", floatVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_FNEG, -3f).init(mm, opLabels, opVar));
            floatVar.set(mm.var(float.class).set(3).neg());
            mm.invoke("add", floatVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_DADD, 3d).init(mm, opLabels, opVar));
            doubleVar.set(mm.var(double.class).set(1).add(2));
            mm.invoke("add", doubleVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_DSUB, 1d).init(mm, opLabels, opVar));
            doubleVar.set(mm.var(double.class).set(3).sub(2));
            mm.invoke("add", doubleVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_DMUL, 6d).init(mm, opLabels, opVar));
            doubleVar.set(mm.var(double.class).set(3).mul(2));
            mm.invoke("add", doubleVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_DDIV, 5d).init(mm, opLabels, opVar));
            doubleVar.set(mm.var(double.class).set(10).div(2));
            mm.invoke("add", doubleVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_DREM, 1d).init(mm, opLabels, opVar));
            doubleVar.set(mm.var(double.class).set(10).rem(3));
            mm.invoke("add", doubleVar);
            mm.goto_(contLabel);
        }

        {
            ops.add(new Op(OP_DNEG, -3d).init(mm, opLabels, opVar));
            doubleVar.set(mm.var(double.class).set(3).neg());
            mm.invoke("add", doubleVar);
            mm.goto_(contLabel);
        }

        contLabel.here();
        loopIndex.inc(1);
        mm.goto_(loopStart);

        toSwitch.here();

        int[] switchCases = new int[ops.size()];
        Label[] switchLabels = new Label[opLabels.size()];
        for (int i=0; i<switchCases.length; i++) {
            switchCases[i] = ops.get(i).op;
            switchLabels[i] = opLabels.get(i);
        }

        Label switchDefault = mm.label();

        opVar.set(mm.param(0).aget(loopIndex));
        opVar.field("op").switch_(switchDefault, switchCases, switchLabels);

        switchDefault.here();
        mm.new_(AssertionError.class).throw_();

        var clazz = cm.finish();
        List<?> runner = (List) clazz.getConstructor().newInstance();

        Op[] opArray = ops.toArray(new Op[ops.size()]);
        clazz.getMethod("run", Op[].class).invoke(runner, (Object) opArray);

        for (int i=0; i<opArray.length; i++) {
            assertEquals("case: " + i, opArray[i].expect, runner.get(i));
        }
    }

    static final int
        OP_IADD = 1, OP_ISUB = 2, OP_IMUL = 3, OP_IDIV = 4, OP_IREM = 5, OP_INEG = 6,
        OP_ISHL = 7, OP_ISHR = 8, OP_IUSHR = 9, OP_IAND = 10, OP_IOR = 11, OP_IXOR = 12;

    static final int
        OP_LADD = 21, OP_LSUB = 22, OP_LMUL = 23, OP_LDIV = 24, OP_LREM = 25, OP_LNEG = 26,
        OP_LSHL = 27, OP_LSHR = 28, OP_LUSHR = 29, OP_LAND = 30, OP_LOR = 31, OP_LXOR = 32;

    static final int
        OP_FADD = 41, OP_FSUB = 42, OP_FMUL = 43, OP_FDIV = 44, OP_FREM = 45, OP_FNEG = 46;

    static final int
        OP_DADD = 51, OP_DSUB = 52, OP_DMUL = 53, OP_DDIV = 54, OP_DREM = 55, OP_DNEG = 56;

    public static class Op {
        public final int op;
        final Object expect;

        Op(int op, Object expect) {
            this.op = op;
            this.expect = expect;
        }

        Op init(MethodMaker mm, List<Label> opLabels, Variable opVar) {
            opLabels.add(mm.label().here());
            Label ok = mm.label();
            opVar.field("op").ifEq(op, ok);
            mm.lineNum(100 + opLabels.size() - 1);
            mm.new_(AssertionError.class).throw_();
            ok.here();
            return this;
        }
    }
}
