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
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.lang.invoke.MethodHandles;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.security.ProtectionDomain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.concurrent.ThreadLocalRandom;

import static java.util.Objects.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
final class TheClassMaker extends Attributed implements ClassMaker {
    static final boolean DEBUG = Boolean.getBoolean(ClassMaker.class.getName() + ".DEBUG");

    private static volatile Method cDefineHidden;
    private static Object cHiddenClassOptions;
    private static Object cUnsafe;

    private static Method defineHidden() {
        Method m = cDefineHidden;

        if (m != null) {
            return m;
        }

        try {
            Object options = Array.newInstance
                (Class.forName("java.lang.invoke.MethodHandles$Lookup$ClassOption"), 0);
            m = MethodHandles.Lookup.class.getMethod
                ("defineHiddenClass", byte[].class, boolean.class, options.getClass());
            cHiddenClassOptions = options;
            cDefineHidden = m;
            return m;
        } catch (Throwable e) {
        }

        try {
            var unsafeClass = Class.forName("sun.misc.Unsafe");
            m = unsafeClass.getMethod
                ("defineAnonymousClass", Class.class, byte[].class, Object[].class);
            var field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            cUnsafe = field.get(null);
            cDefineHidden = m;
            return m;
        } catch (Throwable e) {
        }

        throw new UnsupportedOperationException("Cannot define hidden classes");
    }

    private final MethodHandles.Lookup mLookup;
    private final ClassInjector mClassInjector;

    final ConstantPool.C_Class mThisClass;

    private ConstantPool.C_Class mSuperClass;

    // Stashed by Type.begin to prevent GC of this type being defined.
    Object mTypeCache;

    private int mModifiers;

    private Set<ConstantPool.C_Class> mInterfaces;
    private Map<String, TheFieldMaker> mFields;
    private List<TheMethodMaker> mMethods;

    private boolean mHasConstructor;

    private ArrayList<TheMethodMaker> mClinitMethods;

    private Attribute.BootstrapMethods mBootstrapMethods;

    private Attribute.NestMembers mNestMembers;

    // -1: finished, 0: not finished, 1: has complex constants
    private int mFinished;

    static TheClassMaker begin(boolean explicit, String className,
                               ClassLoader parentLoader, ProtectionDomain domain,
                               MethodHandles.Lookup lookup)
    {
        if (parentLoader == null) {
            parentLoader = TheClassMaker.class.getClassLoader();
            if (parentLoader == null) {
                parentLoader = ClassLoader.getSystemClassLoader();
            }
        }

        ClassInjector injector = ClassInjector.lookup(explicit, parentLoader, domain);

        return new TheClassMaker(className, lookup, injector);
    }

    private TheClassMaker(String className, MethodHandles.Lookup lookup, ClassInjector injector) {
        super(new ConstantPool());

        mLookup = lookup;
        mClassInjector = injector;

        if (injector.isExplicit()) {
            Objects.requireNonNull(className);
        } else {
            // Only check the parent loader when it will be used directly. This avoids creating
            // useless class loading lock objects that never get cleaned up.
            className = injector.reserve(className, lookup != null);
        }

        mThisClass = mConstants.addClass(Type.begin(injector, this, className));
    }

    private TheClassMaker(TheClassMaker from, String className) {
        this(className, from.mLookup, from.mClassInjector);
    }

    @Override
    public ClassMaker another(String className) {
        return new TheClassMaker(this, className);
    }

    @Override
    public void finishTo(OutputStream out) throws IOException {
        DataOutput dout;
        if (out instanceof DataOutput) {
            dout = (DataOutput) out;
        } else {
            dout = new DataOutputStream(out);
        }
        finishTo(dout, false);
    }

    /**
     * @param hidden when true, rename the class
     */
    private void finishTo(DataOutput dout, boolean hidden) throws IOException {
        checkFinished();

        // Ensure that mSuperClass has been assigned.
        superClass();

        mFinished = -1;

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

        if (hidden) {
            // Clean up the generated class name. It will be given a unique name by the
            // defineHiddenClass or defineAnonymousClass method.
            String name = mThisClass.mValue.mValue;
            int ix = name.lastIndexOf('-');
            if (ix > 0) {
                mThisClass.rename(mConstants.addUTF8(name.substring(0, ix)));
            }
        }

        dout.writeInt(0xCAFEBABE);
        dout.writeInt(0x0000_0037); // Java 11.

        mConstants.writeTo(dout);

        int flags = mModifiers;
        if (!Modifier.isInterface(flags)) {
            // Set the ACC_SUPER flag for classes only.
            flags |= Modifier.SYNCHRONIZED;
        }
        dout.writeShort(flags);

        dout.writeShort(mThisClass.mIndex);
        dout.writeShort(mSuperClass.mIndex);

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
        type().toInterface();
        return this;
    }

    @Override
    public ClassMaker abstract_() {
        checkFinished();
        mModifiers = Modifiers.toAbstract(mModifiers);
        return this;
    }

