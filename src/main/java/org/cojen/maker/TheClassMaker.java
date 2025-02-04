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

import java.io.IOException;
import java.io.OutputStream;

import java.lang.annotation.Annotation;

import java.lang.invoke.MethodHandles;

import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import java.util.concurrent.ThreadLocalRandom;

import static java.util.Objects.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
final class TheClassMaker extends Attributed implements ClassMaker, Typed {
    static final boolean DEBUG = Boolean.getBoolean(ClassMaker.class.getName() + ".DEBUG");

    private final TheClassMaker mParent;
    private boolean mExternal;
    private final MethodHandles.Lookup mLookup;
    private final ClassInjector mInjector;
    ClassInjector.Group mInjectorGroup; // also accessed by ClassInjector

    final ConstantPool.C_Class mThisClass;

    private ConstantPool.C_Class mSuperClass;

    int mModifiers = Modifiers.ACC_IDENTITY;

    private Set<ConstantPool.C_Class> mInterfaces;
    private LinkedHashMap<String, TheFieldMaker> mFields;
    private List<TheMethodMaker> mMethods;

    private ArrayList<TheMethodMaker> mClinitMethods;

    private Attribute.BootstrapMethods mBootstrapMethods;

    private Attribute.ConstantList mNestMembers;

    private Attribute.InnerClasses mInnerClasses;

    private Attribute.LoadableDescriptors mLoadableDescriptors;

    private Set<ConstantPool.C_Class> mPermittedSubclasses;

    private ArrayList<TheMethodMaker> mRecordCtors;

    // Accessed by ConstantsRegistry.
    Object mExactConstants;

    private IdentityHashMap<Object, Integer> mSharedExactConstants;

    // Accessed by Switcher.
    Map<Class<?>, Class<?>> mEnumMappers;

    static TheClassMaker begin(boolean external, String className, boolean explicit,
                               ClassLoader parentLoader, Object key, MethodHandles.Lookup lookup)
    {
        if (parentLoader == null) {
            parentLoader = ClassLoader.getSystemClassLoader();
        }

        ClassInjector injector = ClassInjector.find(explicit, parentLoader, key);

        return new TheClassMaker(null, external, className, lookup, injector);
    }

    private TheClassMaker(TheClassMaker parent, boolean external,
                          String className, MethodHandles.Lookup lookup, ClassInjector injector)
    {
        super(new ConstantPool());

        mParent = parent;
        mExternal = external;
        mLookup = lookup;
        mInjector = injector;

        className = injector.reserve(this, className, lookup == null);

        mThisClass = mConstants.addClass(BaseType.begin(injector, this, className));
    }

    private TheClassMaker(TheClassMaker from, String className) {
        this(from, from.mExternal, className, from.mLookup, from.mInjector);
    }

    // Called by ClassInjector.Group.
    TheClassMaker(String className, ClassInjector injector, ClassInjector.Group injectorGroup) {
        this(null, false, className, null, injector);
        mInjectorGroup = injectorGroup;
    }

    @Override
    public ClassMaker another(String className) {
        return new TheClassMaker(this, className);
    }

    @Override
    public ClassMaker classMaker() {
        return this;
    }

    @Override
    public ClassMaker public_() {
        checkFinished();
        mModifiers = Modifiers.toPublic(mModifiers);
        return this;
    }

    @Override
    public ClassMaker private_() {
        checkFinished();
        mModifiers = Modifiers.toPrivate(mModifiers);
        return this;
    }

    @Override
    public ClassMaker protected_() {
        checkFinished();
        mModifiers = Modifiers.toProtected(mModifiers);
        return this;
    }

    @Override
    public ClassMaker static_() {
        checkFinished();
        mModifiers = Modifiers.toStatic(mModifiers);
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
        mModifiers = Modifiers.toAbstractClass(mModifiers);
        return this;
    }

    @Override
    public ClassMaker synthetic() {
        checkFinished();
        mModifiers = Modifiers.toSynthetic(mModifiers);
        return this;
    }

    @Override
    public ClassMaker enum_() {
        checkFinished();
        mModifiers = Modifiers.toEnum(mModifiers);
        return this;
    }

    @Override
    public ClassMaker annotation() {
        if (!isAnnotation()) {
            interface_().implement(Annotation.class);
            mModifiers |= 0x2000;
        }
        return this;
    }

