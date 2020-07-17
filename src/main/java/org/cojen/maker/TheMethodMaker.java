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

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatFactory;
import java.lang.invoke.VarHandle;

import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.*;

import static org.cojen.maker.Opcodes.*;
import static org.cojen.maker.Type.*;
import static org.cojen.maker.BytesOut.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
final class TheMethodMaker extends ClassMember implements MethodMaker {
    private final TheClassMaker mClassMaker;
    private final Type.Method mMethod;

    private Var[] mParams;

    private Op mFirstOp;
    private Op mLastOp;

    private Var mThisVar;
    private Var mClassVar;

    private List<Handler> mExceptionHandlers;

    private Lab mReturnLabel;

    // Remaining fields are only used when finish is called.

    private byte[] mCode;
    private int mCodeLen;

    private Var[] mVars;

    private Var[] mStack;
    private int mStackSize;
    private int mMaxStackSlot;

    // Count of labels which need to be positioned to define all branch targets.
    private int mUnpositionedLabels;

    private StackMapTable mStackMapTable;

    private Attribute.LineNumberTable mLineNumberTable;

    private Attribute.LocalVariableTable mLocalVariableTable;

    private int mFinished;

    TheMethodMaker(TheClassMaker classMaker, Type.Method method) {
        super(classMaker.mConstants,
              classMaker.mConstants.addUTF8(method.name()),
              classMaker.mConstants.addUTF8(method.descriptor()));

        mClassMaker = classMaker;
        mMethod = method;
    }

    /**
     * Append another maker, used for defining a clinit sub-method. Also call useReturnLabel.
     */
    TheMethodMaker(TheMethodMaker prev) {
        super(prev.mClassMaker.mConstants, prev.mName, prev.mDescriptor);
        mClassMaker = prev.mClassMaker;
        mMethod = prev.mMethod;
    }

    /**
     * Must call on each clinit method.
     */
    void useReturnLabel() {
        if (mReturnLabel != null) {
            throw new IllegalStateException();
        }
        mReturnLabel = new Lab(this);
    }

    private void positionReturnLabel() {
        if (mReturnLabel != null) {
            mReturnLabel.here();
            mReturnLabel = null;
        }
    }

    void finish() {
        if (mFinished != 0 || Modifier.isAbstract(mModifiers)) {
            return;
        }

        positionReturnLabel();

        boolean isEndReached = true;
        if (mLastOp instanceof BytecodeOp) {
            byte op = ((BytecodeOp) mLastOp).op();
            if ((op >= IRETURN && op <= RETURN) || op == GOTO || op == GOTO_W || op == ATHROW) {
                isEndReached = false;
            }
        }

        if (isEndReached) {
            if (mMethod.returnType() == VOID) {
                if (mLastOp == null &&
                    "<init>".equals(getName()) && mMethod.paramTypes().length == 0)
                {
                    invokeSuperConstructor();
                }
                return_();
            } else {
                throw new IllegalStateException
                    ("End reached without returning: " + mMethod.returnType().name());
            }
        }

        if (mParams == null) {
            initParams();
        }

        List<Var> varList = new ArrayList<>();

        for (Var param : mParams) {
            varList.add(param);
        }

        int varSlot;
        if (mParams.length > 0) {
            Var lastParam = mParams[mParams.length - 1];
            varSlot = lastParam.mSlot + lastParam.slotWidth();
        } else {
            varSlot = mThisVar == null ? 0 : 1;
        }

        // Tag visited operations for removing dead code. This is necessary for building the
        // StackMapTable, since frames are only built for visited labels.
        mFirstOp.flowThrough();

        // Visit the exception handlers too.
        if (mExceptionHandlers != null) {
            Iterator<Handler> it = mExceptionHandlers.iterator();
            while (it.hasNext()) {
                Handler h = it.next();
                if (!h.isVisited()) {
                    it.remove();
                }
            }
        }

        Set<Var> varSet = new LinkedHashSet<>();
        int opCount = 0;
        for (Op op = mFirstOp, prev = null; op != null; ) {
            visited: {
                if (!op.mVisited) {
                    op = op.mNext;
                    break visited;
                }

                if (op instanceof PushVarOp) {
                    varSet.add(((PushVarOp) op).mVar);
                } else if (op instanceof StoreVarOp) {
                    var store = (StoreVarOp) op;
                    varSet.add(store.mVar);

                    // Look for store/push pair to the same variable and remove the pair. Just
                    // use the stack variable and avoid extra steps.
                    Op next = store.mNext;
                    if (next instanceof PushVarOp) {
                        var push = (PushVarOp) next;
                        Var var = store.mVar;
                        if (var == push.mVar && var.mPushCount == 1) {
                            var.mPushCount--;
                            op = next.mNext;
                            break visited;
                        }
                    }
                }

                opCount++;
                prev = op;
                op = op.mNext;
                continue;
            }

            // Remove dead code.
            if (prev == null) {
                mFirstOp = op;
            } else {
                prev.mNext = op;
            }
        }

        mCode = new byte[Math.min(65536, opCount * 2)];

        for (Var var : varSet) {
            if (var.mSlot < 0 && var.mPushCount > 0) {
                var.mValid = false;
                var.mSlot = varSlot;
                varSlot += var.slotWidth();
                varList.add(var);
            }
        }

        mVars = varList.toArray(new Var[varList.size()]);
        mStack = new Var[8];

        while (true) {
            mCodeLen = 0;

            mStackSize = 0;
            mMaxStackSlot = 0;
            mUnpositionedLabels = 0;
            mStackMapTable = new StackMapTable(mConstants, smCodes(mParams, mParams.length));
            mLineNumberTable = null;
            mLocalVariableTable = null;
            mFinished = 0;

            for (Op op = mFirstOp; op != null; op = op.mNext) {
                op.appendTo(this);
            }

            if (mUnpositionedLabels != 0) {
                throw new IllegalStateException("Unpositioned labels in method: " + 
                                                getName() + ", " + mUnpositionedLabels);
            }

            if (mFinished >= 0) {
                break;
            }

            // Wide forward branches were detected, so code needs to be rebuilt.

            for (Op op = mFirstOp; op != null; op = op.mNext) {
                op.reset();
            }

            for (int i=mParams.length; i<mVars.length; i++) {
                mVars[i].mValid = false;
            }
        }

        mParams = null;
        mFirstOp = null;
        mLastOp = null;
        mReturnLabel = null;
        mVars = null;
        mStack = null;

        var codeAttr = new Attribute.Code
            (mConstants, mMaxStackSlot, varSlot, mCode, mCodeLen, mExceptionHandlers);

        mExceptionHandlers = null;

        if (mStackMapTable.finish()) {
            codeAttr.addAttribute(mStackMapTable);
        }

        if (mLineNumberTable != null && mLineNumberTable.finish(mCodeLen)) {
            codeAttr.addAttribute(mLineNumberTable);
        }

        if (mLocalVariableTable != null && mLocalVariableTable.finish(mCodeLen)) {
            codeAttr.addAttribute(mLocalVariableTable);
        }

        addAttribute(codeAttr);

        mFinished = 1;
    }

    /**
     * Stitch methods together and finish as one. List can be null or empty.
     */
    static void finish(List<TheMethodMaker> list) {
        int size;
        if (list == null || (size = list.size()) == 0) {
            return;
        }

        final TheMethodMaker first = list.get(0);
        first.positionReturnLabel();

        for (int i=1; i<size; i++) {
            TheMethodMaker next = list.get(i);
            if (next.mFirstOp != null) {
                next.positionReturnLabel();
                first.mLastOp.mNext = next.mFirstOp;
                first.mLastOp = next.mLastOp;
            }
        }

        first.finish();
    }

    @Override
    public MethodMaker public_() {
        mModifiers = Modifiers.toPublic(mModifiers);
        return this;
    }

    @Override
    public MethodMaker private_() {
        mModifiers = Modifiers.toPrivate(mModifiers);
        return this;
    }

    @Override
    public MethodMaker protected_() {
        mModifiers = Modifiers.toProtected(mModifiers);
        return this;
    }

    @Override
    public MethodMaker static_() {
        mModifiers = Modifiers.toStatic(mModifiers);
        mMethod.toStatic();
        return this;
    }

    @Override
    public MethodMaker final_() {
        mModifiers = Modifiers.toFinal(mModifiers);
        return this;
    }

    @Override
    public MethodMaker synchronized_() {
        mModifiers = Modifiers.toSynchronized(mModifiers);
        return this;
    }

    @Override
    public MethodMaker abstract_() {
        mModifiers = Modifiers.toAbstract(mModifiers);
        return this;
    }

    @Override
    public MethodMaker strictfp_() {
        mModifiers = Modifiers.toStrict(mModifiers);
        return this;
    }

    @Override
    public MethodMaker native_() {
        mModifiers = Modifiers.toNative(mModifiers);
        return this;
    }

    @Override
    public MethodMaker synthetic() {
        mModifiers = Modifiers.toSynthetic(mModifiers);
        return this;
    }

    @Override
    public MethodMaker bridge() {
        mModifiers = Modifiers.toBridge(mModifiers);
        return this;
    }

    @Override
    public MethodMaker varargs() {
        Type[] params = mMethod.paramTypes();
        if (params.length == 0 || !params[params.length - 1].isArray()) {
            throw new IllegalStateException();
        }
        mModifiers = Modifiers.toVarArgs(mModifiers);
        mMethod.makeVarargs();
        return this;
    }

    @Override
    public Variable class_() {
        if (mClassVar == null) {
            mClassVar = new ClassVar(mClassMaker.typeFrom(Class.class));
        }
        return mClassVar;
    }

    @Override
    public Var this_() {
        while (true) {
            if (mThisVar != null) {
                return mThisVar;
            }
            if (mParams == null) {
                initParams();
                continue;
            }
            throw new IllegalStateException("Not an instance method");
        }
    }

