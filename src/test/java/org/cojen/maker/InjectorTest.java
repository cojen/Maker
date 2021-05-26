/*
 *  Copyright 2021 Cojen.org
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

import java.util.WeakHashMap;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class InjectorTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(InjectorTest.class.getName());
    }

    @Test
    public void unloading() throws Exception {
        // Test that classes get unloaded and that classes which need to be in the same package
        // have access to package-private elements.

        var classes = new WeakHashMap<Class, Boolean>();

        for (int q=0; q<100; q++) {
            String parentGroup = "foo" + q;

            Class<?> parent = Object.class;
            Class<?> clazz = null;

            for (int i=25; --i>=0; ) {
                int group = i / 10;
                String name = parentGroup + ".bar" + group + ".Thing";

                ClassMaker cm = ClassMaker.begin(name).extend(parent);

                if (i == 0 || ((i - 1) / 10) != group) {
                    cm.public_();
                }

                MethodMaker mm = cm.addConstructor().public_();
                mm.invokeSuperConstructor();

                clazz = cm.finish();
                classes.put(clazz, true);

                parent = clazz;
            }

            Object obj = clazz.getConstructor().newInstance();
        }

        for (int i=0; i<10; i++) {
            if (classes.isEmpty()) {
                return;
            }
            System.gc();
        }

        fail();
    }
}
