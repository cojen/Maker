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
public class ArrayTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ArrayTest.class.getName());
    }

    private ClassMaker cm;
    private MethodMaker mm;

    @Before
    public void setup() {
        cm = ClassMaker.begin().public_();
        mm = cm.addMethod(null, "run").static_().public_();
    }

    @Test
    public void primitives() throws Exception {
        var assertVar = mm.var(Assert.class);

        {
            var v1 = mm.new_(boolean[].class, 10);
            v1.aset(1, true);
            assertVar.invoke("assertEquals", true, v1.aget(1));
        }

        {
            var v1 = mm.new_(byte[].class, 10);
            v1.aset(1, 5);
            assertVar.invoke("assertEquals", (byte) 5, v1.aget(1));
        }

        {
            var v1 = mm.new_(char[].class, 10);
            v1.aset(1, 'A');
            assertVar.invoke("assertEquals", 'A', v1.aget(1));
        }

        {
            var v1 = mm.new_(short[].class, 10);
            v1.aset(1, 1000);
            assertVar.invoke("assertEquals", (short) 1000, v1.aget(1));
        }

        {
            var v1 = mm.new_(int[].class, 10);
            v1.aset(1, 1000);
            assertVar.invoke("assertEquals", 1000, v1.aget(1));
        }

        {
            var v1 = mm.new_(long[].class, 10);
            v1.aset(1, Long.MAX_VALUE);
            assertVar.invoke("assertEquals", Long.MAX_VALUE, v1.aget(1));
        }

        {
            var v1 = mm.new_(float[].class, 10);
            v1.aset(1, 1.2f);
            assertVar.invoke("assertEquals", 1.2f, v1.aget(1), 0.0f);
        }

        {
            var v1 = mm.new_(double[].class, 10);
            var v2 = mm.var(int.class).set(1);
            v1.aset(v2, 1.2);
            assertVar.invoke("assertEquals", 1.2, v1.aget(v2), 0.0);
        }

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void objects() throws Exception {
        var assertVar = mm.var(Assert.class);

        {
            var v1 = mm.new_(String[].class, 10);
            v1.aset(1, "hello");
            assertVar.invoke("assertEquals", "hello", v1.aget(1));
        }

        {
            var v1 = mm.new_(Object[].class, 10);
            var v2 = mm.var(int.class).set(1);
            v1.aset(v2, "hello");
            assertVar.invoke("assertEquals", "hello", v1.aget(v2));
        }

        {
            var v1 = mm.new_(String[].class, 10).cast(Object[].class);
            Label l1 = mm.label().here();
            v1.aset(1, 123);
            assertVar.invoke("fail");
            mm.return_();
            Label l2 = mm.label().here();
            var ex = mm.catch_(l1, l2, ArrayStoreException.class);
        }

        cm.finish().getMethod("run").invoke(null);
    }

    @Test
    public void multidimensional() throws Exception {
        var assertVar = mm.var(Assert.class);

        {
            var v1 = mm.var(int.class).set(5);
            var v2 = mm.new_(int[][].class, v1, 10);
            v2.aget(1).aset(2, 300);
            assertVar.invoke("assertEquals", 300, v2.aget(1).aget(2));
        }

        {
            var v1 = mm.new_(String[][].class, 10, 10);
            var v2 = mm.new_(String[][][].class, 10, 10, 10);
            v2.aset(2, v1);
            assertVar.invoke("assertEquals", v1, v2.aget(2));
        }

        cm.finish().getMethod("run").invoke(null);
    }
}
