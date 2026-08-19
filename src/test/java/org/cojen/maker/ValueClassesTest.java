/*
 *  Copyright 2024 Cojen.org
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

import java.lang.management.ManagementFactory;

import java.time.Instant;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class ValueClassesTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ValueClassesTest.class.getName());
    }

    static boolean isAvailable() {
        return Runtime.version().feature() >= 28 &&
            ManagementFactory.getRuntimeMXBean().getInputArguments().contains("--enable-preview");
    }

    @Test
    public void basic() throws Exception {
        org.junit.Assume.assumeTrue(isAvailable());

        assertTrue(Type.from(Instant.class).isValueClass());

        doBasic();
    }

    @Test
    public void failBasic() throws Exception {
        if (!isAvailable()) {
            assertFalse(Type.from(Instant.class).isValueClass());

            try {
                doBasic();
                fail();
            } catch (UnsupportedClassVersionError e) {
            }
        }
    }

    private void doBasic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        assertFalse(cm.type().isValueClass());
        cm.valueClass();
        assertTrue(cm.type().isValueClass());

        cm.addField(int.class, "data").public_().strict().final_();
        cm.addField(Instant.class, "when").public_().strict().final_();
        cm.addLoadableType(Instant.class);

        // Should be harmless.
        cm.addLoadableType(int.class);

        cm.addLoadableType(Instant[].class);
        cm.addLoadableType(int[].class);
        cm.addLoadableType(Object.class);

        MethodMaker ctor = cm.addConstructor(int.class, Instant.class).public_();
        ctor.field("data").set(ctor.param(0));
        ctor.field("when").set(ctor.param(1));
        ctor.invokeSuperConstructor();

        Class<?> clazz = cm.finish();

        assertTrue((boolean) Class.class.getMethod("isValue").invoke(clazz));

        var now = Instant.now();
        var obj = clazz.getConstructor(int.class, Instant.class).newInstance(10, now);

        assertEquals(10, clazz.getField("data").get(obj));
        assertEquals(now, clazz.getField("when").get(obj));
    }

    @Test
    public void earlyLarval() throws Exception {
        org.junit.Assume.assumeTrue(isAvailable());

        doEarlyLarval();
    }

    @Test
    public void failEarlyLarval() throws Exception {
        if (!isAvailable()) {
            try {
                doEarlyLarval();
                fail();
            } catch (UnsupportedClassVersionError e) {
            }
        }
    }

    private void doEarlyLarval() throws Exception {
        // Very simple test of early larval frames.

        var cm = ClassMaker.begin().public_().valueClass();
        cm.addField(int.class, "a").private_();

        var mm = cm.addConstructor(int.class).public_();
        var aVar = mm.field("a");
        mm.param(0).ifEq(0, () -> {aVar.clear();}, () -> {aVar.set(1);});

        mm.invokeSuperConstructor();

        Class<?> clazz = cm.finish();

        clazz.getConstructor(int.class).newInstance(0);
    }

    @Test
    public void notValueClasses() {
        assertFalse(Type.from(int.class).isValueClass());
        assertFalse(BaseType.Null.THE.isValueClass());
        assertFalse(Type.from(Instant[].class).isValueClass());
        assertFalse(Type.from(ValueClassesTest.class).isValueClass());
    }
}
