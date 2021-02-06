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

package org.cojen.example;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import org.cojen.maker.Bootstrap;
import org.cojen.maker.ClassMaker;
import org.cojen.maker.Label;
import org.cojen.maker.MethodMaker;
import org.cojen.maker.Variable;

/**
 * Example bootstrap utility for generating hashCode, equals, and toString methods. It simply
 * examines all of the instance fields.
 *
 * @author Brian S O'Neill
 */
public class ObjectMethods {
    /**
     * Test method.
     */
    public static void main(String[] args) throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        cm.addField(String.class, "name").private_().final_();
        cm.addField(int.class, "status").private_().final_();

        {
            MethodMaker mm = cm.addConstructor(String.class, int.class).public_();
            mm.invokeSuperConstructor();
            mm.field("name").set(mm.param(0));
            mm.field("status").set(mm.param(1));
        }

        makeHashCode(cm);
        makeEquals(cm);
        makeToString(cm);

        var clazz = cm.finish();
        var ctor = clazz.getConstructor(String.class, int.class);

        Object[] instances = {
            ctor.newInstance("bob", 10),
            ctor.newInstance("bob", 10),
            ctor.newInstance("bob", 11),
            ctor.newInstance("jane", 11),
            ctor.newInstance(null, 0),
        };

        for (Object instance : instances) {
            System.out.println(instance + ", " + instance.hashCode());
        }

        System.out.println("---");

        for (Object a : instances) {
            for (Object b : instances) {
                System.out.println(a + ".equals(" + b + "): " + a.equals(b));
            }
        }
    }

    public static void makeHashCode(ClassMaker cm) {
        MethodMaker mm = cm.addMethod(int.class, "hashCode").public_();
        var bootstrap = mm.var(ObjectMethods.class).indy("bootstrap");
        mm.return_(bootstrap.invoke(int.class, "hashCode", new Object[]{cm}, mm.this_()));
    }

    public static void makeEquals(ClassMaker cm) {
        MethodMaker mm = cm.addMethod(boolean.class, "equals", Object.class).public_();
        var bootstrap = mm.var(ObjectMethods.class).indy("bootstrap");
        mm.return_(bootstrap.invoke(boolean.class, "equals",
                                    new Object[]{cm, Object.class},
                                    mm.this_(), mm.param(0)));
    }

    public static void makeToString(ClassMaker cm) {
        MethodMaker mm = cm.addMethod(String.class, "toString").public_();
        var bootstrap = mm.var(ObjectMethods.class).indy("bootstrap");
        mm.return_(bootstrap.invoke(String.class, "toString", new Object[]{cm}, mm.this_()));
    }

    public static CallSite bootstrap(MethodHandles.Lookup lookup,
                                     String methodName,
                                     MethodType type)
    {
        Class<?> targetClass = lookup.lookupClass();
        MethodMaker mm = MethodMaker.begin(lookup, methodName, type);

        var fields = targetClass.getDeclaredFields();

        switch (methodName) {
        case "hashCode":
            bootHashCode(lookup, mm, fields);
            break;
        case "equals":
            bootEquals(lookup, mm, fields, targetClass);
            break;
        case "toString":
            bootToString(lookup, mm, fields, targetClass.getSimpleName());
            break;
        default:
            throw new Error();
        }

        return new ConstantCallSite(mm.finish());
    }

    /**
     * Returns a VarHandle to the field, enabling private access.
     *
     * @return null if unsupported
     */
    private static VarHandle varHandle(MethodHandles.Lookup lookup, Field field) {
        if (!Modifier.isStatic(field.getModifiers())) {
            try {
                return lookup.unreflectVarHandle(field);
            } catch (IllegalAccessException e) {
            }
        }
        return null;
    }

    private static void bootHashCode(MethodHandles.Lookup lookup, MethodMaker mm, Field[] fields) {
        var target = mm.param(0);
        Variable result = null;

        for (Field field : fields) {
            VarHandle vh = varHandle(lookup, field);
            if (vh != null) {
                Class<?> fieldType = field.getType();

                Class invoker;
                String method = "hashCode";

                if (fieldType.isPrimitive()) {
                    invoker = fieldType;
                } else if (!fieldType.isArray()) {
                    invoker = Objects.class;
                } else {
                    invoker = Arrays.class;
                    if (fieldType.getComponentType().isArray()) {
                        method = "deepHashCode";
                    }
                }

                final var subHash = mm.var(invoker).invoke(method, mm.access(vh, target));

                if (result == null) {
                    result = mm.var(int.class).set(subHash);
                } else {
                    result.set(result.mul(31).add(subHash));
                }
            }
        }

        mm.return_(result == null ? 0 : result);
    }

    private static void bootEquals(MethodHandles.Lookup lookup,
                                   MethodMaker mm, Field[] fields, Class<?> targetClass)
    {
        var target = mm.param(0);
        var other = mm.param(1);

        // Quick instance equality check.
        Label L1 = mm.label();
        target.ifNe(other, L1);
        mm.return_(true);

        // InstanceOf check.
        L1.here();
        Label notEqual = mm.label();
        target.instanceOf(targetClass).ifFalse(notEqual);

        // Check all the fields.

        other = other.cast(targetClass);

        for (Field field : fields) {
            VarHandle vh = varHandle(lookup, field);
            if (vh != null) {
                Class<?> fieldType = field.getType();

                var targetField = mm.access(vh, target);
                var otherField = mm.access(vh, other);

                if (fieldType.isPrimitive()) {
                    targetField.ifNe(otherField, notEqual);
                } else {
                    String method = fieldType.isArray() ? "deepEquals" : "equals";
                    mm.var(Objects.class).invoke(method, targetField, otherField).ifFalse(notEqual);
                }
            }
        }

        mm.return_(true);

        notEqual.here();
        mm.return_(false);
    }

    private static void bootToString(MethodHandles.Lookup lookup,
                                     MethodMaker mm, Field[] fields, String prefix)
    {
        var target = mm.param(0);
        var toConcat = new ArrayList<Object>();

        toConcat.add(prefix);
        toConcat.add('[');

        boolean any = false;
        for (Field field : fields) {
            VarHandle vh = varHandle(lookup, field);
            if (vh != null) {
                if (any) {
                    toConcat.add(", ");
                }
                toConcat.add(field.getName());
                toConcat.add('=');
                toConcat.add(mm.access(vh, target));
                any = true;
            }
        }

        toConcat.add(']');

        mm.return_(mm.concat(toConcat.toArray()));
    }
}
