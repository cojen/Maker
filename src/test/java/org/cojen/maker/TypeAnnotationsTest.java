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

import java.lang.reflect.*;

import java.util.*;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class TypeAnnotationsTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(TypeAnnotationsTest.class.getName());
    }

    @Test
    public void extendClass() {
        ClassMaker cm = ClassMaker.begin().public_();

        Type ext = Type.from(ArrayList.class);

        try {
            ext.addAnnotation(Ann.class, true);
            fail();
        } catch (IllegalStateException e) {
        }

        ext = ext.annotatable();

        AnnotationMaker am = ext.addAnnotation(Ann.class, true);
        am.put("message", "hello");

        cm.extend(ext);

        try {
            ext.addAnnotation(Ann.class, true);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("frozen"));
        }

        Class<?> clazz = cm.finish();

        AnnotatedType at = clazz.getAnnotatedSuperclass();

        assertEquals(ArrayList.class, at.getType());

        var ann = (Ann) at.getAnnotation(Ann.class);
        assertEquals("hello", ann.message());
    }
}
