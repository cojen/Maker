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

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class NestTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(NestTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        // Add nest classes and verify private access.

        ClassMaker parent = ClassMaker.begin().public_();

        try {
            parent.addClass("A.B.C");
            fail();
        } catch (IllegalArgumentException e) {
        }

        ClassMaker child1 = parent.addClass(null).public_();
        ClassMaker child2 = parent.addClass("Child2");
        ClassMaker child3 = parent.addClass(null).public_().interface_();
        child2.addConstructor().private_();

        parent.addField(int.class, "f0").private_().static_();
        child1.addField(int.class, "f1").private_().static_();
        child2.addField(int.class, "f2").private_();

        // Parent can access the children.
        {
            MethodMaker mm = parent.addConstructor().public_();
            mm.invokeSuperConstructor();
            var f1 = mm.var(child1).field("f1");
            mm.var(Assert.class).invoke("assertEquals", 0, f1);
            var f2 = mm.new_(child2).field("f2");
            mm.var(Assert.class).invoke("assertEquals", 0, f2);
        }

        // Child can access the parent.
        {
            MethodMaker mm = child1.addMethod(null, "test1").public_().static_();
            var f0 = mm.var(parent).field("f0");
            mm.var(Assert.class).invoke("assertEquals", 0, f0);
        }

        // Parent to child to parent.
        {
            MethodMaker mm = child2.addMethod(null, "internal").private_();
            var f0 = mm.var(parent).field("f0");
            mm.var(Assert.class).invoke("assertEquals", 0, f0);
        }
        {
            MethodMaker mm = parent.addMethod(null, "test2").public_().static_();
            mm.new_(child2).invoke("internal");
        }

        Class<?> parentClass = parent.finish();
        Class<?> child1Class = child1.finish();
        Class<?> child2Class = child2.finish();
        Class<?> child3Class = child3.finish();

        parentClass.getConstructor().newInstance();
        child1Class.getMethod("test1").invoke(null);
        parentClass.getMethod("test2").invoke(null);
    }
}
