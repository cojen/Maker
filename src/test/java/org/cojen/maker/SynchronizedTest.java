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
public class SynchronizedTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(SynchronizedTest.class.getName());
    }

    @Test
    public void variable() throws Exception {
        basic(false);
    }

    @Test
    public void field() throws Exception {
        basic(true);
    }

    private void basic(boolean field) throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        {
            MethodMaker mm = cm.addConstructor().public_();

            if (field) {
                cm.addField(Object.class, "this").private_().final_();
                mm.invokeSuperConstructor();
                mm.field("this").set(mm.this_());
            }
        }

        {
            MethodMaker mm = cm.addMethod(null, "signal").public_();

            Variable thisVar;
            if (field) {
                thisVar = mm.field("this");
            } else {
                thisVar = mm.this_();
            }

            thisVar.synchronized_(() -> {
                thisVar.invoke("notify");
            });
        }

        var clazz = cm.finish();
        var instance = clazz.getConstructor().newInstance();
        var method = clazz.getMethod("signal");
        method.invoke(instance);

        var thread = new Thread(() -> {
            try {
                synchronized (instance) {
                    instance.wait();
                }
            } catch (InterruptedException e) {
            }
        });

        thread.start();

        while (true) {
            Thread.State state = thread.getState();
            if (state != Thread.State.NEW && state != Thread.State.RUNNABLE) {
                break;
            }
            Thread.yield();
        }

        method.invoke(instance);
        thread.join();
    }
}
