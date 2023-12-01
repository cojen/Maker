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

import java.lang.reflect.Field;
import java.lang.reflect.GenericSignatureFormatError;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class SignatureTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(SignatureTest.class.getName());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void basic() throws Exception {
        try {
            ClassMaker.begin().signature();
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            ClassMaker.begin().signature(5);
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            ClassMaker.begin().signature("x", 5);
            fail();
        } catch (IllegalArgumentException e) {
        }

        ClassMaker cm = ClassMaker.begin().public_().implement(Comparator.class)
            .signature(Object.class, Comparator.class, "<", String.class, ">");

        cm.addMethod(int.class, "compare", Object.class, Object.class).public_().return_(0);

        cm.addMethod(void.class, "nothing", Object.class, cm).public_().static_()
            .signature("<E:", Comparator.class, ">(TE;", cm, ")V");

        cm.addField(Comparator.class, "cmp").public_()
            .signature(Comparator.class, "<-", String.class, ">");

        cm.addField(Comparator.class, "cmp2").public_()
            .signature("Ljava/util/Comparator<-Ljava/lang/String;>;");

        cm.addField(Comparator.class, "cmp3").public_()
            .signature("Ljava/util/Comparator", "<-", String.class, ">;");

        cm.addField(Comparator.class, "cmp4").public_()
            .signature(Comparator.class, "<", Comparator.class, "<*>>");

        // Illegal syntax.
        cm.addField(Comparator.class, "cmp5").public_()
            .signature(Comparator.class, "<", Comparator.class, "<*>");

        Class clazz = cm.finish();

        Type[] ifaces = clazz.getGenericInterfaces();
        assertEquals(1, ifaces.length);
        assertEquals("java.util.Comparator<java.lang.String>", ifaces[0].getTypeName());

        Method m = clazz.getMethod("nothing", Object.class, clazz);
        TypeVariable[] tvars = m.getTypeParameters();
        assertEquals(1, tvars.length);
        assertEquals("E", tvars[0].getName());
        Type[] bounds = tvars[0].getBounds();
        assertEquals(1, bounds.length);
        assertEquals(Comparator.class, bounds[0]);
        Type[] params = m.getGenericParameterTypes();
        assertEquals(2, params.length);
        var tv = (TypeVariable) params[0];
        assertEquals("E", tv.getName());

        {
            Field f = clazz.getField("cmp");
            var ftype = (ParameterizedType) f.getGenericType();
            assertEquals("java.util.Comparator<? super java.lang.String>", ftype.getTypeName());
        }

        {
            Field f = clazz.getField("cmp2");
            var ftype = (ParameterizedType) f.getGenericType();
            assertEquals("java.util.Comparator<? super java.lang.String>", ftype.getTypeName());
        }

        {
            Field f = clazz.getField("cmp3");
            var ftype = (ParameterizedType) f.getGenericType();
            assertEquals("java.util.Comparator<? super java.lang.String>", ftype.getTypeName());
        }

        {
            Field f = clazz.getField("cmp4");
            var ftype = (ParameterizedType) f.getGenericType();
            assertEquals("java.util.Comparator<java.util.Comparator<?>>", ftype.getTypeName());
        }

        try {
            clazz.getField("cmp5").getGenericType();
            fail();
        } catch (GenericSignatureFormatError e) {
        }
    }

    @Test
    public void locals() throws Exception {
        // Note: Cannot verify that the local variable has a signature applied to it using
        // reflection. Instead, the class must be manually disassembled and inspected.

        ClassMaker cm = ClassMaker.begin().public_();

        MethodMaker mm = cm.addMethod(List.class, "list", String.class).public_().static_()
            .signature("(", String.class, ")", List.class, "<", String.class, ">");

        var list = mm.var(ArrayList.class)
            .signature(ArrayList.class, "<", String.class, ">").name("list");

        list.set(mm.new_(ArrayList.class));
        list.invoke("add", mm.param(0));
        mm.return_(list);

        Class<?> clazz = cm.finish();
        var result = (ArrayList) clazz.getMethod("list", String.class).invoke(null, "hello");

        assertEquals(1, result.size());
        assertEquals("hello", result.get(0));
    }
}
