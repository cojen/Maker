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

import java.lang.invoke.*;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Reproduction of a cojen/Maker bug in the flow analysis "Overflow" mechanism
 * (TheMethodMaker.Flow.run), which defers branch targets when the flow recursion becomes
 * deeper than 100 levels. It produces a class which fails verification, with:
 *
 *   VerifyError: Bad local variable type
 *
 * Cause: a deferred label can be removed from the op list by a later optimization (when the
 * flow analysis revisits a conditional branch which is followed by a goto, it flips the
 * condition and removes the goto and the else label, if that label is no longer used). The
 * deferred entry still refers to the removed label, and when it's finally processed, the flow
 * continues from the stale label into ops which are still in the list, and visits them a
 * second time. Visiting the same "push a variable, then store it into an unused variable"
 * pair twice decrements the variable's push count twice, and so the variable is mistakenly
 * considered to be unused. The store which assigns it is then removed, but a load of it remains.
 *
 * The first loop in the build method does nothing useful, except to make the flow recursion
 * deep enough (about 95 sequential conditional branches) that the rest of the method is
 * analyzed right around the point where branch targets start being deferred. Without it, the
 * remainder of the method is analyzed without deferral, and it works fine. This is a
 * machine-reduced program, and so the remainder isn't very meaningful on its own.
 *
 * @author Claude
 * @author Brian S. O'Neill
 */
public class OverflowStaleLabelVerifyTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(OverflowStaleLabelVerifyTest.class.getName());
    }

    private static Variable asVar(MethodMaker mm, Object o) {
        return o instanceof Variable v ? v : mm.var(int.class).set(o);
    }

    @Test
    public void overflow() throws Throwable {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(int.class, "f", int.class).public_().static_();

        /** v0 is the parameter, and v1 is an accumulator which is the method result. */
        Variable v0 = mm.param(0);
        Variable v1 = mm.var(int.class).set(v0);
        for (int i0 = 2; i0 < 97; i0++) {
            Label skip1 = mm.label();
            v0.add(i0).rem(3).ifEq(0, skip1);
            skip1.here();
        }
        {
            Label start2 = mm.label().here();
            {
                Variable sel4 = asVar(mm, asVar(mm, asVar(mm, asVar(mm, v0).sub(v1)).sub(asVar(mm, v0).add(v0))).rem(5));
                Label end5 = mm.label(), def6 = mm.label();
                Label[] cases7 = new Label[2];
                for (int i = 0; i < cases7.length; i++) cases7[i] = mm.label();
                sel4.switch_(def6, new int[] {0, 3}, cases7);
                cases7[0].here();
                cases7[1].here();
                {
                    Label start8 = mm.label().here();
                    mm.catch_(start8, ArithmeticException.class, ex9 -> {
                        Variable v6 = mm.var(long.class);
                        v6.set(asVar(mm, v1).div(asVar(mm, v0).mul(v1)));
                        v1.set(v1.mul(31).add(asVar(mm, asVar(mm, v1).mul(v6.cast(int.class))).add(asVar(mm, v6.cast(int.class)).sub(3))));
                    });
                }
                def6.here();
                {
                    Label elseL10 = mm.label(), endL11 = mm.label();
                    Variable l12 = asVar(mm, asVar(mm, v1).sub(4));
                    Object r13 = asVar(mm, v0).mul(v1);
                    l12.ifEq(r13, elseL10);
                    elseL10.here();
                }
            }
            mm.catch_(start2, ArithmeticException.class, ex3 -> {
                {
                    Variable sel14 = asVar(mm, asVar(mm, asVar(mm, asVar(mm, v1).mul(v0)).sub(v1)).rem(5));
                    Label end15 = mm.label(), def16 = mm.label();
                    Label[] cases17 = new Label[1];
                    for (int i = 0; i < cases17.length; i++) cases17[i] = mm.label();
                    sel14.switch_(def16, new int[] {-1048576}, cases17);
                    cases17[0].here();
                    def16.here();
                    {
                        Label elseL18 = mm.label(), endL19 = mm.label();
                        Variable l20 = asVar(mm, asVar(mm, v1).mul(-1));
                        Object r21 = asVar(mm, 3).add(v0);
                        l20.ifLt(r21, elseL18);
                        elseL18.here();
                    }
                    Variable v23 = mm.var(int.class);
                    {
                        Label start22 = mm.label().here();
                        mm.catch_(start22, ArithmeticException.class, ex23 -> v23.set(2));
                    }
                }
                {
                    Variable sel24 = asVar(mm, asVar(mm, asVar(mm, v1).sub(0)).rem(5));
                    Label end25 = mm.label(), def26 = mm.label();
                    Label[] cases27 = new Label[1];
                    for (int i = 0; i < cases27.length; i++) cases27[i] = mm.label();
                    sel24.switch_(def26, new int[] {2}, cases27);
                    cases27[0].here();
                    mm.return_(v1.mul(7).add(v0));
                    def26.here();
                }
            });
        }
        Variable v31 = mm.var(int.class);
        {
            Label elseL28 = mm.label(), endL29 = mm.label();
            Variable l30 = asVar(mm, asVar(mm, v1).mul(3));
            Object r31 = asVar(mm, v0).sub(3);
            l30.ifNe(r31, elseL28);
            v31.set(asVar(mm, asVar(mm, v1).sub(v0)).sub(asVar(mm, v1).sub(0)));
            mm.goto_(endL29);
            elseL28.here();
            v31.set(asVar(mm, v1).add(asVar(mm, 0).sub(4)));
            endL29.here();
        }
        Variable v32 = mm.var(int.class);
        {
            Label elseL32 = mm.label(), endL33 = mm.label();
            Variable l34 = asVar(mm, asVar(mm, 3).sub(v31));
            Object r35 = v1;
            l34.ifLe(r35, elseL32);
            v32.set(-1);
            mm.goto_(endL33);
            elseL32.here();
            v32.set(v31);
            endL33.here();
        }
        mm.return_(v1);

        try {
            var lookup = cm.finishHidden();
            var h = lookup.findStatic(lookup.lookupClass(), "f",
                                      MethodType.methodType(int.class, int.class));
            for (int p : new int[] {0, 1, -7, 3}) {
                try {
                    int r = (int) h.invokeExact(p);
                } catch (ArithmeticException e) {
                }
            }
        } catch (VerifyError e) {
            fail("verify error");
        }
    }
}
