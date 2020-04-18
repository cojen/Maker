/*
 *  Copyright (C) 2019 Cojen.org
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

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import java.lang.reflect.Modifier;

import java.security.ProtectionDomain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
final class TheClassMaker extends Attributed implements ClassMaker {
    static final boolean DEBUG = Boolean.getBoolean(ClassMaker.class.getName() + ".DEBUG");

    private final ClassLoader mParentLoader;
    private final Class mHostClass;
    private final ClassInjector.Reservation mReservation;

    final ConstantPool.C_Class mThisClass;
    final ConstantPool.C_Class mSuperClass;

    private int mModifiers;

    private List<ConstantPool.C_Class> mInterfaces;
    private Map<String, TheFieldMaker> mFields;
    private List<TheMethodMaker> mMethods;

    private boolean mHasConstructor;

    private ArrayList<TheMethodMaker> mClinitMethods;

    private Map<ConstantPool.C_Field, String> mVarHandles;
    private MethodMaker mVarHandleClinit;

    private Attribute.BootstrapMethods mBootstrapMethods;

    private boolean mFinished;

    TheClassMaker(String className, String superClassName,
                  ClassLoader parentLoader, ProtectionDomain domain)
    {
        this(className, superClassName, parentLoader, domain, null);
    }

    TheClassMaker(String className, String superClassName, Class hostClass) {
        this(className, superClassName, null, null, hostClass);
    }

    private TheClassMaker(String className, String superClassName,
                          ClassLoader parentLoader, ProtectionDomain domain,
                          Class hostClass)
    {
        super(new ConstantPool());

        mParentLoader = parentLoader;
        mHostClass = hostClass;

        if (hostClass == null) {
            mReservation = ClassInjector.reserve(className, parentLoader, domain, false);
            className = mReservation.mClassName;
        } else {
            if (className == null) {
                throw new NullPointerException("Class name is required");
            }
            mReservation = null;
        }

        if (superClassName == null) {
            String rootName = Object.class.getName();
            if (!className.equals(rootName)) {
                superClassName = rootName;
            }
        }

        Type superType;
        if (superClassName == null) {
            superType = null;
            mSuperClass = null;
        } else {
            Object superClass = superClassName;
            try {
                if (parentLoader == null) {
                    superClass = Class.forName(superClassName);
                } else {
                    superClass = parentLoader.loadClass(superClassName);
                }
            } catch (ClassNotFoundException e) {
            }

            superType = typeFrom(superClass);
            mSuperClass = mConstants.addClass(superType);
        }

        mThisClass = mConstants.addClass(Type.from(mParentLoader, className, superType));
    }

    @Override
    public void finishTo(DataOutput dout) throws IOException {
        if (mFinished) {
            throw new IllegalStateException("Already finished");
        }

        mFinished = true;

        mVarHandles = null;
        mVarHandleClinit = null;

        mBootstrapMethods = null;

        TheMethodMaker.finish(mClinitMethods);
        mClinitMethods = null;

        checkSize(mInterfaces, 65535, "Interface");
        checkSize(mFields, 65535, "Field");
        checkSize(mMethods, 65535, "Method");

        if (mMethods != null) {
            for (TheMethodMaker method : mMethods) {
                method.finish();
            }
        }

        dout.writeInt(0xCAFEBABE);
        dout.writeInt(0x0000_0035); // Java version 9

        mConstants.writeTo(dout);

        int flags = mModifiers;
        if (!Modifier.isInterface(flags)) {
            // Set the ACC_SUPER flag for classes only.
            flags |= Modifier.SYNCHRONIZED;
        }
        dout.writeShort(flags);

        dout.writeShort(mThisClass.mIndex);
        dout.writeShort(mSuperClass == null ? 0 : mSuperClass.mIndex);

        if (mInterfaces == null) {
            dout.writeShort(0);
        } else {
            dout.writeShort(mInterfaces.size());
            for (ConstantPool.C_Class iface : mInterfaces) {
                dout.writeShort(iface.mIndex);
            }
            mInterfaces = null;
        }

        if (mFields == null) {
            dout.writeShort(0);
        } else {
            dout.writeShort(mFields.size());
            for (TheFieldMaker field : mFields.values()) {
                field.writeTo(dout);
            }
            mFields = null;
        }

        if (mMethods == null) {
            dout.writeShort(0);
        } else {
            dout.writeShort(mMethods.size());
            for (TheMethodMaker method : mMethods) {
                method.writeTo(dout);
            }
            mMethods = null;
        }

        writeAttributesTo(dout);

        mAttributes = null;
    }

    static void checkSize(Map<?,?> c, int maxSize, String desc) {
        if (c != null) {
            checkSize(c.keySet(), maxSize, desc);
        }
    }

    static void checkSize(Collection<?> c, int maxSize, String desc) {
        if (c != null && c.size() > maxSize) {
            throw new IllegalStateException
                (desc + " count cannot exceed " + maxSize + ": " + c.size());
        }
    }

    @Override
    public ClassMaker public_() {
        checkFinished();
        mModifiers = Modifiers.toPublic(mModifiers);
        return this;
    }

    @Override
    public ClassMaker final_() {
        checkFinished();
        mModifiers = Modifiers.toFinal(mModifiers);
        return this;
    }

    @Override
    public ClassMaker interface_() {
        checkFinished();
        mModifiers = Modifiers.toInterface(mModifiers);
        return this;
    }

    @Override
    public ClassMaker abstract_() {
        checkFinished();
        mModifiers = Modifiers.toAbstract(mModifiers);
        return this;
    }

    @Override
    public ClassMaker implement(String interfaceName) {
        requireNonNull(interfaceName);
        checkFinished();
        if (mInterfaces == null) {
            mInterfaces = new ArrayList<>(4);
        }
        mInterfaces.add(mConstants.addClass(typeFrom(interfaceName)));
        return this;
    }

    @Override
    public TheFieldMaker addField(Object type, String name) {
        requireNonNull(type);
        requireNonNull(name);

        checkFinished();

        if (mFields == null) {
            mFields = new LinkedHashMap<>();
        } else if (mFields.containsKey(name)) {
            throw new IllegalStateException("Field is already defined: " + name);
        }

        Type tType = typeFrom(type);

        var fm = new TheFieldMaker(mConstants, mThisClass.mType.defineField(false, tType, name));
        mFields.put(name, fm);

        return fm;
    }
 
    @Override
    public TheMethodMaker addMethod(Object retType, String name, Object... paramTypes) {
        if (name.equals("<clinit>")) {
            throw new IllegalArgumentException("Use the addClinit method");
        }
        if (name.equals("<init>")) {
            throw new IllegalArgumentException("Use the addConstructor method");
        }
        checkFinished();
        return doAddMethod(retType, name, paramTypes);
    }

    @Override
    public TheMethodMaker addConstructor(Object... paramTypes) {
        checkFinished();
        return doAddMethod(null, "<init>", paramTypes);
    }

    private TheMethodMaker doAddMethod(Object retType, String name, Object... paramTypes) {
        if (mMethods == null) {
            mMethods = new ArrayList<>();
        }

        Type tRetType = retType == null ? Type.VOID : typeFrom(retType);

        Type[] tParamTypes;
        if (paramTypes == null) {
            tParamTypes = new Type[0];
        } else {
            tParamTypes = new Type[paramTypes.length];
            for (int i=0; i<paramTypes.length; i++) {
                tParamTypes[i] = typeFrom(paramTypes[i]);
            }
        }

        Type.Method method = mThisClass.mType.defineMethod(false, tRetType, name, tParamTypes);

        TheMethodMaker mm = new TheMethodMaker(this, method);
        mMethods.add(mm);

        if (!mHasConstructor && name.equals("<init>")) {
            mHasConstructor = true;
        }

        return mm;
    }

    @Override
    public TheMethodMaker addClinit() {
        checkFinished();

        TheMethodMaker mm;
        if (mClinitMethods == null) {
            mClinitMethods = new ArrayList<>();
            mm = doAddMethod(null, "<clinit>");
        } else {
            mm = new TheMethodMaker(mClinitMethods.get(mClinitMethods.size() - 1));
        }

        mm.static_();
        mm.useReturnLabel();
        mClinitMethods.add(mm);
        return mm;
    }

    @Override
    public ClassMaker sourceFile(String fileName) {
        checkFinished();
        addAttribute(new Attribute.SourceFile(mConstants, fileName));
        return this;
    }

    @Override
    public String name() {
        return mThisClass.mType.name();
    }

    @Override
    public Class<?> finish() {
        byte[] bytes = finishBytes();

        if (mReservation != null) {
            return mReservation.mInjector.define(name(), bytes);
        } else {
            // FIXME: Need JDK 15. Anonymous class cannot refer to self.
            //return UNSAFE.defineAnonymousClass(mHostClass, bytes, null);
            throw null;
        }
    }

    public byte[] finishBytes() {
        byte[] bytes;
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(1000);
            finishTo(bout);
            bytes = bout.toByteArray();
        } catch (IOException e) {
            // Not expected.
            throw new RuntimeException(e);
        }

        if (DEBUG) {
            File file = new File(name().replace('.', '/') + ".class");
            try {
                File tempDir = new File(System.getProperty("java.io.tmpdir"));
                file = new File(tempDir, file.getPath());
            } catch (SecurityException e) {
            }
            try {
                file.getParentFile().mkdirs();
                System.out.println("ClassMaker writing to " + file);
                try (var out = new FileOutputStream(file)) {
                    out.write(bytes);
                }
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }

        return bytes;
    }

    private void checkFinished() {
        if (mFinished) {
            throw new IllegalStateException("Class definition is finished");
        }
    }

    @Override
    public String toString() {
        return "ClassMaker {name=" + name() + '}';
    }

    Type typeFrom(Object type) {
        return Type.from(mParentLoader, type);
    }

    /**
     * Defines (just once) a private static final VarHandle field in this class.
     *
     * @param fieldRef field to reference via a VarHandle
     * @return VarHandle field name
     */
    String varHandleField(ConstantPool.C_Field fieldRef) {
        if (mVarHandles == null) {
            mVarHandles = new HashMap<>();
        }

        String varHandleName = mVarHandles.get(fieldRef);

        if (varHandleName != null) {
            return varHandleName;
        }

        MethodMaker clinit = mVarHandleClinit;
        if (clinit == null) {
            mVarHandleClinit = clinit = addClinit();
        }

        varHandleName = "VARHANDLE$" + mVarHandles.size();
        addField(VarHandle.class, varHandleName).private_().static_().final_();

        Field varHandle = clinit.field(varHandleName);

        String findMethod = fieldRef.mField.isStatic() ? "findStaticVarHandle" : "findVarHandle";

        varHandle.set(clinit.invokeStatic(MethodHandles.class, "lookup")
                      .invoke(findMethod,
                              clinit.var(Class.class).set(fieldRef.mClass.mType),
                              fieldRef.mNameAndType.mName.mValue,
                              clinit.var(Class.class).set(fieldRef.mField.type()) ));

        mVarHandles.put(fieldRef, varHandleName);

        return varHandleName;
    }

    /**
     * @return bootstrap index
     */
    int addBootstrapMethod(ConstantPool.C_MethodHandle method, ConstantPool.Constant[] args) {
        if (mBootstrapMethods == null) {
            mBootstrapMethods = new Attribute.BootstrapMethods(mConstants);
            addAttribute(mBootstrapMethods);
        }
        return mBootstrapMethods.add(method, args);
    }
}
