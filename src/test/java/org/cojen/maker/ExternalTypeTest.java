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

import java.lang.reflect.Modifier;

import java.util.HashMap;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class ExternalTypeTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ExternalTypeTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        var provider = new Type.Provider() {
            @Override
            public void init(ClassMaker cm) {
                cm.public_().synthetic();
                cm.extend(java.util.AbstractList.class);
                cm.implement(java.util.RandomAccess.class);
                try {
                    cm.extend(Object.class);
                    fail();
                } catch (IllegalStateException e) {
                }
                cm.addClinit().label().here();
            }

            @Override
            public void addFields(ClassMaker cm) {
                cm.addField(String.class, "tf1").protected_().final_();
            }

            @Override
            public void addMethods(ClassMaker cm) {
                MethodMaker mm = cm.addMethod(int.class, "tm1", int.class).public_();
                mm.return_(mm.param(0).add(mm.param(1)));
                cm.addMethod(null, "nop").private_().signature();
            }

            @Override
            public void addConstructors(ClassMaker cm) {
                cm.addConstructor().public_();
            }
        };

        Type t1 = Type.external("ext.Type1", provider);

        var cm = ClassMaker.begin().extend(t1);

        MethodMaker ctor = cm.addConstructor();
        ctor.invokeSuperConstructor();

        MethodMaker mm = cm.addMethod(null, "a");
        mm.invoke("tm1", 10);
        mm.field("tf1").invoke("length");
        mm.invoke("size");
        mm.field("modCount");
    }

    @Test
    public void modifiers() throws Exception {
        var fref = new FieldMaker[1];
        var mref = new MethodMaker[1];

        var provider = new Type.Provider() {
            @Override
            public void init(ClassMaker cm) {
                fref[0] = cm.addField(int.class, "f");
                mref[0] = cm.addMethod(int.class, "m");
            }

            @Override
            public void addFields(ClassMaker cm) {
            }

            @Override
            public void addMethods(ClassMaker cm) {
            }

            @Override
            public void addConstructors(ClassMaker cm) {
            }
        };

        Type t = Type.external("abc", provider);

        // Force init.
        assertFalse(t.isInterface());

        FieldMaker fm = fref[0];
        var field = (BaseType.Field) ((ExternalType.MMaker) fm).member;

        assertFalse(Modifier.isPublic(field.mFlags));
        fm.public_();
        assertTrue(Modifier.isPublic(field.mFlags));
        fm.private_();
        assertTrue(Modifier.isPrivate(field.mFlags));
        fm.protected_();
        assertTrue(Modifier.isProtected(field.mFlags));
        fm.static_();
        assertTrue(Modifier.isStatic(field.mFlags));
        fm.final_();
        assertTrue(Modifier.isFinal(field.mFlags));
        fm.volatile_();
        assertTrue(Modifier.isVolatile(field.mFlags));
        fm.transient_();
        assertTrue(Modifier.isTransient(field.mFlags));
        fm.synthetic();
        assertTrue((field.mFlags & 0x1000) != 0);
        fm.enum_();
        assertTrue((field.mFlags & 0x4000) != 0);

        MethodMaker mm = mref[0];
        var method = (BaseType.Method) ((ExternalType.MMaker) mm).member;

        assertFalse(Modifier.isSynchronized(method.mFlags));
        mm.synchronized_();
        assertTrue(Modifier.isSynchronized(method.mFlags));
        mm.abstract_();
        assertTrue(Modifier.isAbstract(method.mFlags));
        mm.native_();
        assertTrue(Modifier.isNative(method.mFlags));
        mm.bridge();
        assertTrue(Modifier.isVolatile(method.mFlags));
        mm.varargs();
        assertTrue(Modifier.isTransient(method.mFlags));
    }

    @Test
    public void asRecord() throws Exception {
        var provider = new Type.Provider() {
            private HashMap<String, Boolean> mInitMap = new HashMap<>();

            @Override
            public void init(ClassMaker cm) {
                if (mInitMap.putIfAbsent(cm.name(), true) == null) {
                    if (cm.name().equals("ext.Type1")) { 
                        cm.addField(String.class, "name");
                    }
                    cm.asRecord();
                }
            }

            @Override
            public void addFields(ClassMaker cm) {
                init(cm);
            }

            @Override
            public void addMethods(ClassMaker cm) {
                init(cm);
            }

            @Override
            public void addConstructors(ClassMaker cm) {
                init(cm);
            }
        };

        Type t1 = Type.external("ext.Type1", provider);
        Type t2 = Type.external("ext.Type2", provider);

        var cm = ClassMaker.begin();
        MethodMaker mm = cm.addMethod(null, "test");
        mm.new_(t1, "hello");
        mm.new_(t2);
    }

    @Test
    public void misc() throws Exception {
        var ref = new ClassMaker[1];

        var provider = new Type.Provider() {
            @Override
            public void init(ClassMaker cm) {
                ref[0] = cm;
                cm.interface_();
            }

            @Override
            public void addFields(ClassMaker cm) {
            }

            @Override
            public void addMethods(ClassMaker cm) {
            }

            @Override
            public void addConstructors(ClassMaker cm) {
            }
        };

        Type t = Type.external("xyz", provider);

        assertTrue(t.isInterface());

        var bt = (BaseType) t;

        assertEquals(BaseType.from(Object.class), bt.superType());
        assertTrue(bt.interfaces().isEmpty());
        assertTrue(bt.fields().isEmpty());
        assertTrue(bt.methods().isEmpty());

        ClassMaker cm = ref[0];

        assertEquals(cm, cm.classMaker());

        cm = cm.public_();
        cm = cm.private_();
        cm = cm.protected_();
        cm = cm.static_();
        cm = cm.final_();
        cm = cm.interface_();
        cm = cm.abstract_();
        cm = cm.synthetic();
        cm = cm.enum_();
        cm = cm.annotation();
        cm = cm.signature();
        cm = cm.permitSubclass(HashMap.class);
        cm = cm.sourceFile("");

        assertNull(cm.classLoader());
        assertTrue(cm.installClass(String.class));
        assertTrue(cm.unimplementedMethods().isEmpty());

        cm.addAttribute("hello", "world");

        AnnotationMaker am = cm.addAnnotation(String.class, true);
        am.newAnnotation(FunctionalInterface.class).put("hello", "world");

        try {
            cm.finish();
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            cm.finishLookup();
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            cm.finishHidden();
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            cm.finishBytes();
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            cm.finishTo(System.out);
            fail();
        } catch (IllegalStateException e) {
        }

        assertEquals(1, bt.interfaces().size());
        assertTrue(bt.fields().isEmpty());
        assertTrue(bt.methods().isEmpty());

        assertEquals("An", cm.another("An").name());

        try {
            cm.addInnerClass(null);
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            cm.addInnerClass("a.b");
            fail();
        } catch (IllegalArgumentException e) {
        }

        assertEquals("xyz$abc", cm.addInnerClass("abc").name());
    }
}
