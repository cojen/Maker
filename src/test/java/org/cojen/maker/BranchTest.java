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
            Label l6 = mm.label().goto_();
            l5.here();
            assertVar.invoke("fail");
            l6.here();

            Label l7 = mm.label();
            v1.ifLe(v2, l7);
            Label l8 = mm.label().goto_();
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
            Label l6 = mm.label().goto_();
            l5.here();
            assertVar.invoke("fail");
            l6.here();

            Label l7 = mm.label();
            v1.ifLe(v2, l7);
            Label l8 = mm.label().goto_();
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

        // tiny
        {
            Label def = mm.label();
            Label cont = mm.label();
            var reached = mm.var(boolean.class).set(false);
            int[] cases = {5};
            Label[] labels = {mm.label()};
            v1.cast(Byte.class).switch_(def, cases, labels);
            labels[0].here();
            reached.set(true);
            mm.goto_(cont);
            def.here();
            assertVar.invoke("fail");
            cont.here();
            assertVar.invoke("assertTrue", reached);
        }

        // tiny zero
        {
            Label def = mm.label();
            int[] cases = {0};
            Label[] labels = {mm.label()};
            v1.cast(Byte.class).switch_(def, cases, labels);
            labels[0].here();
            assertVar.invoke("fail");
            def.here();
        }

        // illegal
        try {
            Label def = mm.label();
            int[] cases = {0};
            Label[] labels = {mm.label()};
            mm.var(Object.class).switch_(def, cases, labels);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().startsWith("Automatic conversion"));
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

    @Test
    public void invalidate() throws Exception {
        // Test that variables are invalidated to prevent broken stack map frames.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        var v1 = mm.var(int.class).set(1);
        var v2 = mm.var(int.class).set(2);

        for (int i=0; i<2; i++) {
            Label l1 = mm.label();
            v1.ifEq(0, l1);

            var v3 = mm.var(int.class).set(v2.add(1));
            var v4 = mm.var(int.class).set(v2.add(1));
            v2.set(v3);

            l1.here();

            var v5 = mm.var(int.class).set(v2.add(1));
            var v6 = mm.var(int.class).set(v2.add(1));
            v2.set(v5);
        }

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void ctor() throws Exception {
        // Test branch that acts upon "this" in a constructor.

        ClassMaker cm = ClassMaker.begin().public_();
        cm.addField(int.class, "foo");

        MethodMaker mm = cm.addConstructor(int.class).public_();
        mm.invokeSuperConstructor();
        Label cont = mm.label();
        mm.param(0).ifNe(0, cont);
        mm.field("foo").set(mm.param(0));
        cont.here();

        var clazz = cm.finish();
        clazz.getConstructor(int.class).newInstance(10);
    }

    @Test
    public void ctorTernary() throws Exception {
        // Emulates this pattern in a constructor: super(a ? 1 : 2)

        // A branch before calling the super constructor should create a proper stack map table
        // entry which shows "this" as an uninitialized type.

        ClassMaker cm = ClassMaker.begin(null).extend(java.util.Vector.class).public_();
        cm.addField(int.class, "init").public_().final_();

        MethodMaker mm = cm.addConstructor(boolean.class).public_();

        var sizeVar = mm.var(int.class);
        Label L1 = mm.label();
        mm.param(0).ifFalse(L1);
        Label L2 = mm.label();
        sizeVar.set(1);
        mm.goto_(L2);
        L1.here();
        sizeVar.set(2);
        L2.here();

        mm.invokeSuperConstructor(sizeVar);

        var initVar = mm.field("init");
        Label L3 = mm.label();
        mm.param(0).ifFalse(L3);
        Label L4 = mm.label();
        initVar.set(1);
        mm.goto_(L4);
        L3.here();
        initVar.set(2);
        L4.here();

        var clazz = cm.finish();
        var ctor = clazz.getConstructor(boolean.class);
        var capacity = clazz.getMethod("capacity");
        var init = clazz.getField("init");

        {
            var instance = ctor.newInstance(true);
            assertEquals(1, capacity.invoke(instance));
            assertEquals(1, init.get(instance));
        }

        {
            var instance = ctor.newInstance(false);
            assertEquals(2, capacity.invoke(instance));
            assertEquals(2, init.get(instance));
        }
    }

    @Test
    public void sillyConditional() throws Exception {
        // Test that unnecessary conditional statements aren't "optimized" improperly.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run", int.class).static_().public_();

        Label end = mm.label();
        mm.param(0).ifEq(0, end);

        var v1 = mm.param(0).add(1);
        var v2 = v1.add(1);

        v1.ifEq(0, end);
        v2.ifEq(0, end); // unnecessary

        end.here();

        var clazz = cm.finish();
        clazz.getMethod("run", int.class).invoke(null, 10);
    }

    @Test
    public void mergeLocals() throws Exception {
        // Test that local variable conflicts at branch targets are resolved.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run", int.class).static_().public_();

        Label end = mm.label();
        mm.param(0).ifEq(0, end);

        var v1 = mm.param(0).add(1);
        var v2 = v1.add(1);

        Label l2 = mm.label();
        v1.ifEq(0, l2);
        v2.ifEq(0, end);
        l2.here();

        end.here();

        var clazz = cm.finish();
        clazz.getMethod("run", int.class).invoke(null, 10);
    }

    @Test
    public void chop() throws Exception {
        // Define a branch pattern which creates a chop frame.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(int.class, "run", int.class).static_().public_();

        Label end = mm.label();
        mm.param(0).ifEq(0, end);

        var result = mm.var(int.class);
        Label L1 = mm.label();
        mm.param(0).ifEq(1, L1);
        result.set(mm.param(0).sub(1));
        Label L2 = mm.label().goto_();
        L1.here();
        result.set(mm.param(0).add(1));
        L2.here();
        mm.return_(result);

        end.here();
        mm.return_(mm.param(0));

        var clazz = cm.finish();
        var method =  clazz.getMethod("run", int.class);
        assertEquals(0, method.invoke(null, 0));
        assertEquals(2, method.invoke(null, 1));
        assertEquals(1, method.invoke(null, 2));
    }

    @Test
    public void chop2() throws Exception {
        // Define a branch pattern which creates a chop frame, resulting in nothing.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(int.class, "run").static_().public_();

        Label end = mm.label();
        mm.var(System.class).field("out").ifEq(null, end);

        var result = mm.var(int.class);
        Label L1 = mm.label();
        mm.var(System.class).field("err").ifEq(null, L1);
        result.set(1);
        Label L2 = mm.label().goto_();
        L1.here();
        result.set(2);
        L2.here();
        mm.return_(result);

        end.here();
        mm.return_(0);

        var clazz = cm.finish();
        var method =  clazz.getMethod("run");
        assertEquals(1, method.invoke(null));
    }

    @Test
    public void overflow() throws Exception {
        // Test the code that prevents stack overflow by limiting recursion.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        var v1 = mm.var(int.class).set(0);

        for (int i=0; i<5000; i++) {
            Label lab = mm.label();
            v1.ifEq(0, lab);
            mm.nop();
            lab.here();
        }

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void altBranch() throws Exception {
        // Test that flow analysis for StackMapTable can cope with branches that return.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(long.class, "run").static_().public_();

        var v1 = mm.var(int.class);
        v1.set(1);

        Label L1 = mm.label();
        v1.ifEq(0, L1);

        var v2 = mm.var(long.class).set(v1.add(v1));
        Label L2 = mm.label();
        v1.ifEq(0, L2);
        mm.nop();
        L2.here();
        mm.return_(v2.add(v2));

        L1.here();
        var v3 = mm.var(long.class).set(v1.sub(v1));
        mm.return_(v3.sub(v3));

        var clazz = cm.finish();
        Object result = clazz.getMethod("run").invoke(null);
        assertEquals(4L, result);
    }

    @Test
    public void booleanResult() throws Exception {
        // Test the convenience methods which store 'if' results to boolean variables.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(void.class, "run").static_().public_();
        var assertVar = mm.var(Assert.class);

        var v1 = mm.var(int.class).set(10);
        var v11 = mm.var(int.class).set(11);

        mm.var(Assert.class).invoke("assertEquals", true, v1.eq(10));
        mm.var(Assert.class).invoke("assertEquals", false, v1.eq(11));
        mm.var(Assert.class).invoke("assertEquals", true, v1.ne(v11));
        mm.var(Assert.class).invoke("assertEquals", false, v1.ne(10));
        mm.var(Assert.class).invoke("assertEquals", true, v1.lt(11));
        mm.var(Assert.class).invoke("assertEquals", false, v1.lt(10));
        mm.var(Assert.class).invoke("assertEquals", false, v1.ge(11));
        mm.var(Assert.class).invoke("assertEquals", true, v1.ge(10));
        mm.var(Assert.class).invoke("assertEquals", false, v1.gt(11));
        mm.var(Assert.class).invoke("assertEquals", false, v1.gt(10));
        mm.var(Assert.class).invoke("assertEquals", true, v1.le(v11));
        mm.var(Assert.class).invoke("assertEquals", true, v1.le(10));

        var v2 = mm.var(String.class).set(null);
        var v3 = mm.var(String.class).set("hello");

        mm.var(Assert.class).invoke("assertEquals", true, v2.eq(null));
        mm.var(Assert.class).invoke("assertEquals", false, v2.ne(null));
        mm.var(Assert.class).invoke("assertEquals", false, v3.eq(null));
        mm.var(Assert.class).invoke("assertEquals", true, v3.ne(null));

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void booleanConstants() throws Exception {
        // Test comparisons to boolean constants.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();
        var assertVar = mm.var(Assert.class);

        {
            var v1 = mm.var(boolean.class).set(false);
            Label l1 = mm.label();
            v1.ifEq(false, l1);
            assertVar.invoke("fail");
            l1.here();
            Label l2 = mm.label();
            v1.ifEq(true, l2);
            Label cont = mm.label().goto_();
            l2.here();
            assertVar.invoke("fail");
            cont.here();
        }

        {
            var v1 = mm.var(Boolean.class).set(true);
            Label l1 = mm.label();
            v1.ifEq(true, l1);
            assertVar.invoke("fail");
            l1.here();
            Label l2 = mm.label();
            v1.ifEq(false, l2);
            Label cont = mm.label().goto_();
            l2.here();
            assertVar.invoke("fail");
            cont.here();
        }

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void booleanRelational() throws Exception {
        // Test relational comparisons against booleans. The Boolean class is Comparable, and
        // so an expression like "false < true" should be allowed.

        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();
        var assertVar = mm.var(Assert.class);

        var v1 = mm.var(boolean.class).set(false);
        var v2 = mm.var(boolean.class).set(true);

        {
            Label L = mm.label();
            v1.ifLt(v2, L);
            assertVar.invoke("fail");
            L.here();
        }

        {
            Label L = mm.label();
            v2.ifGe(v1, L);
            assertVar.invoke("fail");
            L.here();
        }

        {
            Label L = mm.label();
            v1.ifLe(true, L);
            assertVar.invoke("fail");
            L.here();
        }

        {
            Label L = mm.label();
            v2.ifGe(false, L);
            assertVar.invoke("fail");
            L.here();
        }

        mm.var(Assert.class).invoke("assertEquals", true, v1.lt(v2));
        mm.var(Assert.class).invoke("assertEquals", true, v2.ge(v1));
        mm.var(Assert.class).invoke("assertEquals", true, v1.le(true));
        mm.var(Assert.class).invoke("assertEquals", true, v2.ge(false));

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void rebranch() throws Exception {
        // Test conversion of a conditional store into a boolean variable back into a direct
        // branch operation.

        for (int variant=0; variant<=6; variant++) {
            ClassMaker cm = ClassMaker.begin().public_();
            rebranch(cm, 0, variant);
            var method = cm.finish().getMethod("run", Integer.class);
            assertEquals("true", method.invoke(null, 5));
            assertEquals("false", method.invoke(null, 10));
            assertEquals("false", method.invoke(null, 11));
        }

        for (int variant=0; variant<=6; variant++) {
            ClassMaker cm = ClassMaker.begin().public_();
            rebranch(cm, 1, variant);
            var method = cm.finish().getMethod("run", Integer.class);
            assertEquals("true", method.invoke(null, (Integer) null));
            assertEquals("false", method.invoke(null, 5));
        }

        // Verify that the code is the same in all cases.
        for (int test=0; test<=1; test++) {
            byte[] last = null;
            for (int variant=0; variant<=6; variant++) {
                ClassMaker cm = ClassMaker.beginExternal("Test").public_();
                rebranch(cm, test, variant);
                byte[] code = cm.finishBytes();
                if (last != null) {
                    assertArrayEquals(last, code);
                }
                last = code;
            }
        }
    }

    /**
     * @param test 0: "p < 10",  1: "p == null"
     * @param variant conditional code variant
     */
    private void rebranch(ClassMaker cm, int test, int variant) throws Exception {
        MethodMaker mm = cm.addMethod(String.class, "run", Integer.class).static_().public_();

        var isTrue = mm.label();
        var isFalse = mm.label();

        if (variant == 0) {
            // The desired outcome in all cases.
            if (test == 0) {
                mm.param(0).ifLt(10, isTrue);
            } else {
                mm.param(0).ifEq(null, isTrue);
            }
            mm.goto_(isFalse);
        } else {
            Variable x;
            if (test == 0) {
                x = mm.param(0).lt(10);
            } else {
                x = mm.param(0).eq(null);
            }

            switch (variant) {
            case 1:
                x.ifEq(true, isTrue);
                mm.goto_(isFalse);
                break;

            case 2:
                x.ifEq(false, isFalse);
                mm.goto_(isTrue);
                break;

            case 3:
                x.ifNe(false, isTrue);
                mm.goto_(isFalse);
                break;

            case 4:
                x.ifNe(true, isFalse);
                mm.goto_(isTrue);
                break;

            case 5:
                x.ifTrue(isTrue);
                mm.goto_(isFalse);
                break;

            case 6:
                x.ifFalse(isFalse);
                mm.goto_(isTrue);
                break;

            default:
                fail();
            }
        }

        isTrue.here();
        mm.return_("true");

        isFalse.here();
        mm.return_("false");
    }
}
