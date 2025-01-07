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

import java.lang.reflect.InvocationTargetException;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Tests for special code reducing steps.
 *
 * @author Brian S O'Neill
 */
public class ReduceTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ReduceTest.class.getName());
    }

    @Test
    public void deadStore() throws Exception {
        // Creates a bunch of useless push/store operations which should get eliminated. Short
        // of disassembly, there's no way to verify that this works. Check the coverage report.

        ClassMaker cm = ClassMaker.begin(null).public_();
        MethodMaker mm = cm.addMethod(null, "test").public_().static_();

        var a = mm.var(int.class).set(0);

        for (int i=0; i<100; i++) {
            a.get();
        }

        cm.finish().getMethod("test").invoke(null);
    }

    @Test
    public void definiteAssignment() throws Exception {
        // A store to a variable can be reduced until it's replaced with a pop instruction.
        // This is a problem when a variable slot has been defined, because then it must be
        // definitely assigned. Otherwise, verification can fail.
        
        ClassMaker cm = ClassMaker.begin(null).public_();
        MethodMaker mm = cm.addMethod(int.class, "test", int.class).public_().static_();

        var a = mm.var(int.class).set(0);
        a.get();
        a.get();
        Label end = mm.label();
        mm.param(0).ifEq(0, end);
        mm.param(0).inc(1);
        end.here();
        mm.return_(mm.param(0));

        cm.finish().getMethod("test", int.class).invoke(null, 10);
    }

    @Test
    public void deadCode() throws Exception {
        // Handles cases where the code flows through the end, even when the end is dead code.

        ClassMaker cm = ClassMaker.begin().public_();

        MethodMaker mm = cm.addConstructor();
        mm.invokeSuperConstructor();
        mm.nop();

        mm = cm.addMethod(int.class, "test").public_().static_();
        mm.return_(1);
        mm.label().here();

        mm = cm.addMethod(void.class, "test2").public_().static_();
        mm.return_();
        mm.nop();

        cm.finish().getMethod("test").invoke(null);
    }

    @Test
    public void preserveVariable() throws Exception {
        // An unnecessary variable shouldn't be eliminated when it has a name.

        ClassMaker cm = ClassMaker.begin().public_();

        var mm = cm.addMethod(int.class, "test", String.class).public_().static_();
        var h = mm.var(String.class).name("hello");
        h.set(mm.param(0));
        mm.return_(h.invoke("length"));

        try {
            cm.finish().getMethod("test", String.class).invoke(null, (String) null);
            fail();
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause().getMessage().contains("hello"));
        }
    }
}
