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
public class ExceptionTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ExceptionTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        MethodMaker mm = cm.addMethod(null, "run").static_().public_();

        Label L1 = mm.label().here();
        mm.new_(Exception.class, "message").throw_();
        Label L2 = mm.label().here();

        mm.var(System.class).field("out").invoke("println", "dead");
        Label L3 = mm.label().here();

        var ex = mm.exceptionHandler(L1, L2, Exception.class);
        mm.var(Assert.class).invoke("assertEquals", "message", ex.invoke("getMessage"));
        mm.return_();

        mm.exceptionHandler(L1, L2, null).throw_();

        mm.exceptionHandler(L2, L3, null).throw_();

        var clazz = cm.finish();
        clazz.getMethod("run").invoke(null);
    }
}
