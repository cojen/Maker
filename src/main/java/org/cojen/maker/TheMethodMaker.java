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
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.lang.invoke.MethodHandleInfo.*;

import static java.util.Objects.*;

import static org.cojen.maker.Opcodes.*;
import static org.cojen.maker.Type.*;
import static org.cojen.maker.BytesOut.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
class TheMethodMaker extends ClassMember implements MethodMaker {
    final TheClassMaker mClassMaker;
    final Type.Method mMethod;

    private LocalVar[] mParams;

    private Op mFirstOp;
    private Op mLastOp;

    private LocalVar mThisVar;
    private ClassVar mClassVar;

    private List<Handler> mExceptionHandlers;

    private Lab mReturnLabel;

    // Remaining fields are only used when doFinish is called.

    private byte[] mCode;
    private int mCodeLen;

    private LocalVar[] mVars;

    private LocalVar[] mStack;
    private int mStackSize;
    private int mMaxStackSlot;

    // Count of labels which need to be positioned to define all branch targets.
    private int mUnpositionedLabels;

    private StackMapTable mStackMapTable;

    private Attribute.LineNumberTable mLineNumberTable;

    private Attribute.LocalVariableTable mLocalVariableTable;

    private int mFinished;

    TheMethodMaker(TheClassMaker classMaker, Type.Method method) {
        super(classMaker.mConstants, method.name(), method.descriptor());
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

    void doFinish() {
        if (mFinished != 0 || (mModifiers & (Modifier.ABSTRACT | Modifier.NATIVE)) != 0) {
            return;
        }

        positionReturnLabel();

        boolean isEndReached = true;
        if (mLastOp instanceof BytecodeOp) {
            if (mLastOp instanceof ReturnOp) {
                isEndReached = false;
            } else {
                byte op = ((BytecodeOp) mLastOp).op();
                if (op == GOTO || op == GOTO_W || op == ATHROW) {
                    isEndReached = false;
                }
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
            // Note that "this" is treated as param 0.
            initParams();
        }

        List<LocalVar> varList = new ArrayList<>();
        BitSet varUsage = new BitSet();

        for (LocalVar param : mParams) {
            varList.add(param);
            varUsage.set(param.mSlot);
        }

        // Perform flow analysis for assigning variable slots and building the StackMapTable.
        int opCount, maxLocals;
        {
            Flow flow = new Flow(varList, varUsage);
            flow.run(mFirstOp);
            opCount = flow.mOpCount;
            maxLocals = flow.nextSlot();
            if (maxLocals >= 65536) {
                throw new IllegalStateException("Too many local variables");
            }
        }

        // Remove unvisited exception handlers.
        if (mExceptionHandlers != null) {
            mExceptionHandlers.removeIf(h -> !h.mHandlerLab.mVisited);
        }

        mVars = varList.toArray(new LocalVar[varList.size()]);

        // Prepare the StackMapTable.
        {
            Arrays.sort(mVars); // sort by slot

            int[] initCodes;
            if (mParams.length == 0) {
                initCodes = null;
            } else {
                initCodes = new int[mParams.length];
                for (int i=0; i<initCodes.length; i++) {
                    initCodes[i] = mParams[i].smCode();
                }
            }

            mStackMapTable = new StackMapTable(mConstants, initCodes);
        }

        mCode = new byte[Math.min(65536, opCount * 2)];
        mStack = new LocalVar[8];

        while (true) {
            mCodeLen = 0;

            mStackSize = 0;
            mMaxStackSlot = 0;
            mUnpositionedLabels = 0;
            mLineNumberTable = null;
            mLocalVariableTable = null;
            mFinished = 0;

            for (Op op = mFirstOp; op != null; op = op.mNext) {
                if (op.mVisited) { // only append if visited by flow analysis
                    op.appendTo(this);
                }
            }

            if (mUnpositionedLabels != 0) {
                throw new IllegalStateException("Unpositioned labels in method: " + 
                                                getName() + ": " + mUnpositionedLabels);
            }

            if (mFinished >= 0) {
                break;
            }

            // Wide forward branches were detected, so code needs to be rebuilt.

            for (Op op = mFirstOp; op != null; op = op.mNext) {
                op.reset();
            }

            mStackMapTable.reset();

            if (mFinished < -1) {
                // Need to perform flow analysis again too to account for new labels.
                new Flow(varList, varUsage).run(mFirstOp);
            }
        }

        mParams = null;
        mFirstOp = null;
        mLastOp = null;
        mReturnLabel = null;
        mVars = null;
        mStack = null;

        var codeAttr = new Attribute.Code
            (mConstants, mMaxStackSlot, maxLocals, mCode, mCodeLen, mExceptionHandlers);

        mExceptionHandlers = null;

        if (mStackMapTable.finish()) {
            codeAttr.addAttribute(mStackMapTable);
            mStackMapTable = null;
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
    static void doFinish(List<TheMethodMaker> list) {
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

        first.doFinish();
    }

    @Override
    public ClassMaker classMaker() {
        return mClassMaker;
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
    public ClassVar class_() {
        if (mClassVar == null) {
            mClassVar = new ClassVar(Type.from(Class.class));
        }
        return mClassVar;
    }

    @Override
    public LocalVar this_() {
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
    public LocalVar param(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException();
        }
        LocalVar[] params = mParams;
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
            Type type = mClassMaker.type();
            mThisVar = "<init>".equals(getName()) ? new InitThisVar(type) : new LocalVar(type);
            mThisVar.mSlot = 0;
            count++;
            slot = 1;
        }

        int i = 0;
        mParams = new LocalVar[count];

        if (mThisVar != null) {
            mParams[i++] = mThisVar;
        }

        for (Type t : mMethod.paramTypes()) {
            var param = new LocalVar(t);
            param.mSlot = slot;
            slot += param.slotWidth();
            mParams[i++] = param;
        }
    }

    @Override
    public LocalVar var(Object type) {
        requireNonNull(type);
        return new LocalVar(mClassMaker.typeFrom(type));
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
            addOp(new ReturnOp(RETURN, 0));
        } else {
            addOp(new BranchOp(GOTO, 0, mReturnLabel));
        }
    }

    @Override
    public void return_(Object value) {
        Type type = mMethod.returnType();
        if (type == VOID) {
            throw new IllegalStateException("Cannot return a value from this method");
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
        addOp(new ReturnOp(op, 1));
    }

    @Override
    public FieldVar field(String name) {
        Type type = mClassMaker.type();
        Type.Field field = findField(type, name);

        LocalVar instance = mThisVar;
        if (instance == null && !field.isStatic()) {
            instance = this_();
        }

        return new FieldVar(instance, mConstants.addField(field));
    }

    private FieldVar field(LocalVar var, String name) {
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
        if (name.equals("new")) {
            return doNew(mClassMaker.type(), values, null);
        } else {
            return doInvoke(name, 0, values);
        }
    }

    @Override
    public Variable invokeSuper(String name, Object... values) {
        return doInvoke(name, 1, values);
    }

    private LocalVar doInvoke(String name, int inherit, Object... values) {
        LocalVar this_ = mThisVar;
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
                // Flow analysis doesn't support variable types changing, so change it when the
                // code is built. This trick doesn't work when making spaghetti code.
                ((InitThisVar) this_()).ready();
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
    LocalVar doInvoke(Type type,
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

        int staticAllowed;

        if (instance != null) {
            staticAllowed = 0; // maybe static
        } else if ("<init>".equals(methodName)) {
            // Calling a constructor for new object allocation.
            staticAllowed = -1; // not static
        } else {
            staticAllowed = 1; // only static
        }

        Op savepoint = mLastOp;

        // Push all arguments and obtain their actual types.
        Type[] paramTypes = new Type[args.length];
        for (int i=0; i<args.length; i++) {
            paramTypes[i] = addPushOp(null, args[i]);
        }

        Type.Method method;
        try {
            method = type.findMethod(methodName, paramTypes, inherit, staticAllowed,
                                     specificReturnType, specificParamTypes);
        } catch (Throwable e) {
            rollback(savepoint);
            throw e;
        }

        // Check if a signature polymorphic method.
        if (!method.isStatic() && method.isVarargs()) {
            Type enclosingType = method.enclosingType();
            Class enclosingClass = enclosingType.clazz();
            if (enclosingClass == MethodHandle.class || enclosingClass == VarHandle.class) {
                Type returnType = method.returnType();
                if (specificReturnType != null) {
                    returnType = specificReturnType;
                }
                method = enclosingType.inventMethod(false, returnType, method.name(), paramTypes);
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

                var vararg = doNew(actualTypes[firstLen], new Object[] {args.length - i}, null);

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

        return storeToNewVar(returnType);
    }

    @Override
    public Variable invoke(MethodHandle handle, Object... values) {
        MethodType mtype = handle.type();

        if (mtype.parameterCount() != values.length) {
            throw new IllegalArgumentException
                ("Wrong number of parameters (expecting " + mtype.parameterCount() + ')');
        }

        Type returnType = Type.from(mtype.returnType());

        Type handleType = Type.from(MethodHandle.class);
        var handleVar = new LocalVar(handleType);

        if (mClassMaker.allowComplexConstants()) {
            handleVar.setExact(handle);
        } else {
            handleVar.set(handle);
        }

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

        return storeToNewVar(returnType);
    }

    @Override
    public Variable new_(Object objType, Object... values) {
        return doNew(mClassMaker.typeFrom(objType), values, null);
    }

    private LocalVar doNew(Type type, Object[] values, Type[] specificParamTypes) {
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

            doInvoke(type, null, "<init>", -1, values, null, specificParamTypes);
        }

        return storeToNewVar(type);
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

        var handlerLab = new HandlerLab(this, catchType, smCatchCode);

        // Insert an operation at the start of the handled block, to capture the set of defined
        // local variables during flow analysis.
        Op startOp = new Op() {
            @Override
            void appendTo(TheMethodMaker m) { }

            @Override
            Op flow(Flow flow, Op prev) {
                // The end label isn't reached normally, but it cannot be dropped. An address
                // must be captured to build the exception table.
                endLab.mVisited = true;

                // Flow into the handler itself.
                flow.run(handlerLab);

                // Return the first operation in the handled block.
                return mNext;
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

        return storeToNewVar(catchType);
    }

    private static final class Handler implements ExceptionHandler {
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
    }

    @Override
    public void finally_(Label start, Runnable handler) {
        Lab startLab = target(start);
        Lab endLab = new Lab(this);
        addOp(endLab);

        // Scan between the labels and gather all the labels which are in between. Any branch
        // to one of these labels is considered to be inside the range, and so no finally
        // handler needs to be generated for these. All other branches are considered to exit
        // the range, and so they need a finally handler.

        HashSet<Lab> inside = null;

        for (Op op = startLab;;) {
            op = op.mNext;
            if (op == null) {
                throw new IllegalStateException("Start is unpositioned");
            }
            if (op == endLab) {
                break;
            }
            if (op instanceof Lab) {
                if (inside == null) {
                    inside = new HashSet<>();
                }
                inside.add((Lab) op);
            }
        }

        // Scan between the labels and gather/modify all the exits.

        // Maps final targets to handler labels.
        Map<Lab, Lab> exits = new LinkedHashMap<>();

        // Is set when a return exit is found.
        Lab retHandler = null;
        LocalVar retVar = null;

        boolean lastTransformed = false;
        Lab veryEnd = null;

        for (Op prev = startLab;;) {
            Op op = prev.mNext;

            if (op == endLab) {
                break;
            }

            lastTransformed = true;

            if (op instanceof BranchOp) {
                var branchOp = (BranchOp) op;
                branchOp.mTarget = finallyExit(inside, exits, branchOp.mTarget);
            } else if (op instanceof SwitchOp) {
                ((SwitchOp) op).finallyExits(this, inside, exits);
            } else if (op instanceof ReturnOp) {
                var retOp = (ReturnOp) op;

                if (retHandler == null) {
                    retHandler = new Lab(this);
                    if (retOp.op() != RETURN) {
                        retVar = new LocalVar(mMethod.returnType());
                    }
                }

                if (retVar != null) {
                    // Store the result into a local variable before the return, which will be
                    // replaced below.
                    var storeOp = new StoreVarOp(retVar);
                    prev.mNext = storeOp;
                    prev = storeOp;
                }

                // Replace the return with a goto.
                var gotoOp = new BranchOp(GOTO, 0, retHandler);
                prev.mNext = gotoOp;
                gotoOp.mNext = op.mNext;
                op = gotoOp;
            } else {
                lastTransformed = false;
            }

            prev = op;
        }

        if (!lastTransformed) {
            // Add a finally handler here, and then go past everything else.
            handler.run();
            veryEnd = new Lab(this);
            goto_(veryEnd);
        }

        // Add the "catch all" exception handler.
        {
            var exVar = catch_(startLab, endLab, null);
            handler.run();
            exVar.throw_();
        }

        // Add finally handlers for each branch-based exit.
        for (Map.Entry<Lab, Lab> e : exits.entrySet()) {
            e.getValue().here();
            handler.run();
            goto_(e.getKey());
        }

        // Add a finally handler for any return exits.
        if (retHandler != null) {
            retHandler.here();
            handler.run();
            if (retVar == null) {
                return_();
            } else {
                return_(retVar);
            }
        }

        if (veryEnd != null) {
            veryEnd.here();
        }
    }

    /**
     * Gather/modify a branch target outside a finally range.
     *
     * @param inside labels inside the finally range; can be null if empty
     * @param exits maps final targets to handler labels
     * @return new or existing target label
     */
    private Lab finallyExit(HashSet<Lab> inside, Map<Lab, Lab> exits, Lab target) {
        if (inside == null || !inside.contains(target)) {
            Lab handlerLab = exits.get(target);
            if (handlerLab == null) {
                handlerLab = new Lab(this);
                exits.put(target, handlerLab);
            }
            target = handlerLab;
            target.used();
        }
        return target;
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
                    char c = (Character) value;
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
            mConstants.addMethodHandle(REF_invokeStatic, ref);

        int bi = mClassMaker.addBootstrapMethod(bootstrapHandle, bootArgs);
        String desc = Type.makeDescriptor(strType, valueTypes);
        ConstantPool.C_Dynamic dynamic = mConstants.addInvokeDynamic(bi, bootName, desc);

        addOp(new InvokeDynamicOp(valueTypes.size(), dynamic, strType));

        return storeToNewVar(strType);
    }

    @Override
    public Field access(VarHandle handle, Object... values) {
        List<Class<?>> coordTypes = handle.coordinateTypes();

        if (coordTypes.size() != values.length) {
            throw new IllegalArgumentException
                ("Wrong number of coordinates (expecting " + coordTypes.size() + ')');
        }

        Type[] coordinateTypes;
        {
            coordinateTypes = new Type[coordTypes.size()];
            int i = 0;
            for (Class<?> clazz : coordTypes) {
                coordinateTypes[i++] = Type.from(clazz);
            }
        }

        Type handleType = Type.from(VarHandle.class);
        var handleVar = new LocalVar(handleType);

        if (mClassMaker.allowComplexConstants()) {
            handleVar.setExact(handle);
        } else {
            handleVar.set(handle);
        }

        return new HandleVar(handleVar, Type.from(handle.varType()), coordinateTypes, values);
    }

    @Override
    public void nop() {
        addOp(new BytecodeOp(NOP, 0));
    }

    @Override
    public MethodHandle finish() {
        throw new IllegalStateException("Not a standalone method");
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
            LocalVar top = stackTop();
            slot = top.mSlot + top.slotWidth();
        }

        LocalVar top;
        if (newOffset < 0) {
            top = new LocalVar(type);
        } else {
            top = new NewVar(type, newOffset);
        }

        top.mSlot = slot;

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

    private LocalVar stackTop() {
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
            stackPush(Null.THE);
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
     * Push a local variable to the stack.
     */
    private void pushVar(LocalVar var) {
        int slot = var.mSlot;

        if (slot < 0) {
            // Must be a local variable with a slot assigned by flow analysis.
            throw new AssertionError();
        }

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
    private void convert(Type from, Type to, int code) {
        if (code <= 0) {
            return;
        }

        if (code < 5) {
            convertPrimitive(to, code);
            return;
        }

        if (code < 10) {
            code -= 5;
            Type primTo = convertPrimitive(to, code);
            if (primTo == null) {
                // Assume converting to Object/Number. Need something specific.
                primTo = convertPrimitive(from.box(), code);
            }
            box(primTo);
            return;
        }

        if (code < 15) {
            // See doAddConversionOp.
            throw new AssertionError();
        }

        if (code < 20) {
            unbox(from);
            convertPrimitive(to, code - 15);
            return;
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
     */
    private void box(Type primType) {
        Type objType = primType.box();
        Type.Method method = objType.defineMethod(true, objType, "valueOf", primType);
        appendOp(INVOKESTATIC, 1);
        appendShort(mConstants.addMethod(method).mIndex);
        stackPush(objType);
    }

    /**
     * Unbox a boxed primitive type on the stack, resulting in a primitive type on the stack.
     * Can throw a NullPointerException at runtime.
     */
    private void unbox(Type objType) {
        Type primType = objType.unbox();
        Type.Method method = objType.defineMethod(false, primType, primType.name() + "Value");
        appendOp(INVOKEVIRTUAL, 1);
        appendShort(mConstants.addMethod(method).mIndex);
        stackPush(primType);
    }

    /**
     * Pop an item off the stack and store the result into a local variable.
     */
    private void storeVar(LocalVar var) {
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

    private void addOp(Op op) {
        if (mLastOp == null) {
            mFirstOp = op;
        } else {
            mLastOp.mNext = op;
        }
        mLastOp = op;
    }

    /**
     * Assigns next op for the previous op.
     */
    private void removeOps(Op prev, Op next) {
        if (prev == null) {
            mFirstOp = next;
        } else {
            prev.mNext = next;
        }
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

    /**
     * Add a push constant or push variable operation, and optionally perform a conversion.
     *
     * @param type desired type of entry on the stack; pass null if anything can be pushed
     * @return actual type
     */
    private Type addPushOp(Type type, Object value) {
        if (value instanceof OwnedVar) {
            var owned = (OwnedVar) value;
            if (owned.tryPushTo(this)) {
                return addConversionOp(owned.type(), type);
            }
            throw new IllegalArgumentException("Unknown variable");
        }

        Type constantType;

        if (value == null) {
            if (type != null && type.isPrimitive()) {
                throw new IllegalArgumentException("Cannot store null into primitive variable");
            }
            constantType = Null.THE;
        } else if (value instanceof String) {
            constantType = Type.from(String.class);
        } else if (value instanceof Class) {
            constantType = Type.from(Class.class);
            Class clazz = (Class) value;
            if (clazz.isPrimitive()) {
                new LocalVar(Type.from(clazz).box()).field("TYPE").push();
                return addConversionOp(constantType, type);
            }
        } else if (value instanceof Number) {
            if (value instanceof Integer) {
                constantType = INT;
                if (type != null) {
                    int v = (Integer) value;
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
                    long v = (Long) value;
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
                    float v = (Float) value;
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
                    double v = (Double) value;
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
                    byte v = (Byte) value;
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
                    short v = (Short) value;
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
                new LocalVar(actualType.box()).field("TYPE").push();
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
            new LocalVar(constantType).field(((Enum) value).name()).push();
            return addConversionOp(constantType, type);
        } else {
            Type actualType = ConstableSupport.THE.toConstantType(this, value);
            if (actualType != null) {
                constantType = Type.from(Class.class);
                if (actualType.isPrimitive()) {
                    new LocalVar(actualType.box()).field("TYPE").push();
                    return addConversionOp(constantType, type);
                }
                value = actualType;
            } else {
                ConstantVar cv = ConstableSupport.THE.toConstantVar(this, value);
                if (cv != null) {
                    cv.push();
                    return addConversionOp(cv.type(), type);
                }
                throw unsupportedConstant(value);
            }
        }

        addOp(new BasicConstantOp(value, constantType));

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

        if (code < 10 || code >= 15) {
            addOp(new Op() {
                @Override
                void appendTo(TheMethodMaker m) {
                    m.convert(from, to, code);
                }
            });
            return;
        }

        // Rebox without throwing NPE. Code is in the range 10..14.

        LocalVar fromVar;
        if (mLastOp instanceof PushVarOp) {
            fromVar = ((PushVarOp) mLastOp).mVar;
        } else {
            fromVar = new LocalVar(from);
            addOp(new StoreVarOp(fromVar));
            fromVar.push();
        }

        Lab nonNull = new Lab(this);
        addBranchOp(IFNONNULL, 1, nonNull);
        var toVar = new LocalVar(to);
        toVar.set(null);
        Lab cont = new Lab(this);
        goto_(cont);
        nonNull.here();
        addOp(new PushVarOp(fromVar));
        Type fromPrim = from.unbox();
        addConversionOp(from, fromPrim);
        addConversionOp(fromPrim, to);
        addOp(new StoreVarOp(toVar));
        cont.here();
        addOp(new PushVarOp(toVar));
    }

    /**
     * Adds a constant using the ConstantsRegistry.
     */
    private ConstantPool.C_Dynamic addComplexConstant(Type type, Object value) {
        Set<Type.Method> bootstraps = Type.from(ConstantsRegistry.class).findMethods
            ("remove",
             new Type[] {Type.from(MethodHandles.Lookup.class), Type.from(String.class),
                         Type.from(Class.class), Type.INT},
             0, 1, null, null);

        if (bootstraps.size() != 1) {
            throw new AssertionError();
        }

        ConstantPool.C_Method ref = mConstants.addMethod(bootstraps.iterator().next());
        ConstantPool.C_MethodHandle bootHandle = mConstants.addMethodHandle(REF_invokeStatic, ref);

        int slot = mClassMaker.addComplexConstant(value);

        ConstantPool.Constant[] bootArgs = {
            addLoadableConstant(Type.INT, slot)
        };

        int bi = mClassMaker.addBootstrapMethod(bootHandle, bootArgs);

        // Note that "_" isn't used by the bootstrap method. It's a dummy name.
        return mConstants.addDynamicConstant(bi, "_", type);
    }

    /**
     * Intended for loading bootstrap constants, although it works in other cases too.
     *
     * @param type expected constant type; can pass null to derive from the arg itself
     */
    ConstantPool.Constant addLoadableConstant(Type type, Object value) {
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
                mConstants.addMethodHandle(REF_invokeStatic, ref);

            return mConstants.addDynamicConstant
                (mClassMaker.addBootstrapMethod(bootHandle, new ConstantPool.Constant[0]),
                 name, paramType);
        }

        if (value instanceof Variable) {
            if (value instanceof ConstantVar) {
                if ((c = ((ConstantVar) value).tryObtain(this)) != null) {
                    return c;
                }
            }
            throw unsupportedConstant(value);
        }

        // If the value is Constable, toConstantVar might still work, but the resulting
        // ConstantDesc might throw some state away. VarHandle and MethodHandle are Constable,
        // but the Lookup object is lost. For this reason, always pass the value as a complex
        // constant if allowed. If for some reason the "lossy" behavior is desired, the
        // application must provide a ConstantDesc instead of a Constable.

        if (!mClassMaker.allowComplexConstants() || ConstableSupport.THE.isConstantDesc(value)) {
            ConstantVar cv = ConstableSupport.THE.toConstantVar(this, value);
            if (cv != null) {
                return cv.mConstant;
            }
            if (!mClassMaker.allowComplexConstants()) {
                throw unsupportedConstant(value);
            }
        }

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
     * Add a store to local variable operation without checking for proper ownership.
     */
    private void addStoreOp(LocalVar var) {
        addOp(new StoreVarOp(var));
    }

    /**
     * Stores the top stack item into a new local variable.
     */
    private LocalVar storeToNewVar(Type type) {
        LocalVar var = new LocalVar(type);
        addStoreOp(var);
        return var;
    }

    /**
     * Add a math operation, supporting ints, longs, floats, and doubles.
     *
     * @param op IADD, ISUB, IMUL, IDIV, IREM, or INEG
     * @param value must be null for unary operation
     * @return new variable
     */
    private LocalVar addMathOp(String name, byte op, OwnedVar var, Object value) {
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

        return storeToNewVar(varType);
    }

    /**
     * Adds a logical operation, supporting ints and longs.
     *
     * @param op ISHL, ISHR, IUSHR, IAND, IOR, or IXOR
     * @return new variable
     */
    private LocalVar addLogicalOp(String name, byte op, OwnedVar var, Object value) {
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

        return storeToNewVar(varType);
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
        long lSize = 8 + 8L * cases.length;
        byte op = (tSize <= lSize) ? TABLESWITCH : LOOKUPSWITCH;
        addOp(new SwitchOp(op, defaultLabel, cases, labels));
    }

    /**
     * State tracked for flow analysis, which is used to build the StackMapTable.
     */
    final class Flow {
        // List of variables that have been visited and have a slot assigned.
        final List<LocalVar> mVarList;
        final int mMinVars;

        // Bits are set for variables known to be available at the current flow position.
        BitSet mVarUsage;

        int mOpCount;

        private Op mRemoved;

        private int mDepth;
        private Overflow mOverflow;

        Flow(List<LocalVar> varList, BitSet varUsage) {
            mVarList = varList;
            mMinVars = varList.size();
            mVarUsage = varUsage;
        }

        /**
         * Entry point for flow analysis.
         *
         * @param op always expected to be a Lab except for the very first op
         */
        void run(Op op) {
            final BitSet original = (BitSet) mVarUsage.clone();

            if (mDepth >= 100) {
                // Prevent stack overflow.
                mOverflow = new Overflow(mOverflow, op, original);
                return;
            }

            while (true) {
                mDepth++;

                // Track the previous op for supporting removal, but cannot remove Lab ops.
                Op prev = null;

                while (true) {
                    Op next;
                    if (!op.mVisited) {
                        op.mVisited = true;
                        mOpCount++;
                        next = op.flow(this, prev);
                    } else {
                        next = op.revisit(this, prev);
                    }
                    if (next == null) {
                        break;
                    }
                    if (next instanceof HandlerLab) {
                        throw new IllegalStateException("Code flows into an exception handler");
                    }

                    if (mRemoved == op) {
                        // Keep existing prev node in order for subsequent removes to be correct.
                        mRemoved = null;
                    } else {
                        prev = op;
                    }

                    op = next;
                }

                if (--mDepth > 0 || mOverflow == null) {
                    mVarUsage = original;
                    break;
                }

                // Pop the overflow stack.
                op = mOverflow.mOp;
                mVarUsage = mOverflow.mVarUsage;
                mOverflow = mOverflow.mPrev;
            }
        }

        /**
         * @param op the op being removed; pass null if not the current operation
         * @param next the next op to keep
         */
        void removeOps(Op prev, Op op, Op next, int amt) {
            mRemoved = op;
            TheMethodMaker.this.removeOps(prev, next);
            mOpCount -= amt;
        }

        int nextSlot() {
            List<LocalVar> vars = mVarList;
            if (vars.isEmpty()) {
                return 0;
            }
            LocalVar last = vars.get(vars.size() - 1);
            return last.mSlot + last.slotWidth();
        }
    }

    private static class Overflow {
        final Overflow mPrev;
        final Op mOp;
        final BitSet mVarUsage;

        Overflow(Overflow prev, Op op, BitSet varUsage) {
            mPrev = prev;
            mOp = op;
            mVarUsage = varUsage;
        }
    }

    abstract static class Op {
        Op mNext;
        boolean mVisited;

        abstract void appendTo(TheMethodMaker m);

        /**
         * Should be called before running another pass over the code.
         */
        void reset() {
            mVisited = false;
        }

        /**
         * Recursively flows through unvisited operations and returns the next operation.
         * Subclasses should override this method if they have special flow patterns.
         */
        Op flow(Flow flow, Op prev) {
            return mNext;
        }

        /**
         * Called if already visited. Return the next op if flow should continue.
         */
        Op revisit(Flow flow, Op prev) {
            return flow(flow, prev);
        }
    }

    static class Lab extends Op implements Label {
        final TheMethodMaker mOwner;

        int mAddress = -1;

        private int mUsedCount;
        private int[] mTrackOffsets;
        private BranchOp[] mTrackBranches;
        private int mTrackCount;

        // Bits are set for variables known to be available at this label.
        private BitSet mVarUsage;

        Lab(TheMethodMaker owner) {
            mOwner = owner;
        }

        @Override
        void reset() {
            super.reset();
            mAddress = -1;
            mTrackBranches = null;
            mTrackCount = 0;
            mVarUsage = null;
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
            // Pseudo-op (no code to append).

            mAddress = m.mCodeLen;

            if (isTarget()) {
                LocalVar[] vars = m.mVars;
                BitSet usage = mVarUsage;

                // First figure out the number of local codes to fill in. Assume that caller
                // has already sorted the variables by slot.

                int numCodes = 0;
                for (int i=vars.length; --i>=0; ) {
                    LocalVar var = vars[i];
                    int slot = var.mSlot;
                    if (usage.get(slot)) {
                        if (numCodes <= 0) {
                            numCodes = slot + 1;
                        } else {
                            // Wide sm codes consume two slots.
                            numCodes -= var.slotWidth() - 1;
                        }
                    }
                }

                int[] localCodes;
                if (numCodes <= 0) {
                    localCodes = null;
                } else {
                    // Note that SM_TOP is zero, so all codes are SM_TOP by default.
                    localCodes = new int[numCodes];

                    int adjust = 0;
                    for (LocalVar var : vars) {
                        int slot = var.mSlot;
                        int codeSlot = slot + adjust;
                        if (codeSlot >= localCodes.length) {
                            break;
                        }
                        if (usage.get(slot)) {
                            localCodes[codeSlot] = var.smCode();
                            // Wide sm codes consume two slots.
                            adjust -= var.slotWidth() - 1;
                        }
                    }
                }

                int[] stackCodes = stackCodes();

                m.mStackMapTable.add(mAddress, localCodes, stackCodes);
            }

            if (mTrackOffsets != null && mTrackCount != 0) {
                byte[] code = m.mCode;
                for (int i=0; i<mTrackCount; i++) {
                    int offset = mTrackOffsets[i];
                    if (offset >= 0) {
                        int srcAddr = ((short) cShortArrayHandle.get(code, offset)) & 0xffff;
                        int branchAmount = mAddress - srcAddr;
                        if (branchAmount <= 32767) {
                            cShortArrayHandle.set(code, offset, (short) branchAmount);
                        } else {
                            // Convert to wide branch and perform another code pass.
                            mTrackBranches[i].makeWide(m);
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
        }

        @Override
        Op flow(Flow flow, Op prev) {
            mVarUsage = (BitSet) flow.mVarUsage.clone();
            return mNext;
        }

        @Override
        Op revisit(Flow flow, Op prev) {
            if (mVarUsage != null) {
                if (mVarUsage.equals(flow.mVarUsage)) {
                    // Nothing changed.
                    return null;
                }
                flow.mVarUsage.and(mVarUsage);
                if (mVarUsage.equals(flow.mVarUsage)) {
                    // Still nothing changed.
                    return null;
                }
            }
            // Variable of usage at this label changed, so the code that follows will need to
            // be revisited.
            mVarUsage = (BitSet) flow.mVarUsage.clone();
            return mNext;
        }

        /**
         * Must be called when label is used as a branch target, but never when used as an
         * exception handler.
         */
        void used() {
            mUsedCount++;
            if (mTrackOffsets == null) {
                mTrackOffsets = new int[4];
            }
        }

        /**
         * @return true if totally unused
         */
        boolean lessUsed() {
            if (--mUsedCount <= 0) {
                mTrackOffsets = null;
                return true;
            }
            return false;
        }

        /**
         * By default, only returns true if label was reached by a branch.
         */
        boolean isTarget() {
            return mUsedCount > 0;
        }

        /**
         * Must be called after a branch operation to this label has been appended. The used
         * method must have already been called when the operation was initially created.
         */
        void comesFrom(BranchOp branch, TheMethodMaker m) {
            int srcAddr = m.mCodeLen - 1; // opcode address

            if (mAddress >= 0) {
                int distance = mAddress - srcAddr;

                if (distance >= -32768) {
                    m.appendShort(distance);
                } else {
                    // Wide back branch.
                    byte op = m.mCode[srcAddr];
                    if (op == GOTO) {
                        m.mCode[srcAddr] = GOTO_W;
                        m.appendInt(distance);
                    } else {
                        // Convert to wide branch and perform another code pass.
                        branch.makeWide(m);
                    }
                }

                return;
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
         * Return the StackMapTable codes at this label.
         */
        int[] stackCodes() {
            return null;
        }
    }

    /**
     * Label which has one item on the stack.
     */
    static class StackLab extends Lab {
        private final int mSmCode;

        StackLab(TheMethodMaker owner, int smCode) {
            super(owner);
            mSmCode = smCode;
        }

        @Override
        int[] stackCodes() {
            return new int[] {mSmCode};
        }
    }

    /**
     * Exception handler catch label.
     */
    static class HandlerLab extends StackLab {
        private final Type mCatchType;

        HandlerLab(TheMethodMaker owner, Type catchType, int smCatchCode) {
            super(owner, smCatchCode);
            mCatchType = catchType;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            super.appendTo(m);
            m.stackPush(mCatchType);
        }

        @Override
        boolean isTarget() {
            // Won't be reached by a branch, but instead will only be reached by tryCatchFlow.
            return mVisited;
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
        Op flow(Flow flow, Op prev) {
            byte op = op();
            if (op == ATHROW) {
                return null;
            }
            return mNext;
        }
    }

    static class ReturnOp extends BytecodeOp {
        ReturnOp(byte op, int stackPop) {
            super(op, stackPop);
        }

        @Override
        Op flow(Flow flow, Op prev) {
            return null;
        }
    }

    static final class BranchOp extends BytecodeOp {
        Lab mTarget;

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
            if (op == GOTO_W) {
                int srcAddr = m.mCodeLen;
                m.appendByte(GOTO_W);
                mTarget.comesFromWide(m, srcAddr);
            } else {
                m.appendOp(op, stackPop());
                mTarget.comesFrom(this, m);
            }
        }

        @Override
        Op flow(Flow flow, Op prev) {
            Lab target;
            Op next;

            while (true) {
                target = mTarget;
                next = mNext;

                byte op = op();

                if (op == GOTO || op == GOTO_W) {
                    if (target == next) {
                        // Remove silly goto.
                        flow.removeOps(prev, this, next, 1);
                        target.lessUsed();
                    }
                    return target;
                }

                // If the next op is a goto, then flip the condition and remove the goto.

                if (!(next instanceof BranchOp)) {
                    break;
                }

                BranchOp nextBranch = (BranchOp) next;

                if (nextBranch.op() != GOTO || next.mNext != target) {
                    break;
                }

                Op newNext = target;
                int amtRemoved = 1;
                if (target.lessUsed()) {
                    newNext = target.mNext;
                    amtRemoved++;
                }
                flip(op);
                mTarget = nextBranch.mTarget;
                flow.removeOps(this, null, newNext, amtRemoved);
            }

            flow.run(target);
            return next;
        }

        void makeWide(TheMethodMaker m) {
            byte op = op();
            if (op == GOTO) {
                mCode = GOTO_W;
                // Need to rebuild the code to obtain new addresses.
                m.mFinished = -1;
            } else {
                flip(op);
                Op cont = mNext;
                mNext = new BranchOp(GOTO_W, 0, mTarget);
                mTarget = new Lab(m);
                mTarget.used();
                mNext.mNext = mTarget;
                mTarget.mNext = cont;
                // Need to perform flow analysis again because a new label was added.
                m.mFinished = -2;
            }
        }

        /**
         * @param an "if" opcode
         */
        private void flip(byte op) {
            /*
              Adjust the opcode to branch to the opposite target.
             
              ==  to  !=
              !=  to  ==
              <   to  >=
              >=  to  <
              >   to  <=
              <=  to  >
            */
            op = (byte) (op >= IFNULL ? (op ^ 1) : ((op - 1) ^ 1) + 1);

            mCode = (stackPop() << 8) | (op & 0xff);
        }
    }

    static final class SwitchOp extends BytecodeOp {
        Lab mDefault;
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
        Op flow(Flow flow, Op prev) {
            for (Lab lab : mLabels) {
                flow.run(lab);
            }
            return mDefault;
        }

        /**
         * Gather/modify all targets from this switch which branch outside a finally range.
         *
         * @param inside labels inside the finally range; can be null if empty
         * @param exits maps final targets to handler labels
         */
        void finallyExits(TheMethodMaker m, HashSet<Lab> inside, Map<Lab, Lab> exits) {
            mDefault = m.finallyExit(inside, exits, mDefault);

            for (int i=0; i<mLabels.length; i++) {
                mLabels[i] = m.finallyExit(inside, exits, mLabels[i]);
            }
        }
    }

    static final class FieldOp extends BytecodeOp {
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

    static final class InvokeOp extends BytecodeOp {
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

    static final class InvokeDynamicOp extends BytecodeOp {
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
    abstract static class ConstantOp extends Op {
        @Override
        Op flow(Flow flow, Op prev) {
            // Check if storing to an unused variable and remove the pair.

            Op next = mNext;
            if (next instanceof StoreVarOp && ((StoreVarOp) next).unusedVar()) {
                next = next.mNext;
                // Removing 2 ops, but specify 1 because the store op won't be visited.
                flow.removeOps(prev, this, next, 1);
                return next;
            }

            return next;
        }
    }

    static final class BasicConstantOp extends ConstantOp {
        final Object mValue;
        final Type mType;

        /**
         * @param type non-null
         */
        BasicConstantOp(Object value, Type type) {
            mValue = value;
            mType = type;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            m.pushConstant(mValue, mType);
        }
    }

    static final class ExplicitConstantOp extends ConstantOp {
        final ConstantPool.Constant mConstant;
        final Type mType;

        ExplicitConstantOp(ConstantPool.Constant constant, Type type) {
            mConstant = constant;
            mType = type;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            int typeCode = mType.typeCode();
            if (typeCode == T_DOUBLE || typeCode == T_LONG) {
                int index = mConstant.mIndex;
                m.appendOp(LDC2_W, 0);
                m.appendShort(index);
                m.stackPush(mType);
            } else {
                m.pushConstant(mConstant, mType);
            }
        }
    }

    /**
     * Accesses a local variable.
     */
    abstract static class LocalVarOp extends Op {
        final LocalVar mVar;

        LocalVarOp(LocalVar var) {
            mVar = var;
        }

        @Override
        Op flow(Flow flow, Op prev) {
            LocalVar var = mVar;
            int slot = var.mSlot;

            if (slot < 0) {
                List<LocalVar> varList = flow.mVarList;
                slot = flow.nextSlot();
                var.mSlot = slot;
                varList.add(var);
            }

            flow.mVarUsage.set(slot);

            return mNext;
        }
    }

    /**
     * Push a local variable to the stack.
     */
    static final class PushVarOp extends LocalVarOp {
        PushVarOp(LocalVar var) {
            super(var);
            var.mPushCount++;
        }

        @Override
        void appendTo(TheMethodMaker m) {
            m.pushVar(mVar);
        }

        @Override
        Op flow(Flow flow, Op prev) {
            // Check if storing to an unused variable and remove the pair.

            Op next = mNext;
            if (next instanceof StoreVarOp && ((StoreVarOp) next).unusedVar()) {
                mVar.mPushCount--;
                next = next.mNext;
                // Removing 2 ops, but specify 1 because the store op won't be visited.
                flow.removeOps(prev, this, next, 1);
                return next;
            }

            return super.flow(flow, prev);
        }
    }

    /**
     * Stores to a local variable from the stack.
     */
    static final class StoreVarOp extends LocalVarOp {
        StoreVarOp(LocalVar var) {
            super(var);
        }

        @Override
        void appendTo(TheMethodMaker m) {
            if (unusedVar()) {
                m.stackPop();
            } else {
                m.storeVar(mVar);
            }
        }

        @Override
        Op flow(Flow flow, Op prev) {
            // Look for store/push pair to the same variable and remove the pair. Just use the
            // stack variable and avoid extra steps.

            Op next = mNext;
            if (next instanceof PushVarOp) {
                var push = (PushVarOp) next;
                LocalVar var = mVar;
                if (var == push.mVar && var.mPushCount == 1) {
                    var.mPushCount = 0;
                    next = next.mNext;
                    // Removing 2 ops, but specify 1 because the push op won't be visited.
                    flow.removeOps(prev, this, next, 1);
                    return next;
                }
            }

            if (unusedVar()) {
                // Won't actually store, but will pop. See appendTo method above.
                return next;
            }

            return super.flow(flow, prev);
        }

        boolean unusedVar() {
            return mVar.mPushCount == 0;
        }
    }

    /**
     * Increments an integer variable.
     */
    static final class IncOp extends LocalVarOp {
        final int mAmount;

        IncOp(LocalVar var, int amount) {
            super(var);
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
            } else {
                m.appendOp(WIDE, 0);
                m.appendByte(IINC);
                m.appendShort(slot);
                m.appendShort(mAmount);
            }
        }
    }

    static final class LineNumOp extends Op {
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

    static final class NameLocalVarOp extends Op {
        final LocalVar mVar;
        final String mName;

        NameLocalVarOp(LocalVar var, String name) {
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

    abstract class OwnedVar implements Variable, Typed {
        @Override
        public Class classType() {
            return type().clazz();
        }

        @Override
        public ClassMaker makerType() {
            return type().maker();
        }

        boolean tryPushTo(TheMethodMaker mm) {
            if (TheMethodMaker.this == mm) {
                push();
                return true;
            }
            return false;
        }

        abstract void push();

        void push(Type type) {
            push();
            addConversionOp(type(), type);
        }

        void pushObject() {
            push();
            Type type = type();
            if (type.isPrimitive()) {
                addConversionOp(type, type.box());
            }
        }

        @Override
        public Variable setExact(Object value) {
            if (value == null) {
                return set(null);
            }

            Type type = type();

            if (!type.isAssignableFrom(Type.from(value.getClass()))) {
                throw new IllegalStateException("Mismatched type");
            }

            addStoreConstantOp(new ExplicitConstantOp(addComplexConstant(type, value), type));

            return this;
        }

        abstract void addStoreConstantOp(ExplicitConstantOp op);

        @Override
        public LocalVar get() {
            push();
            return storeToNewVar(type());
        }

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

            if (value instanceof LocalVar) {
                typeCmp = comparisonType(((LocalVar) value).mType, eq);
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
        public LocalVar add(Object value) {
            return addMathOp("add", IADD, this, value);
        }

        @Override
        public LocalVar sub(Object value) {
            return addMathOp("subtract", ISUB, this, value);
        }

        @Override
        public LocalVar mul(Object value) {
            return addMathOp("multiply", IMUL, this, value);
        }

        @Override
        public LocalVar div(Object value) {
            return addMathOp("divide", IDIV, this, value);
        }

        @Override
        public LocalVar rem(Object value) {
            return addMathOp("remainder", IREM, this, value);
        }

        @Override
        public LocalVar eq(Object value) {
            if (value == null) {
                return isNull(IFNULL);
            } else {
                return relational(value, true, IF_ICMPEQ, IFEQ);
            }
        }

        @Override
        public LocalVar ne(Object value) {
            if (value == null) {
                return isNull(IFNONNULL);
            } else {
                return relational(value, true, IF_ICMPNE, IFNE);
            }
        }

        /**
         * @param op IFNULL or IFNONNULL
         */
        private LocalVar isNull(byte op) {
            Label match = label();
            pushForNull();
            addBranchOp(op, 1, match);
            addOp(new BasicConstantOp(false, BOOLEAN));
            Label cont = new StackLab(TheMethodMaker.this, SM_INT);
            goto_(cont);
            match.here();
            addOp(new BasicConstantOp(true, BOOLEAN));
            cont.here();
            return storeToNewVar(BOOLEAN);
        }

        @Override
        public LocalVar lt(Object value) {
            return relational(value, false, IF_ICMPLT, IFLT);
        }

        @Override
        public LocalVar ge(Object value) {
            return relational(value, false, IF_ICMPGE, IFGE);
        }

        @Override
        public LocalVar gt(Object value) {
            return relational(value, false, IF_ICMPGT, IFGT);
        }

        @Override
        public LocalVar le(Object value) {
            return relational(value, false, IF_ICMPLE, IFLE);
        }

        /**
         * @param eq true if performing an equality check
         * @param op normal op to use for ints
         * @param zeroOp op to use when comparing against a constant zero int
         */
        private LocalVar relational(Object value, boolean eq, byte op, byte zeroOp) {
            Label match = label();
            ifRelational(value, match, eq, op, zeroOp);
            addOp(new BasicConstantOp(false, BOOLEAN));
            Label cont = new StackLab(TheMethodMaker.this, SM_INT);
            goto_(cont);
            match.here();
            addOp(new BasicConstantOp(true, BOOLEAN));
            cont.here();
            return storeToNewVar(BOOLEAN);
        }

        @Override
        public LocalVar instanceOf(Object clazz) {
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

            return storeToNewVar(BOOLEAN);
        }

        @Override
        public LocalVar cast(Object clazz) {
            requireNonNull(clazz);

            Type fromType = type();
            Type toType = mClassMaker.typeFrom(clazz);
            int code = fromType.canConvertTo(toType);

            if (code != Integer.MAX_VALUE) {
                // Widening conversion, boxing, unboxing, or equal types.
                push();
                if (code != 0) {
                    doAddConversionOp(fromType, toType, code);
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
            } else narrowing: {
                // Narrowing conversion, or converting boolean to a number.

                Type primType = fromType.unbox();

                if (primType == null) {
                    if (Type.from(Number.class).isAssignableFrom(fromType)) {
                        if (toType != Type.BOOLEAN && toType != Type.CHAR) {
                            return invoke(toType.name() + "Value");
                        }
                    }
                    if (fromType.equals(Type.from(Object.class))) {
                        LocalVar casted;
                        if (toType == Type.BOOLEAN) {
                            casted = cast(Boolean.class);
                        } else if (toType == Type.CHAR) {
                            casted = cast(Character.class);
                        } else {
                            casted = cast(Number.class);
                        }
                        return casted.cast(clazz);
                    }
                    throw new IllegalStateException("Unsupported conversion");
                }

                int toTypeCode = toType.typeCode();
                byte op = 0;

                switch (primType.stackMapCode()) {
                case SM_INT:
                    switch (toTypeCode) {
                    case T_INT: {
                        push();
                        break narrowing;
                    }
                    case T_BOOLEAN: {
                        (primType == INT ? this : cast(int.class)).and(1).push();
                        break narrowing;
                    }
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
                    case T_BOOLEAN: case T_BYTE: case T_CHAR: case T_SHORT:
                        break;
                    case T_LONG: case T_FLOAT: case T_DOUBLE:
                        if (primType == BOOLEAN) {
                            break;
                        }
                        // fallthrough
                    default:
                        throw new IllegalStateException("Unsupported conversion");
                    }

                    return cast(int.class).cast(clazz);
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

            return storeToNewVar(toType);
        }

        @Override
        public LocalVar not() {
            LocalVar var = new LocalVar(BOOLEAN);
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
        public LocalVar and(Object value) {
            return addLogicalOp("and", IAND, this, value);
        }

        @Override
        public LocalVar or(Object value) {
            return addLogicalOp("or", IOR, this, value);
        }

        @Override
        public LocalVar xor(Object value) {
            return addLogicalOp("xor", IXOR, this, value);
        }

        @Override
        public LocalVar shl(Object value) {
            return addLogicalOp("shift", ISHL, this, value);
        }

        @Override
        public LocalVar shr(Object value) {
            return addLogicalOp("shift", ISHR, this, value);
        }

        @Override
        public LocalVar ushr(Object value) {
            return addLogicalOp("shift", IUSHR, this, value);
        }

        @Override
        public LocalVar neg() {
            return addMathOp("negate", INEG, this, null);
        }

        @Override
        public LocalVar com() {
            return addLogicalOp("complement", IXOR, this, -1);
        }

        @Override
        public LocalVar alength() {
            arrayCheck();
            push();
            addBytecodeOp(ARRAYLENGTH, 0);
            return storeToNewVar(INT);
        }

        @Override
        public LocalVar aget(Object index) {
            byte op = aloadOp();
            push();
            addPushOp(INT, index);
            addBytecodeOp(op, 1);
            return storeToNewVar(type().elementType());
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
        public LocalVar invoke(String name, Object... values) {
            if (name.equals("new")) {
                return doNew(type(), values, null);
            } else {
                return doInvoke(type(), this, name, 0, values, null, null);
            }
        }

        @Override
        public LocalVar invoke(Object retType, String name, Object[] types, Object... values) {
            Type returnType = null;
            Type[] paramTypes = null;

            if (retType != null) {
                returnType = mClassMaker.typeFrom(retType);
            }

            if (types != null) {
                paramTypes = new Type[types.length];
                for (int i=0; i<types.length; i++) {
                    paramTypes[i] = mClassMaker.typeFrom(types[i]);
                }
            }

            if (name.equals("new") && (returnType == null || returnType == type())) {
                return doNew(type(), values, paramTypes);
            } else {
                return doInvoke(type(), this, name, 0, values, returnType, paramTypes);
            }
        }

        @Override
        public Variable methodHandle(Object retType, String name, Object... types) {
            Type returnType = retType == null ? Type.VOID : mClassMaker.typeFrom(retType);

            Type[] paramTypes;
            if (types == null) {
                paramTypes = new Type[0];
            } else {
                paramTypes = new Type[types.length];
                for (int i=0; i<types.length; i++) {
                    paramTypes[i] = mClassMaker.typeFrom(types[i]);
                }
            }

            Type type = type();
            Type.Method method;
            int kind;

            if (name.equals("new") && (retType == null || returnType == type)) {
                method = type.findMethod("<init>", paramTypes, -1, -1, null, paramTypes);
                kind = REF_newInvokeSpecial;
            } else {
                if (type.isPrimitive()) {
                    type = type.box();
                }

                method = type.findMethod(name, paramTypes, 0, 0, returnType, paramTypes);

                if (method.isStatic()) {
                    kind = REF_invokeStatic;
                } else if (method.enclosingType().isInterface()) {
                    kind = REF_invokeInterface;
                } else {
                    kind = REF_invokeVirtual;
                }
            }

            return new ConstantVar(Type.from(MethodHandle.class),
                                   mConstants.addMethodHandle(kind, mConstants.addMethod(method)));
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
                if (arg == null) {
                    type = Null.THE;
                } else if (arg instanceof Typed) {
                    type = ((Typed) arg).type();
                } else if (arg instanceof MethodHandleInfo) {
                    // Conversion to MethodHandle is automatic.
                    type = Type.from(MethodHandle.class);
                } else {
                    type = ConstableSupport.THE.toConstantDescType(TheMethodMaker.this, arg);
                    if (type == null) {
                        type = mClassMaker.typeFrom(arg.getClass());
                    }
                }

                types[3 + i] = type;
            }

            Type.Method bootstrap = type().findMethod(name, types, 0, 1, null, null);

            ConstantPool.C_Method ref = mConstants.addMethod(bootstrap);
            ConstantPool.C_MethodHandle bootHandle =
                mConstants.addMethodHandle(REF_invokeStatic, ref);

            Type[] bootTypes = bootstrap.paramTypes();
            var bootArgs = new ConstantPool.Constant[args.length];
            if (!bootstrap.isVarargs()) {
                for (int i=0; i<args.length; i++) {
                    // +3 to skip these: Lookup caller, String name, and MethodType type
                    bootArgs[i] = addLoadableConstant(bootTypes[i + 3], args[i]);
                }
            } else {
                // +3 to skip these: Lookup caller, String name, and MethodType type
                int i = 3;
                for (; i < bootTypes.length - 1; i++) {
                    bootArgs[i - 3] = addLoadableConstant(bootTypes[i], args[i - 3]);
                }
                // Remaining args are passed as varargs.
                Type varargType = bootTypes[i].elementType();
                i -= 3;
                for (; i < args.length; i++) {
                    bootArgs[i] = addLoadableConstant(varargType, args[i]);
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


        @Override
        public void monitorEnter() {
            monitor(MONITORENTER);
        }

        @Override
        public void monitorExit() {
            monitor(MONITOREXIT);
        }

        private void monitor(byte op) {
            if (!type().isObject()) {
                throw new IllegalStateException("Not an object type");
            }
            push();
            addBytecodeOp(op, 1);
        }
    }

    class LocalVar extends OwnedVar implements Variable, Comparable<LocalVar> {
        final Type mType;

        int mSlot = -1;

        // Updated as Op list is built.
        int mPushCount;

        private String mName;

        LocalVar(Type type) {
            requireNonNull(type);
            mType = type;
        }

        @Override
        public int compareTo(LocalVar other) {
            return Integer.compare(mSlot, other.mSlot);
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
         * @return SM code at byte 0; additional bytes are filled in for object types
         */
        int smCode() {
            int code = mType.stackMapCode();
            if (code == SM_OBJECT) {
                code |= (mConstants.addClass(mType).mIndex << 8);
            }
            return code;
        }

        @Override
        public Type type() {
            return mType;
        }

        @Override
        void push() {
            addOp(new PushVarOp(this));
        }

        @Override
        public String name() {
            return mName;
        }

        @Override
        public LocalVar name(String name) {
            Objects.requireNonNull(name);
            if (mName != null) {
                throw new IllegalStateException("Already named");
            }
            addOp(new NameLocalVarOp(this, name));
            mName = name;
            return this;
        }

        @Override
        public LocalVar set(Object value) {
            addPushOp(mType, value);
            addStoreOp(this);
            return this;
        }

        @Override
        void addStoreConstantOp(ExplicitConstantOp op) {
            addOp(op);
            addStoreOp(this);
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
    }

    /**
     * Unmodifiable variable which refers to a constant.
     */
    class ConstantVar extends LocalVar {
        final ConstantPool.Constant mConstant;

        ConstantVar(Type type, ConstantPool.Constant constant) {
            super(type);
            mConstant = constant;
        }

        @Override
        public LocalVar set(Object value) {
            throw new IllegalStateException("Unmodifiable variable");
        }

        @Override
        public Variable setExact(Object value) {
            throw new IllegalStateException("Unmodifiable variable");
        }

        @Override
        public void inc(Object value) {
            throw new IllegalStateException("Unmodifiable variable");
        }

        /**
         * @return null if the constant isn't defined in the same ClassMaker.
         */
        ConstantPool.Constant tryObtain(TheMethodMaker mm) {
            return (mm.mClassMaker == TheMethodMaker.this.mClassMaker) ? mConstant : null;
        }

        @Override
        boolean tryPushTo(TheMethodMaker mm) {
            if (mClassMaker == mm.mClassMaker) {
                pushTo(mm);
                return true;
            }
            return false;
        }

        @Override
        void push() {
            pushTo(TheMethodMaker.this);
        }

        void pushTo(TheMethodMaker mm) {
            mm.addOp(new ExplicitConstantOp(mConstant, mType));
        }
    }

    /**
     * Special variable which refers to the enclosing class.
     */
    final class ClassVar extends ConstantVar {
        ClassVar(Type type) {
            super(type, mClassMaker.mThisClass);
        }

        @Override
        public LocalVar name(String name) {
            throw new IllegalStateException("Already named");
        }
    }

    /**
     * Stack variable which represents an uninitialized new object.
     */
    final class NewVar extends LocalVar {
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

    /**
     * Special variable which represents "this" inside a constructor.
     */
    final class InitThisVar extends LocalVar {
        private int mSmCode;

        InitThisVar(Type type) {
            super(type);
            mSmCode = SM_UNINIT_THIS;
        }

        @Override
        int smCode() {
            return mSmCode;
        }

        void ready() {
            mSmCode = super.smCode();
        }
    }

    abstract class BaseFieldVar extends OwnedVar implements Field {
        @Override
        public void inc(Object value) {
            set(add(value));
        }

        @Override
        public Field field(String name) {
            return get().field(name);
        }

        @Override
        public LocalVar getPlain() {
            return vhGet("get");
        }

        @Override
        public void setPlain(Object value) {
            vhSet("set", value);
        }

        @Override
        public LocalVar getOpaque() {
            return vhGet("getOpaque");
        }

        @Override
        public void setOpaque(Object value) {
            vhSet("setOpaque", value);
        }

        @Override
        public LocalVar getAcquire() {
            return vhGet("getAcquire");
        }

        @Override
        public void setRelease(Object value) {
            vhSet("setRelease", value);
        }

        @Override
        public LocalVar getVolatile() {
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

        abstract LocalVar vhGet(String name);

        abstract void vhSet(String name, Object value);

        abstract LocalVar vhCas(String name, Type retType, Object expectedValue, Object newValue);

        abstract LocalVar vhGas(String name, Object value);
    }

    /**
     * Normal static or instance field.
     */
    final class FieldVar extends BaseFieldVar {
        final LocalVar mInstance;
        final ConstantPool.C_Field mFieldRef;

        private ConstantPool.C_Dynamic mVarHandle;

        FieldVar(LocalVar instance, ConstantPool.C_Field fieldRef) {
            mInstance = instance;
            mFieldRef = fieldRef;
        }

        @Override
        public Type type() {
            return mFieldRef.mField.type();
        }

        @Override
        public String name() {
            return mFieldRef.mField.name();
        }

        @Override
        public BaseFieldVar set(Object value) {
            addBeginStoreFieldOp();
            addPushOp(type(), value);
            addFinishStoreFieldOp();
            return this;
        }

        @Override
        public ConstantVar varHandle() {
            Type vhType = Type.from(VarHandle.class);
            return new ConstantVar(vhType, vh(vhType));
        }

        @Override
        public ConstantVar methodHandleSet() {
            int kind = mFieldRef.mField.isStatic() ? REF_putStatic : REF_putField;
            return new ConstantVar(Type.from(MethodHandle.class),
                                   mConstants.addMethodHandle(kind, mFieldRef));
        }

        @Override
        public ConstantVar methodHandleGet() {
            int kind = mFieldRef.mField.isStatic() ? REF_getStatic : REF_getField;
            return new ConstantVar(Type.from(MethodHandle.class),
                                   mConstants.addMethodHandle(kind, mFieldRef));
        }

        @Override
        void push() {
            ConstantPool.C_Field fieldRef = mFieldRef;
            Type.Field field = fieldRef.mField;

            byte op;
            int stackPop;
            if (field.isStatic()) {
                op = GETSTATIC;
                stackPop = 0;
            } else {
                addPushOp(null, mInstance);
                op = GETFIELD;
                stackPop = 1;
            }

            addOp(new FieldOp(op, stackPop, fieldRef));
        }

        @Override
        void addStoreConstantOp(ExplicitConstantOp op) {
            addBeginStoreFieldOp();
            addOp(op);
            addFinishStoreFieldOp();
        }

        private void addBeginStoreFieldOp() {
            if (!mFieldRef.mField.isStatic()) {
                addPushOp(null, mInstance);
            }
        }

        private void addFinishStoreFieldOp() {
            ConstantPool.C_Field fieldRef = mFieldRef;
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

        @Override
        LocalVar vhGet(String name) {
            Type thisType = type();
            Type vhType = pushVarHandle();

            int stackPop;
            Type.Method method;
            if (mInstance == null) {
                stackPop = 1;
                method = vhType.inventMethod(false, thisType, name);
            } else {
                stackPop = 2;
                addOp(new PushVarOp(mInstance));
                method = vhType.inventMethod(false, thisType, name, mInstance.type());
            }

            ConstantPool.C_Method ref = mConstants.addMethod(method);
            addOp(new InvokeOp(INVOKEVIRTUAL, stackPop, ref));

            return storeToNewVar(thisType);
        }

        @Override
        void vhSet(String name, Object value) {
            Type thisType = type();
            Type vhType = pushVarHandle();

            int stackPop;
            Type.Method method;
            if (mInstance == null) {
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

        @Override
        LocalVar vhCas(String name, Type retType, Object expectedValue, Object newValue) {
            Type thisType = type();
            Type vhType = pushVarHandle();

            if (retType == null) {
                retType = thisType;
            }

            int stackPop;
            Type.Method method;
            if (mInstance == null) {
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

            return storeToNewVar(retType);
        }

        @Override
        LocalVar vhGas(String name, Object value) {
            Type thisType = type();
            Type vhType = pushVarHandle();

            int stackPop;
            Type.Method method;
            if (mInstance == null) {
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

            return storeToNewVar(thisType);
        }

        private Type pushVarHandle() {
            Type vhType = Type.from(VarHandle.class);
            addOp(new ExplicitConstantOp(vh(vhType), vhType));
            return vhType;
        }

        private ConstantPool.C_Dynamic vh(Type vhType) {
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
                    mConstants.addMethodHandle(REF_invokeStatic, ref);

                ConstantPool.Constant[] bootArgs = {
                    mFieldRef.mClass, addLoadableConstant(null, mFieldRef.mField.type())
                };

                mVarHandle = mConstants.addDynamicConstant
                    (mClassMaker.addBootstrapMethod(bootHandle, bootArgs),
                     mFieldRef.mNameAndType.mName, vhType);
            }

            return mVarHandle;
        }
    }

    /**
     * Pseudo field which accesses a VarHandle.
     */
    final class HandleVar extends BaseFieldVar {
        private final LocalVar mHandleVar;
        private final Type mType;
        private final Type[] mCoordinateTypes;
        private final Object[] mCoordinates;

        private Variable mHandleGet, mHandleSet;

        /**
         * @param handleVar must be of type VarHandle
         * @param type VarHandle.varType
         * @param coordinates variables and constants
         */
        HandleVar(LocalVar handleVar, Type type, Type[] coordinateTypes, Object[] coordinates) {
            mHandleVar = handleVar;
            mType = type;
            mCoordinateTypes = coordinateTypes;
            mCoordinates = coordinates;
        }

        @Override
        public Type type() {
            return mType;
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public LocalVar get() {
            return getPlain();
        }

        @Override
        public HandleVar set(Object value) {
            setPlain(value);
            return this;
        }

        @Override
        public Variable varHandle() {
            // Return a copy.
            return mHandleVar.get();
        }

        @Override
        public Variable methodHandleGet() {
            if (mHandleGet == null) {
                mHandleGet = mHandleVar.invoke("toMethodHandle", VarHandle.AccessMode.GET);
            }
            return mHandleGet;
        }

        @Override
        public Variable methodHandleSet() {
            if (mHandleSet == null) {
                mHandleSet = mHandleVar.invoke("toMethodHandle", VarHandle.AccessMode.SET);
            }
            return mHandleSet;
        }

        @Override
        void push() {
            vhPush("get");
        }

        @Override
        void addStoreConstantOp(ExplicitConstantOp op) {
            vhSet("set", op);
        }

        @Override
        LocalVar vhGet(String name) {
            vhPush(name);
            return storeToNewVar(mType);
        }

        void vhPush(String name) {
            mHandleVar.push();

            for (int i=0; i<mCoordinates.length; i++) {
                addPushOp(mCoordinateTypes[i], mCoordinates[i]);
            }

            Type vhType = mHandleVar.type();
            Type.Method method = vhType.inventMethod(false, mType, name, mCoordinateTypes);

            ConstantPool.C_Method ref = mConstants.addMethod(method);
            addOp(new InvokeOp(INVOKEVIRTUAL, 1 + mCoordinates.length, ref));
        }

        @Override
        void vhSet(String name, Object value) {
            mHandleVar.push();

            Type[] allTypes = new Type[mCoordinateTypes.length + 1];

            int i = 0;
            for (; i<mCoordinates.length; i++) {
                allTypes[i] = addPushOp(mCoordinateTypes[i], mCoordinates[i]);
            }

            if (value instanceof ExplicitConstantOp) {
                allTypes[i] = mType;
                addOp((ExplicitConstantOp) value);
            } else {
                allTypes[i] = addPushOp(mType, value);
            }

            Type vhType = mHandleVar.type();
            Type.Method method = vhType.inventMethod(false, Type.VOID, name, allTypes);

            ConstantPool.C_Method ref = mConstants.addMethod(method);
            addOp(new InvokeOp(INVOKEVIRTUAL, 2 + mCoordinates.length, ref));
        }

        @Override
        LocalVar vhCas(String name, Type retType, Object expectedValue, Object newValue) {
            mHandleVar.push();

            Type[] allTypes = new Type[mCoordinateTypes.length + 2];

            int i = 0;
            for (; i<mCoordinates.length; i++) {
                allTypes[i] = addPushOp(mCoordinateTypes[i], mCoordinates[i]);
            }

            allTypes[i++] = addPushOp(mType, expectedValue);
            allTypes[i] = addPushOp(mType, newValue);

            Type vhType = mHandleVar.type();

            if (retType == null) {
                retType = mType;
            }

            Type.Method method = vhType.inventMethod(false, retType, name, allTypes);

            ConstantPool.C_Method ref = mConstants.addMethod(method);
            addOp(new InvokeOp(INVOKEVIRTUAL, 3 + mCoordinates.length, ref));

            return storeToNewVar(retType);
        }

        @Override
        LocalVar vhGas(String name, Object value) {
            mHandleVar.push();

            Type[] allTypes = new Type[mCoordinateTypes.length + 1];

            int i = 0;
            for (; i<mCoordinates.length; i++) {
                allTypes[i] = addPushOp(mCoordinateTypes[i], mCoordinates[i]);
            }

            allTypes[i] = addPushOp(mType, value);

            Type vhType = mHandleVar.type();
            Type.Method method = vhType.inventMethod(false, mType, name, allTypes);

            ConstantPool.C_Method ref = mConstants.addMethod(method);
            addOp(new InvokeOp(INVOKEVIRTUAL, 2 + mCoordinates.length, ref));

            return storeToNewVar(mType);
        }
    }

    final class BootstrapImpl implements Bootstrap {
        final int mBootstrapIndex;
        final boolean mCondy;

        BootstrapImpl(int bi, boolean condy) {
            mBootstrapIndex = bi;
            mCondy = condy;
        }

        @Override
        public LocalVar invoke(Object returnType, String name, Object[] types, Object... values) {
            Type retType = mClassMaker.typeFrom(returnType);
            int length = values == null ? 0 : values.length;

            LocalVar var;

            if (mCondy) {
                if ((types != null && types.length != 0) || length != 0) {
                    throw new IllegalStateException("Dynamic constant has no parameters");
                }

                ConstantPool.C_Dynamic dynamic = mConstants
                    .addDynamicConstant(mBootstrapIndex, name, retType);

                addOp(new ExplicitConstantOp(dynamic, retType));

                var = new ConstantVar(retType, dynamic);
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

                var = new LocalVar(retType);
            }

            addStoreOp(var);
            return var;
        }
    }
}
