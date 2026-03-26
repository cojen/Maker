/*
 *  Copyright 2026 Cojen.org
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
 * @author Brian S. O'Neill
 */
public class NotFoundTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(NotFoundTest.class.getName());
    }

    @Test
    public void noField() {
        ClassMaker cm = ClassMaker.begin();
        MethodMaker mm = cm.addMethod(null, "test");

        try {
            mm.var("some.missing.Class").field("xxx");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("class not found"));
        }

        try {
            mm.var(int.class).field("xxx");
        } catch (IllegalStateException e) {
            assertFalse(e.getMessage().contains("class not found"));
        }

        try {
            mm.var(int[].class).field("xxx");
        } catch (IllegalStateException e) {
            assertFalse(e.getMessage().contains("class not found"));
        }

        try {
            mm.field("xxx");
        } catch (IllegalStateException e) {
            assertFalse(e.getMessage().contains("class not found"));
        }
    }

    @Test
    public void noMethod() {
        ClassMaker cm = ClassMaker.begin();
        MethodMaker mm = cm.addMethod(null, "test");

        try {
            mm.var("some.missing.Class").invoke("xxx");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("class not found"));
        }

        try {
            mm.var(int.class).invoke("xxx");
        } catch (IllegalStateException e) {
            assertFalse(e.getMessage().contains("class not found"));
        }

        try {
            mm.var(int[].class).invoke("xxx");
        } catch (IllegalStateException e) {
            assertFalse(e.getMessage().contains("class not found"));
        }

        try {
            mm.invoke("xxx");
        } catch (IllegalStateException e) {
            assertFalse(e.getMessage().contains("class not found"));
        }
    }

    @Test
    public void noConstructor() {
        ClassMaker cm = ClassMaker.begin();
        MethodMaker mm = cm.addMethod(null, "test");

        try {
            mm.new_("some.missing.Class");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("class not found"));
        }

        try {
            mm.var(Type.from(int.class));
        } catch (IllegalStateException e) {
            assertFalse(e.getMessage().contains("class not found"));
        }

        try {
            mm.var(Type.from(int[].class));
        } catch (IllegalStateException e) {
            assertFalse(e.getMessage().contains("class not found"));
        }

        try {
            mm.new_(cm);
        } catch (IllegalStateException e) {
            assertFalse(e.getMessage().contains("class not found"));
        }
    }
}
