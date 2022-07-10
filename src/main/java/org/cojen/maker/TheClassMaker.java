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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    int mModifiers;

    private Set<ConstantPool.C_Class> mInterfaces;
    private Map<String, TheFieldMaker> mFields;
    private List<TheMethodMaker> mMethods;

    private boolean mHasConstructor;

    private ArrayList<TheMethodMaker> mClinitMethods;

    private Attribute.BootstrapMethods mBootstrapMethods;

    private Attribute.ConstantList mNestMembers;

    private Attribute.InnerClasses mInnerClasses;

    private Set<ConstantPool.C_Class> mPermittedSubclasses;

    // Accessed by ConstantsRegistry.
    Object mExactConstants;

    // Maps constants to static final fields. Accessed by TheMethodMaker.
    Map<ConstantPool.Constant, ConstantPool.C_Field> mResolvedConstants;

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

        mThisClass = mConstants.addClass(Type.begin(injector, this, className));
    }

    private TheClassMaker(TheClassMaker from, String className) {
        this(from, from.mExternal, className, from.mLookup, from.mInjector);
    }

    // Called by ClassInjector.Group.
    TheClassMaker(String className, ClassInjector injector, ClassInjector.Group injectorGroup) {
        super(new ConstantPool());
        mParent = null;
        mExternal = false;
        mLookup = null;
        mInjector = injector;
        mInjectorGroup = injectorGroup;
        className = injector.reserve(this, className, false);
        mThisClass = mConstants.addClass(Type.begin(injector, this, className));
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
    Set<Type> allInterfaces() {
        if (mInterfaces == null) {
            return Collections.emptySet();
        }

        Set<Type> all = new LinkedHashSet<>(mInterfaces.size());

        for (ConstantPool.C_Class clazz : mInterfaces) {
            Type type = clazz.mType;
            all.add(type);
            all.addAll(type.interfaces());
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

        var fm = new TheFieldMaker(this, type().defineField(0, tType, name));
        mFields.put(name, fm);

        return fm;
    }

    TheFieldMaker addSyntheticField(Type type, String prefix) {
        checkFinished();

        String name;
        if (mFields == null) {
            mFields = new LinkedHashMap<>();
            name = prefix + '0';
        } else {
            name = prefix + mFields.size();
            while (mFields.containsKey(name)) {
                name = prefix + ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
            }
        }

        var fm = new TheFieldMaker(this, type().defineField(0, type, name));
        fm.synthetic();
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
        var mm = new TheMethodMaker(this, defineMethod(retType, name, paramTypes));
        doAddMethod(mm);

        if (!mHasConstructor && name.equals("<init>")) {
            mHasConstructor = true;
        }

        return mm;
    }

    void doAddMethod(TheMethodMaker mm) {
        if (mMethods == null) {
            mMethods = new ArrayList<>();
        }
        mMethods.add(mm);
    }

    Type.Method defineMethod(Object retType, String name, Object... paramTypes) {
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
    public TheClassMaker addInnerClass(String className) {
        return addInnerClass(className, null);
    }

    TheClassMaker addInnerClass(final String className, final Type.Method hostMethod) {
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

    private void setNestHost(Type nestHost) {
        ConstantPool cp = mConstants;
        addAttribute(new Attribute.Constant(cp, "NestHost", cp.addClass(nestHost)));
    }

    private synchronized void addNestMember(Type nestMember) {
        if (mNestMembers == null) {
            mNestMembers = new Attribute.ConstantList(mConstants, "NestMembers");
            addAttribute(mNestMembers);
        }
        mNestMembers.add(mConstants.addClass(nestMember));
    }

    private void setEnclosingMethod(Type hostType, Type.Method hostMethod) {
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
    public AnnotationMaker addAnnotation(Object annotationType, boolean visible) {
        return addAnnotation(new TheAnnotationMaker(this, annotationType), visible);
    }

    @Override
    public ClassMaker sourceFile(String fileName) {
        checkFinished();
        ConstantPool cp = mConstants;
        addAttribute(new Attribute.Constant(cp, "SourceFile", cp.addUTF8(fileName)));
        return this;
    }

    @Override
    public Object arrayType(int dimensions) {
        if (dimensions < 1 || dimensions > 255) {
            throw new IllegalArgumentException();
        }

        Type type = type();
        do {
            type = type.asArray();
        } while (--dimensions > 0);

        final Type fType = type;

        return (Typed) () -> fType;
    }

    @Override
    public ClassLoader classLoader() {
        return mLookup != null ? mLookup.lookupClass().getClassLoader() : mInjectorGroup;
    }

    String name() {
        return type().name();
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
        String name = name();

        Class clazz;
        if (mLookup == null) {
            clazz = mInjector.define(mInjectorGroup, name, finishBytes(false));
        } else {
            try {
                clazz = mLookup.defineClass(finishBytes(false));
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

        // Find the MethodHandles.Lookup.ensureInitialized method, available in Java 15.
        Method m = ensureInitialized();

        // Allow exact constant to be set. Once the caller has decided to finish into a loaded
        // class, they've already decided to not bother creating an external class anyhow.
        boolean wasExternal = mExternal;
        mExternal = false;
        try {
            MethodMaker mm = addClinit();
            var lookupVar = mm.var(MethodHandles.class).invoke("lookup");
            mm.var(lookupRef.getClass()).setExact(lookupRef).aset(0, lookupVar);

            if (m != null) {
                MethodHandles.Lookup lookup = mLookup;

                if (lookup == null) {
                    lookup = mInjectorGroup.lookup(name(), false);
                }

                m.invoke(lookup, finish());
            } else {
                // Without the ensureInitialized method, do something much more complicated
                // which involves defining a special "init" method.

                String initName;

                selectName: for (int id=0;;) {
                    initName = "$init-" + id;
                    if (mMethods == null) {
                        break;
                    }
                    for (TheMethodMaker method : mMethods) {
                        if (initName.equals(method.getName())) {
                            id = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
                            continue selectName;
                        }
                    }
                    break;
                }

                addMethod(null, initName).static_().synthetic();

                var clazz = finish();

                if (mLookup != null) {
                    MethodHandle init = mLookup.findStatic
                        (clazz, initName, MethodType.methodType(void.class));
                    init.invokeExact();
                } else {
                    MethodHandles.Lookup lookup = mInjectorGroup.lookup(name(), true);
                    MethodHandle hook = lookup.findStatic
                        (lookup.lookupClass(), "hook",
                         MethodType.methodType(void.class, Class.class, String.class));
                    hook.invokeExact(clazz, initName);
                }
            }
        } catch (Throwable e) {
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

    // These fields are modified by AccessTest.
    static volatile Method cEnsureInitialized;
    static boolean cNoEnsureInitialized;

    private static Method ensureInitialized() {
        Method m = cEnsureInitialized;

        if (m == null && !cNoEnsureInitialized) {
            try {
                m = MethodHandles.Lookup.class.getMethod("ensureInitialized", Class.class);
                cEnsureInitialized = m;
            } catch (Throwable e) {
                cNoEnsureInitialized = true;
            }
        }

        return m;
    }

    boolean supportsHiddenClasses() {
        defineHidden();
        return cHiddenClassOptions != null;
    }

    @Override
    public MethodHandles.Lookup finishHidden() {
        return finishHidden(false);
    }

    /**
     * @param strong pass true to maintain a strong reference to the class
     * @throws RuntimeException if strong and hidden classes aren't truly supported
     */
    MethodHandles.Lookup finishHidden(boolean strong) {
        MethodHandles.Lookup lookup = mLookup;

        if (lookup == null) {
            lookup = mInjectorGroup.lookup(name(), ensureInitialized() == null);
        }

        Method m = defineHidden();
        Object options = cHiddenClassOptions;

        if (strong && (options = cHiddenClassStrongOptions) == null) {
            try {
                cHiddenClassStrongOptions = options = hiddenOptions("STRONG");
            } catch (Throwable e) {
                throw toUnchecked(e);
            }
        }

        String originalName = name();

        byte[] bytes = finishBytes(true);

        MethodHandles.Lookup result;
        try {
            if (options == null) {
                var clazz = (Class<?>) m.invoke(cUnsafe, lookup.lookupClass(), bytes, null);
                result = lookup.in(clazz);
            } else {
                result = ((MethodHandles.Lookup) m.invoke(lookup, bytes, false, options));
            }
        } catch (Exception e) {
            throw toUnchecked(e);
        } finally {
            mInjector.unreserve(originalName);
        }

        ConstantsRegistry.finish(this, lookup, result.lookupClass());

        return result;
    }

    private static volatile Method cDefineHidden;
    private static Object cHiddenClassOptions;
    private static Object cHiddenClassStrongOptions;
    private static Object cUnsafe;

    private static Method defineHidden() {
        Method m = cDefineHidden;

        if (m != null) {
            return m;
        }

        try {
            Object options = hiddenOptions("NESTMATE");
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

    private static Object hiddenOptions(String optionName) throws Throwable {
        var optionClass = Class.forName("java.lang.invoke.MethodHandles$Lookup$ClassOption");
        Object options = Array.newInstance(optionClass, 1);
        Array.set(options, 0, optionClass.getField(optionName).get(null));
        return options;
    }

    @Override
    public byte[] finishBytes() {
        noExactConstants();
        String name = name();
        try {
            return finishBytes(false);
        } finally {
            mInjector.unreserve(name);
        }
    }

    private byte[] finishBytes(boolean hidden) {
        byte[] bytes;
        try {
            var out = new BytesOut(null, 1000);
            finishTo(out, hidden);
            bytes = out.toByteArray();
        } catch (IOException e) {
            // Not expected.
            throw new RuntimeException(e);
        } finally {
            mConstants = null;
        }

        if (DEBUG) {
            DebugWriter.write(name(), bytes);
        }

        return bytes;
    }

    @Override
    public void finishTo(OutputStream out) throws IOException {
        noExactConstants();
        String name = name();
        try {
            var bout = new BytesOut(out, 1000);
            finishTo(bout, false);
            bout.flush();
        } finally {
            mConstants = null;
            mInjector.unreserve(name);
        }
    }

    /**
     * @param hidden when true, rename the class
     */
    private void finishTo(BytesOut out, boolean hidden) throws IOException {
        checkFinished();

        // Ensure that mSuperClass has been assigned.
        superClass();

        int version = 0x0000_0037; // Java 11.

        if (mPermittedSubclasses != null) {
            version = 0x0000_003d; // Java 17.
            addAttribute(new Attribute.PermittedSubclasses(mConstants, mPermittedSubclasses));
            mPermittedSubclasses = null;
        }

        mBootstrapMethods = null;

        TheMethodMaker.doFinish(mClinitMethods);
        mClinitMethods = null;

        checkSize(mInterfaces, 65535, "Interface");
        checkSize(mFields, 65535, "Field");
        checkSize(mMethods, 65535, "Method");

        if (mMethods != null) {
            for (TheMethodMaker method : mMethods) {
                method.doFinish();
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
            mInterfaces = null;
        }

        if (mFields == null) {
            out.writeShort(0);
        } else {
            out.writeShort(mFields.size());
            for (TheFieldMaker field : mFields.values()) {
                field.writeTo(out);
            }
            mFields = null;
        }

        if (mMethods == null) {
            out.writeShort(0);
        } else {
            out.writeShort(mMethods.size());
            for (TheMethodMaker method : mMethods) {
                method.writeTo(out);
            }
            mMethods = null;
        }

        writeAttributesTo(out);

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
    public Type type() {
        return mThisClass.mType;
    }

    Type typeFrom(Object type) {
        return Type.from(mInjector, type);
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
    int addExactConstant(Object value) {
        checkFinished();
        if (mExternal) {
            throw new IllegalStateException("Making an external class");
        }
        return ConstantsRegistry.add(this, value);
    }

    /**
     * Always throws an exception.
     */
    static RuntimeException toUnchecked(Throwable e) {
        while (true) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            if (e instanceof Error) {
                throw (Error) e;
            }
            Throwable cause = e.getCause();
            if (cause == null) {
                throw new IllegalStateException(e);
            }
            e = cause;
        }
    }
}
