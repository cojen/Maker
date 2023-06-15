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
public class SealedTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(SealedTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        ClassMaker cm1 = ClassMaker.begin().public_();
        ClassMaker cm2 = ClassMaker.begin().public_().final_().extend(cm1);
        ClassMaker cm3 = ClassMaker.begin().public_().final_().extend(cm1);
        ClassMaker cm4 = ClassMaker.begin().public_().final_().extend(cm1);

        cm1.permitSubclass(cm2).permitSubclass(cm3);

        cm1.addConstructor().public_();
        cm2.addConstructor().public_();
        cm3.addConstructor().public_();
        cm4.addConstructor().public_();

        cm1.addMethod(String.class, "test").public_().return_("c1");
        cm2.addMethod(String.class, "test").public_().override().return_("c2");
        cm3.addMethod(String.class, "test").public_().override().return_("c3");
        cm4.addMethod(String.class, "test").public_().override().return_("c4");

        var class1 = cm1.finish();
        var class2 = cm2.finish();
        var class3 = cm3.finish();

        try {
            cm4.finish();
            fail();
        } catch (IncompatibleClassChangeError e) {
            // Not permitted to extend cm1.
        }

        assertEquals("c1", invokeTest(class1));
        assertEquals("c2", invokeTest(class2));
        assertEquals("c3", invokeTest(class3));
    }

    private static String invokeTest(Class<?> clazz) throws Exception {
        var instance = clazz.getConstructor().newInstance();
        return (String) clazz.getMethod("test").invoke(instance);
    }
}
