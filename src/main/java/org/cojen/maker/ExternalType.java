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

import java.io.OutputStream;

import java.lang.annotation.Annotation;

import java.lang.invoke.MethodHandles;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
final class ExternalType extends BaseType.Clazz implements ClassMaker, AnnotationMaker {
    private final Provider mProvider;

    private boolean mInitialized;

    ExternalType(String name, Provider provider) {
        super(Objects.requireNonNull(name), null, null);
        mProvider = Objects.requireNonNull(provider);
    }

    private BaseType typeFrom(Object type) {
        return from(getClass().getClassLoader(), type);
    }

    @Override
    public Class<?> classType() {
        return null;
    }

    @Override
    public ClassMaker classMaker() {
        return this;
    }

    @Override
    public AnnotationMaker addAnnotation(Object annotationType, boolean visible) {
        return this;
    }

    @Override // from AnnotationMaker
    public void put(String name, Object value) {
    }

    @Override // from AnnotationMaker
    public AnnotationMaker newAnnotation(Object annotationType) {
        return this;
    }

    @Override
    public void addAttribute(String name, Object value) {
    }

    @Override
    public ClassMaker another(String className) {
        // Assume the provider looks at ClassMaker.name().
        return new ExternalType(className, mProvider);
    }

    @Override
    public ClassMaker public_() {
        return this;
    }

    @Override
    public ClassMaker private_() {
        return this;
    }

    @Override
    public ClassMaker protected_() {
        return this;
    }

    @Override
    public ClassMaker static_() {
        return this;
    }

    @Override
    public ClassMaker final_() {
        return this;
    }

    @Override
    public ClassMaker interface_() {
        mIsInterface = true;
        return this;
    }

    @Override
    public ClassMaker abstract_() {
        return this;
    }

    @Override
    public ClassMaker synthetic() {
        return this;
    }

    @Override
    public ClassMaker enum_() {
        return this;
    }

    @Override
    public ClassMaker annotation() {
        interface_().implement(Annotation.class);
        return this;
    }

    @Override
    public ClassMaker extend(Object superClass) {
        BaseType type = typeFrom(superClass);

        synchronized (this) {
            if (mSuperType != null) {
                throw new IllegalStateException();
            }
            mSuperType = type;
        }

        return this;
    }

    @Override
    public ClassMaker implement(Object iface) {
        BaseType type = typeFrom(iface);

        synchronized (this) {
            Set<BaseType> interfaces = mInterfaces;
            if (interfaces == null) {
                mInterfaces = interfaces = new ConcurrentHashMap<BaseType, Boolean>().keySet(true);
            }
            interfaces.add(type);
        }

        return this;
    }

    @Override
    public ClassMaker signature(Object... components) {
        return this;
    }

    @Override
    public ClassMaker permitSubclass(Object subclass) {
        return this;
    }

    @Override
    public FieldMaker addField(Object otype, String name) {
        BaseType type = typeFrom(otype);

        Map<String, Field> fields = mFields;

        if (fields == null) {
            synchronized (this) {
                fields = mFields;
                if (fields == null) {
                    mFields = fields = new ConcurrentHashMap<>();
                }
            }
        }

        var field = new Field(0, type, name);
        fields.put(name, field);

        var maker = StubMaker.newInstance(MMaker.class);
        maker.member = field;

        return maker;
    }

    @Override
    public MethodMaker addMethod(Object oretType, String name, Object... oparamTypes) {
        BaseType retType = oretType == null ? BaseType.VOID : typeFrom(oretType);

        var paramTypes = new BaseType[oparamTypes.length];
        for (int i=0; i<paramTypes.length; i++) {
            paramTypes[i] = typeFrom(oparamTypes[i]);
        }

        return doAddMethod(retType, name, paramTypes);
    }

    private MethodMaker doAddMethod(BaseType retType, String name, BaseType... paramTypes) {
        Map<MethodKey, Method> methods = mMethods;

        if (methods == null) {
            synchronized (this) {
                methods = mMethods;
                if (methods == null) {
                    mMethods = methods = new ConcurrentHashMap<>();
                }
            }
        }

        var method = new Method(0, retType, name, paramTypes);
        methods.put(new MethodKey(retType, name, paramTypes), method);

        var maker = StubMaker.newInstance(MMaker.class);
        maker.member = method;

        return maker;
    }

    @Override
    public MethodMaker addConstructor(Object... paramTypes) {
        return addMethod(BaseType.VOID, "<init>", paramTypes);
    }

    @Override
    public MethodMaker addClinit() {
        return StubMaker.newInstance(MethodMaker.class);
    }

    @Override
    public MethodMaker asRecord() {
        extend(Record.class).final_();

        Map<String, Field> fields = mFields;
        BaseType[] paramTypes;

        if (fields == null) {
            paramTypes = new BaseType[0];
        } else {
            paramTypes = new BaseType[fields.size()];
            Iterator<Field> it = fields.values().iterator();
            for (int i=0; i<paramTypes.length; i++) {
                paramTypes[i] = it.next().type();
            }
        }

        return doAddMethod(BaseType.VOID, "<init>", paramTypes);
    }

