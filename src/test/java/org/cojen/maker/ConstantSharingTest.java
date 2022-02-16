/*
 *  Copyright 2021 Cojen.org
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
 * Tests that constants defined from one MethodMaker can be used in another, as long as they
 * are defined in the same ClassMaker.
 *
 * @author Brian S O'Neill
 */
public class ConstantSharingTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ConstantSharingTest.class.getName());
    }

    @Test
    public void classLiteral() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        MethodMaker mm1 = cm.addMethod(Class.class, "getClass").public_().static_();
        var mc = mm1.class_();
        mm1.return_(mc);

        MethodMaker mm2 = cm.addMethod(Class.class, "getClassAgain").public_().static_();
        mm2.return_(mc);

        Class<?> clazz = cm.finish();

        Object c1 = clazz.getMethod("getClass").invoke(null);
        Object c2 = clazz.getMethod("getClassAgain").invoke(null);

        assertSame(c1, c2);
        assertEquals(clazz, c1);
    }

    @Test
    public void methodHandles() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        // Gather some MethodHandles from one method and pass them to a bootstrap method from
        // another.

        MethodMaker mm1 = cm.addMethod(null, "nothing").public_().static_();
        mm1.return_();
        var mc = mm1.var(ConstantSharingTest.class);
        var h1 = mc.field("someField").methodHandleGet();
        var h2 = mc.methodHandle(int.class, "addStuff", int.class);

        MethodMaker mm2 = cm.addMethod(Object.class, "doSomething").public_().static_();
        // Note that (presently), direct use of 'mc' from 'mm1' isn't allowed.
        var bootstrap = mm2.var(mc).indy("bootMe", h1, h2);
        var v1 = bootstrap.invoke(Object.class, "anything",
                                  new Object[] {MethodHandle.class, MethodHandle.class},
                                  h1, h2);
        mm2.return_(v1);

        someField = 100;

        Object result = cm.finish().getMethod("doSomething").invoke(null);

        assertEquals(110, result);
    }

    public static int addStuff(int a) {
        return a + 10;
    }

    public static int someField;

    public static CallSite bootMe(MethodHandles.Lookup caller, String name, MethodType type,
                                  MethodHandle h1, MethodHandle h2)
    {
        MethodMaker mm = MethodMaker.begin(caller, name, type);
        var v1 = mm.invoke(h1);
        var v2 = mm.invoke(h2, v1);
        mm.return_(v2);

        return new ConstantCallSite(mm.finish());
    }
}
