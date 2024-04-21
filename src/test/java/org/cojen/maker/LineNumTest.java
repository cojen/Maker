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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class LineNumTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(LineNumTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        cm.sourceFile("Basic");

        MethodMaker mm = cm.addMethod(null, "test", int.class).public_().static_();

        mm.lineNum(1);
        mm.lineNum(1);
        mm.nop();
        mm.lineNum(1);

        mm.param(0).ifEq(1, () -> mm.new_(Exception.class, "1").throw_());
        mm.lineNum(2);
        mm.param(0).ifEq(2, () -> mm.new_(Exception.class, "2").throw_());

        mm.lineNum(65536); // too high; is ignored
        mm.new_(Exception.class, "3").throw_();

        Method m = cm.finish().getMethod("test", int.class);

        for (int num = 1; num <= 3; num++) {
            try {
                m.invoke(null, num);
                fail();
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                assertTrue(cause.getClass() == Exception.class);
                StackTraceElement[] trace = cause.getStackTrace();
                assertEquals(Math.min(2, num), trace[0].getLineNumber());
                assertEquals("Basic", trace[0].getFileName());
            }
        }
    }
}
