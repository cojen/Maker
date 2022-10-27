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

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class RecordTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(RecordTest.class.getName());
    }

    @Test
    public void empty() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        cm.asRecord();

        Class<?> clazz = cm.finish();
        assertIsRecord(clazz);

        Object obj = clazz.getConstructor().newInstance();

        assertTrue(obj.equals(obj));
        assertEquals(0, obj.hashCode());
        assertTrue(obj.toString().contains(clazz.getSimpleName()));
    }

    @Test
    public void basic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        cm.addField(int.class, "num").private_().final_();
        cm.addField(String.class, "str").private_().final_();

        {
            MethodMaker ctor = cm.asRecord();
            var num = ctor.param(0);
            num.set(num.mul(10));
        }

        Class<?> clazz = cm.finish();
        assertIsRecord(clazz);

        var ctor = clazz.getConstructor(int.class, String.class);

        Object obj1 = ctor.newInstance(123, "hello");
        Object obj2 = ctor.newInstance(123, "hello");
        Object obj3 = ctor.newInstance(456, "world");

        assertEquals(obj1, obj2);
        assertEquals(obj1.hashCode(), obj2.hashCode());
        assertNotEquals(obj1, obj3);
        assertEquals(obj1.toString(), obj2.toString());
        assertNotEquals(obj1.toString(), obj3.toString());

        assertTrue(obj1.toString().contains(clazz.getSimpleName()));
        assertTrue(obj1.toString().contains("1230"));
        assertTrue(obj1.toString().contains("hello"));
    }

    @Test
    public void methodsExist() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        cm.addField(int.class, "num").private_().final_();
        cm.addField(String.class, "str").private_().final_();

        MethodMaker mm = cm.addMethod(String.class, "str").public_().final_();
        mm.return_(mm.concat(mm.field("str"), '!'));

        cm.addMethod(boolean.class, "equals", Object.class).public_().return_(false);

        mm = cm.addMethod(int.class, "hashCode").public_();
        mm.return_(mm.field("num").neg());

        cm.addMethod(String.class, "toString").public_().return_("hello");

        cm.asRecord();

        Class<?> clazz = cm.finish();
        assertIsRecord(clazz);

        var ctor = clazz.getConstructor(int.class, String.class);

        Object obj = ctor.newInstance(123, "hello");

        assertEquals("hello!", clazz.getMethod("str").invoke(obj));
        assertFalse(obj.equals(obj));
        assertEquals(-123, obj.hashCode());
        assertEquals("hello", obj.toString());
    }

    private static void assertIsRecord(Class<?> clazz) throws Exception {
        if (Runtime.version().feature() >= 16) {
            assertEquals(true, Class.class.getMethod("isRecord").invoke(clazz));
        }
    }
}
