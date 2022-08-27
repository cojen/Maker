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

import java.io.Serializable;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class InterfaceTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(InterfaceTest.class.getName());
    }

    @Test
    public void defaultMethod() throws Exception {
        Class iface;
        {
            ClassMaker cm = ClassMaker.begin().public_().interface_();

            cm.addMethod(int.class, "test1", int.class).public_().abstract_();

            // Default interface method.
            MethodMaker mm = cm.addMethod(int.class, "test2", int.class).public_();
            mm.return_(mm.invoke("test1", mm.param(0)).add(1));

            iface = cm.finish();
        }

        ClassMaker cm = ClassMaker.begin().public_().implement(iface);

        cm.addConstructor().public_();

        MethodMaker mm = cm.addMethod(int.class, "test1", int.class).public_();
        mm.return_(mm.param(0).add(100));

        Class<?> clazz = cm.finish();

        Object obj = clazz.getConstructor().newInstance();
        Object result = clazz.getMethod("test2", int.class).invoke(obj, 10);

        assertEquals(10 + 100 + 1, result);
    }

    @Test
    public void useMakerAfterFinished() throws Exception {
        ClassMaker cm1 = ClassMaker.begin().public_().implement(Serializable.class);
        cm1.addConstructor().public_();
        Class<?> c1 = cm1.finish();

        ClassMaker cm2 = ClassMaker.begin().public_();
        MethodMaker mm = cm2.addMethod(Serializable.class, "test").public_().static_();
        // Should be able to reference cm1 and see that it implements Serializable.
        mm.return_(mm.new_(cm1));

        Class<?> c2 = cm2.finish();
        Object result = c2.getMethod("test").invoke(null);

        assertEquals(c1, result.getClass());
    }
}
