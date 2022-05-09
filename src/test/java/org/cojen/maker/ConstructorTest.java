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

import java.lang.invoke.MethodType;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class ConstructorTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ConstructorTest.class.getName());
    }

    @Test
    public void withMethodType() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        try {
            cm.addConstructor(MethodType.methodType(int.class));
            fail();
        } catch (IllegalArgumentException e) {
            // Expected, since return type is int.
        }

        MethodMaker mm = cm.addConstructor(MethodType.methodType(void.class)).public_();
        mm.invokeSuperConstructor();

        mm = cm.addConstructor(MethodType.methodType(void.class, int.class, String.class));
        mm.public_();
        mm.invokeSuperConstructor();

        var clazz = cm.finish();

        clazz.getConstructor().newInstance();
        clazz.getConstructor(int.class, String.class).newInstance(10, "hello");
    }

    @Test
    public void broken() throws Exception {
        try {
            ClassMaker cm = ClassMaker.begin();
            MethodMaker mm = cm.addConstructor();
            mm.nop();
            cm.finish();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("never invoked"));
        }

        try {
            ClassMaker cm = ClassMaker.begin();
            MethodMaker mm = cm.addConstructor();
            mm.return_();
            mm.invokeSuperConstructor();
            cm.finish();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("never invoked"));
        }

        try {
            ClassMaker cm = ClassMaker.begin();
            MethodMaker mm = cm.addConstructor();
            mm.invokeSuperConstructor();
            mm.invokeSuperConstructor();
            cm.finish();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("invoked multiple"));
        }

        try {
            ClassMaker cm = ClassMaker.begin();
            MethodMaker mm = cm.addConstructor();
            mm.invokeThisConstructor();
            mm.invokeSuperConstructor();
            cm.finish();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("invoked multiple"));
        }

        {
            ClassMaker cm = ClassMaker.begin();
            MethodMaker mm = cm.addConstructor(boolean.class);
            Label a = mm.label();
            mm.param(0).ifTrue(a);
            Label b = mm.label().goto_();
            mm.invokeSuperConstructor(); // dead code
            a.here();
            mm.invokeSuperConstructor();
            b.here();
            cm.finish();
        }

        try {
            ClassMaker cm = ClassMaker.begin();
            MethodMaker mm = cm.addConstructor(boolean.class);
            Label a = mm.label();
            mm.param(0).ifTrue(a);
            mm.invokeSuperConstructor();
            Label b = mm.label().goto_();
            a.here();
            mm.invokeSuperConstructor();
            b.here();
            cm.finish();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("invoked multiple"));
        }

        // The remaining cases aren't detected, and instead a VerifyError is thrown. This isn't
        // a feature, it's a limitation.

        try {
            ClassMaker cm = ClassMaker.begin();
            MethodMaker mm = cm.addConstructor();
            Label a = mm.label().here();
            mm.invokeThisConstructor();
            a.goto_();
            var clazz = cm.finish();
            clazz.getConstructor().newInstance();
            fail();
        } catch (VerifyError e) {
        }

        try {
            ClassMaker cm = ClassMaker.begin();
            MethodMaker mm = cm.addConstructor(boolean.class);
            Label a = mm.label();
            mm.param(0).ifTrue(a);
            mm.invokeSuperConstructor();
            a.here();
            var clazz = cm.finish();
            clazz.getConstructor().newInstance();
            fail();
        } catch (VerifyError e) {
        }
    }
}
