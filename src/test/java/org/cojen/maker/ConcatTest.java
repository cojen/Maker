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
public class ConcatTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ConcatTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        {
            mm.var(Assert.class).invoke("assertEquals", "", mm.concat());
            mm.var(Assert.class).invoke("assertEquals", "hello", mm.concat("hello"));
            mm.var(Assert.class).invoke("assertEquals", "null", mm.concat((Object) null));
        }

        {
            var v1 = mm.var(String.class).set("hello").name("v1");
            assertEquals("v1", v1.name());
            mm.var(Assert.class).invoke("assertEquals", "hello", mm.concat(v1));
        }

        {
            var v1 = mm.var(String.class).set(null);
            mm.var(Assert.class).invoke("assertEquals", "null", mm.concat(v1));
        }

        {
            var v1 = mm.var(String.class).set("hello");
            var v2 = mm.var(String.class).set("world");
            mm.var(Assert.class).invoke("assertEquals", "helloworld", mm.concat(v1, v2));
        }

        {
            var v1 = mm.var(double.class).set(123.456);
            var v2 = mm.var(Double.class).set(null);
            var v3 = mm.concat("hello_", "world", 'A', v1, -999, v2, int.class, 1.2, String.class);
            mm.var(Assert.class).invoke
                ("assertEquals", "hello_worldA123.456-999nullint1.2class java.lang.String", v3);
        }

        {
            var v1 = mm.var(double.class).set(123.456);
            var v2 = mm.concat('\0', '\u0001', v1);
            mm.var(Assert.class).invoke("assertEquals", "\0\u0001123.456", v2);
        }

        {
            var v1 = mm.concat(Long.MAX_VALUE, (float) Math.PI, (byte) 1);
            String expect = "" + Long.MAX_VALUE + (float) Math.PI + "1";
            mm.var(Assert.class).invoke("assertEquals", expect, v1);
        }

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void huge() throws Exception {
        // StringConcatFactory is limited to 200 values. StringBuilder is used instead.

        ClassMaker cm = ClassMaker.begin().public_().implement(Runnable.class);
        cm.addConstructor().public_();
        MethodMaker mm = cm.addMethod(null, "run").public_();

        Object[] values = new Object[300];
        StringBuilder expect = new StringBuilder();

        for (int i=0; i<values.length; ) {
            values[i] = mm.var(int.class).set(i);
            values[i + 1] = i;
            expect.append(i).append(i);
            i += 2;
        }

        mm.var(Assert.class).invoke("assertEquals", expect.toString(), mm.concat(values));

        var clazz = cm.finish();
        ((Runnable) clazz.getConstructor().newInstance()).run();
    }

    @Test
    public void huge2() throws Exception {
        // Variables of type double and long require special attention when passed to the
        // concat method.

        ClassMaker cm = ClassMaker.begin().public_();

        {
            MethodMaker mm = cm.addMethod(String.class, "test0").public_().static_();
            mm.return_(mm.concat(new Object[150]));
        }

        for (int n=1; n<=2; n++) {
            MethodMaker mm = cm.addMethod(String.class, "test" + n).public_().static_();
            var values = new Object[n == 1 ? 100 : 134];
            for (int i=0; i<values.length; i++) {
                switch (i % 4) {
                case 0: values[i] = mm.var(long.class).set(0); break;
                case 1: values[i] = mm.var(double.class).set(1); break;
                case 2: values[i] = 2; break;
                default:values[i] = mm.var(int.class).set(3); break;
                }
            }
            mm.return_(mm.concat(values));
        }

        var clazz = cm.finish();

        assertEquals("null".repeat(150), clazz.getMethod("test0").invoke(null));

        for (int n=1; n<=2; n++) {
            var expect = new StringBuilder();
            int num = n == 1 ? 100 : 134;
            for (int i=0; i<num; i++) {
                expect.append(i % 4 == 1 ? "1.0" : String.valueOf(i % 4));
            }
            var result = (String) clazz.getMethod("test" + n).invoke(null);
            assertEquals(expect.toString(), result);
        }
    }
}
