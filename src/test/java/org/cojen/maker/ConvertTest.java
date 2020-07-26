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
public class ConvertTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ConvertTest.class.getName());
    }

    private ClassMaker cm;
    private MethodMaker mm;

    @Before
    public void setup() {
        cm = ClassMaker.begin().public_();
        mm = cm.addMethod(null, "run").static_().public_();
    }

    void verify(boolean expect, Variable actual) {
        mm.var(ConvertTest.class).invoke("verify", expect, actual);
    }

    void verify(byte expect, Variable actual) {
        mm.var(ConvertTest.class).invoke("verify", expect, actual);
    }

    void verify(char expect, Variable actual) {
        mm.var(ConvertTest.class).invoke("verify", expect, actual);
    }

    void verify(short expect, Variable actual) {
        mm.var(ConvertTest.class).invoke("verify", expect, actual);
    }

    void verify(int expect, Variable actual) {
        mm.var(ConvertTest.class).invoke("verify", expect, actual);
    }

    void verify(long expect, Variable actual) {
        mm.var(ConvertTest.class).invoke("verify", expect, actual);
    }

    void verify(float expect, Variable actual) {
        mm.var(ConvertTest.class).invoke("verify", expect, actual);
    }

    void verify(double expect, Variable actual) {
        mm.var(ConvertTest.class).invoke("verify", expect, actual);
    }

    void verify(Object expect, Variable actual) {
        mm.var(ConvertTest.class).invoke("verify", expect, actual);
    }

    public static void verify(boolean a, boolean b) {
        assertEquals(a, b);
    }

    public static void verify(byte a, byte b) {
        assertEquals(a, b);
    }

    public static void verify(char a, char b) {
        assertEquals(a, b);
    }

    public static void verify(short a, short b) {
        assertEquals(a, b);
    }

    public static void verify(int a, int b) {
        assertEquals(a, b);
    }

    public static void verify(long a, long b) {
        assertEquals(a, b);
    }

    public static void verify(float a, float b) {
        assertTrue(Float.compare(a, b) == 0);
    }

    public static void verify(double a, double b) {
        assertTrue(Double.compare(a, b) == 0);
    }

    public static void verify(Object a, Object b) {
        assertEquals(a, b);
    }

    @Test
    public void fromPrimitive() throws Exception {
        // From primitive boolean....
        {
            // To self boxed type.
            var v1 = mm.var(boolean.class).set(true);
            var v2 = mm.var(Boolean.class).set(v1);
            verify(v1, v2.cast(boolean.class));
        }

        // From primitive byte...
        {
            // To wider primitive type.
            {
                var v1 = mm.var(byte.class).set(-100);
                var v2 = mm.var(short.class).set(v1);
                verify(v1, v2.cast(byte.class));

                var v3 = mm.var(byte.class).set(123);
                var v4 = mm.var(int.class).set(v3);
                verify(v3, v4.cast(byte.class));

                var v5 = mm.var(byte.class).set(10);
                var v6 = mm.var(float.class).set(v5);
                verify(v5, v6.cast(byte.class));

                var v7 = mm.var(byte.class).set(-10);
                var v8 = mm.var(long.class).set(v7);
                verify(v7, v8.cast(byte.class));

                var v9 = mm.var(byte.class).set(-11);
                var v10 = mm.var(double.class).set(v9);
                verify(v9, v10.cast(byte.class));
            }

            // To self boxed type.
            {
                var v1 = mm.var(byte.class).set(-100);
                var v2 = mm.var(Byte.class).set(v1);
                verify(v1, v2.cast(byte.class));
            }

            // To wider boxed types.
            {
                var v1 = mm.var(byte.class).set(-100);
                var v2 = mm.var(Short.class).set(v1);
                verify(v1, v2.cast(byte.class));

                var v3 = mm.var(byte.class).set(123);
                var v4 = mm.var(Integer.class).set(v3);
                verify(v3, v4.cast(byte.class));

                var v5 = mm.var(byte.class).set(10);
                var v6 = mm.var(Float.class).set(v5);
                verify(v5, v6.cast(byte.class));

                var v7 = mm.var(byte.class).set(-10);
                var v8 = mm.var(Long.class).set(v7);
                verify(v7, v8.cast(byte.class));

                var v9 = mm.var(byte.class).set(-11);
                var v10 = mm.var(Double.class).set(v9);
                verify(v9, v10.cast(byte.class));
            }
        }

        // From primitive char...
        {
            // To wider primitive type.
            {
                var v1 = mm.var(char.class).set('\u1000');
                var v2 = mm.var(int.class).set(v1);
                verify(v1, v2.cast(char.class));

                var v3 = mm.var(char.class).set('A');
                var v4 = mm.var(long.class).set(v3);
                verify(v3, v4.cast(char.class));

                var v5 = mm.var(char.class).set('B');
                var v6 = mm.var(float.class).set(v5);
                verify(v5, v6.cast(char.class));

                var v7 = mm.var(char.class).set('\0');
                var v8 = mm.var(double.class).set(v7);
                verify(v7, v8.cast(char.class));
            }

            // To self boxed type.
            {
                var v1 = mm.var(char.class).set('\r');
                var v2 = mm.var(Character.class).set(v1);
                verify(v1, v2.cast(char.class));
            }

            // To wider boxed types.
            {
                var v1 = mm.var(char.class).set('\u1000');
                var v2 = mm.var(Integer.class).set(v1);
                verify(v1, v2.cast(char.class));

                var v3 = mm.var(char.class).set('A');
                var v4 = mm.var(Long.class).set(v3);
                verify(v3, v4.cast(char.class));

                var v5 = mm.var(char.class).set('B');
                var v6 = mm.var(Float.class).set(v5);
                verify(v5, v6.cast(char.class));

                var v7 = mm.var(char.class).set('\0');
                var v8 = mm.var(Double.class).set(v7);
                verify(v7, v8.cast(char.class));
            }
        }

        // From primitive short...
        {
            // To wider primitive type.
            {
                var v1 = mm.var(short.class).set(-1000);
                var v2 = mm.var(int.class).set(v1);
                verify(v1, v2.cast(short.class));

                var v3 = mm.var(short.class).set(1);
                var v4 = mm.var(long.class).set(v3);
                verify(v3, v4.cast(short.class));

                var v5 = mm.var(short.class).set(0);
                var v6 = mm.var(float.class).set(v5);
                verify(v5, v6.cast(short.class));

                var v7 = mm.var(short.class).set(32767);
                var v8 = mm.var(double.class).set(v7);
                verify(v7, v8.cast(short.class));
            }

            // To self boxed type.
            {
                var v1 = mm.var(short.class).set(-32768);
                var v2 = mm.var(Short.class).set(v1);
                verify(v1, v2.cast(short.class));
            }

            // To wider boxed types.
            {
                var v1 = mm.var(short.class).set(-100);
                var v2 = mm.var(Integer.class).set(v1);
                verify(v1, v2.cast(short.class));

                var v3 = mm.var(short.class).set(11);
                var v4 = mm.var(Long.class).set(v3);
                verify(v3, v4.cast(short.class));

                var v5 = mm.var(short.class).set(-1);
                var v6 = mm.var(Float.class).set(v5);
                verify(v5, v6.cast(short.class));

                var v7 = mm.var(short.class).set(10000);
                var v8 = mm.var(Double.class).set(v7);
                verify(v7, v8.cast(short.class));
            }
        }

        // From primitive int...
        {
            // To wider primitive type.
            {
                var v1 = mm.var(int.class).set(-100000);
                var v2 = mm.var(long.class).set(v1);
                verify(v1, v2.cast(int.class));

                var v3 = mm.var(int.class).set(Integer.MAX_VALUE);
                var v4 = mm.var(double.class).set(v3);
                verify(v3, v4.cast(int.class));
            }

            // To self boxed type.
            {
                var v1 = mm.var(int.class).set(1);
                var v2 = mm.var(Integer.class).set(v1);
                verify(v1, v2.cast(int.class));
            }

            // To wider boxed types.
            {
                var v1 = mm.var(int.class).set(100000);
                var v2 = mm.var(Long.class).set(v1);
                verify(v1, v2.cast(int.class));

                var v3 = mm.var(int.class).set(Integer.MIN_VALUE);
                var v4 = mm.var(Double.class).set(v3);
                verify(v3, v4.cast(int.class));
            }
        }

        // From primitive float...
        {
            // To wider primitive type.
            {
                var v1 = mm.var(float.class).set(10.0f);
                var v2 = mm.var(double.class).set(v1);
                verify(v1, v2.cast(float.class));
            }

            // To self boxed type.
            {
                var v1 = mm.var(float.class).set(-123.456f);
                var v2 = mm.var(Float.class).set(v1);
                verify(v1, v2.cast(float.class));
            }

            // To wider boxed types.
            {
                var v1 = mm.var(float.class).set(0.0f/0.0f);
                var v2 = mm.var(Double.class).set(v1);
                verify(v1, v2.cast(float.class));
            }
        }

        // From primitive double...
        {
            // To self boxed type.
            var v1 = mm.var(double.class).set(-123.456);
            var v2 = mm.var(Double.class).set(v1);
            verify(v1, v2.cast(double.class));
        }

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void fromBoxed() throws Exception {
        // From boxed boolean....
        {
            // To self primitive type.
            var v1 = mm.var(Boolean.class).set(true);
            var v2 = mm.var(boolean.class).set(v1);
            verify(v1, v2.cast(Boolean.class));
        }

        // From boxed byte...
        {
            // To wider primitive type.
            {
                var v1 = mm.var(Byte.class).set(-100);
                var v2 = mm.var(short.class).set(v1);
                verify(v1, v2.cast(Byte.class));

                var v3 = mm.var(Byte.class).set(123);
                var v4 = mm.var(int.class).set(v3);
                verify(v3, v4.cast(Byte.class));

                var v5 = mm.var(Byte.class).set(10);
                var v6 = mm.var(float.class).set(v5);
                verify(v5, v6.cast(Byte.class));

                var v7 = mm.var(Byte.class).set(-10);
                var v8 = mm.var(long.class).set(v7);
                verify(v7, v8.cast(Byte.class));

                var v9 = mm.var(Byte.class).set(-11);
                var v10 = mm.var(double.class).set(v9);
                verify(v9, v10.cast(Byte.class));
            }

            // To self primitive type.
            {
                var v1 = mm.var(Byte.class).set(-100);
                var v2 = mm.var(byte.class).set(v1);
                verify(v1, v2.cast(Byte.class));
            }

            // To wider boxed types.
            {
                var v1 = mm.var(Byte.class).set(-100);
                var v2 = mm.var(Short.class).set(v1);
                verify(v1, v2.cast(short.class).cast(Byte.class));

                var v3 = mm.var(Byte.class).set(123);
                var v4 = mm.var(Integer.class).set(v3);
                verify(v3, v4.cast(int.class).cast(Byte.class));

                var v5 = mm.var(Byte.class).set(10);
                var v6 = mm.var(Float.class).set(v5);
                verify(v5, v6.cast(float.class).cast(Byte.class));

                var v7 = mm.var(Byte.class).set(-10);
                var v8 = mm.var(Long.class).set(v7);
                verify(v7, v8.cast(long.class).cast(Byte.class));

                var v9 = mm.var(Byte.class).set(-11);
                var v10 = mm.var(Double.class).set(v9);
                verify(v9, v10.cast(double.class).cast(Byte.class));
            }

        }

        // From boxed char...
        {
            // To wider primitive type.
            {
                var v1 = mm.var(Character.class).set('\u1000');
                var v2 = mm.var(int.class).set(v1);
                verify(v1, v2.cast(Character.class));

                var v3 = mm.var(Character.class).set('A');
                var v4 = mm.var(long.class).set(v3);
                verify(v3, v4.cast(Character.class));

                var v5 = mm.var(Character.class).set('B');
                var v6 = mm.var(float.class).set(v5);
                verify(v5, v6.cast(Character.class));

                var v7 = mm.var(Character.class).set('\0');
                var v8 = mm.var(double.class).set(v7);
                verify(v7, v8.cast(Character.class));
            }

            // To self primitive type.
            {
                var v1 = mm.var(Character.class).set('\r');
                var v2 = mm.var(char.class).set(v1);
                verify(v1, v2.cast(Character.class));
            }

            // To wider boxed types.
            {
                var v1 = mm.var(Character.class).set('\u1000');
                var v2 = mm.var(Integer.class).set(v1);
                verify(v1, v2.cast(int.class).cast(Character.class));

                var v3 = mm.var(Character.class).set('A');
                var v4 = mm.var(Long.class).set(v3);
                verify(v3, v4.cast(long.class).cast(Character.class));

                var v5 = mm.var(Character.class).set('B');
                var v6 = mm.var(Float.class).set(v5);
                verify(v5, v6.cast(float.class).cast(Character.class));

                var v7 = mm.var(Character.class).set('\0');
                var v8 = mm.var(Double.class).set(v7);
                verify(v7, v8.cast(double.class).cast(Character.class));
            }
        }

        // From boxed short...
        {
            // To wider primitive type.
            {
                var v1 = mm.var(Short.class).set(-1000);
                var v2 = mm.var(int.class).set(v1);
                verify(v1, v2.cast(Short.class));

                var v3 = mm.var(Short.class).set(1);
                var v4 = mm.var(long.class).set(v3);
                verify(v3, v4.cast(Short.class));

                var v5 = mm.var(Short.class).set(0);
                var v6 = mm.var(float.class).set(v5);
                verify(v5, v6.cast(Short.class));

                var v7 = mm.var(Short.class).set(32767);
                var v8 = mm.var(double.class).set(v7);
                verify(v7, v8.cast(Short.class));
            }

            // To self primitive type.
            {
                var v1 = mm.var(Short.class).set(-32768);
                var v2 = mm.var(short.class).set(v1);
                verify(v1, v2.cast(Short.class));
            }

            // To wider boxed types.
            {
                var v1 = mm.var(Short.class).set(-100);
                var v2 = mm.var(Integer.class).set(v1);
                verify(v1, v2.cast(int.class).cast(Short.class));

                var v3 = mm.var(Short.class).set(11);
                var v4 = mm.var(Long.class).set(v3);
                verify(v3, v4.cast(long.class).cast(Short.class));

                var v5 = mm.var(Short.class).set(-1);
                var v6 = mm.var(Float.class).set(v5);
                verify(v5, v6.cast(float.class).cast(Short.class));

                var v7 = mm.var(Short.class).set(10000);
                var v8 = mm.var(Double.class).set(v7);
                verify(v7, v8.cast(double.class).cast(Short.class));
            }
        }

        // From boxed int...
        {
            // To wider primitive type.
            {
                var v1 = mm.var(Integer.class).set(-100000);
                var v2 = mm.var(long.class).set(v1);
                verify(v1, v2.cast(Integer.class));

                var v3 = mm.var(Integer.class).set(Integer.MAX_VALUE);
                var v4 = mm.var(double.class).set(v3);
                verify(v3, v4.cast(Integer.class));
            }

            // To self primitive type.
            {
                var v1 = mm.var(Integer.class).set(1);
                var v2 = mm.var(int.class).set(v1);
                verify(v1, v2.cast(Integer.class));
            }

            // To wider boxed types.
            {
                var v1 = mm.var(Integer.class).set(100000);
                var v2 = mm.var(Long.class).set(v1);
                verify(v1, v2.cast(long.class).cast(Integer.class));

                var v3 = mm.var(Integer.class).set(Integer.MIN_VALUE);
                var v4 = mm.var(Double.class).set(v3);
                verify(v3, v4.cast(double.class).cast(Integer.class));
            }
        }

        // From boxed float...
        {
            // To wider primitive type.
            {
                var v1 = mm.var(Float.class).set(10.0f);
                var v2 = mm.var(double.class).set(v1);
                verify(v1, v2.cast(Float.class));
            }

            // To self primitive type.
            {
                var v1 = mm.var(Float.class).set(-123.456f);
                var v2 = mm.var(float.class).set(v1);
                verify(v1, v2.cast(Float.class));
            }

            // To wider boxed types.
            {
                var v1 = mm.var(Float.class).set(0.0f/0.0f);
                var v2 = mm.var(Double.class).set(v1);
                verify(v1, v2.cast(double.class).cast(Float.class));
            }
        }

        // From boxed double...
        {
            // To self primitive type.
            var v1 = mm.var(Double.class).set(-123.456);
            var v2 = mm.var(double.class).set(v1);
            verify(v1, v2.cast(Double.class));
        }

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void narrowing() throws Exception {
        {
            var v1 = mm.var(long.class).set(Long.MAX_VALUE);
            verify((byte) -1, v1.cast(byte.class));
            verify('\uffff', v1.cast(char.class));
            verify((short) -1, v1.cast(short.class));
            verify(-1, v1.cast(int.class));
            verify((float) Long.MAX_VALUE, v1.cast(float.class));
            verify((double) Long.MAX_VALUE, v1.cast(double.class));
        }

        {
            var v1 = mm.var(int.class).set(Integer.MAX_VALUE);
            verify((byte) -1, v1.cast(byte.class));
            verify('\uffff', v1.cast(char.class));
            verify((short) -1, v1.cast(short.class));
            verify((float) Integer.MAX_VALUE, v1.cast(float.class));
        }

        {
            var v1 = mm.var(short.class).set(Short.MAX_VALUE);
            verify((byte) -1, v1.cast(byte.class));
            verify('\u7fff', v1.cast(char.class));
        }

        {
            var v1 = mm.var(char.class).set('\uffff');
            verify((byte) -1, v1.cast(byte.class));
            verify((short) -1, v1.cast(short.class));
        }

        {
            var v1 = mm.var(double.class).set((double) Integer.MAX_VALUE);
            verify((byte) -1, v1.cast(byte.class));
            verify('\uffff', v1.cast(char.class));
            verify((short) -1, v1.cast(short.class));
            verify(Integer.MAX_VALUE, v1.cast(int.class));
            verify((float) Integer.MAX_VALUE, v1.cast(float.class));
            verify((long) Integer.MAX_VALUE, v1.cast(long.class));
        }

        {
            var v1 = mm.var(float.class).set(65.0f);
            verify((byte) 65, v1.cast(byte.class));
            verify('A', v1.cast(char.class));
            verify((short) 65, v1.cast(short.class));
            verify(65, v1.cast(int.class));
            verify(65L, v1.cast(long.class));
        }

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void objectCast() throws Exception {
        var v1 = mm.var(String.class).set("hello");
        var v2 = mm.var(Object.class).set(v1);
        var v3 = mm.var(String.class).set(v2.cast(String.class));

        verify(true, v2.instanceOf(String.class));

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void objectCastToPrimitiveNumber() throws Exception {
        var v1 = mm.var(Object.class).set(1);
        var v2 = mm.var(Number.class).set(2.0d);
        var v3 = mm.var(Float.class).set(3.0f);
        var v4 = mm.new_(java.math.BigInteger.class, "123");

        var v5 = v1.cast(int.class);
        var v6 = v2.cast(double.class);
        var v7 = v3.cast(float.class);
        var v8 = v2.cast(float.class);
        var v9 = v4.cast(int.class);

        verify(1, v5);
        verify(2.0d, v6);
        verify(3.0f, v7);
        verify(2.0f, v8);
        verify(123, v9);

        try {
            mm.var(String.class).set("hello").cast(int.class);
            fail();
        } catch (IllegalStateException e) {
        }

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void objectCastToBoolean() throws Exception {
        var v1 = mm.var(Object.class).set(true);
        var v2 = v1.cast(boolean.class);
        verify(true, v2);

        try {
            mm.var(Number.class).set(10).cast(boolean.class);
            fail();
        } catch (IllegalStateException e) {
        }

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void objectCastToChar() throws Exception {
        var v1 = mm.var(Object.class).set('a');
        var v2 = v1.cast(char.class);
        verify('a', v2);

        try {
            mm.var(Number.class).set(10).cast(char.class);
            fail();
        } catch (IllegalStateException e) {
        }

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void safeConstants() throws Exception {
        // Setting a mismatched constant type is allowed if no information is lost.

        verify((byte) 100, mm.var(byte.class).set(100));
        verify((short) 10_000, mm.var(short.class).set(10_000));
        verify(10f, mm.var(float.class).set(10));
        verify(10.0, mm.var(double.class).set(10));
        verify(10L, mm.var(long.class).set(10));
 
        verify((byte) 100, mm.var(byte.class).set(100L));
        verify((short) 10_000, mm.var(short.class).set(10_000L));
        verify(10_000_000, mm.var(int.class).set(10_000_000L));
        verify(10f, mm.var(float.class).set(10L));
        verify(10.0d, mm.var(double.class).set(10L));

        verify((byte) 100, mm.var(byte.class).set(100.0f));
        verify((short) 10_000, mm.var(short.class).set(10_000.0f));
        verify(10_000_000, mm.var(int.class).set(10_000_000.0f));
        verify(10.0d, mm.var(double.class).set(10.0f));
        verify(10L, mm.var(long.class).set(10.0f));
        verify(0.0d/0.0d, mm.var(float.class).set(0.0f/0.0f));

        verify((byte) 100, mm.var(byte.class).set(100.0d));
        verify((short) 10_000, mm.var(short.class).set(10_000.0d));
        verify(10_000_000, mm.var(int.class).set(10_000_000.0d));
        verify(10L, mm.var(long.class).set(10.0d));
        verify(10.0f, mm.var(float.class).set(10.0d));
        verify(0.0f/0.0f, mm.var(float.class).set(0.0d/0.0d));
        
        verify((short) -10, mm.var(short.class).set((byte) -10));
        verify(100, mm.var(int.class).set((byte) 100));
        verify(10f, mm.var(float.class).set((byte) 10));
        verify(10.0, mm.var(double.class).set((byte) 10));
        verify(10L, mm.var(long.class).set((byte) 10));

        verify((byte) 100, mm.var(byte.class).set((short) 100));
        verify(10_000, mm.var(int.class).set((short) 10_000));
        verify(10f, mm.var(float.class).set((short) 10));
        verify(10.0, mm.var(double.class).set((short) 10));
        verify(10L, mm.var(long.class).set((short) 10));

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }

    @Test
    public void unsafeConstants() throws Exception {
        // Setting a mismatched constant type is disallowed if information would be lost.

        try {
            mm.var(byte.class).set(1000);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(short.class).set(100_000);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(float.class).set(Integer.MAX_VALUE - 1);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(byte.class).set(1000L);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(short.class).set(100_000L);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(int.class).set(Long.MAX_VALUE);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(float.class).set(Long.MAX_VALUE - 1);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(double.class).set(Long.MAX_VALUE - 1);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(byte.class).set(10.1f);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(short.class).set(10.1f);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(int.class).set(10.1f);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(long.class).set(10.1f);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(byte.class).set(10.1);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(short.class).set(10.1);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(int.class).set(10.1);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(float.class).set(Math.PI);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(long.class).set(10.1);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(char.class).set((byte) -1);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            mm.var(byte.class).set((short) 10_000);
            fail();
        } catch (IllegalStateException e) {
        }
    }
}
