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
public class ClinitTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ClinitTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        cm.addField(String.class, "test1").static_().final_().public_();
        cm.addField(String.class, "test2").static_().public_();
        cm.addField(String.class, "test3").static_().public_();

        {
            MethodMaker mm = cm.addClinit();
            mm.field("test1").set("hello");
        }

        {
            MethodMaker mm = cm.addClinit();
        }

        {
            MethodMaker mm = cm.addClinit();
            mm.field("test2").set("hello!");
            mm.return_();
        }

        {
            MethodMaker mm = cm.addClinit();
            mm.field("test3").set("world");
        }

        var clazz = cm.finish();

        assertEquals("hello", clazz.getField("test1").get(null));
        assertEquals("hello!", clazz.getField("test2").get(null));
        assertEquals("world", clazz.getField("test3").get(null));
    }
}
