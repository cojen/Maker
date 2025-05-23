/*
 *  Copyright 2022 Cojen.org
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

import java.lang.constant.MethodTypeDesc;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class MethodParametersTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(MethodParametersTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        MethodMaker mm = cm.addMethod(null, "t1", String.class, long.class).public_();
        mm.param(0).name("p1");
        mm.param(1).name("p2");
        mm.this_().name("self"); // ignored

        assertEquals("t1", mm.name());
        assertEquals(2, mm.paramCount());
        assertEquals(Type.from(void.class), mm.getReturnType());
        assertArrayEquals(new Type[] {Type.from(String.class), Type.from(long.class)},
                          mm.getParamTypes());

        mm = cm.addMethod(null, "t2", String.class, long.class).public_().static_();
        mm.param(0).name("p1");
        mm.param(1).name("p2");

        assertEquals("t2", mm.name());
        assertEquals(2, mm.paramCount());

        mm = cm.addMethod(int.class, "t3", String.class, long.class).public_().static_();
        mm.param(1).name("p2");
        mm.return_(1);

        assertEquals(Type.from(int.class), mm.getReturnType());
        assertArrayEquals(new Type[] {Type.from(String.class), Type.from(long.class)},
                          mm.getParamTypes());

        var clazz = cm.finish();

        {
            Method m = clazz.getMethod("t1", String.class, long.class);
            Parameter[] params = m.getParameters();
            assertEquals(2, params.length);
            assertEquals("p1", params[0].getName());
            assertEquals("p2", params[1].getName());
        }

        {
            Method m = clazz.getMethod("t2", String.class, long.class);
            Parameter[] params = m.getParameters();
            assertEquals(2, params.length);
            assertEquals("p1", params[0].getName());
            assertEquals("p2", params[1].getName());
        }

        {
            Method m = clazz.getMethod("t3", String.class, long.class);
            Parameter[] params = m.getParameters();
            assertEquals(2, params.length);
            assertEquals("arg0", params[0].getName());
            assertEquals("p2", params[1].getName());
        }
    }

    @Test
    public void withMethodTypeDesc() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod("test", MethodTypeDesc.ofDescriptor("(I)J"))
            .public_().static_();
        mm.return_(mm.param(0).add(1));
        Object result = cm.finish().getMethod("test", int.class).invoke(null, 1);
        assertEquals(2L, result);
    }

    @Test
    public void flags() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        MethodMaker mm = cm.addMethod(null, "t1", int.class, int.class, int.class, int.class);
        mm.param(0).name("p1").final_();
        mm.param(1).synthetic().name("p2");
        mm.param(2).mandated();
        mm.param(3).mandated().final_();

        if (mm.this_() instanceof org.cojen.maker.Parameter p) {
            // Shouldn't do anything.
            p.final_();
        }

        var clazz = cm.finish();

        Method m = clazz.getDeclaredMethod("t1", int.class, int.class, int.class, int.class);
        Parameter[] params = m.getParameters();
        assertEquals(4, params.length);

        assertEquals("p1", params[0].getName());
        assertEquals("p2", params[1].getName());
        assertFalse(params[2].isNamePresent());
        assertFalse(params[3].isNamePresent());

        assertTrue(params[1].isSynthetic());
        assertTrue(params[2].isImplicit());
        assertTrue(params[3].isImplicit());

        assertEquals(0x0010, params[0].getModifiers());
        assertEquals(0x1000, params[1].getModifiers());
        assertEquals(0x8000, params[2].getModifiers());
        assertEquals(0x8010, params[3].getModifiers());
    }
}
