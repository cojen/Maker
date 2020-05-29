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

import java.net.URL;
import java.net.URLClassLoader;

import java.util.ArrayList;
import java.util.List;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class TypeTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(TypeTest.class.getName());
    }

    @Test
    public void isAssignable() {
        // Test assignability of types currently being generated.

        {
            var cm = (TheClassMaker) ClassMaker.begin(null, ArrayList.class);
            Type type = cm.type();

            assertFalse(type.isAssignableFrom(Type.from(int.class)));
            assertTrue(Type.from(ArrayList.class).isAssignableFrom(type));
            assertTrue(Type.from(Object.class).isAssignableFrom(type));
        }

        {
            var cm = (TheClassMaker) ClassMaker.begin(null)
                .implement(List.class).implement(Serializable.class);
            Type type = cm.type();

            assertTrue(Type.from(List.class).isAssignableFrom(type));
            assertTrue(Type.from(Serializable.class).isAssignableFrom(type));
            assertFalse(Type.from(String.class).isAssignableFrom(type));
        }

        {
            var cm1 = (TheClassMaker) ClassMaker.begin();
            Type type1 = cm1.type();

            var loader = new URLClassLoader(new URL[0]);
            Type type2 = Type.begin(loader, cm1, cm1.name(), Type.from(Object.class));

            assertFalse(type1 == type2);
            assertTrue(type1.equals(type2));

            assertTrue(type1.isAssignableFrom(type2));
            assertTrue(type2.isAssignableFrom(type1));
        }
    }
}
