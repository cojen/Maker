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

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.IncompleteAnnotationException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import java.lang.module.ModuleDescriptor;

import java.lang.reflect.InvocationTargetException;

import java.io.ByteArrayInputStream;

import java.util.List;
import java.util.Set;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class AttributeTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(AttributeTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();

        try {
            cm.addAttribute(null, null);
            fail();
        } catch (NullPointerException e) {
        }

        try {
            cm.addAttribute("Junk", new StringBuilder());
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            cm.addAttribute("Junk", new int[10]);
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            cm.addAttribute("Junk", java.math.BigDecimal.ZERO);
            fail();
        } catch (IllegalArgumentException e) {
        }

        cm.addAttribute("Synthetic", null);
        cm.addAttribute("SourceFile", "basic");

        MethodMaker mm = cm.addMethod(null, "test").public_().static_();
        mm.new_(Exception.class).throw_();

        Class<?> clazz = cm.finish();

        assertTrue(clazz.isSynthetic());

        try {
            clazz.getMethod("test").invoke(null);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertEquals("basic", cause.getStackTrace()[0].getFileName());
        }
    }

    @Test
    public void misc() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_();
        ClassMaker cm2 = ClassMaker.begin().public_().extend(cm);

        cm.addAttribute("Junk1", "hello");
        cm.addAttribute("Junk2", String.class);
        cm.addAttribute("Junk3", 10);
        cm.addAttribute("Junk4", 10L);
        cm.addAttribute("Junk5", 10.1f);
        cm.addAttribute("Junk6", 10.1d);
        cm.addAttribute("Junk7", new String[] {"hello"});
        cm.addAttribute("Junk8", new Integer[] {1, 2, Integer.MAX_VALUE});
        cm.addAttribute("Junk9", new Object[] {Integer.MAX_VALUE, "hello", cm});
        cm.addAttribute("Junk10", new Object[] {});
        cm.addAttribute("Junk11", new Object[] {null});

        // Encode an array of arrays, each with a one-byte array length prefix.
        cm.addAttribute("Junk12", new Object[] {
            new byte[] {2}, new Object[][] {
                {new byte[] {2}, "hello", 10}, {new byte[] {3}, 10L, 10.1d, cm}
            }
        });

        cm.addAttribute("SourceDebugExtension", new byte[10]);

        // The first value encodes the short array length in big-endian format.
        // Note: This doesn't seal the class unless the major version is 61 or higher (Java 17).
        cm.addAttribute("PermittedSubclasses", new Object[] {new byte[] {0, 1}, cm2});

        // Format for an enum default.
        cm.addAttribute("AnnotationDefault", new Object[] {
            new byte[] {'e'}, "java.lang.Thread.State", "NEW"
        });

        Class<?> clazz = cm.finish();
        Class<?> clazz2 = cm2.finish();
    }

    @Test
    public void module() throws Exception {
        var b = ModuleDescriptor.newModule
            ("test", Set.of(//ModuleDescriptor.Modifier.OPEN,
                            ModuleDescriptor.Modifier.SYNTHETIC,
                            ModuleDescriptor.Modifier.MANDATED));

        b.version("0.0.1");

        b.requires(Set.of(ModuleDescriptor.Requires.Modifier.TRANSITIVE,
                          ModuleDescriptor.Requires.Modifier.STATIC,
                          ModuleDescriptor.Requires.Modifier.SYNTHETIC,
                          ModuleDescriptor.Requires.Modifier.MANDATED),
                   "a.m1", ModuleDescriptor.Version.parse("1.2.3"));

        b.requires(Set.of(), "a.m2");

        b.exports(Set.of(ModuleDescriptor.Exports.Modifier.SYNTHETIC,
                         ModuleDescriptor.Exports.Modifier.MANDATED),
                  "a.p1", Set.of("a.m3"));

        b.opens(Set.of(ModuleDescriptor.Opens.Modifier.SYNTHETIC,
                       ModuleDescriptor.Opens.Modifier.MANDATED),
                "a.p2", Set.of("a.m4"));

        b.uses("java.time.chrono.Chronology").uses("java.security.Provider");

        b.provides(AttributeTest.class.getName(), List.of("a.c1"));

        b.packages(Set.of("a.p1", "a.p2"));

        b.mainClass("a.p1.Foo");

        var md = b.build();

        try {
            ClassMaker.beginExplicit("module-info", null, null).extend(Object.class)
                .addAttribute("Module", md);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            ClassMaker.beginExplicit("xxx", null, null).addAttribute("Module", md);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            ClassMaker.beginExplicit("module-info", null, null).addAttribute("xxx", md);
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            ClassMaker.begin().addClinit().addAttribute("Module", md);
            fail();
        } catch (IllegalArgumentException e) {
        }

        ClassMaker cm = ClassMaker.beginExplicit("module-info", null, null);
        cm.addAttribute("Module", md);

        byte[] bytes = cm.finishBytes();

        var actual = ModuleDescriptor.read(new ByteArrayInputStream(bytes));

        assertEquals(0, md.compareTo(actual));

        if (Runtime.version().feature() >= 20) {
            // https://bugs.openjdk.org/browse/JDK-8290041.
            assertEquals(md, actual);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void annotationDefault() throws Exception {
        Class annClass;
        {
            ClassMaker cm = ClassMaker.begin().public_().annotation();
            cm.addAnnotation(Retention.class, true)
                .put("value", RetentionPolicy.RUNTIME);
            cm.addAnnotation(Target.class, true)
                .put("value", new ElementType[] {ElementType.METHOD});
            {
                MethodMaker mm = cm.addMethod(int.class, "num").public_().abstract_();
                mm.addAttribute("AnnotationDefault", 100);
            }
            {
                MethodMaker mm = cm.addMethod(String.class, "name").public_().abstract_();
                mm.addAttribute("AnnotationDefault", "bob");
            }
            annClass = cm.finish();
        }

        ClassMaker cm = ClassMaker.begin().public_();

        {
            MethodMaker mm = cm.addMethod(null, "test1").public_().static_();
            mm.return_();
            mm.addAnnotation(annClass, true);
        }

        {
            MethodMaker mm = cm.addMethod(null, "test2").public_().static_();
            mm.return_();
            mm.addAnnotation(annClass, true).put("num", 99);
        }

        var clazz = cm.finish();

        {
            Object ann = clazz.getMethod("test1").getAnnotation(annClass);
            assertEquals(100, ann.getClass().getMethod("num").invoke(ann));
            assertEquals("bob", ann.getClass().getMethod("name").invoke(ann));
        }

        {
            Object ann = clazz.getMethod("test2").getAnnotation(annClass);
            assertEquals(99, ann.getClass().getMethod("num").invoke(ann));
            assertEquals("bob", ann.getClass().getMethod("name").invoke(ann));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void brokenAnnotationDefault() throws Throwable {
        {
            ClassMaker cm = ClassMaker.begin().public_().annotation();
            cm.addAnnotation(Retention.class, true)
                .put("value", RetentionPolicy.RUNTIME);
            cm.addAnnotation(Target.class, true)
                .put("value", new ElementType[] {ElementType.METHOD});

            MethodMaker mm = cm.addMethod(int.class, "num").public_().abstract_();

            // Adding the annotation to the class instead of the method.
            cm.addAttribute("AnnotationDefault", 100);

            Class annClass = cm.finish();

            cm = ClassMaker.begin().public_();
            mm = cm.addMethod(null, "test").public_().static_();
            mm.return_();
            mm.addAnnotation(annClass, true);

            var clazz = cm.finish();

            Object ann = clazz.getMethod("test").getAnnotation(annClass);
            try {
                ann.getClass().getMethod("num").invoke(ann);
                fail();
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (!(cause instanceof IncompleteAnnotationException)) {
                    throw cause;
                }
            }
        }

        {
            // Not calling the annotation method.
            ClassMaker cm = ClassMaker.begin().public_().interface_().implement(Annotation.class);
            cm.addAnnotation(Retention.class, true)
                .put("value", RetentionPolicy.RUNTIME);
            cm.addAnnotation(Target.class, true)
                .put("value", new ElementType[] {ElementType.METHOD});

            MethodMaker mm = cm.addMethod(int.class, "num").public_().abstract_();
            mm.addAttribute("AnnotationDefault", 100);

            Class annClass = cm.finish();

            cm = ClassMaker.begin().public_();
            mm = cm.addMethod(null, "test").public_().static_();
            mm.return_();
            mm.addAnnotation(annClass, true);

            var clazz = cm.finish();

            assertNull(clazz.getMethod("test").getAnnotation(annClass));
        }

        {
            ClassMaker cm = ClassMaker.begin().public_().annotation();
            cm.addAnnotation(Retention.class, true)
                .put("value", RetentionPolicy.RUNTIME);
            cm.addAnnotation(Target.class, true)
                .put("value", new ElementType[] {ElementType.METHOD});

            MethodMaker mm = cm.addMethod(int.class, "num").public_().abstract_();

            // Wrong name.
            mm.addAttribute("Fake", 100);

            Class annClass = cm.finish();

            cm = ClassMaker.begin().public_();
            mm = cm.addMethod(null, "test").public_().static_();
            mm.return_();
            mm.addAnnotation(annClass, true);

            var clazz = cm.finish();

            Object ann = clazz.getMethod("test").getAnnotation(annClass);
            try {
                ann.getClass().getMethod("num").invoke(ann);
                fail();
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (!(cause instanceof IncompleteAnnotationException)) {
                    throw cause;
                }
            }
        }
    }
}
