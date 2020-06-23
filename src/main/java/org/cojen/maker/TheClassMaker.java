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
import java.util.Set;

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

    private final ClassLoader mParentLoader;
    private final ClassInjector.Reservation mReservation;

    final ConstantPool.C_Class mThisClass;
    final ConstantPool.C_Class mSuperClass;

    // Stashed by Type.begin to prevent GC of this type being defined.
    Object mTypeCache;

    private int mModifiers;

    private Set<ConstantPool.C_Class> mInterfaces;
    private Map<String, TheFieldMaker> mFields;
    private List<TheMethodMaker> mMethods;

    private boolean mHasConstructor;

    private ArrayList<TheMethodMaker> mClinitMethods;

    private Attribute.BootstrapMethods mBootstrapMethods;

    // -1: finished, 0: not finished, 1: has complex constants
    private int mFinished;

    TheClassMaker(String className, String superClassName,
                  ClassLoader parentLoader, ProtectionDomain domain)
    {
        super(new ConstantPool());

        if (parentLoader == null) {
            parentLoader = getClass().getClassLoader();
        }

        mParentLoader = parentLoader;
        
        mReservation = ClassInjector.lookup(parentLoader, domain).reserve(className, false);
        className = mReservation.mClassName;

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
                superClass = Class.forName(superClassName, true, parentLoader);
            } catch (ClassNotFoundException e) {
            }

            superType = typeFrom(superClass);
            mSuperClass = mConstants.addClass(superType);
        }

        mThisClass = mConstants.addClass(Type.begin(mParentLoader, this, className, superType));
    }

    @Override
    public void finishTo(DataOutput dout) throws IOException {
        finishTo(dout, false);
    }

    private void finishTo(DataOutput dout, boolean hidden) throws IOException {
        checkFinished();

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
    public ClassMaker implement(String interfaceName) {
        requireNonNull(interfaceName);
        checkFinished();
        if (mInterfaces == null) {
            mInterfaces = new LinkedHashSet<>(4);
        }
        mInterfaces.add(mConstants.addClass(typeFrom(interfaceName)));
        type().resetInterfaces();
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
        Class clazz = mReservation.mInjector.define(name, finishBytes(false));

        Type.uncache(mTypeCache, name);

        if (hasComplexConstants) {
            ConstantsRegistry.finish(this, clazz);
        }

        return clazz;
    }

    @Override
    public MethodHandles.Lookup finishHidden(MethodHandles.Lookup lookup)
        throws IllegalAccessException
    {
        Method m = defineHidden();
        Object options = cHiddenClassOptions;

        if (lookup == null) {
            lookup = MethodHandles.lookup();
        }

        boolean hasComplexConstants = mFinished == 1;
        byte[] bytes = finishBytes(true);

        MethodHandles.Lookup result;
        try {
            if (options == null) {
                var clazz = (Class<?>) m.invoke(cUnsafe, lookup.lookupClass(), bytes, null);
                result = MethodHandles.lookup().in(clazz);
            } else {
                result = ((MethodHandles.Lookup) m.invoke(lookup, bytes, false, options));
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
        }

        Type.uncache(mTypeCache, name());

        if (hasComplexConstants) {
            ConstantsRegistry.finish(this, result.lookupClass());
        }

        return result;
    }

    /**
     * @param hidden when true, class is renamed first to strip off the generated identifier
     */
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
        return Type.from(mParentLoader, type);
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