    @Override
    public Variable param(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException();
        }
        Var[] params = mParams;
        if (params == null) {
            initParams();
            params = mParams;
        }
        if (mThisVar != null) {
            index++;
        }
        return params[index];
    }

    private void initParams() {
        int count = mMethod.paramTypes().length;
        int slot = 0;

        if (!Modifier.isStatic(mModifiers)) {
            mThisVar = new Var(mClassMaker.type());
            mThisVar.mValid = !"<init>".equals(getName());
            mThisVar.mSlot = 0;
            count++;
            slot = 1;
        }

        int i = 0;
        mParams = new Var[count];

        if (mThisVar != null) {
            mParams[i++] = mThisVar;
        }

        for (Type t : mMethod.paramTypes()) {
            Var param = new Var(t);
            param.mValid = true;
            param.mSlot = slot;
            slot += param.slotWidth();
            mParams[i++] = param;
        }
    }

    @Override
    public Var var(Object type) {
        requireNonNull(type);

        Type tType;
        if (type instanceof OwnedVar) {
            tType = ((OwnedVar) type).type();
        } else {
            tType = mClassMaker.typeFrom(type);
        }

        return new Var(tType);
    }

    @Override
    public void lineNum(int num) {
        addOp(new LineNumOp(num));
    }

    @Override
    public Label label() {
        return new Lab(this);
    }

    @Override
    public void goto_(Label label) {
        addBranchOp(GOTO, 0, label);
    }

    @Override
    public void return_() {
        if (mMethod.returnType() != VOID) {
            throw new IllegalStateException("Must return a value from this method");
        }
        if (mReturnLabel == null) {
            addBytecodeOp(RETURN, 0);
        } else {
            addOp(new BranchOp(GOTO, 0, mReturnLabel));
        }
    }

    @Override
    public void return_(Object value) {
        Type type = mMethod.returnType();
        if (type == VOID) {
            throw new IllegalStateException("Must return void from this method");
        }

        byte op;
        switch (type.stackMapCode()) {
        default:
            throw new IllegalStateException("Unsupported return type");
        case SM_INT:
            op = IRETURN;
            break;
        case SM_FLOAT:
            op = FRETURN;
            break;
        case SM_DOUBLE:
            op = DRETURN;
            break;
        case SM_LONG:
            op = LRETURN;
            break;
        case SM_OBJECT:
            op = ARETURN;
            break;
        }

        addPushOp(type, value);
        addBytecodeOp(op, 1);
    }

    @Override
    public FieldVar field(String name) {
        Type type = mClassMaker.type();
        Type.Field field = findField(type, name);

        Var var = mThisVar;
        if (var == null) {
            if (field.isStatic()) {
                var = new Var(type);
            } else {
                var = this_();
            }
        }

        return new FieldVar(var, mConstants.addField(field));
    }

    private FieldVar field(Var var, String name) {
        Type type = var.mType.box();
        Type.Field field = findField(type, name);
        return new FieldVar(var, mConstants.addField(field));
    }

    private Type.Field findField(Type type, String name) {
        Type.Field field = type.findField(name);
        if (field == null) {
            throw new IllegalStateException("Field not found in " + type.name() + ": " + name);
        }
        return field;
    }

    @Override
    public Variable invoke(String name, Object... values) {
        return doInvoke(name, 0, values);
    }

    @Override
    public Variable invokeSuper(String name, Object... values) {
        return doInvoke(name, 1, values);
    }

    private Var doInvoke(String name, int inherit, Object... values) {
        Var this_ = mThisVar;
        if (this_ == null && mParams == null) {
            initParams();
            this_ = mThisVar;
        }

        return doInvoke(mClassMaker.type(), this_, name, inherit, values, null, null);
    }

    @Override
    public void invokeSuperConstructor(Object... values) {
        invokeConstructor(mClassMaker.superClass(), values);
    }

    @Override
    public void invokeThisConstructor(Object... values) {
        invokeConstructor(mClassMaker.mThisClass, values);
    }

    private void invokeConstructor(ConstantPool.C_Class type, Object[] values) {
        if (!"<init>".equals(getName())) {
            throw new IllegalStateException("Not defining a constructor");
        }

        doInvoke(type.mType, this_(), "<init>", -1, values, null, null);

        addOp(new Op() {
            @Override
            void appendTo(TheMethodMaker m) {
                mThisVar.mValid = true;
            }
        });
    }

    /**
     * @param instance non-null if can invoke an instance method; null if static only
     * @param inherit -1: cannot invoke inherited method, 0: can invoke inherited method,
     * 1: can only invoke super class method
     * @param args contains constants and variables
     * @param specificReturnType optional
     * @param specificParamTypes optional
     */
    Var doInvoke(Type type,
                 OwnedVar instance,
                 String methodName,
                 int inherit,
                 Object[] args,
                 Type specificReturnType,
                 Type[] specificParamTypes)
    {
        if (type.isPrimitive()) {
            type = type.box();
        }

        int staticMatch;

        if (instance != null) {
            staticMatch = 0; // maybe static
        } else if ("<init>".equals(methodName)) {
            // Calling a constructor for new object allocation.
            staticMatch = -1; // not static
        } else {
            staticMatch = 1; // only static
        }

        Op savepoint = mLastOp;

        // Push all arguments and obtain their actual types.
        Type[] paramTypes = new Type[args.length];
        for (int i=0; i<args.length; i++) {
            paramTypes[i] = addPushOp(null, args[i]);
        }

        Set<Type.Method> candidates =
            type.findMethods(methodName, paramTypes, inherit, staticMatch,
                             specificReturnType, specificParamTypes);

        Type.Method method;
        if (candidates.size() == 1) {
            method = candidates.iterator().next();
        } else {
            rollback(savepoint);
            if (candidates.isEmpty()) {
                throw noCandidates(type, methodName);
            } else {
                throw noBestCandidate(type, methodName, candidates);
            }
        }

        if (instance != null && !method.isStatic()) {
            // Need to go back and push the instance before the arguments.
            Op end = mLastOp;
            if (end == savepoint) {
                instance.pushObject();
                savepoint = mLastOp;
            } else {
                Op rest = rollback(savepoint);
                instance.pushObject();
                savepoint = mLastOp;
                mLastOp.mNext = rest;
                mLastOp = end;
            }
        }

        // Convert the parameter types if necessary.
        convert: {
            Type[] actualTypes = method.paramTypes();
            int len = actualTypes.length;

            if (!method.isVarargs() ||
                // Also check if method is varargs but array can be passed as-is.
                (len == paramTypes.length &&
                 paramTypes[len - 1].canConvertTo(actualTypes[len - 1]) != Integer.MAX_VALUE))
            {
                if (len != 0) {
                    check: {
                        for (int i=0; i<len; i++) {
                            if (!actualTypes[i].equals(paramTypes[i])) {
                                break check;
                            }
                        }
                        // Nothing to convert.
                        break convert;
                    }

                    rollback(savepoint);
                    
                    for (int i=0; i<args.length; i++) {
                        addPushOp(actualTypes[i], args[i]);
                    }
                }
            } else {
                rollback(savepoint);

                int firstLen = len - 1;
                int i = 0;
                for (; i < firstLen; i++) {
                    addPushOp(actualTypes[i], args[i]);
                }

                var vararg = new_(actualTypes[firstLen], args.length - i);

                for (; i<args.length; i++) {
                    vararg.aset(i - firstLen, args[i]);
                }

                addPushOp(null, vararg);
            }
        }

        int stackPop;
        byte op;
        if (method.isStatic()) {
            stackPop = 0;
            op = INVOKESTATIC;
        } else {
            stackPop = 1;
            if (method.enclosingType().isInterface()) {
                op = INVOKEINTERFACE;
            } else if (inherit == 0) {
                op = INVOKEVIRTUAL;
            } else {
                op = INVOKESPECIAL;
            }
        }

        stackPop += method.paramTypes().length;

        addOp(new InvokeOp(op, stackPop, mConstants.addMethod(method)));

        Type returnType = method.returnType();
        if (returnType == null || returnType == VOID) {
            return null;
        }

        Var var = new Var(returnType);
        addStoreOp(var);
        return var;
    }

    private static IllegalStateException noCandidates(Type type, String name) {
        return new IllegalStateException
            ("No matching methods found for: " + type.name() + '.' + name);
    }

    private static IllegalStateException noBestCandidate(Type type, String name,
                                                         Set<Type.Method> candidates)
    {
        var b = new StringBuilder()
            .append("No best matching method found for: ")
            .append(type.name()).append('.').append(name);

        if (!candidates.isEmpty()) {
            b.append(". Remaining candidates: ");
            int amt = 0;
            for (Type.Method m : candidates) {
                if (amt > 0) {
                    b.append(", ");
                }
                b.append(m);
                amt++;
            }
        }

        return new IllegalStateException(b.toString());
    }

    @Override
    public Variable invoke(MethodHandle handle, Object... values) {
        MethodType mtype = handle.type();

        if (mtype.parameterCount() != values.length) {
            throw new IllegalArgumentException("Wrong number of parameters");
        }

        Type returnType = mClassMaker.typeFrom(mtype.returnType());

        Type handleType = Type.from(MethodHandle.class);
        Var handleVar = new Var(handleType);
        handleVar.setConstant(handle);
        addOp(new PushVarOp(handleVar));

        // Push all arguments and obtain their actual types.
        Type[] paramTypes = new Type[values.length];
        for (int i=0; i<values.length; i++) {
            paramTypes[i] = addPushOp(Type.from(mtype.parameterType(i)), values[i]);
        }

        ConstantPool.C_Method ref = mConstants.addMethod
            (handleType.inventMethod(false, returnType, "invokeExact", paramTypes));

        addOp(new InvokeOp(INVOKEVIRTUAL, 1 + paramTypes.length, ref));

        if (returnType == VOID) {
            return null;
        }

        Var var = new Var(returnType);
        addStoreOp(var);
        return var;
    }

    @Override
    public Variable new_(Object objType, Object... values) {
        return new_(mClassMaker.typeFrom(objType), values);
    }

    private Variable new_(Type type, Object... values) {
        if (type.isArray()) {
            if (values == null || values.length == 0) {
                throw new IllegalArgumentException("At least one dimension required");
            }

            int dims = type.dimensions();

            if (values.length > dims || dims > 255) {
                throw new IllegalArgumentException("Too many dimensions");
            }

            for (Object size : values) {
                addPushOp(INT, size);
            }

            if (values.length == 1) {
                Type elementType = type.elementType();

                if (elementType.isObject()) {
                    ConstantPool.C_Class constant = mConstants.addClass(elementType);

                    addOp(new BytecodeOp(ANEWARRAY, 0) {
                        @Override
                        void appendTo(TheMethodMaker m) {
                            super.appendTo(m);
                            m.appendShort(constant.mIndex);
                        }
                    });
                } else {
                    byte atype;
                    switch (elementType.typeCode()) {
                    case T_BOOLEAN: atype = 4; break;
                    case T_CHAR: atype = 5; break;
                    case T_FLOAT: atype = 6; break;
                    case T_DOUBLE: atype = 7; break;
                    case T_BYTE: atype = 8; break;
                    case T_SHORT: atype = 9; break;
                    case T_INT: atype = 10; break;
                    case T_LONG: atype = 11; break;
                    default: throw new IllegalArgumentException(elementType.name());
                    }

                    addOp(new BytecodeOp(NEWARRAY, 0) {
                        @Override
                        void appendTo(TheMethodMaker m) {
                            super.appendTo(m);
                            m.appendByte(atype);
                        }
                    });
                }
            } else {
                ConstantPool.C_Class constant = mConstants.addClass(type);

                addOp(new BytecodeOp(MULTIANEWARRAY, values.length - 1) {
                    @Override
                    void appendTo(TheMethodMaker m) {
                        super.appendTo(m);
                        m.appendShort(constant.mIndex);
                        m.appendByte((byte) values.length);
                    }
                });
            }
        } else {
            ConstantPool.C_Class constant = mConstants.addClass(type);

            addOp(new BytecodeOp(NEW, 0) {
                @Override
                void appendTo(TheMethodMaker m) {
                    int newOffset = m.mCodeLen;
                    super.appendTo(m);
                    m.appendShort(constant.mIndex);
                    m.stackPush(type, newOffset);
                    m.appendOp(DUP, 0);
                    m.stackPush(type, newOffset);
                }
            });

            doInvoke(type, null, "<init>", -1, values, null, null);
        }

        Var var = new Var(type);
        addStoreOp(var);
        return var;
    }

    @Override
    public Variable catch_(Label start, Label end, Object type) {
        Lab startLab = target(start);
        Lab endLab = target(end);

        Type catchType;
        if (type == null) {
            catchType = Type.from(Throwable.class);
        } else {
            catchType = mClassMaker.typeFrom(type);
        }

        ConstantPool.C_Class catchClass = mConstants.addClass(catchType);
        int smCatchCode = SM_OBJECT | catchClass.mIndex << 8;

        // Generated catch class should "catch all" when given type is null. Granted, it's
        // always Throwable, but it matches what's generated for finally blocks.
        if (type == null) {
            catchClass = null;
        }

        var handlerLab = new HandlerLab(this, catchType);

        // Insert an operation at the start of the handled block, to capture the set of defined
        // local variables.
        Op startOp = new Op() {
            @Override
            void appendTo(TheMethodMaker m) {
                handlerLab.tryCatchStart(m, smCatchCode);
            }
        };

        Op next = startLab.mNext;
        startLab.mNext = startOp;
        startOp.mNext = next;
        if (next == null) {
            mLastOp = startOp;
        }

        handlerLab.here();

        var handler = new Handler(startLab, endLab, handlerLab, catchClass);

        if (mExceptionHandlers == null) {
            mExceptionHandlers = new ArrayList<>(4);
        }

        mExceptionHandlers.add(handler);

        Var var = new Var(catchType);
        addStoreOp(var);
        return var;
    }

    private static class Handler implements ExceptionHandler {
        final Lab mStartLab, mEndLab;
        final HandlerLab mHandlerLab;
        final ConstantPool.C_Class mCatchClass;

        Handler(Lab start, Lab end, HandlerLab handler, ConstantPool.C_Class catchClass) {
            mStartLab = start;
            mEndLab = end;
            mHandlerLab = handler;
            mCatchClass = catchClass;
        }

        @Override
        public int startAddr() {
            return mStartLab.mAddress;
        }

        @Override
        public int endAddr() {
            return mEndLab.mAddress;
        }

        @Override
        public int handlerAddr() {
            return mHandlerLab.mAddress;
        }

        @Override
        public ConstantPool.C_Class catchClass() {
            return mCatchClass;
        }

        boolean isVisited() {
            // Check if anything is visited in between the start and end labels.
            check: {
                for (Op op = mStartLab.mNext; op != null && op != mEndLab; op = op.mNext) {
                    if (op.mVisited) {
                        break check;
                    }
                }
                return false;
            }

            // Makes sure the labels aren't dropped. They're needed to build the exception table.
            mStartLab.mVisited = true;
            mEndLab.mVisited = true;

            mHandlerLab.flowThrough();

            return true;
        }
    }

    @Override
    public Variable concat(Object... values) {
        if (values.length == 0) {
            return var(String.class).set("");
        }

        if (values.length == 1) {
            Object value = values[0];
            String strValue;
            if (value == null) {
                strValue = "null";
            } else if (value instanceof String) {
                strValue = (String) value;
            } else {
                return var(String.class).invoke("valueOf", value);
            }
            return var(String.class).set(strValue);
        }

        if (values.length > 200) {
            // StringConcatFactory is limited to 200 values.
            var sb = new_(StringBuilder.class, values.length * 10);
            for (Object value : values) {
                sb = sb.invoke("append", value);
            }
            return sb.invoke("toString");
        }

        char[] recipe = null;
        List<ConstantPool.Constant> constants = null;
        List<Type> valueTypes = new ArrayList<>(values.length);

        for (int i=0; i<values.length; i++) {
            Object value = values[i];

            if (!(value instanceof Variable)) {
                if (value instanceof Character) {
                    char c = ((Character) value).charValue();
                    if (c > '\u0002' || c == '\u0000') {
                        if (recipe == null) {
                            recipe = new char[values.length];
                            Arrays.fill(recipe, '\u0001');
                        }
                        recipe[i] = c;
                        continue;
                    }
                }

                ConstantPool.Constant c = mConstants.tryAddLoadableConstant(value);

                if (c != null) {
                    if (recipe == null) {
                        recipe = new char[values.length];
                        Arrays.fill(recipe, '\u0001');
                    }
                    recipe[i] = '\u0002';
                    if (constants == null) {
                        constants = new ArrayList<>();
                    }
                    constants.add(c);
                    continue;
                }
            }

            valueTypes.add(addPushOp(null, value));
        }

        ConstantPool.Constant[] bootArgs;
        if (constants == null) {
            if (recipe == null) {
                bootArgs = new ConstantPool.Constant[0];
            } else {
                bootArgs = new ConstantPool.Constant[1];
                bootArgs[0] = mConstants.addString(new String(recipe));
            }
        } else {
            bootArgs = new ConstantPool.Constant[1 + constants.size()];
            bootArgs[0] = mConstants.addString(new String(recipe));
            for (int i=1; i<bootArgs.length; i++) {
                bootArgs[i] = constants.get(i - 1);
            }
        }

        Type strType = Type.from(String.class);

        String bootName;
        Type[] bootParams;

        if (recipe == null) {
            bootName = "makeConcat";
            bootParams = new Type[3];
        } else {
            bootName = "makeConcatWithConstants";
            bootParams = new Type[5];
            bootParams[3] = strType;
            bootParams[4] = Type.from(Object[].class);
        }

        bootParams[0] = Type.from(MethodHandles.Lookup.class);
        bootParams[1] = strType;
        bootParams[2] = Type.from(MethodType.class);

        ConstantPool.C_Method ref = mConstants.addMethod
            (Type.from(StringConcatFactory.class).inventMethod
             (true, Type.from(CallSite.class), bootName, bootParams));

        ConstantPool.C_MethodHandle bootstrapHandle =
            mConstants.addMethodHandle(MethodHandleInfo.REF_invokeStatic, ref);

        int bi = mClassMaker.addBootstrapMethod(bootstrapHandle, bootArgs);
        String desc = Type.makeDescriptor(strType, valueTypes);
        ConstantPool.C_Dynamic dynamic = mConstants.addInvokeDynamic(bi, bootName, desc);

        addOp(new InvokeDynamicOp(valueTypes.size(), dynamic, strType));

        Var var = new Var(strType);
        addStoreOp(var);
        return var;
    }

    @Override
    public void nop() {
        addOp(new BytecodeOp(NOP, 0));
    }

    /**
     * Track an entry which has just been pushed to the stack.
     */
    private void stackPush(Type type) {
        stackPush(type, -1);
    }

    /**
     * Track an entry which has just been pushed to the stack.
     *
     * @param newOffset offset of "new" instruction; pass -1 if not pushing a new object
     */
    private void stackPush(Type type, int newOffset) {
        int slot;
        if (mStackSize == 0) {
            slot = 0;
        } else {
            Var top = stackTop();
            slot = top.mSlot + top.slotWidth();
        }

        Var top;
        if (newOffset < 0) {
            top = new Var(type);
        } else {
            top = new NewVar(type, newOffset);
        }

        top.mSlot = slot;
        top.mValid = true;

        if (mStackSize >= mStack.length) {
            mStack = Arrays.copyOf(mStack, mStack.length << 1);
        }

        mStack[mStackSize++] = top;

        int max = slot + top.slotWidth();
        if (max > mMaxStackSlot) {
            mMaxStackSlot = max;
        }
    }

    /**
     * Duplicate the top stack entry.
     */
    private void stackDup() {
        Type type = stackTop().mType;

        byte op;
        switch (type.typeCode()) {
        default: op = DUP; break;
        case T_LONG: case T_DOUBLE: op = DUP2; break;
        }

        appendOp(op, 0);
        stackPush(type);
    }

    /**
     * Remove the top stack entry.
     */
    private void stackPop() {
        byte op;
        switch (stackTop().mType.typeCode()) {
        default: op = POP; break;
        case T_LONG: case T_DOUBLE: op = POP2; break;
        }

        appendOp(op, 1);
    }

    private Var stackTop() {
        return mStack[mStackSize - 1];
    }

    /**
     * Push a constant to the stack.
     *
     * @param type non-null
     */
    private void pushConstant(Object value, Type type) {
        if (value == null) {
            appendOp(ACONST_NULL, 0);
            stackPush(NULL);
        } else if (value instanceof String) {
            pushConstant(mConstants.addString((String) value), type);
        } else if (value instanceof Class) {
            pushConstant(mConstants.addClass(Type.from((Class) value)), type);
        } else if (value instanceof Number) {
            if (value instanceof Integer) {
                pushConstant(((Integer) value).intValue(), type);
            } else if (value instanceof Long) {
                pushConstant(((Long) value).longValue(), type);
            } else if (value instanceof Float) {
                pushConstant(((Float) value).floatValue(), type);
            } else if (value instanceof Double) {
                pushConstant(((Double) value).doubleValue(), type);
            } else if (value instanceof Byte) {
                pushConstant(((Byte) value).byteValue(), type);
            } else if (value instanceof Short) {
                pushConstant(((Short) value).shortValue(), type);
            }
        } else if (value instanceof Boolean) {
            pushConstant(((Boolean) value) ? 1 : 0, type);
        } else if (value instanceof Character) {
            pushConstant(((Character) value).charValue(), type);
        } else if (value instanceof Type) {
            pushConstant(mConstants.addClass((Type) value), type);
        } else if (value instanceof MethodType) {
            pushConstant(mConstants.addMethodType((MethodType) value), type);
        } else if (value instanceof MethodHandleInfo) {
            pushConstant(mConstants.addMethodHandle((MethodHandleInfo) value), type);
        } else {
            throw new AssertionError();
        }
    }

    /**
     * Push a constant value to the stack.
     */
    private void pushConstant(int value, Type type) {
        if (value >= -1 && value <= 5) {
            appendOp((byte) (ICONST_0 + value), 0);
        } else if (value >= -128 && value < 128) {
            appendOp(BIPUSH, 0);
            appendByte(value);
        } else if (value >= -32768 && value < 32768) {
            appendOp(SIPUSH, 0);
            appendShort(value);
        } else {
            pushConstant(mConstants.addInteger(value), type);
            return;
        }
        stackPush(type);
    }

    /**
     * Push a constant value to the stack.
     */
    private void pushConstant(long value, Type type) {
        if (value >= 0 && value <= 1) {
            appendOp((byte) (LCONST_0 + value), 0);
        } else {
            appendOp(LDC2_W, 0);
            appendShort(mConstants.addLong(value).mIndex);
        }
        stackPush(type);
    }

    /**
     * Push a constant value to the stack.
     */
    private void pushConstant(float value, Type type) {
        byte op;
        if (Float.compare(value, 0.0f) == 0) { // account for -0.0 constant
            op = FCONST_0;
        } else if (value == 1) {
            op = FCONST_1;
        } else if (value == 2) {
            op = FCONST_2;
        } else {
            pushConstant(mConstants.addFloat(value), type);
            return;
        }
        appendOp(op, 0);
        stackPush(type);
    }

    /**
     * Push a constant value to the stack.
     */
    private void pushConstant(double value, Type type) {
        doAppend: {
            byte op;
            if (Double.compare(value, 0.0) == 0) { // account for -0.0 constant
                op = DCONST_0;
            } else if (value == 1) {
                op = DCONST_1;
            } else {
                appendOp(LDC2_W, 0);
                appendShort(mConstants.addDouble(value).mIndex);
                break doAppend;
            }
            appendOp(op, 0);
        }
        stackPush(type);
    }

    /**
     * Push a constant value to the stack. Must not be used for long or double types.
     */
    private void pushConstant(ConstantPool.Constant constant, Type type) {
        int index = constant.mIndex;

        if (index < 256) {
            appendOp(LDC, 0);
            appendByte(index);
        } else {
            appendOp(LDC_W, 0);
            appendShort(index);
        }

        stackPush(type);
    }

    /**
     * Push a variable to the stack.
     */
    private void pushVar(Var var) {
        if (var != null && var == mClassVar) {
            pushConstant(mClassMaker.mThisClass, var.mType);
            return;
        }

        int slot = var.mSlot;

        doPush: {
            byte op;
            tiny: {
                switch (var.mType.stackMapCode()) {
                default:
                    throw new IllegalStateException("Unsupported variable type");
                case SM_INT:
                    if (slot <= 3) {
                        op = ILOAD_0;
                        break tiny;
                    }
                    op = ILOAD;
                    break;
                case SM_FLOAT:
                    if (slot <= 3) {
                        op = FLOAD_0;
                        break tiny;
                    }
                    op = FLOAD;
                    break;
                case SM_DOUBLE:
                    if (slot <= 3) {
                        op = DLOAD_0;
                        break tiny;
                    }
                    op = DLOAD;
                    break;
                case SM_LONG:
                    if (slot <= 3) {
                        op = LLOAD_0;
                        break tiny;
                    }
                    op = LLOAD;
                    break;
                case SM_OBJECT:
                    if (slot <= 3) {
                        op = ALOAD_0;
                        break tiny;
                    }
                    op = ALOAD;
                    break;
                }
                if (slot < 256) {
                    appendOp(op, 0);
                    appendByte(slot);
                } else {
                    appendOp(WIDE, 0);
                    appendByte(op);
                    appendShort(slot);
                }
                break doPush;
            }

            appendOp((byte) (op + slot), 0);
        }

        stackPush(var.mType);
    }

    /**
     * Converts the item on the top of the stack. Must call canConvert first and pass in the code.
     */
    private Type convert(Type from, Type to, int code) {
        if (code <= 0) {
            return to;
        }

        if (code < 5) {
            return convertPrimitive(to, code);
        }

        if (code < 10) {
            code -= 5;
            Type primTo = convertPrimitive(to, code);
            if (primTo == null) {
                // Assume converting to Object/Number. Need something specific.
                primTo = convertPrimitive(from.box(), code);
            }
            return box(primTo);
        }

        if (code < 15) {
            // Rebox without throwing NPE.
            stackDup();
            Lab nonNull = new Lab(this);
            new BranchOp(IFNONNULL, 1, nonNull).appendTo(this);
            // Pop/push of null is required to make verifier happy.
            stackPop();
            appendOp(ACONST_NULL, 0);
            stackPush(to);
            Lab end = new Lab(this);
            new BranchOp(GOTO, 0, end).appendTo(this);
            nonNull.appendTo(this);
            unbox(from);
            Type toPrim = to.unbox();
            convertPrimitive(toPrim, code - 10);
            box(toPrim);
            end.appendTo(this);
            return to;
        }

        if (code < 20) {
            unbox(from);
            return convertPrimitive(to, code - 15);
        }

        throw new AssertionError();
    }

    private Type convertPrimitive(Type to, int code) {
        switch (code) {
        default:
            return to.unbox();
        case 1:
            appendOp(I2L, 1);
            to = LONG;
            break;
        case 2:
            appendOp(I2F, 1);
            to = FLOAT;
            break;
        case 3:
            appendOp(I2D, 1);
            to = DOUBLE;
            break;
        case 4:
            appendOp(F2D, 1);
            to = DOUBLE;
            break;
        }

        stackPush(to);
        return to;
    }

    /**
     * Box a primitive type on the stack, resulting in an object on the stack.
     *
     * @return object type
     */
    private Type box(Type primType) {
        Type objType = primType.box();
        Type.Method method = objType.defineMethod(true, objType, "valueOf", primType);
        appendOp(INVOKESTATIC, 1);
        appendShort(mConstants.addMethod(method).mIndex);
        stackPush(objType);
        return objType;
    }

    /**
     * Unbox a boxed primitive type on the stack, resulting in a primitive type on the stack.
     * Can throw a NullPointerException at runtime.
     *
     * @return primitive type
     */
    private Type unbox(Type objType) {
        Type primType = objType.unbox();
        Type.Method method = objType.defineMethod(false, primType, primType.name() + "Value");
        appendOp(INVOKEVIRTUAL, 1);
        appendShort(mConstants.addMethod(method).mIndex);
        stackPush(primType);
        return primType;
    }

    /**
     * Pop an item off the stack and store the result into a variable.
     */
    private void storeVar(Var var) {
        int slot = var.mSlot;

        byte op;
        tiny: {
            switch (var.mType.stackMapCode()) {
            default:
                throw new IllegalStateException("Unsupported variable type");
            case SM_INT:
                if (slot <= 3) {
                    op = ISTORE_0;
                    break tiny;
                }
                op = ISTORE;
                break;
            case SM_FLOAT:
                if (slot <= 3) {
                    op = FSTORE_0;
                    break tiny;
                }
                op = FSTORE;
                break;
            case SM_DOUBLE:
                if (slot <= 3) {
                    op = DSTORE_0;
                    break tiny;
                }
                op = DSTORE;
                break;
            case SM_LONG:
                if (slot <= 3) {
                    op = LSTORE_0;
                    break tiny;
                }
                op = LSTORE;
                break;
            case SM_OBJECT:
                if (slot <= 3) {
                    op = ASTORE_0;
                    break tiny;
                }
                op = ASTORE;
                break;
            }

            if (slot < 256) {
                appendOp(op, 1);
                appendByte(slot);
            } else {
                appendOp(WIDE, 1);
                appendByte(op);
                appendShort(slot);
            }

            return;
        }

        appendOp((byte) (op + slot), 1);
    }

    /**
     * @param stackPop amount of stack elements popped by this operation
     */
    private void appendOp(byte op, int stackPop) {
        appendByte(op);

        if (stackPop > 0) {
            int newSize = mStackSize - stackPop;
            if (newSize < 0) {
                throw new IllegalStateException("Stack is empty");
            }
            mStackSize = newSize;
        }
    }

    private void appendByte(int v) {
        ensureSpace(1);
        mCode[mCodeLen++] = (byte) v;
    }

    private void appendShort(int v) {
        ensureSpace(2);
        cShortArrayHandle.set(mCode, mCodeLen, (short) v);
        mCodeLen += 2;
    }

    private void appendInt(int v) {
        ensureSpace(4);
        cIntArrayHandle.set(mCode, mCodeLen, v);
        mCodeLen += 4;
    }

    private void appendPad(int pad) {
        ensureSpace(pad);
        mCodeLen += pad;
    }

    private void ensureSpace(int amt) {
        int require = mCodeLen + amt - mCode.length;
        if (require > 0) {
            growSpace(require);
        }
    }

    private void growSpace(int require) {
        int newLen = Math.max(mCode.length + require, mCode.length << 1);
        newLen = Math.min(newLen, 65536);
        if (newLen <= mCode.length) {
            throw new IllegalStateException("Code limit reached");
        }
        mCode = Arrays.copyOf(mCode, newLen);
    }

    @Override
    public String toString() {
        return mMethod.toString();
    }

    private void addOp(Op op) {
        if (mLastOp == null) {
            mFirstOp = op;
        } else {
            mLastOp.mNext = op;
        }
        mLastOp = op;
    }

    /**
     * @param savepoint was mLastOp
     * @return start of chain which was clipped off
     */
    private Op rollback(Op savepoint) {
        if (savepoint == mLastOp) {
            // Nothing to rollback.
            return null;
        }
        Op start;
        if (savepoint == null) {
            start = mFirstOp;
            mFirstOp = null;
        } else {
            start = savepoint.mNext;
            savepoint.mNext = null;
        }
        mLastOp = savepoint;
        return start;
    }

    /**
     * @param stackPop amount of stack elements popped by this operation
     */
    private void addBytecodeOp(byte op, int stackPop) {
        addOp(new BytecodeOp(op, stackPop));
    }

    private void addPushFieldOp(FieldVar fieldVar) {
        Type.Field field = fieldVar.mFieldRef.mField;

        byte op;
        int stackPop;
        if (field.isStatic()) {
            op = GETSTATIC;
            stackPop = 0;
        } else {
            addPushOp(null, fieldVar.mInstance);
            op = GETFIELD;
            stackPop = 1;
        }

        addOp(new FieldOp(op, stackPop, fieldVar.mFieldRef));
    }

    private void addBeginStoreFieldOp(FieldVar fieldVar) {
        if (!fieldVar.mFieldRef.mField.isStatic()) {
            addPushOp(null, fieldVar.mInstance);
        }
    }

    private void addFinishStoreFieldOp(FieldVar fieldVar) {
        ConstantPool.C_Field fieldRef = fieldVar.mFieldRef;
        Type.Field field = fieldRef.mField;

        byte op;
        int stackPop;
        if (field.isStatic()) {
            op = PUTSTATIC;
            stackPop = 1;
        } else {
            op = PUTFIELD;
            stackPop = 2;
        }

        addOp(new FieldOp(op, stackPop, fieldRef));
    }

    /**
     * Add a push constant or push variable operation, and optionally perform a conversion.
     *
     * @param type desired type of entry on the stack; pass null if anything can be pushed
     * @return actual type
     */
    private Type addPushOp(Type type, Object value) {
        if (value instanceof OwnedVar) {
            OwnedVar owned = (OwnedVar) value;
            if (owned.owner() != this) {
                throw new IllegalArgumentException("Unknown variable");
            }
            Type actualType;
            if (owned instanceof Var) {
                Var var = (Var) owned;
                addOp(new PushVarOp(var));
                actualType = var.mType;
            } else {
                FieldVar field = (FieldVar) owned;
                addPushFieldOp(field);
                actualType = field.type();
            }
            return addConversionOp(actualType, type);
        }

        Type constantType;

        if (value == null) {
            if (type != null && type.isPrimitive()) {
                throw new IllegalArgumentException("Cannot store null into primitive variable");
            }
            constantType = NULL;
        } else if (value instanceof String) {
            constantType = Type.from(String.class);
        } else if (value instanceof Class) {
            constantType = Type.from(Class.class);
            Class clazz = (Class) value;
            if (clazz.isPrimitive()) {
                addPushFieldOp(new Var(Type.from(clazz).box()).field("TYPE"));
                return addConversionOp(constantType, type);
            }
        } else if (value instanceof Number) {
            if (value instanceof Integer) {
                constantType = INT;
                if (type != null) {
                    int v = ((Integer) value).intValue();
                    switch (type.unboxTypeCode()) {
                    case T_BYTE:
                        if (((byte) v) == v) {
                            value = (byte) v;
                            constantType = BYTE;
                        }
                        break;
                    case T_SHORT:
                        if (((short) v) == v) {
                            value = (short) v;
                            constantType = SHORT;
                        }
                        break;
                    case T_FLOAT:
                        float fv = (float) v;
                        if ((int) fv == v) {
                            value = fv;
                            constantType = FLOAT;
                        }
                        break;
                    case T_DOUBLE:
                        value = (double) v;
                        constantType = DOUBLE;
                        break;
                    case T_LONG:
                        value = (long) v;
                        constantType = LONG;
                        break;
                    }
                }
            } else if (value instanceof Long) {
                constantType = LONG;
                if (type != null) {
                    long v = ((Long) value).longValue();
                    switch (type.unboxTypeCode()) {
                    case T_BYTE:
                        byte bv = (byte) v;
                        if ((long) bv == v) {
                            value = bv;
                            constantType = BYTE;
                        }
                        break;
                    case T_SHORT:
                        short sv = (short) v;
                        if ((long) sv == v) {
                            value = sv;
                            constantType = SHORT;
                        }
                        break;
                    case T_INT:
                        int iv = (int) v;
                        if ((long) iv == v) {
                            value = iv;
                            constantType = INT;
                        }
                        break;
                    case T_FLOAT:
                        float fv = (float) v;
                        if ((long) fv == v) {
                            value = fv;
                            constantType = FLOAT;
                        }
                        break;
                    case T_DOUBLE:
                        double dv = (double) v;
                        if ((long) dv == v) {
                            value = dv;
                            constantType = DOUBLE;
                        }
                        break;
                    }
                }
            } else if (value instanceof Float) {
                constantType = FLOAT;
                if (type != null) {
                    float v = ((Float) value).floatValue();
                    switch (type.unboxTypeCode()) {
                    case T_BYTE:
                        byte bv = (byte) v;
                        if ((float) bv == v) {
                            value = bv;
                            constantType = BYTE;
                        }
                        break;
                    case T_SHORT:
                        short sv = (short) v;
                        if ((float) sv == v) {
                            value = sv;
                            constantType = SHORT;
                        }
                        break;
                    case T_INT:
                        int iv = (int) v;
                        if ((float) iv == v) {
                            value = iv;
                            constantType = INT;
                        }
                        break;
                    case T_LONG:
                        long lv = (long) v;
                        if ((float) lv == v) {
                            value = lv;
                            constantType = LONG;
                        }
                        break;
                    case T_DOUBLE:
                        value = (double) v;
                        constantType = DOUBLE;
                        break;
                    }
                }
            } else if (value instanceof Double) {
                constantType = DOUBLE;
                if (type != null) {
                    double v = ((Double) value).doubleValue();
                    switch (type.unboxTypeCode()) {
                    case T_BYTE:
                        byte bv = (byte) v;
                        if ((double) bv == v) {
                            value = bv;
                            constantType = BYTE;
                        }
                        break;
                    case T_SHORT:
                        short sv = (short) v;
                        if ((double) sv == v) {
                            value = sv;
                            constantType = SHORT;
                        }
                        break;
                    case T_INT:
                        int iv = (int) v;
                        if ((double) iv == v) {
                            value = iv;
                            constantType = INT;
                        }
                        break;
                    case T_FLOAT:
                        float fv = (float) v;
                        if (Double.doubleToLongBits(fv) == Double.doubleToLongBits(v)) {
                            value = fv;
                            constantType = FLOAT;
                        }
                        break;
                    case T_LONG:
                        long lv = (long) v;
                        if ((double) lv == v) {
                            value = lv;
                            constantType = LONG;
                        }
                        break;
                    }
                }
            } else if (value instanceof Byte) {
                constantType = BYTE;
                if (type != null) {
                    byte v = ((Byte) value).byteValue();
                    switch (type.unboxTypeCode()) {
                    case T_SHORT:
                        value = (short) v;
                        constantType = SHORT;
                        break;
                    case T_INT:
                        value = (int) v;
                        constantType = INT;
                        break;
                    case T_FLOAT:
                        value = (float) v;
                        constantType = FLOAT;
                        break;
                    case T_LONG:
                        value = (long) v;
                        constantType = LONG;
                        break;
                    case T_DOUBLE:
                        value = (double) v;
                        constantType = DOUBLE;
                        break;
                    }
                }
            } else if (value instanceof Short) {
                constantType = SHORT;
                if (type != null) {
                    short v = ((Short) value).shortValue();
                    switch (type.unboxTypeCode()) {
                    case T_BYTE:
                        byte bv = (byte) v;
                        if ((short) bv == v) {
                            value = (bv);
                            constantType = BYTE;
                        }
                        break;
                    case T_INT:
                        value = (int) v;
                        constantType = INT;
                        break;
                    case T_FLOAT:
                        value = (float) v;
                        constantType = FLOAT;
                        break;
                    case T_LONG:
                        value = (long) v;
                        constantType = LONG;
                        break;
                    case T_DOUBLE:
                        value = (double) v;
                        constantType = DOUBLE;
                        break;
                    }
                }
            } else {
                throw unsupportedConstant(value);
            }
        } else if (value instanceof Boolean) {
            constantType = BOOLEAN;
        } else if (value instanceof Character) {
            constantType = CHAR;
        } else if (value instanceof Type) {
            constantType = Type.from(Class.class);
            Type actualType = (Type) value;
            if (actualType.isPrimitive()) {
                addPushFieldOp(new Var(actualType.box()).field("TYPE"));
                return addConversionOp(constantType, type);
            }
        } else if (value instanceof MethodType) {
            constantType = Type.from(MethodType.class);
        } else if (value instanceof MethodHandleInfo) {
            constantType = Type.from(MethodHandleInfo.class);
            if (type != null && type.equals(Type.from(MethodHandle.class))) {
                // Conversion to MethodHandle is automatic.
                constantType = type;
            }
        } else if (value instanceof Enum) {
            constantType = Type.from(value.getClass());
            addPushFieldOp(new Var(constantType).field(((Enum) value).name()));
            return addConversionOp(constantType, type);
        } else {
            throw unsupportedConstant(value);
        }

        addOp(new PushConstantOp(value, constantType));

        return addConversionOp(constantType, type);
    }

    /**
     * Add a conversion operation, except if the types are the same, or if "to" is null.
     *
     * @throws IllegalStateException if conversion is disallowed
     * @return actual type
     */
    private Type addConversionOp(Type from, Type to) {
        if (to != null && !from.equals(to)) {
            doAddConversionOp(from, to, from.canConvertTo(to));
            return to;
        }
        return from;
    }

    private void doAddConversionOp(Type from, Type to, int code) {
        if (code == Integer.MAX_VALUE) {
            throw new IllegalStateException
                ("Automatic conversion disallowed: " + from.name() + " to " + to.name());
        }
        addOp(new ConversionOp(from, to, code));
    }

    /**
     * Adds a constant using the ConstantsRegistry.
     */
    private ConstantPool.C_Dynamic addComplexConstant(Type type, Object value) {
        Type classType = Type.from(Class.class);

        Set<Type.Method> bootstraps = Type.from(ConstantsRegistry.class).findMethods
            ("remove",
             new Type[] {Type.from(MethodHandles.Lookup.class), Type.from(String.class),
                         classType, classType, Type.INT},
             0, 1, null, null);

        if (bootstraps.size() != 1) {
            throw new AssertionError();
        }

        ConstantPool.C_Method ref = mConstants.addMethod(bootstraps.iterator().next());
        ConstantPool.C_MethodHandle bootHandle =
            mConstants.addMethodHandle(MethodHandleInfo.REF_invokeStatic, ref);

        int slot = mClassMaker.addComplexConstant(value);

        ConstantPool.Constant[] bootArgs = {
            addLoadableConstant(classType, class_()), addLoadableConstant(Type.INT, slot)
        };

        int bi = mClassMaker.addBootstrapMethod(bootHandle, bootArgs);

        // Note that "const" isn't used by the bootstrap method. It's a dummy.
        return mConstants.addDynamicConstant(bi, "dummy", type);
    }

    /**
     * Intended for loading bootstrap constants, although it works in other cases too.
     *
     * @param type expected constant type; can pass null to derive from the arg itself
     */
    private ConstantPool.Constant addLoadableConstant(Type type, Object value) {
        ConstantPool.Constant c = mConstants.tryAddLoadableConstant(value);
        if (c != null) {
            return c;
        }

        if (value != null && value == mClassVar) {
            return mClassMaker.mThisClass;
        }

        // Pass as a dynamic constant. First, try to use the ConstantBootstraps class.

        final Type classType = Type.from(Class.class);

        special: {
            final String method, name;
            final Type retType, paramType;

            if (value == null) {
                method = "nullConstant";
                name = method; // unused
                retType = paramType = Type.from(Object.class);
            } else {
                prim: {
                    if (value instanceof Type) {
                        type = (Type) value;
                        if (!type.isPrimitive()) {
                            // Not expected. Should have been handled by tryAddLoadableConstant.
                            break special;
                        }
                    } else if (value instanceof Class) {
                        var clazz = (Class) value;
                        if (!clazz.isPrimitive()) {
                            // Not expected. Should have been handled by tryAddLoadableConstant.
                            break special;
                        }
                        type = Type.from(clazz);
                    } else if (value instanceof Enum) {
                        method = "enumConstant";
                        name = ((Enum) value).name();
                        retType = Type.from(Enum.class);
                        paramType = Type.from(value.getClass());
                        break prim;
                    } else {
                        break special;
                    }
                    method = "primitiveClass";
                    name = type.descriptor();
                    retType = paramType = classType;
                }
            }

            Type[] bootParams = {
                Type.from(MethodHandles.Lookup.class), Type.from(String.class), classType
            };

            ConstantPool.C_Method ref = mConstants.addMethod
                (Type.from(ConstantBootstraps.class).inventMethod
                 (true, retType, method, bootParams));

            ConstantPool.C_MethodHandle bootHandle =
                mConstants.addMethodHandle(MethodHandleInfo.REF_invokeStatic, ref);

            return mConstants.addDynamicConstant
                (mClassMaker.addBootstrapMethod(bootHandle, new ConstantPool.Constant[0]),
                 name, paramType);
        }

        if (value instanceof Variable) {
            // A variable isn't a constant.
            throw unsupportedConstant(value);
        }

        // Use ConstantsRegistry. In doing so, the generated class cannot be loaded from a file.

        if (type == null) {
            type = Type.from(value.getClass());
        }

        return addComplexConstant(type, value);
    }

    private static IllegalArgumentException unsupportedConstant(Object value) {
        return new IllegalArgumentException
            ("Unsupported constant type: " + (value == null ? "null" : value.getClass()));
    }

    /**
     * Add a store to variable operation.
     */
    private void addStoreOp(Var var) {
        if (var.owner() != this) {
            throw new IllegalArgumentException("Unknown variable");
        }
        if (var instanceof ClassVar) {
            throw new IllegalStateException("Unmodifiable variable");
        }
        addOp(new StoreVarOp(var));
    }

    /**
     * Add a math operation, supporting ints, longs, floats, and doubles.
     *
     * @param op IADD, ISUB, IMUL, IDIV, IREM, or INEG
     * @param value must be null for unary operation
     * @return new variable
     */
    private Var addMathOp(String name, byte op, OwnedVar var, Object value) {
        Type varType = var.type();
        Type primType = varType.unbox();

        if (primType == null) {
            throw new IllegalStateException("Cannot " + name + " to a non-numeric type");
        }

        if (op != INEG && value == null) {
            throw new IllegalArgumentException("Cannot " + name + " by null");
        }

        switch (primType.stackMapCode()) {
        case SM_LONG:
            op += 1;
            break;
        case SM_FLOAT:
            op += 2;
            break;
        case SM_DOUBLE:
            op += 3;
            break;
        }

        addPushOp(primType, var);

        int stackPop = 0;
        if (value != null) {
            addPushOp(primType, value);
            stackPop = 1;
        }

        addBytecodeOp(op, stackPop);
        addConversionOp(primType, varType);

        Var dst = new Var(varType);
        addStoreOp(dst);
        return dst;
    }

    /**
     * Adds a logical operation, supporting ints and longs.
     *
     * @param op ISHL, ISHR, IUSHR, IAND, IOR, or IXOR
     * @return new variable
     */
    private Var addLogicalOp(String name, byte op, OwnedVar var, Object value) {
        Type varType = var.type();
        Type primType = varType.unbox();

        if (primType == null) {
            throw new IllegalStateException("Cannot " + name + " to a non-numeric type");
        }

        if (value == null) {
            throw new IllegalArgumentException("Cannot " + name + " by null");
        }

        switch (primType.stackMapCode()) {
        case SM_LONG:
            op += 1;
            break;
        case SM_FLOAT: case SM_DOUBLE:
            throw new IllegalStateException("Cannot " + name + " to a non-integer type");
        }

        addPushOp(primType, var);

        if ((op & 0xff) < IAND) {
            // Second argument to shift instruction is always an int.
            // Note: Automatic downcast from long could be allowed, but it's not really necessary.
            addPushOp(INT, value);
        } else {
            addPushOp(varType, value);
        }

        addBytecodeOp(op, 1);
        addConversionOp(primType, varType);

        Var dst = new Var(varType);
        addStoreOp(dst);
        return dst;
    }

    private void addBranchOp(byte op, int stackPop, Label label) {
        addOp(new BranchOp(op, stackPop, target(label)));
    }

    private Lab target(Label label) {
        if (label instanceof Lab) {
            Lab lab = (Lab) label;
            if (lab.mOwner == this) {
                return lab;
            }
        }
        if (label == null) {
            throw new IllegalArgumentException("Label is null");
        }
        throw new IllegalArgumentException("Unknown label");
    }

    private static void sortSwitchCases(int[] cases, Label[] labels) {
        sortSwitchCases(cases, labels, 0, cases.length - 1);
    }

    private static void sortSwitchCases(int[] cases, Label[] labels, int low, int high) {
        if (low < high) {
            swapSwitchCases(cases, labels, low, (low + high) / 2); // move middle element to 0
            int last = low;
            for (int i = low + 1; i <= high; i++) {
                if (cases[i] < cases[low]) {
                    swapSwitchCases(cases, labels, ++last, i);
                }
            }
            swapSwitchCases(cases, labels, low, last);
            sortSwitchCases(cases, labels, low, last - 1);
            sortSwitchCases(cases, labels, last + 1, high);
        }
    }

    private static void swapSwitchCases(int[] cases, Label[] labels, int i, int j) {
        int tempInt = cases[i];
        cases[i] = cases[j];
        cases[j] = tempInt;
        Label tempLabel = labels[i];
        labels[i] = labels[j];
        labels[j] = tempLabel;
    }

    /**
     * @param defaultLabel required
     * @param cases must be sorted
     */
    private void addSwitchOp(Lab defaultLabel, int[] cases, Lab[] labels) {
        // Determine which kind of switch to use based on encoding size.
        long tSize = 12 + 4 * (((long) cases[cases.length - 1]) - cases[0] + 1);
        long lSize = 8 + 8 * cases.length;
        byte op = (tSize <= lSize) ? TABLESWITCH : LOOKUPSWITCH;
        addOp(new SwitchOp(op, defaultLabel, cases, labels));
    }

    abstract static class Op {
        Op mNext;
        boolean mVisited;

        abstract void appendTo(TheMethodMaker m);

        /**
         * Should be called before running another pass over the code.
         */
        void reset() {
        }

        /**
         * Recursively flows through unvisited operations and returns the next operation.
         */
        Op flow() {
            return mNext;
        }

        final void flowThrough() {
            Op op = this;
            while (true) {
                if (op.mVisited) {
                    break;
                }
                op.mVisited = true;
                Op next = op.flow();
                if (next == null) {
                    break;
                }
                if (next instanceof HandlerLab) {
                    throw new IllegalStateException("Code flows into an exception handler");
                }
                op = next;
            }
        }
    }

    static class Lab extends Op implements Label {
        final TheMethodMaker mOwner;

        int mAddress = -1;

        private int[] mTrackOffsets;
        private BranchOp[] mTrackBranches;
        private int mTrackCount;

        StackMapTable.Frame mFrame;

        Lab(TheMethodMaker owner) {
            mOwner = owner;
        }

        @Override
        void reset() {
            mAddress = -1;
            mTrackCount = 0;
            mFrame = null;
        }

        @Override
        public Label here() {
            if (mNext != null || mOwner.mLastOp == this) {
                throw new IllegalStateException();
            }
            mOwner.addOp(this);
            return this;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            // Pseudo-op (nothing to append).

            mAddress = m.mCodeLen;
            reached(m);

            if (mTrackOffsets != null && mTrackCount != 0) {
                byte[] code = m.mCode;
                for (int i=0; i<mTrackCount; i++) {
                    int offset = mTrackOffsets[i];
                    if (offset >= 0) {
                        int branchAmount = mAddress - (short) cShortArrayHandle.get(code, offset);
                        if (branchAmount <= 32767) {
                            cShortArrayHandle.set(code, offset, (short) branchAmount);
                        } else {
                            // Convert to wide branch and perform another code pass.
                            mTrackBranches[i].makeWide();
                            m.mFinished = -1;
                        }
                    } else {
                        // Wide encoding.
                        offset = ~offset;
                        int branchAmount = mAddress - (int) cIntArrayHandle.get(code, offset);
                        cIntArrayHandle.set(code, offset, branchAmount);
                    }
                }
                m.mUnpositionedLabels--;
                mTrackCount = 0;
            }

            if (mFrame != null) {
                int stackSize = mFrame.setAddress(m.mStackMapTable, mAddress);
                int growth = stackSize - m.mStackSize;
                if (growth > 0) {
                    if (mTrackOffsets != null || growth > 1) {
                        throw new IllegalStateException("False stack growth");
                    }
                    // This label is being used for an exception handler catch location. Don't
                    // adjust the stack because the next operation should do that. See the
                    // operation appended by the catch_ method after calling handlerLab.here().
                } else if (growth < 0) {
                    m.mStackSize = stackSize;
                }

                m.invalidateVariables(mFrame);
            }
        }

        /**
         * Must be called when label is used as a branch target, but never when used as an
         * exception handler.
         */
        void used() {
            if (mTrackOffsets == null) {
                mTrackOffsets = new int[4];
            }
        }

        /**
         * Must be called after a branch operation to this label has been appended. The used
         * method must have already been called when the operation was initially created.
         */
        void comesFrom(BranchOp branch, TheMethodMaker m) {
            reached(m);

            int srcAddr = m.mCodeLen - 1; // opcode address

            if (mAddress >= 0) {
                int distance = mAddress - srcAddr;

                if (distance >= -32768) {
                    m.appendShort(distance);
                    return;
                }

                // Wide back branch.

                byte op = m.mCode[srcAddr];
                if (op == GOTO) {
                    m.mCode[srcAddr] = GOTO_W;
                    m.appendInt(distance);
                    return;
                }

                if (IFEQ <= op && op <= IF_ACMPNE || IFNULL <= op && op <= IFNONNULL) {
                    m.mCode[srcAddr] = flipIf(op);
                    m.appendShort(3 + 5); // branch past the modified op and the wide goto
                    srcAddr = m.mCodeLen;
                    m.appendByte(GOTO_W);
                    m.appendInt(mAddress - srcAddr);
                    m.addStackMapFrame(m.mCodeLen); // need a frame for modified op target
                    return;
                }

                // Unknown branch op.
                throw new AssertionError();
            }

            // Track the offset and update the code when the label is positioned. Encode the
            // offset for later calculation of the relative branch amount.

            int offset = m.mCodeLen;
            m.appendShort(srcAddr);

            addTrackOffset(m, offset);

            // Also track the branch itself, in case it needs to be wide.

            if (mTrackBranches == null) {
                mTrackBranches = new BranchOp[mTrackOffsets.length];
            } if (mTrackBranches.length < mTrackOffsets.length) {
                mTrackBranches = Arrays.copyOf(mTrackBranches, mTrackOffsets.length);
            }

            mTrackBranches[mTrackCount - 1] = branch;
        }

        private void addTrackOffset(TheMethodMaker m, int offset) {
            if (mTrackCount == 0) {
                m.mUnpositionedLabels++;
            } else if (mTrackCount >= mTrackOffsets.length) {
                mTrackOffsets = Arrays.copyOf(mTrackOffsets, mTrackOffsets.length << 1);
            }
            mTrackOffsets[mTrackCount++] = offset;
        }

        void comesFromWide(TheMethodMaker m, int srcAddr) {
            reached(m);

            if (mAddress >= 0) {
                m.appendInt(mAddress - srcAddr);
                return;
            }

            // Track the offset and update the code when the label is positioned. Encode the
            // offset for later calculation of the relative branch amount.
            int offset = m.mCodeLen;
            m.appendInt(srcAddr);

            // Encode negative offset to indicate wide encoding.
            addTrackOffset(m, ~offset);
        }

        /**
         * Must be called at the start of a try-catch block, when this label is used as an
         * exception handler.
         */
        void tryCatchStart(TheMethodMaker m, int smCatchCode) {
            if (mFrame == null) {
                mFrame = m.addStackMapFrameForCatch(mAddress, smCatchCode);
            }
        }

        void reached(TheMethodMaker m) {
            if (mTrackOffsets != null && mFrame == null) {
                // First time used as a target, so capture the stack map state.
                mFrame = m.addStackMapFrame(mAddress);
            }
        }
    }

    /**
     * Exception handler catch label.
     */
    static class HandlerLab extends Lab {
        private final Type mCatchType;

        HandlerLab(TheMethodMaker owner, Type catchType) {
            super(owner);
            mCatchType = catchType;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            super.appendTo(m);
            m.stackPush(mCatchType);
        }
    }

    /**
     * Simple bytecode operation.
     */
    static class BytecodeOp extends Op {
        int mCode;

        /**
         * @param stackPop amount of stack elements popped by this operation
         */
        BytecodeOp(byte op, int stackPop) {
            mCode = (stackPop << 8) | (op & 0xff);
        }

        @Override
        void appendTo(TheMethodMaker m) {
            m.appendOp(op(), stackPop());
        }

        final byte op() {
            return (byte) mCode;
        }

        final int stackPop() {
            return mCode >>> 8;
        }

        @Override
        Op flow() {
            int op = op();
            if ((IRETURN <= op && op <= RETURN) || op == ATHROW) {
                return null;
            }
            return mNext;
        }
    }

    static class BranchOp extends BytecodeOp {
        final Lab mTarget;

        /**
         * @param stackPop amount of stack elements popped by this operation
         */
        BranchOp(byte op, int stackPop, Lab target) {
            super(op, stackPop);
            mTarget = target;
            target.used();
        }

        @Override
        void appendTo(TheMethodMaker m) {
            byte op = op();

            // Branch opcodes are always negative, so ensure that the high bit is set.
            m.appendOp((byte) (op | 0x80), stackPop());

            if (op < 0) {
                mTarget.comesFrom(this, m);
                return;
            } else {
                // When op bit is zero, perform a wide branch.
                m.appendShort(3 + 5); // branch past the modified op and the wide goto
                int srcAddr = m.mCodeLen;
                m.appendByte(GOTO_W);
                mTarget.comesFromWide(m, srcAddr);
                m.addStackMapFrame(m.mCodeLen); // need a frame for modified op target
            }
        }

        void makeWide() {
            byte op = op();
            if (op < 0) {
                // Zero the high opcode bit to indicate a wide branch.
                mCode = (stackPop() << 8) | (flipIf(op) & 0x7f);
            }
        }

        @Override
        Op flow() {
            int op = op();
            if (op == GOTO || op == GOTO_W) {
                return mTarget;
            }
            mTarget.flowThrough();
            return mNext;
        }
    }

    static class SwitchOp extends BytecodeOp {
        final Lab mDefault;
        final int[] mCases;
        final Lab[] mLabels;

        SwitchOp(byte op, Lab defaultLabel, int[] cases, Lab[] labels) {
            super(op, 1);
            mDefault = defaultLabel;
            mCases = cases;
            mLabels = labels;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            final int srcAddr = m.mCodeLen;
            super.appendTo(m);
            m.appendPad(3 - (srcAddr & 3));
            mDefault.comesFromWide(m, srcAddr);

            if (op() == LOOKUPSWITCH) {
                m.appendInt(mCases.length);
                for (int i = 0; i < mCases.length; i++) {
                    m.appendInt(mCases[i]);
                    mLabels[i].comesFromWide(m, srcAddr);
                }
            } else {
                int smallest = mCases[0];
                int largest = mCases[mCases.length - 1];
                m.appendInt(smallest);
                m.appendInt(largest);
                for (int i = 0, c = smallest; c <= largest; c++) {
                    if (c == mCases[i]) {
                        mLabels[i].comesFromWide(m, srcAddr);
                        i++;
                    } else {
                        mDefault.comesFromWide(m, srcAddr);
                    }
                }
            }
        }

        @Override
        Op flow() {
            for (Lab lab : mLabels) {
                lab.flowThrough();
            }
            return mDefault;
        }
    }

    static class FieldOp extends BytecodeOp {
        final ConstantPool.C_Field mFieldRef;

        FieldOp(byte op, int stackPop, ConstantPool.C_Field fieldRef) {
            super(op, stackPop);
            mFieldRef = fieldRef;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            super.appendTo(m);
            m.appendShort(mFieldRef.mIndex);
            byte op = op();
            if (op == GETFIELD || op == GETSTATIC) {
                m.stackPush(mFieldRef.mField.type());
            }
        }
    }

    static class InvokeOp extends BytecodeOp {
        final ConstantPool.C_Method mMethodRef;

        InvokeOp(byte op, int stackPop, ConstantPool.C_Method methodRef) {
            super(op, stackPop);
            mMethodRef = methodRef;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            super.appendTo(m);
            m.appendShort(mMethodRef.mIndex);
            if (op() == INVOKEINTERFACE) {
                m.appendByte(stackPop()); // nargs
                m.appendByte(0);
            }
            Type returnType = mMethodRef.mMethod.returnType();
            if (returnType != VOID) {
                m.stackPush(returnType);
            }
        }
    }

    static class InvokeDynamicOp extends BytecodeOp {
        final ConstantPool.C_Dynamic mDynamic;
        final Type mReturnType;

        InvokeDynamicOp(int stackPop, ConstantPool.C_Dynamic dynamic, Type returnType) {
            super(INVOKEDYNAMIC, stackPop);
            mDynamic = dynamic;
            mReturnType = returnType;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            super.appendTo(m);
            m.appendShort(mDynamic.mIndex);
            m.appendShort(0);
            if (mReturnType != VOID) {
                m.stackPush(mReturnType);
            }
        }
    }

    /**
     * Push a constant to the stack an optionally performs a conversion.
     */
    static class PushConstantOp extends Op {
        final Object mValue;
        final Type mType;

        /**
         * @param type non-null
         */
        PushConstantOp(Object value, Type type) {
            mValue = value;
            mType = type;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            m.pushConstant(mValue, mType);
        }
    }

    static class DynamicConstantOp extends Op {
        final ConstantPool.C_Dynamic mDynamic;
        final Type mType;

        DynamicConstantOp(ConstantPool.C_Dynamic dynamic, Type type) {
            mDynamic = dynamic;
            mType = type;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            int typeCode = mType.typeCode();
            if (typeCode == T_DOUBLE || typeCode == T_LONG) {
                int index = mDynamic.mIndex;
                m.appendOp(LDC2_W, 0);
                m.appendShort(index);
                m.stackPush(mType);
            } else {
                m.pushConstant(mDynamic, mType);
            }
        }
    }

    /**
     * Converts a value on the stack and pushes it back.
     */
    static class ConversionOp extends Op {
        final Type mFrom, mTo;
        final int mCode;

        ConversionOp(Type from, Type to, int code) {
            mFrom = from;
            mTo = to;
            mCode = code;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            m.convert(mFrom, mTo, mCode);
        }
    }

    /**
     * Push a variable to the stack.
     */
    static final class PushVarOp extends Op {
        final Var mVar;

        PushVarOp(Var var) {
            mVar = var;
            var.mPushCount++;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            m.pushVar(mVar);
        }
    }

    /**
     * Stores to a variable from the stack.
     */
    static final class StoreVarOp extends Op {
        final Var mVar;

        StoreVarOp(Var var) {
            mVar = var;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            if (mVar.mPushCount > 0) {
                mVar.mValid = true;
                m.storeVar(mVar);
            } else {
                m.stackPop();
            }
        }
    }

    /**
     * Increments an integer variable.
     */
    static class IncOp extends Op {
        final Var mVar;
        final int mAmount;

        IncOp(Var var, int amount) {
            mVar = var;
            mAmount = amount;
            var.mPushCount++;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            int slot = mVar.mSlot;
            if (-128 <= mAmount && mAmount < 128 && slot < 256) {
                m.appendOp(IINC, 0);
                m.appendByte(slot);
                m.appendByte(mAmount);
            } else if (slot < 65536) {
                m.appendOp(WIDE, 0);
                m.appendByte(IINC);
                m.appendShort(slot);
                m.appendShort(mAmount);
            } else {
                m.pushVar(mVar);
                m.pushConstant(mAmount, INT);
                m.appendOp(IADD, 1);
                m.storeVar(mVar);
            }
        }
    }

    static class LineNumOp extends Op {
        final int mNum;

        LineNumOp(int num) {
            mNum = num;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            Attribute.LineNumberTable table = m.mLineNumberTable;
            if (table == null) {
                m.mLineNumberTable = table = new Attribute.LineNumberTable(m.mConstants);
            }
            table.add(m.mCodeLen, mNum);
        }
    }

    static class LocalVarOp extends Op {
        final Var mVar;
        final String mName;

        LocalVarOp(Var var, String name) {
            mVar = var;
            mName = name;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            ConstantPool constants = m.mConstants;
            Attribute.LocalVariableTable table = m.mLocalVariableTable;
            if (table == null) {
                m.mLocalVariableTable = table = new Attribute.LocalVariableTable(constants);
            }
            // Without support for variable scopes, just cover the whole range.
            table.add(0, Integer.MAX_VALUE,
                      constants.addUTF8(mName),
                      constants.addUTF8(mVar.mType.descriptor()),
                      mVar.mSlot);
        }
    }

    private StackMapTable.Frame addStackMapFrame(int address) {
        return mStackMapTable.add
            (address, smCodes(mVars, mVars.length), smCodes(mStack, mStackSize));
    }

    private StackMapTable.Frame addStackMapFrameForCatch(int address, int smCatchCode) {
        return mStackMapTable.add(address, smCodes(mVars, mVars.length), new int[] {smCatchCode});
    }

    private void invalidateVariables(StackMapTable.Frame frame) {
        // Any variables which aren't known by the current frame must be invalidated. These
        // variables aren't guaranteed to have been assigned for all code paths. Invalidating
        // them ensures that future frames are defined correctly.

        int[] localCodes = frame.mLocalCodes;
        int i = 0;
        if (localCodes != null) {
            for (; i<localCodes.length; i++) {
                if (localCodes[i] == SM_TOP) {
                    mVars[i].mValid = false;
                }
            }
        }

        // Remaining ones are implicitly "top" but were pruned.
        for (; i < mVars.length; i++) {
            mVars[i].mValid = false;
        }
    }

    /**
     * Needed for StackMapTable.
     *
     * @return null if empty
     */
    private static int[] smCodes(Var[] vars, int len) {
        int[] codes = null;

        while (--len >= 0) {
            int code = vars[len].smCode();
            if (codes == null) {
                if ((code & 0xff) == SM_TOP) {
                    // Prune off the last consecutive "top" vars.
                    continue;
                }
                codes = new int[len + 1];
            }
            codes[len] = code;
        }

        return codes;
    }

    abstract class OwnedVar implements Variable {
        TheMethodMaker owner() {
            return TheMethodMaker.this;
        }

        abstract Type type();

        abstract void push();

        /**
         * @return actual type
         */
        Type push(Type type) {
            push();
            return addConversionOp(type(), type);
        }

        void pushObject() {
            push();
            Type type = type();
            if (type.isPrimitive()) {
                addConversionOp(type, type.box());
            }
        }

        @Override
        public Variable setConstant(Object value) {
            if (value == null) {
                return set(null);
            }

            Type type = type();

            if (!type.isAssignableFrom(Type.from(value.getClass()))) {
                throw new IllegalStateException("Mismatched type");
            }

            ConstantPool.C_Dynamic dynamic = addComplexConstant(type, value);

            beginSetConstant();
            addOp(new DynamicConstantOp(dynamic, type));
            finishSetConstant();

            return this;
        }

        protected abstract void beginSetConstant();

        protected abstract void finishSetConstant();

        @Override
        public void ifTrue(Label label) {
            push(Type.BOOLEAN);
            addBranchOp(IFNE, 1, label);
        }

        @Override
        public void ifFalse(Label label) {
            push(Type.BOOLEAN);
            addBranchOp(IFEQ, 1, label);
        }

        @Override
        public void ifEq(Object value, Label label) {
            if (value == null) {
                pushForNull();
                addBranchOp(IFNULL, 1, label);
            } else {
                ifRelational(value, label, true, IF_ICMPEQ, IFEQ);
            }
        }

        @Override
        public void ifNe(Object value, Label label) {
            if (value == null) {
                pushForNull();
                addBranchOp(IFNONNULL, 1, label);
            } else {
                ifRelational(value, label, true, IF_ICMPNE, IFNE);
            }
        }

        private void pushForNull() {
            if (type().isPrimitive()) {
                throw new IllegalArgumentException("Cannot compare a primitive type to null");
            }
            push();
        }

        @Override
        public void ifLt(Object value, Label label) {
            ifRelational(value, label, false, IF_ICMPLT, IFLT);
        }

        @Override
        public void ifGe(Object value, Label label) {
            ifRelational(value, label, false, IF_ICMPGE, IFGE);
        }

        @Override
        public void ifGt(Object value, Label label) {
            ifRelational(value, label, false, IF_ICMPGT, IFGT);
        }

        @Override
        public void ifLe(Object value, Label label) {
            ifRelational(value, label, false, IF_ICMPLE, IFLE);
        }

        /**
         * @param eq true if performing an equality check
         * @param op normal op to use for ints
         * @param zeroOp op to use when comparing against a constant zero int
         */
        private void ifRelational(Object value, Label label, boolean eq, byte op, byte zeroOp) {
            requireNonNull(value);

            Type typeCmp;

            if (value instanceof Var) {
                typeCmp = comparisonType(((Var) value).mType, eq);
                push(typeCmp);
                addPushOp(typeCmp, value);
            } else {
                if (value instanceof Number) {
                    Number num = (Number) value;
                    if (num.longValue() == 0 && num.doubleValue() == 0
                        && (Type.from(value.getClass()).unboxTypeCode() != T_OBJECT))
                    {
                        Type unbox = type().unbox();
                        if (unbox != null) {
                            int code = unbox.typeCode();
                            if (T_BYTE <= code && code <= T_INT) {
                                // Simple zero comparison. Be lenient with the type. Zero is zero.
                                push(unbox);
                                addBranchOp(zeroOp, 1, label);
                                return;
                            }
                        }
                    }
                }

                // Need to push value first to get the type, then swap around.
                Op savepoint = mLastOp;
                Type valueType = addPushOp(null, value);
                typeCmp = comparisonType(valueType, eq);
                Op end = mLastOp;
                Op rest = rollback(savepoint);
                push(typeCmp);
                mLastOp.mNext = rest;
                mLastOp = end;
                addConversionOp(valueType, typeCmp);
            }

            byte cmpOp;

            switch (typeCmp.stackMapCode()) {
            case SM_OBJECT:
                addBranchOp((byte) (op + (IF_ACMPEQ - IF_ICMPEQ)), 2, label);
                return;

            case SM_INT:
                addBranchOp(op, 2, label);
                return;

            case SM_FLOAT:
                cmpOp = (zeroOp == IFLE || zeroOp == IFLT) ? FCMPG : FCMPL;
                break;

            case SM_DOUBLE:
                cmpOp = (zeroOp == IFLE || zeroOp == IFLT) ? DCMPG : DCMPL;
                break;
            
            case SM_LONG:
                cmpOp = LCMP;
                break;

            default:
                throw new AssertionError();
            }

            addOp(new BytecodeOp(cmpOp, 2) {
                @Override
                void appendTo(TheMethodMaker m) {
                    super.appendTo(m);
                    stackPush(INT);
                }
            });

            addBranchOp(zeroOp, 1, label);
        }

        /**
         * @return type to use for comparison
         * @param eq true if performing an equality check
         */
        private Type comparisonType(Type other, boolean eq) {
            check: {
                Type thisCmp = type();
                Type otherCmp = other;

                if (eq && thisCmp.isObject() && otherCmp.isObject()) {
                    // Just do a plain object equality comparison.
                } else {
                    // Attempt a primitive comparison.
                    thisCmp = thisCmp.unbox();
                    otherCmp = otherCmp.unbox();
                    if (thisCmp == null || otherCmp == null
                        || (!eq && (thisCmp == BOOLEAN || otherCmp == BOOLEAN)))
                    {
                        // Incomparable.
                        break check;
                    }
                }

                int cost1 = other.canConvertTo(thisCmp);
                int cost2;
                if (cost1 == 0 || cost1 < (cost2 = type().canConvertTo(otherCmp))) {
                    return thisCmp;
                } else if (cost2 != Integer.MAX_VALUE) {
                    return otherCmp;
                }
            }

            throw new IllegalStateException
                ("Incomparable types: " + type().name() + " with " + other.name());
        }

        @Override
        public void switch_(Label defaultLabel, int[] cases, Label... labels) {
            if (cases.length != labels.length) {
                throw new IllegalArgumentException("Number of cases and labels doesn't match");
            }

            if (cases.length == 0) {
                goto_(defaultLabel);
                return;
            }

            Lab defaultLab = target(defaultLabel);
            defaultLab.used();

            Lab[] labs = new Lab[labels.length];
            for (int i=0; i<labels.length; i++) {
                (labs[i] = target(labels[i])).used();
            }

            cases = cases.clone();

            sortSwitchCases(cases, labs);

            for (int i=1; i<cases.length; i++) {
                if (cases[i] == cases[i - 1]) {
                    throw new IllegalArgumentException("Duplicate switch cases: " + cases[i]);
                }
            }

            push(INT);

            addSwitchOp(defaultLab, cases, labs);
        }

        @Override
        public Var add(Object value) {
            return addMathOp("add", IADD, this, value);
        }

        @Override
        public Var sub(Object value) {
            return addMathOp("subtract", ISUB, this, value);
        }

        @Override
        public Var mul(Object value) {
            return addMathOp("multiply", IMUL, this, value);
        }

        @Override
        public Var div(Object value) {
            return addMathOp("divide", IDIV, this, value);
        }

        @Override
        public Var rem(Object value) {
            return addMathOp("remainder", IREM, this, value);
        }

        @Override
        public Var instanceOf(Object clazz) {
            requireNonNull(clazz);

            if (!type().isObject()) {
                throw new IllegalStateException("Not an object type");
            }

            Type isType = mClassMaker.typeFrom(clazz);
            ConstantPool.C_Class constant = mConstants.addClass(isType);

            push();

            addOp(new BytecodeOp(INSTANCEOF, 1) {
                @Override
                void appendTo(TheMethodMaker m) {
                    super.appendTo(m);
                    m.appendShort(constant.mIndex);
                    m.stackPush(BOOLEAN);
                }
            });

            Var var = new Var(BOOLEAN);
            addStoreOp(var);
            return var;
        }

        @Override
        public Var cast(Object clazz) {
            requireNonNull(clazz);

            Type fromType = type();
            Type toType = mClassMaker.typeFrom(clazz);
            int code = fromType.canConvertTo(toType);

            if (code != Integer.MAX_VALUE) {
                // Widening conversion, boxing, unboxing, or equal types.
                push();
                if (code != 0) {
                    addOp(new ConversionOp(fromType, toType, code));
                }
            } else if (toType.isObject()) {
                if (!fromType.isObject()) {
                    Type unbox;
                    if (fromType.isPrimitive() && (unbox = toType.unbox()) != null) {
                        // Narrowing and boxing conversion.
                        return cast(unbox.clazz()).cast(clazz);
                    }
                    throw new IllegalStateException("Unsupported cast");
                }

                // Basic object cast.

                ConstantPool.C_Class constant = mConstants.addClass(toType);

                push();

                addOp(new BytecodeOp(CHECKCAST, 0) {
                    @Override
                    void appendTo(TheMethodMaker m) {
                        super.appendTo(m);
                        m.appendShort(constant.mIndex);
                    }
                });
            } else {
                // Narrowing conversion.

                Type primType = fromType.unbox();

                if (primType == null) {
                    if (Type.from(Number.class).isAssignableFrom(fromType)) {
                        return invoke(toType.name() + "Value");
                    }
                    if (fromType.equals(Type.from(Object.class))) {
                        return cast(Number.class).cast(clazz);
                    }
                    throw new IllegalStateException("Unsupported conversion");
                }

                int toTypeCode = toType.typeCode();
                byte op = 0;

                switch (primType.stackMapCode()) {
                case SM_INT:
                    switch (toTypeCode) {
                    case T_BYTE:  op = I2B; break;
                    case T_CHAR:  op = I2C; break;
                    case T_SHORT: op = I2S; break;
                    case T_FLOAT: op = I2F; break;
                    }
                    break;
                case SM_FLOAT:
                    switch (toTypeCode) {
                    case T_INT:  op = F2I; break;
                    case T_LONG: op = F2L; break;
                    }
                    break;
                case SM_DOUBLE:
                    switch (toTypeCode) {
                    case T_INT:   op = D2I; break;
                    case T_FLOAT: op = D2F; break;
                    case T_LONG:  op = D2L; break;
                    }
                    break;
                case SM_LONG:
                    switch (toTypeCode) {
                    case T_INT:    op = L2I; break;
                    case T_FLOAT:  op = L2F; break;
                    case T_DOUBLE: op = L2D; break;
                    }
                    break;
                }

                if (op == 0) {
                    switch (toTypeCode) {
                    case T_BYTE: case T_CHAR: case T_SHORT:
                        return cast(int.class).cast(clazz);
                    }
                    throw new IllegalStateException("Unsupported conversion");
                }

                push(primType);

                addOp(new BytecodeOp(op, 1) {
                    @Override
                    void appendTo(TheMethodMaker m) {
                        super.appendTo(m);
                        m.stackPush(toType);
                    }
                });
            }

            Var var = new Var(toType);
            addStoreOp(var);
            return var;
        }

        @Override
        public Var not() {
            Var var = new Var(BOOLEAN);
            Label tru = label();
            ifTrue(tru);
            var.set(true);
            Label end = label();
            goto_(end);
            tru.here();
            var.set(false);
            end.here();
            return var;
        }

        @Override
        public Var and(Object value) {
            return addLogicalOp("and", IAND, this, value);
        }

        @Override
        public Var or(Object value) {
            return addLogicalOp("or", IOR, this, value);
        }

        @Override
        public Var xor(Object value) {
            return addLogicalOp("xor", IXOR, this, value);
        }

        @Override
        public Var shl(Object value) {
            return addLogicalOp("shift", ISHL, this, value);
        }

        @Override
        public Var shr(Object value) {
            return addLogicalOp("shift", ISHR, this, value);
        }

        @Override
        public Var ushr(Object value) {
            return addLogicalOp("shift", IUSHR, this, value);
        }

        @Override
        public Var neg() {
            return addMathOp("negate", INEG, this, null);
        }

        @Override
        public Var com() {
            return addLogicalOp("complement", IXOR, this, -1);
        }

        @Override
        public Var alength() {
            arrayCheck();
            push();
            addBytecodeOp(ARRAYLENGTH, 0);
            Var var = new Var(INT);
            addStoreOp(var);
            return var;
        }

        @Override
        public Var aget(Object index) {
            byte op = aloadOp();
            push();
            addPushOp(INT, index);
            addBytecodeOp(op, 1);
            Var var = new Var(type().elementType());
            addStoreOp(var);
            return var;
        }

        @Override
        public void aset(Object index, Object value) {
            byte op = aloadOp();
            push();
            addPushOp(INT, index);
            addPushOp(type().elementType(), value);
            addBytecodeOp((byte) (op + (IASTORE - IALOAD)), 3);
        }

        private Type arrayCheck() throws IllegalStateException {
            Type type = type();
            if (!type.isArray()) {
                throw new IllegalStateException("Not an array type");
            }
            return type;
        }

        private byte aloadOp() {
            switch (arrayCheck().elementType().typeCode()) {
            case T_BOOLEAN:
            case T_BYTE:   return BALOAD;
            case T_CHAR:   return CALOAD;
            case T_SHORT:  return SALOAD;
            case T_INT:    return IALOAD;
            case T_FLOAT:  return FALOAD;
            case T_LONG:   return LALOAD;
            case T_DOUBLE: return DALOAD;
            default:       return AALOAD;
            }
        }

        @Override
        public Var invoke(String name, Object... values) {
            return doInvoke(type(), this, name, 0, values, null, null);
        }

        @Override
        public Var invoke(Object returnType, String name, Object[] types, Object... values) {
            Type specificReturnType = null;
            Type[] specificParamTypes = null;

            if (returnType != null) {
                specificReturnType = mClassMaker.typeFrom(returnType);
            }

            if (types != null) {
                specificParamTypes = new Type[types.length];
                for (int i=0; i<types.length; i++) {
                    specificParamTypes[i] = mClassMaker.typeFrom(types[i]);
                }
            }

            return doInvoke(type(), this, name, 0, values, specificReturnType, specificParamTypes);
        }

        @Override
        public Bootstrap indy(String name, Object... args) {
            return bootstrap(false, name, args);
        }

        @Override
        public Bootstrap condy(String name, Object... args) {
            return bootstrap(true, name, args);
        }

        private Bootstrap bootstrap(boolean condy, String name, Object... args) {
            var types = new Type[3 + args.length];

            types[0] = Type.from(MethodHandles.Lookup.class);
            types[1] = Type.from(String.class);
            types[2] = Type.from(condy ? Class.class : MethodType.class);

            for (int i=0; i<args.length; i++) {
                Object arg = args[i];

                Type type;
                if (arg == mClassVar) {
                    type = Type.from(Class.class);
                } else {
                    type = Type.from(arg.getClass());
                }

                types[3 + i] = type;
            }

            Type thisType = type();
            Set<Type.Method> candidates = thisType.findMethods(name, types, 0, 1, null, null);

            if (candidates.size() != 1) {
                if (candidates.isEmpty()) {
                    throw noCandidates(thisType, name);
                } else {
                    throw noBestCandidate(thisType, name, candidates);
                }
            }

            Type.Method bootstrap = candidates.iterator().next();

            ConstantPool.C_Method ref = mConstants.addMethod(bootstrap);
            ConstantPool.C_MethodHandle bootHandle =
                mConstants.addMethodHandle(MethodHandleInfo.REF_invokeStatic, ref);

            ConstantPool.Constant[] bootArgs;
            if (args == null) {
                bootArgs = new ConstantPool.Constant[0];
            } else {
                Type[] bootTypes = bootstrap.paramTypes();
                bootArgs = new ConstantPool.Constant[args.length];
                for (int i=0; i<args.length; i++) {
                    // +3 to skip these: Lookup caller, String name, and MethodType type
                    bootArgs[i] = addLoadableConstant(bootTypes[i + 3], args[i]);
                }
            }

            int bi = mClassMaker.addBootstrapMethod(bootHandle, bootArgs);

            return new BootstrapImpl(bi, condy);
        }

        @Override
        public void throw_() {
            if (type().isPrimitive()) {
                throw new IllegalStateException("Cannot throw primitive type");
            }
            push();
            addBytecodeOp(ATHROW, 1);
        }
    }

    class Var extends OwnedVar implements Variable {
        final Type mType;
        int mSlot = -1;

        // Updated as Op list is built.
        int mPushCount;

        // Updated when code is appended. Must be reset to false each time.
        boolean mValid;

        boolean mNamed;

        Var(Type type) {
            requireNonNull(type);
            mType = type;
        }

        int slotWidth() {
            switch (mType.typeCode()) {
            default: return 1;
            case T_DOUBLE: case T_LONG: return 2;
            }
        }

        /**
         * Needed for StackMapTable.
         *
         * @return SM code at byte 0; additional bytes used if necessary
         */
        int smCode() {
            if (!mValid) {
                return SM_TOP;
            } else {
                int code = mType.stackMapCode();
                if (code == SM_OBJECT) {
                    code |= (mConstants.addClass(mType).mIndex << 8);
                }
                return code;
            }
        }

        @Override
        Type type() {
            return mType;
        }

        @Override
        void push() {
            addOp(new PushVarOp(this));
        }

        @Override
        public Var name(String name) {
            Objects.requireNonNull(name);
            if (mNamed) {
                throw new IllegalStateException("Already named");
            }
            addOp(new LocalVarOp(this, name));
            mNamed = true;
            return this;
        }

        @Override
        public Var set(Object value) {
            addPushOp(mType, value);
            addStoreOp(this);
            return this;
        }

        @Override
        protected void beginSetConstant() {
        }

        @Override
        protected void finishSetConstant() {
            addStoreOp(this);
        }

        @Override
        public Var get() {
            Var var = new Var(mType);
            var.set(this);
            return var;
        }

        @Override
        public void inc(Object value) {
            if (mType == INT
                && (value instanceof Long || value instanceof Integer
                    || value instanceof Byte || value instanceof Short))
            {
                long amount = ((Number) value).longValue();
                if (-32768 <= amount && amount < 32768) {
                    addOp(new IncOp(this, (int) amount));
                    return;
                }
            }

            set(add(value));
        }

        @Override
        public FieldVar field(String name) {
            return TheMethodMaker.this.field(this, name);
        }

        @Override
        public void monitorEnter() {
            monitor(MONITORENTER);
        }

        @Override
        public void monitorExit() {
            monitor(MONITOREXIT);
        }

        private void monitor(byte op) {
            if (!mType.isObject()) {
                throw new IllegalStateException("Not an object type");
            }
            push();
            addBytecodeOp(op, 1);
        }
    }

    /**
     * Special variable which refers to the enclosing class.
     */
    class ClassVar extends Var {
        ClassVar(Type type) {
            super(type);
        }

        @Override
        public Var name(String name) {
            throw new IllegalStateException("Already named");
        }
    }

    /**
     * Stack variable which represents an uninitialized new object.
     */
    class NewVar extends Var {
        private final int mNewOffset;

        NewVar(Type type, int newOffset) {
            super(type);
            mNewOffset = newOffset;
        }

        @Override
        int smCode() {
            return SM_UNINIT | (mNewOffset << 8);
        }
    }

    class FieldVar extends OwnedVar implements Field {
        final Var mInstance;
        final ConstantPool.C_Field mFieldRef;

        private ConstantPool.C_Dynamic mVarHandle;

        FieldVar(Var instance, ConstantPool.C_Field fieldRef) {
            mInstance = instance;
            mFieldRef = fieldRef;
        }

        @Override
        Type type() {
            return mFieldRef.mField.type();
        }

        @Override
        void push() {
            addPushFieldOp(this);
        }

        @Override
        public FieldVar set(Object value) {
            addBeginStoreFieldOp(this);
            addPushOp(type(), value);
            addFinishStoreFieldOp(this);
            return this;
        }

        @Override
        protected void beginSetConstant() {
            addBeginStoreFieldOp(this);
        }

        @Override
        protected void finishSetConstant() {
            addFinishStoreFieldOp(this);
        }

        @Override
        public Var get() {
            Var var = new Var(type());
            addPushFieldOp(this);
            addStoreOp(var);
            return var;
        }

        @Override
        public void inc(Object value) {
            set(add(value));
        }

        @Override
        public Field field(String name) {
            return get().field(name);
        }

        @Override
        public void monitorEnter() {
            throw new IllegalStateException();
        }

        @Override
        public void monitorExit() {
            throw new IllegalStateException();
        }

        @Override
        public Var getPlain() {
            return vhGet("get");
        }

        @Override
        public void setPlain(Object value) {
            vhSet("set", value);
        }

        @Override
        public Var getOpaque() {
            return vhGet("getOpaque");
        }

        @Override
        public void setOpaque(Object value) {
            vhSet("setOpaque", value);
        }

        @Override
        public Var getAcquire() {
            return vhGet("getAcquire");
        }

        @Override
        public void setRelease(Object value) {
            vhSet("setRelease", value);
        }

        @Override
        public Var getVolatile() {
            return vhGet("getVolatile");
        }

        @Override
        public void setVolatile(Object value) {
            vhSet("setVolatile", value);
        }

        @Override
        public Variable compareAndSet(Object expectedValue, Object newValue) {
            return vhCas("compareAndSet", Type.BOOLEAN, expectedValue, newValue);
        }

        @Override
        public Variable compareAndExchange(Object expectedValue, Object newValue) {
            return vhCas("compareAndExchange", null, expectedValue, newValue);
        }

        @Override
        public Variable compareAndExchangeAcquire(Object expectedValue, Object newValue) {
            return vhCas("compareAndExchangeAcquire", null, expectedValue, newValue);
        }

        @Override
        public Variable compareAndExchangeRelease(Object expectedValue, Object newValue) {
            return vhCas("compareAndExchangeRelease", null, expectedValue, newValue);
        }

        @Override
        public Variable weakCompareAndSetPlain(Object expectedValue, Object newValue) {
            return vhCas("weakCompareAndSetPlain", Type.BOOLEAN, expectedValue, newValue);
        }

        @Override
        public Variable weakCompareAndSet(Object expectedValue, Object newValue) {
            return vhCas("weakCompareAndSet", Type.BOOLEAN, expectedValue, newValue);
        }

        @Override
        public Variable weakCompareAndSetAcquire(Object expectedValue, Object newValue) {
            return vhCas("weakCompareAndSetAcquire", Type.BOOLEAN, expectedValue, newValue);
        }

        @Override
        public Variable weakCompareAndSetRelease(Object expectedValue, Object newValue) {
            return vhCas("weakCompareAndSetRelease", Type.BOOLEAN, expectedValue, newValue);
        }

        @Override
        public Variable getAndSet(Object value) {
            return vhGas("getAndSet", value);
        }

        @Override
        public Variable getAndSetAcquire(Object value) {
            return vhGas("getAndSetAcquire", value);
        }

        @Override
        public Variable getAndSetRelease(Object value) {
            return vhGas("getAndSetRelease", value);
        }

        @Override
        public Variable getAndAdd(Object value) {
            return vhGas("getAndAdd", value);
        }

        @Override
        public Variable getAndAddAcquire(Object value) {
            return vhGas("getAndAddAcquire", value);
        }

        @Override
        public Variable getAndAddRelease(Object value) {
            return vhGas("getAndAddRelease", value);
        }

        @Override
        public Variable getAndBitwiseOr(Object value) {
            return vhGas("getAndBitwiseOr", value);
        }

        @Override
        public Variable getAndBitwiseOrAcquire(Object value) {
            return vhGas("getAndBitwiseOrAcquire", value);
        }

        @Override
        public Variable getAndBitwiseOrRelease(Object value) {
            return vhGas("getAndBitwiseOrRelease", value);
        }

        @Override
        public Variable getAndBitwiseAnd(Object value) {
            return vhGas("getAndBitwiseAnd", value);
        }

        @Override
        public Variable getAndBitwiseAndAcquire(Object value) {
            return vhGas("getAndBitwiseAndAcquire", value);
        }

        @Override
        public Variable getAndBitwiseAndRelease(Object value) {
            return vhGas("getAndBitwiseAndRelease", value);
        }

        @Override
        public Variable getAndBitwiseXor(Object value) {
            return vhGas("getAndBitwiseXor", value);
        }

        @Override
        public Variable getAndBitwiseXorAcquire(Object value) {
            return vhGas("getAndBitwiseXorAcquire", value);
        }

        @Override
        public Variable getAndBitwiseXorRelease(Object value) {
            return vhGas("getAndBitwiseXorRelease", value);
        }

        private Var vhGet(String name) {
            Type thisType = type();
            Type vhType = Type.from(VarHandle.class);
            pushVarHandle(vhType);

            int stackPop;
            Type.Method method;
            if (mFieldRef.mField.isStatic()) {
                stackPop = 1;
                method = vhType.inventMethod(false, thisType, name);
            } else {
                stackPop = 2;
                addOp(new PushVarOp(mInstance));
                method = vhType.inventMethod(false, thisType, name, mInstance.type());
            }

            ConstantPool.C_Method ref = mConstants.addMethod(method);
            addOp(new InvokeOp(INVOKEVIRTUAL, stackPop, ref));

            Var var = new Var(thisType);
            addStoreOp(var);
            return var;
        }

        private void vhSet(String name, Object value) {
            Type thisType = type();
            Type vhType = Type.from(VarHandle.class);
            pushVarHandle(vhType);

            int stackPop;
            Type.Method method;
            if (mFieldRef.mField.isStatic()) {
                stackPop = 2;
                addPushOp(thisType, value);
                method = vhType.inventMethod(false, Type.VOID, name, thisType);
            } else {
                stackPop = 3;
                addOp(new PushVarOp(mInstance));
                addPushOp(thisType, value);
                method = vhType.inventMethod(false, Type.VOID, name, mInstance.type(), thisType);
            }

            ConstantPool.C_Method ref = mConstants.addMethod(method);
            addOp(new InvokeOp(INVOKEVIRTUAL, stackPop, ref));
        }

        private Variable vhCas(String name, Type retType, Object expectedValue, Object newValue) {
            Type thisType = type();
            Type vhType = Type.from(VarHandle.class);
            pushVarHandle(vhType);

            if (retType == null) {
                retType = thisType;
            }

            int stackPop;
            Type.Method method;
            if (mFieldRef.mField.isStatic()) {
                stackPop = 3;
                addPushOp(thisType, expectedValue);
                addPushOp(thisType, newValue);
                method = vhType.inventMethod(false, retType, name, thisType, thisType);
            } else {
                stackPop = 4;
                addOp(new PushVarOp(mInstance));
                addPushOp(thisType, expectedValue);
                addPushOp(thisType, newValue);
                method = vhType.inventMethod
                    (false, retType, name, mInstance.type(), thisType, thisType);
            }

            ConstantPool.C_Method ref = mConstants.addMethod(method);
            addOp(new InvokeOp(INVOKEVIRTUAL, stackPop, ref));

            Var var = new Var(retType);
            addStoreOp(var);
            return var;
        }

        private Variable vhGas(String name, Object value) {
            Type thisType = type();
            Type vhType = Type.from(VarHandle.class);
            pushVarHandle(vhType);

            int stackPop;
            Type.Method method;
            if (mFieldRef.mField.isStatic()) {
                stackPop = 2;
                addPushOp(thisType, value);
                method = vhType.inventMethod(false, thisType, name, thisType);
            } else {
                stackPop = 3;
                addOp(new PushVarOp(mInstance));
                addPushOp(thisType, value);
                method = vhType.inventMethod(false, thisType, name, mInstance.type(), thisType);
            }

            ConstantPool.C_Method ref = mConstants.addMethod(method);
            addOp(new InvokeOp(INVOKEVIRTUAL, stackPop, ref));

            Var var = new Var(thisType);
            addStoreOp(var);
            return var;
        }

        private void pushVarHandle(Type vhType) {
            if (mVarHandle == null) {
                Type classType = Type.from(Class.class);

                Type[] bootParams = {
                    Type.from(MethodHandles.Lookup.class),
                    Type.from(String.class), classType, classType, classType
                };

                String bootName = mFieldRef.mField.isStatic()
                    ? "staticFieldVarHandle" : "fieldVarHandle";

                ConstantPool.C_Method ref = mConstants.addMethod
                    (Type.from(ConstantBootstraps.class).inventMethod
                     (true, vhType, bootName, bootParams));

                ConstantPool.C_MethodHandle bootHandle =
                    mConstants.addMethodHandle(MethodHandleInfo.REF_invokeStatic, ref);

                ConstantPool.Constant[] bootArgs = {
                    mFieldRef.mClass, addLoadableConstant(null, mFieldRef.mField.type())
                };

                mVarHandle = mConstants.addDynamicConstant
                    (mClassMaker.addBootstrapMethod(bootHandle, bootArgs),
                     mFieldRef.mNameAndType.mName, vhType);
            }

            addOp(new DynamicConstantOp(mVarHandle, vhType));
        }
    }

    class BootstrapImpl implements Bootstrap {
        final int mBootstrapIndex;
        final boolean mCondy;

        BootstrapImpl(int bi, boolean condy) {
            mBootstrapIndex = bi;
            mCondy = condy;
        }

        @Override
        public Var invoke(Object returnType, String name, Object[] types, Object... values) {
            Type retType = mClassMaker.typeFrom(returnType);
            int length = values == null ? 0 : values.length;

            if (mCondy) {
                if ((types != null && types.length != 0) || length != 0) {
                    throw new IllegalStateException("Dynamic constant has no parameters");
                }

                ConstantPool.C_Dynamic dynamic = mConstants
                    .addDynamicConstant(mBootstrapIndex, name, retType);

                addOp(new DynamicConstantOp(dynamic, retType));
            } else {
                if ((types == null ? 0 : types.length) != length) {
                    throw new IllegalArgumentException("Mismatched parameter types and values");
                }

                Type[] paramTypes = new Type[length];
                for (int i=0; i<paramTypes.length; i++) {
                    paramTypes[i] = mClassMaker.typeFrom(types[i]);
                    addPushOp(paramTypes[i], values[i]);
                }

                String desc = Type.makeDescriptor(retType, paramTypes);

                ConstantPool.C_Dynamic dynamic = mConstants
                    .addInvokeDynamic(mBootstrapIndex, name, desc);

                addOp(new InvokeDynamicOp(length, dynamic, retType));

                if (retType == VOID) {
                    return null;
                }
            }

            Var var = new Var(retType);
            addStoreOp(var);
            return var;
        }
    }
}