    boolean isAnnotation() {
        return (mModifiers & 0x2000) != 0;
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
        BaseType superType = typeFrom(superClass);
        mSuperClass = mConstants.addClass(superType);
        type().resetInherited();
        if (superType.isAnnotatable()) {
            superType.applyAnnotations(this, new TypeAnnotationMaker.Target2(0x10, 65535));
        }
    }

    ConstantPool.C_Class superClass() {
        ConstantPool.C_Class superClass = mSuperClass;
        if (superClass == null) {
            doExtend(Object.class);
            superClass = mSuperClass;
        }
        return superClass;
    }

    BaseType superType() {
        return superClass().mType;
    }

    @Override
    public ClassMaker implement(Object iface) {
        requireNonNull(iface);
        checkFinished();
        if (mInterfaces == null) {
            mInterfaces = new LinkedHashSet<>(4);
        }
        BaseType type = typeFrom(iface);
        mInterfaces.add(mConstants.addClass(type));
        type().resetInherited();
        if (type.isAnnotatable()) {
            type.applyAnnotations
                (this, new TypeAnnotationMaker.Target2(0x10, mInterfaces.size() - 1));
        }
        return this;
    }

    @Override
    public ClassMaker signature(Object... components) {
        checkFinished();
        addSignature(components);
        return this;
    }

    @Override
    public ClassMaker permitSubclass(Object subclass) {
        requireNonNull(subclass);
        checkFinished();
        if (mPermittedSubclasses == null) {
            mPermittedSubclasses = new LinkedHashSet<>(4);
        }
        mPermittedSubclasses.add(mConstants.addClass(typeFrom(subclass)));
        return this;
    }

    /**
     * @return empty set if no interfaces
     */
    Set<BaseType> allInterfaces() {
        Set<BaseType> all = null;

        if (mInterfaces != null) {
            all = new LinkedHashSet<>(mInterfaces.size());

            for (ConstantPool.C_Class clazz : mInterfaces) {
                BaseType type = clazz.mType;
                all.add(type);
                all.addAll(type.interfaces());
            }
        }

        for (BaseType s = superType(); s != null; s = s.superType()) {
            Set<BaseType> inherited = s.interfaces();
            if (inherited != null && !inherited.isEmpty()) {
                if (all == null) {
                    all = new LinkedHashSet<>(inherited.size());
                }
                all.addAll(inherited);
            }
        }

        return all == null ? Collections.emptySet() : all;
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

        BaseType tType = typeFrom(type);

        var fm = new TheFieldMaker(this, type().defineField(0, tType, name));
        mFields.put(name, fm);

        if (tType.isAnnotatable()) {
            tType.applyAnnotations(fm, new TypeAnnotationMaker.Target0(0x13));
        }

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
        var mm = new TheMethodMaker(this, defineMethod(retType, name, paramTypes));
        doAddMethod(mm);
        return mm;
    }

    void doAddMethod(TheMethodMaker mm) {
        if (mMethods == null) {
            mMethods = new ArrayList<>();
        }
        mMethods.add(mm);
    }