    @Override
    public ClassMaker synthetic() {
        checkFinished();
        mModifiers = Modifiers.toSynthetic(mModifiers);
        return this;
    }

    @Override
    public ClassMaker extend(Object superClass) {
        requireNonNull(superClass);
        if (mSuperClass != null) {
            throw new IllegalStateException("Super class has already been assigned");
        }
        doExtend(superClass);
        return this;
    }

    private void doExtend(Object superClass) {
        mSuperClass = mConstants.addClass(typeFrom(superClass));
        type().resetInherited();
    }

    ConstantPool.C_Class superClass() {
        ConstantPool.C_Class superClass = mSuperClass;
        if (superClass == null) {
            doExtend(Object.class);
            superClass = mSuperClass;
        }
        return superClass;
    }

    Type superType() {
        return superClass().mType;
    }

    @Override
    public ClassMaker implement(Object iface) {
        requireNonNull(iface);
        checkFinished();
        if (mInterfaces == null) {
            mInterfaces = new LinkedHashSet<>(4);
        }
        mInterfaces.add(mConstants.addClass(typeFrom(iface)));
        type().resetInherited();
        return this;
    }

    /**
     * @param can be null initially
     * @return new or original set
     */
    Set<Type> allInterfaces(Set<Type> all) {
        if (mInterfaces != null) {
            if (all == null) {
                all = new LinkedHashSet<>(mInterfaces.size());
            }
            for (ConstantPool.C_Class clazz : mInterfaces) {
                Type type = clazz.mType;
                all.add(type);
                all.addAll(type.interfaces());
            }
        }

        return all;
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

        var fm = new TheFieldMaker(mConstants, type().defineField(false, tType, name));
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

        Type.Method method = type().defineMethod(false, tRetType, name, tParamTypes);

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
    public TheClassMaker addClass(String className) {
        checkFinished();

        String prefix = name();
        int ix = prefix.lastIndexOf('-');
        if (ix > 0) {
            prefix = prefix.substring(0, ix);
        }

        if (className == null) {
            className = prefix + '$' + (mNestMembers == null ? 0 : mNestMembers.size());
        } else {
            if (className.indexOf('.') >= 0) {
                throw new IllegalArgumentException("Not a simple name: " + className);
            }
            className = prefix + '$' + className;
        }

        var nest = new TheClassMaker(this, className);
        nest.setNestHost(type());

        if (mNestMembers == null) {
            mNestMembers = new Attribute.NestMembers(mConstants);
            addAttribute(mNestMembers);
        }

        mNestMembers.add(mConstants.addClass(nest.type()));

        return nest;
    }

    private void setNestHost(Type type) {
        addAttribute(new Attribute.NestHost(mConstants, mConstants.addClass(type)));
    }

    @Override
    public ClassMaker sourceFile(String fileName) {
        checkFinished();
        addAttribute(new Attribute.SourceFile(mConstants, fileName));
        return this;
    }

    @Override
    public String name() {
        return type().name();
    }

    @Override
    public Class<?> finish() {
        boolean hasComplexConstants = mFinished == 1;
        String name = name();

        Class clazz;
        if (mLookup == null) {
            clazz = mClassInjector.define(name, finishBytes(false));
        } else {
            try {
                clazz = mLookup.defineClass(finishBytes(false));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        Type.uncache(mTypeCache, name);

        if (hasComplexConstants) {
            ConstantsRegistry.finish(this, clazz);
        }

        return clazz;
    }

    @Override
    public MethodHandles.Lookup finishHidden() {
        if (mLookup == null) {
            throw new IllegalStateException("No lookup was provided to the begin method");
        }

        Method m = defineHidden();
        Object options = cHiddenClassOptions;

        boolean hasComplexConstants = mFinished == 1;
        String originalName = name();

        byte[] bytes = finishBytes(true);

        MethodHandles.Lookup result;
        try {
            if (options == null) {
                var clazz = (Class<?>) m.invoke(cUnsafe, mLookup.lookupClass(), bytes, null);
                result = MethodHandles.lookup().in(clazz);
            } else {
                result = ((MethodHandles.Lookup) m.invoke(mLookup, bytes, false, options));
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            if (cause == null) {
                cause = e;
            }
            throw new IllegalStateException(e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }

        Type.uncache(mTypeCache, originalName);

        if (hasComplexConstants) {
            ConstantsRegistry.finish(this, result.lookupClass());
        }

        return result;
    }

    byte[] finishBytes(boolean hidden) {
        byte[] bytes;
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(1000);
            finishTo(new DataOutputStream(bout), hidden);
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
        if (mFinished < 0) {
            throw new IllegalStateException("Class definition is finished");
        }
    }

    @Override
    public String toString() {
        return "ClassMaker {name=" + name() + '}';
    }

    Type type() {
        return mThisClass.mType;
    }

    Type typeFrom(Object type) {
        return Type.from(mClassInjector, type);
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

    /**
     * @return slot
     */
    int addComplexConstant(Object value) {
        checkFinished();
        mFinished = 1;
        return ConstantsRegistry.add(this, value);
    }
}
