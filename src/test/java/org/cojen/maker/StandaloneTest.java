/*
 *  Copyright 2020 Cojen.org
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

import java.lang.invoke.*;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class StandaloneTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(StandaloneTest.class.getName());
    }

    @Test
    public void nothing() throws Throwable {
        MethodMaker mm = MethodMaker.begin(MethodHandles.lookup(), null, "_");
        MethodHandle mh = mm.finish();
        mh.invoke();
    }

    @Test
    public void simple() throws Throwable {
        MethodMaker mm = MethodMaker.begin
            (MethodHandles.lookup(), int.class, null, int.class, int.class);

        mm.return_(mm.param(0).add(mm.param(1)));
        MethodHandle mh = mm.finish();
        assertEquals(3, (int) mh.invoke(1, 2));

        // Again with a MethodType.
        mm = MethodMaker.begin(MethodHandles.lookup(), "_",
                               MethodType.methodType(int.class, int.class, int.class));
        mm.return_(mm.param(0).add(mm.param(1)));
        mh = mm.finish();
        assertEquals(3, (int) mh.invoke(1, 2));
    }

    @Test
    public void broken() throws Exception {
        try {
            MethodMaker.begin(MethodHandles.lookup(), "FAKE", "_");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().startsWith("Unknown"));
        }
    }

    @Test
    public void extraMethods() throws Throwable {
        MethodMaker mm = MethodMaker.begin(MethodHandles.lookup(), int.class, "_");

        ClassMaker cm = mm.classMaker();
        cm.addField(int.class, "foo").static_();
        cm.addClinit().field("foo").set(100);

        MethodMaker helper = cm.addMethod(int.class, "helper").static_();
        helper.return_(helper.field("foo"));

        mm.return_(mm.invoke("helper"));

        MethodHandle mh = mm.finish();
        int result = (int) mh.invoke();
        assertEquals(100, result);
    }
}