    @Override
    public ClassMaker addInnerClass(String className) {
        if (className == null || className.indexOf('.') >= 0) {
            throw new IllegalArgumentException();
        }
        return addExplicitInnerClass(name() + '$' + className, className);
    }

    @Override
    public ClassMaker addExplicitInnerClass(String fullName, String className) {
        // Assume the provider looks at ClassMaker.name().
        return new ExternalType(fullName, mProvider);
    }

    @Override
    public ClassMaker sourceFile(String fileName) {
        return this;
    }

    @Override
    public ClassLoader classLoader() {
        return null;
    }

    @Override
    public boolean installClass(Class<?> clazz) {
        return true;
    }

    @Override
    public Set<String> unimplementedMethods() {
        return Set.of();
    }

    @Override
    public Class<?> finish() {
        throw new IllegalStateException();
    }

    @Override
    public MethodHandles.Lookup finishLookup() {
        throw new IllegalStateException();
    }

    @Override
    public MethodHandles.Lookup finishHidden() {
        throw new IllegalStateException();
    }

    @Override
    public byte[] finishBytes() {
        throw new IllegalStateException();
    }

    @Override
    public void finishTo(OutputStream out) {
        throw new IllegalStateException();
    }

    @Override
    public boolean isInterface() {
        Boolean is = mIsInterface;

        if (is == null) {
            synchronized (this) {
                doInit();
                is = mIsInterface;
                if (is == null) {
                    mIsInterface = is = false;
                }
            }
        }

        return is;
    }

    @Override
    BaseType superType() {
        BaseType superType = mSuperType;

        if (superType == null) {
            synchronized (this) {
                doInit();
                superType = mSuperType;
                if (superType == null) {
                    mSuperType = superType = BaseType.from(Object.class);
                }
            }
        }

        return superType;
    }

    @Override
    synchronized Set<BaseType> interfaces() {
        doInit();
        Set<BaseType> interfaces = mInterfaces;
        if (interfaces == null) {
            mInterfaces = interfaces = new ConcurrentHashMap<BaseType, Boolean>().keySet(true);
        }
        return interfaces;
    }

    // Caller must be synchronized.
    private void doInit() {
        if (!mInitialized) {
            mInitialized = true;
            mProvider.init(this);
        }
    }

    @Override
    protected synchronized Map<String, Field> initFields() {
        mProvider.addFields(this);

        Map<String, Field> fields = mFields;

        if (fields == null) {
            mFields = fields = new ConcurrentHashMap<>();
        }

        return fields;
    }

    @Override
    protected synchronized Map<MethodKey, Method> initMethods() {
        mProvider.addMethods(this);
        mProvider.addConstructors(this);

        Map<MethodKey, Method> methods = mMethods;

        if (methods == null) {
            mMethods = methods = new ConcurrentHashMap<>();
        }

        return methods;
    }

    public abstract static class MMaker implements FieldMaker, MethodMaker {
        Member member;

        @Override
        public final MMaker signature(Object... components) {
            return this;
        }

        @Override
        public final MMaker public_() {
            member.mFlags = Modifiers.toPublic(member.mFlags);
            return this;
        }

        @Override
        public final MMaker private_() {
            member.mFlags = Modifiers.toPrivate(member.mFlags);
            return this;
        }

        @Override
        public final MMaker protected_() {
            member.mFlags = Modifiers.toProtected(member.mFlags);
            return this;
        }

        @Override
        public final MMaker static_() {
            member.mFlags = Modifiers.toStatic(member.mFlags);
            return this;
        }

        @Override
        public final MMaker final_() {
            member.mFlags = Modifiers.toFinal(member.mFlags);
            return this;
        }

        @Override
        public final MMaker volatile_() {
            member.mFlags = Modifiers.toVolatile(member.mFlags);
            return this;
        }

        @Override
        public final MMaker transient_() {
            member.mFlags = Modifiers.toTransient(member.mFlags);
            return this;
        }

        @Override
        public final MMaker synthetic() {
            member.mFlags = Modifiers.toSynthetic(member.mFlags);
            return this;
        }

        @Override
        public final MMaker enum_() {
            member.mFlags = Modifiers.toEnum(member.mFlags);
            return this;
        }

        @Override
        public final MMaker synchronized_() {
            member.mFlags = Modifiers.toSynchronized(member.mFlags);
            return this;
        }

        @Override
        public final MMaker abstract_() {
            member.mFlags = Modifiers.toAbstract(member.mFlags);
            return this;
        }

        @Override
        public final MMaker native_() {
            member.mFlags = Modifiers.toNative(member.mFlags);
            return this;
        }

        @Override
        public final MMaker bridge() {
            member.mFlags = Modifiers.toBridge(member.mFlags);
            return this;
        }

        @Override
        public final MMaker varargs() {
            member.mFlags = Modifiers.toVarArgs(member.mFlags);
            return this;
        }
    }
}