    BaseType.Method defineMethod(Object retType, String name, Object... paramTypes) {
        BaseType tRetType = retType == null ? BaseType.VOID : typeFrom(retType);

        BaseType[] tParamTypes;
        if (paramTypes == null) {
            tParamTypes = new BaseType[0];
        } else {
            tParamTypes = new BaseType[paramTypes.length];
            for (int i=0; i<paramTypes.length; i++) {
                tParamTypes[i] = typeFrom(paramTypes[i]);
            }
        }

        return type().defineMethod(0, tRetType, name, tParamTypes);
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
    public MethodMaker asRecord() {
        return AsRecord.apply(this);
    }

    @Override
    public TheClassMaker addInnerClass(String className) {
        return addInnerClass(className, null);
    }

    TheClassMaker addInnerClass(final String className, final BaseType.Method hostMethod) {
        TheClassMaker nestHost = nestHost(this);

        String prefix = name();
        int ix = prefix.lastIndexOf('-');
        if (ix > 0) {
            prefix = prefix.substring(0, ix);
        }

        var innerClasses = innerClasses();

        String fullName;

        if (className == null) {
            fullName = prefix + '$' + innerClasses.classNumberFor("");
        } else {
            if (className.indexOf('.') >= 0) {
                throw new IllegalArgumentException("Not a simple name: " + className);
            }
            if (hostMethod == null ||
                ((ix = prefix.indexOf('$')) >= 0 && ++ix < prefix.length()
                 && !Character.isJavaIdentifierStart(prefix.charAt(ix))))
            {
                fullName = prefix + '$' + className;
            } else {
                fullName = prefix + '$' + innerClasses.classNumberFor(className) + className;
            }
        }

        var clazz = new TheClassMaker(this, fullName);
        clazz.setNestHost(nestHost.type());
        nestHost.addNestMember(clazz.type());

        if (hostMethod != null) {
            clazz.setEnclosingMethod(type(), hostMethod);
        }

        innerClasses.add(clazz, this, className);
        clazz.innerClasses().add(clazz, this, className);

        return clazz;
    }

    private static TheClassMaker nestHost(TheClassMaker cm) {
        while (true) {
            cm.checkFinished();
            TheClassMaker parent = cm.mParent;
            if (parent == null) {
                return cm;
            }
            cm = parent;
        }
    }

    private void setNestHost(BaseType nestHost) {
        ConstantPool cp = mConstants;
        addAttribute(new Attribute.Constant(cp, "NestHost", cp.addClass(nestHost)));
    }

    private synchronized void addNestMember(BaseType nestMember) {
        if (mNestMembers == null) {
            mNestMembers = new Attribute.ConstantList(mConstants, "NestMembers");
            addAttribute(mNestMembers);
        }
        mNestMembers.add(mConstants.addClass(nestMember));
    }

    private void setEnclosingMethod(BaseType hostType, BaseType.Method hostMethod) {
        addAttribute(new Attribute.EnclosingMethod
                     (mConstants, mConstants.addClass(hostType),
                      mConstants.addNameAndType(hostMethod.name(), hostMethod.descriptor())));
    }

    private Attribute.InnerClasses innerClasses() {
        if (mInnerClasses == null) {
            mInnerClasses = new Attribute.InnerClasses(mConstants);
            addAttribute(mInnerClasses);
        }
        return mInnerClasses;
    }

    @Override
    public ClassMaker valueClass() {
        checkFinished();
        mModifiers = Modifiers.toValueClass(mModifiers);
        return this;
    }

    @Override
    public void addLoadableType(Object type) {
        Type tType = typeFrom(type);

        if (mLoadableDescriptors == null) {
            mLoadableDescriptors = new Attribute.LoadableDescriptors(mConstants);
            addAttribute(mLoadableDescriptors);
        }

        mLoadableDescriptors.add(tType);
    }

    @Override
    public AnnotationMaker addAnnotation(Object annotationType, boolean visible) {
        return addAnnotationMaker(new TheAnnotationMaker(this, annotationType), visible);
    }

    @Override
    public ClassMaker sourceFile(String fileName) {
        checkFinished();
        ConstantPool cp = mConstants;
        addAttribute(new Attribute.Constant(cp, "SourceFile", cp.addUTF8(fileName)));
        return this;
    }

    @Override
    public ClassLoader classLoader() {
        return mLookup != null ? mLookup.lookupClass().getClassLoader() : mInjectorGroup;
    }

    @Override
    public boolean installClass(Class<?> clazz) {
        if (mLookup != null) {
            throw new IllegalStateException();
        }
        return mInjectorGroup.installClass(clazz);
    }

    @Override
    public Set<String> unimplementedMethods() {
        Set<String> unimplemented = null;
        var methodSet = new HashSet<BaseType.Method>();

        BaseType type = type();
        do {
            for (BaseType.Method method : type.methods().values()) {
                if (methodSet.add(method) && Modifier.isAbstract(method.mFlags)) {
                    if (unimplemented == null) {
                        unimplemented = new TreeSet<>();
                    }
                    unimplemented.add(method.signature());
                }
            }
        } while ((type = type.superType()) != null);

        // Add all the default interface methods.
        for (BaseType ifaceType : type().interfaces()) {
            for (BaseType.Method method : ifaceType.methods().values()) {
                if ((method.mFlags & (Modifier.STATIC | Modifier.ABSTRACT)) == 0) {
                    methodSet.add(method);
                }
            }
        }

        for (BaseType ifaceType : type().interfaces()) {
            for (BaseType.Method method : ifaceType.methods().values()) {
                if (Modifier.isAbstract(method.mFlags) && !methodSet.contains(method)) {
                    if (unimplemented == null) {
                        unimplemented = new TreeSet<>();
                    }
                    unimplemented.add(method.signature());
                }
            }
        }

        return unimplemented == null ? Collections.emptySet() : unimplemented;
    }

    void toModule() {
        if (mSuperClass != null) {
            throw new IllegalStateException();
        }
        mSuperClass = new ConstantPool.C_Class(null, null);
        mModifiers = Modifiers.toModule(mModifiers);
    }

    @Override
    public Class<?> finish() {
        byte[] bytes = doFinishBytes();

        Class clazz;
        if (mLookup == null) {
            clazz = mInjector.define(mInjectorGroup, name(), bytes);
        } else {
            try {
                clazz = mLookup.defineClass(bytes);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        ConstantsRegistry.finish(this, mLookup, clazz);

        return clazz;
    }

    @Override
    public MethodHandles.Lookup finishLookup() {
        checkFinished();

        var lookupRef = new MethodHandles.Lookup[1];

        // Allow exact constant to be set. Once the caller has decided to finish into a loaded
        // class, they've already decided to not bother creating an external class anyhow.
        boolean wasExternal = mExternal;
        mExternal = false;
        try {
            MethodMaker mm = addClinit();
            var lookupVar = mm.var(MethodHandles.class).invoke("lookup");
            mm.var(lookupRef.getClass()).setExact(lookupRef).aset(0, lookupVar);

            MethodHandles.Lookup lookup = mLookup;

            if (lookup == null) {
                lookup = mInjectorGroup.lookup(name());
            }

            lookup.ensureInitialized(finish());
        } catch (Exception e) {
            throw toUnchecked(e);
        } finally {
            if (wasExternal) {
                mExternal = true;
            }
        }

        MethodHandles.Lookup lookup = lookupRef[0];
        lookupRef[0] = null;

        return lookup;
    }

    @Override
    public MethodHandles.Lookup finishHidden() {
        return finishHidden(false);
    }

    /**
     * @param strong pass true to maintain a strong reference to the class
     */
    MethodHandles.Lookup finishHidden(boolean strong) {
        MethodHandles.Lookup lookup = mLookup;

        if (lookup == null) {
            lookup = mInjectorGroup.lookup(name());
        }

        MethodHandles.Lookup.ClassOption[] options;
        if (!strong) {
            options = new MethodHandles.Lookup.ClassOption[] {
                MethodHandles.Lookup.ClassOption.NESTMATE
            };
        } else {
            options = new MethodHandles.Lookup.ClassOption[] {
                MethodHandles.Lookup.ClassOption.NESTMATE,
                MethodHandles.Lookup.ClassOption.STRONG
            };
        }

        byte[] bytes = doFinishBytes();

        MethodHandles.Lookup result;
        try {
            Object classData = mExactConstants;
            if (classData == null) {
                result = lookup.defineHiddenClass(bytes, false, options);
            } else {
                result = lookup.defineHiddenClassWithClassData(bytes, classData, false, options);
                mExactConstants = null;
            }
        } catch (Exception e) {
            throw toUnchecked(e);
        } finally {
            mInjector.unreserve(name());
        }

        ConstantsRegistry.finish(this, lookup, result.lookupClass());

        return result;
    }

    @Override
    public byte[] finishBytes() {
        noExactConstants();
        String name = name();
        try {
            return doFinishBytes();
        } finally {
            mInjector.unreserve(name);
        }
    }

    private byte[] doFinishBytes() {
        byte[] bytes;
        try {
            var out = new BytesOut(null, 1000);
            finishTo(out);
            bytes = out.toByteArray();
        } catch (IOException e) {
            // Not expected.
            throw new RuntimeException(e);
        } finally {
            mConstants = null;
        }

        if (DEBUG) {
            DebugWriter.write(this, bytes);
        }

        return bytes;
    }

    @Override
    public void finishTo(OutputStream out) throws IOException {
        noExactConstants();
        String name = name();
        try {
            var bout = new BytesOut(out, 1000);
            finishTo(bout);
            bout.flush();
        } finally {
            mConstants = null;
            mInjector.unreserve(name);
        }
    }

    /**
     * @param hidden when true, rename the class
     */
    private void finishTo(BytesOut out) throws IOException {
        checkFinished();

        // Ensure that mSuperClass has been assigned.
        superClass();

        int version = 0x0000_003d; // Java 17.

        if (Modifiers.isValueClass(mModifiers)) {
            version = 0xffff_0043; // Java 23, Valhalla EA
        }

        if (mRecordCtors != null) {
            TheMethodMaker.doFinish(mRecordCtors);
        }

        if (mPermittedSubclasses != null) {
            addAttribute(new Attribute.PermittedSubclasses(mConstants, mPermittedSubclasses));
        }

        TheMethodMaker.doFinish(mClinitMethods);

        checkSize(mInterfaces, 65535, "Interface");
        checkSize(mFields, 65535, "Field");
        checkSize(mMethods, 65535, "Method");

        if (mMethods != null) {
            for (TheMethodMaker method : mMethods) {
                method.doFinish();
            }
        }

        out.writeInt(0xCAFEBABE);
        out.writeInt(version);

        mConstants.writeTo(out);

        out.writeShort(mModifiers);

        out.writeShort(mThisClass.mIndex);
        out.writeShort(mSuperClass.mIndex);

        if (mInterfaces == null) {
            out.writeShort(0);
        } else {
            out.writeShort(mInterfaces.size());
            for (ConstantPool.C_Class iface : mInterfaces) {
                out.writeShort(iface.mIndex);
            }
        }

        if (mFields == null) {
            out.writeShort(0);
        } else {
            out.writeShort(mFields.size());
            for (TheFieldMaker field : mFields.values()) {
                field.writeTo(out);
            }
        }

        if (mMethods == null) {
            out.writeShort(0);
        } else {
            out.writeShort(mMethods.size());
            for (TheMethodMaker method : mMethods) {
                method.writeTo(out);
            }
        }

        writeAttributesTo(out);
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

    private void checkFinished() {
        if (mConstants == null) {
            throw new IllegalStateException("Class definition is already finished");
        }
    }

    private void noExactConstants() {
        checkFinished();
        if (mExactConstants != null) {
            throw new IllegalStateException("Class has exact constants defined");
        }
    }

    @Override
    public BaseType type() {
        return mThisClass.mType;
    }

    BaseType typeFrom(Object type) {
        return BaseType.from(mInjector, type);
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

    boolean allowExactConstants() {
        return !mExternal;
    }

    /**
     * @return slot
     */
    int addExactConstant(Object value, boolean shared) {
        checkFinished();

        if (mExternal) {
            throw new IllegalStateException("Making an external class");
        }

        if (!shared) {
            return ConstantsRegistry.add(this, value);
        }

        var map = mSharedExactConstants;

        if (map == null) {
            mSharedExactConstants = map = new IdentityHashMap<>();
        } else {
            Integer slot = map.get(value);
            if (slot != null) {
                return slot;
            }
        }

        int slot = ConstantsRegistry.add(this, value);
        map.put(value, slot);

        return slot;
    }

    /**
     * Always throws an exception.
     */
    static RuntimeException toUnchecked(Throwable e) {
        while (true) {
            if (e instanceof RuntimeException e2) {
                throw e2;
            }
            if (e instanceof Error e2) {
                throw e2;
            }
            Throwable cause = e.getCause();
            if (cause == null) {
                throw new IllegalStateException(e);
            }
            e = cause;
        }
    }

    /**
     * Define this code in a separate class such that it only loads when actually needed.
     */
    private static class AsRecord {
        private static TheMethodMaker apply(TheClassMaker cm) {
            cm.extend("java.lang.Record").final_();

            Attribute.Record recordAttr = new Attribute.Record(cm.mConstants);
            cm.addAttribute(recordAttr);

            Map<String, TheFieldMaker> fields = cm.mFields;

            if (fields == null) {
                fields = Collections.emptyMap();
            }

            Object[] paramTypes = new Object[fields.size()];
            {
                int i = 0;
                for (TheFieldMaker fm : fields.values()) {
                    Attribute.Record.Entry entry = recordAttr.add(fm);
                    paramTypes[i++] = fm;
                    if (fm.type().isAnnotatable()) {
                        fm.type().applyAnnotations
                            (entry, cm, new TypeAnnotationMaker.Target0(0x13));
                    }
                }
            }

            TheMethodMaker ctor = cm.addConstructor(paramTypes);
            ctor.mModifiers = cm.mModifiers
                & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE);
            ctor.useReturnLabel();
            ctor.invokeSuperConstructor();

            cm.mRecordCtors = new ArrayList<TheMethodMaker>(2);
            cm.mRecordCtors.add(ctor);

            if (!fields.isEmpty()) {
                var finisher = new TheMethodMaker(ctor);
                cm.mRecordCtors.add(finisher);

                int i = 0;
                for (TheFieldMaker fm : fields.values()) {
                    finisher.field(fm.name()).set(finisher.param(i++));
                }
            }

            // Identify the methods which have already been added, and don't add them again.

            // 1: add equals, 2: add hashCode, 4: add toString
            int toAdd = 1 | 2 | 4;

            Set<String> toSkip = null;

            if (cm.mMethods != null) {
                for (TheMethodMaker mm : cm.mMethods) {
                    BaseType.Method m = mm.mMethod;
                    String name = m.name();

                    switch (name) {
                    case "equals":
                        if (m.returnType() == BaseType.BOOLEAN && m.paramTypes().length == 1
                            && m.paramTypes()[0] == BaseType.from(Object.class))
                        {
                            toAdd &= ~1;
                        }
                        continue;
                    case "hashCode":
                        if (m.returnType() == BaseType.INT && m.paramTypes().length == 0) {
                            toAdd &= ~2;
                        }
                        continue;
                    case "toString":
                        if (m.paramTypes().length == 0
                            && m.returnType() == BaseType.from(String.class))
                        {
                            toAdd &= ~4;
                        }
                        continue;
                    }

                    TheFieldMaker field = fields.get(name);
                    if (field != null
                        && m.paramTypes().length == 0 && m.returnType() == field.type())
                    {
                        if (toSkip == null) {
                            toSkip = new LinkedHashSet<>();
                        }
                        toSkip.add(name);
                    }
                }
            }

            // Add the accessor methods.

            for (TheFieldMaker fm : fields.values()) {
                String name = fm.name();
                if (toSkip == null || !toSkip.contains(name)) {
                    MethodMaker mm = cm.addMethod(fm, name).public_().final_();
                    mm.return_(mm.field(name));
                }
            }

            // Add the basic Object methods.

            if ((toAdd & 1) != 0) {
                MethodMaker mm = cm.addMethod(boolean.class, "equals", Object.class)
                    .public_().final_();

                var args = new Object[2 + fields.size()];
                args[0] = mm.class_();
                args[1] = ""; // no names
                getters(mm, fields, args, 2);

                var bootstrap =  mm.var("java.lang.runtime.ObjectMethods").indy("bootstrap", args);

                mm.return_(bootstrap.invoke
                           (boolean.class, "equals",
                            new Object[] {cm, Object.class}, mm.this_(), mm.param(0)));
            }

            if ((toAdd & 2) != 0) {
                MethodMaker mm = cm.addMethod(int.class, "hashCode").public_().final_();

                var args = new Object[2 + fields.size()];
                args[0] = mm.class_();
                args[1] = ""; // no names
                getters(mm, fields, args, 2);

                var bootstrap =  mm.var("java.lang.runtime.ObjectMethods").indy("bootstrap", args);

                mm.return_(bootstrap.invoke(int.class, "hashCode",
                                            new Object[] {cm}, mm.this_()));
            }

            if ((toAdd & 4) != 0) {
                MethodMaker mm = cm.addMethod(String.class, "toString").public_().final_();

                var names = new StringBuilder();
                for (String name : fields.keySet()) {
                    if (!names.isEmpty()) {
                        names.append(';');
                    }
                    names.append(name);
                }

                var args = new Object[2 + fields.size()];
                args[0] = mm.class_();
                args[1] = names.toString();
                getters(mm, fields, args, 2);
                
                var bootstrap =  mm.var("java.lang.runtime.ObjectMethods").indy("bootstrap", args);

                mm.return_(bootstrap.invoke(String.class, "toString",
                                            new Object[] {cm}, mm.this_()));
            }

            return ctor;
        }

        private static void getters(MethodMaker mm, Map<String, TheFieldMaker> fields,
                                    Object[] args, int offset)
        {
            for (String name : fields.keySet()) {
                args[offset++] = mm.field(name).methodHandleGet();
            }
        }
    }
}
