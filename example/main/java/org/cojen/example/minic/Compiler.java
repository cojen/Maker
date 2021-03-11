/*
 *  Copyright 2021 Cojen.org
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

package org.cojen.example.minic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import java.util.HashMap;
import java.util.Scanner;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import org.cojen.maker.Label;
import org.cojen.maker.MethodMaker;
import org.cojen.maker.Variable;

import static org.cojen.example.minic.MiniCParser.*;

/**
 * Compiles a Mini-C program directly into a MethodHandle.
 *
 * @author Brian S O'Neill
 */
public class Compiler extends MiniCBaseVisitor<Object> {
    /**
     * Parse, compile, and then run a Mini-C program.
     *
     * @param args [0] - path to source file
     */
    public static void main(String[] args) throws Throwable {
        CharStream chars = CharStreams.fromFileName(args[0]);
        var tokens = new CommonTokenStream(new MiniCLexer(chars));
        var parser = new MiniCParser(tokens);
        var mh = new Compiler().compile(parser.program());
        mh.invoke();
    }

    private MethodMaker mm;
    private Variable scanner;
    private Scope scope;

    public Compiler() {
    }

    public MethodHandle compile(ProgramContext ctx) {
        mm = MethodMaker.begin(MethodHandles.lookup(), null, "_");
        scanner = mm.var(Scanner.class).set(null);
        scope = new Scope(null);
        try {
            visit(ctx);
            return mm.finish();
        } finally {
            mm = null;
            scanner = null;
            scope = null;
        }
    }

    // Objects returned by the visit methods are Variables, constants, or null (for statements).

    @Override
    public Object visitBlock(BlockContext ctx) {
        scope = new Scope(scope);
        visitChildren(ctx);
        scope = scope.parent;
        return null;
    }

    @Override
    public Object visitIfStatement(IfStatementContext ctx) {
        Object test = visit(ctx.parExpression());

        if (test instanceof Variable) {
            var testVar = (Variable) test;
            Label cont = mm.label();
            if (ctx.elseBody == null) {
                testVar.ifFalse(cont);
            } else {
                Label ifLabel = mm.label();
                testVar.ifTrue(ifLabel);
                visit(ctx.elseBody);
                mm.goto_(cont);
                ifLabel.here();
            }
            visit(ctx.ifBody);
            cont.here();
            return null;
        }

        if (test instanceof Boolean) {
            var testVar = (boolean) test;
            if (testVar) {
                visit(ctx.ifBody);
            } else if (ctx.elseBody != null) {
                visit(ctx.elseBody);
            }
            return null;
        }

        throw new IllegalArgumentException("If test doesn't evaluate to a boolean");
    }

    @Override
    public Object visitWhileStatement(WhileStatementContext ctx) {
        scope = new Scope(scope);
        scope.breakTarget = mm.label();

        Label start = mm.label().here();
        Object test = visit(ctx.parExpression());

        body: {
            if (test instanceof Variable) {
                ((Variable) test).ifFalse(scope.breakTarget);
            } else if (test instanceof Boolean) {
                if (!((boolean) test)) {
                    break body;
                }
            } else {
                throw new IllegalArgumentException("While test doesn't evaluate to a boolean");
            }
            visit(ctx.statement());
            mm.goto_(start);
        }

        scope.breakTarget.here();
        scope = scope.parent;
        return null;
    }

    @Override
    public Object visitBreakStatement(BreakStatementContext ctx) {
        Label breakTarget = scope.breakOut();
        if (breakTarget == null) {
            throw new IllegalArgumentException("Not in a while loop");
        }
        mm.goto_(breakTarget);
        return null;
    }

    @Override
    public Object visitExitStatement(ExitStatementContext ctx) {
        mm.return_();
        return null;
    }

    @Override
    public Object visitPrintStatement(PrintStatementContext ctx) {
        var v = visit(ctx.parExpression());
        mm.var(System.class).field("out").invoke("print", v);
        return null;
    }

    @Override
    public Object visitPrintlnStatement(PrintlnStatementContext ctx) {
        var v = visit(ctx.parExpression());
        mm.var(System.class).field("out").invoke("println", v);
        return null;
    }

    @Override
    public Object visitReadInt(ReadIntContext ctx) {
        return mm.var(Integer.class).invoke("parseInt", scanner().invoke("nextLine"));
    }

    @Override
    public Object visitReadDouble(ReadDoubleContext ctx) {
        return mm.var(Double.class).invoke("parseDouble", scanner().invoke("nextLine"));
    }

    @Override
    public Object visitReadLine(ReadLineContext ctx) {
        return scanner().invoke("nextLine");
    }

    private Variable scanner() {
        Label cont = mm.label();
        scanner.ifNe(null, cont);
        scanner.set(mm.new_(Scanner.class, mm.var(System.class).field("in")));
        cont.here();
        return scanner;
    }

    @Override
    public Object visitDeclaration(DeclarationContext ctx) {
        String name = ctx.Identifier().getText();
        if (scope.findLocal(name) != null) {
            throw new IllegalArgumentException("Variable is already declared: " + name);
        }
        var decl = (Variable) visit(ctx.type());
        decl.name(name);
        var expr = ctx.expression();
        if (expr != null) {
            assign(decl, visit(expr));
        }
        scope.locals.put(name, decl);
        return null;
    }

    @Override
    public Object visitAssignment(AssignmentContext ctx) {
        String name = ctx.Identifier().getText();
        var local = scope.findLocal(name);
        if (local == null) {
            throw new IllegalArgumentException("Cannot assign to undeclared variable: " + name);
        }
        local.set(visit(ctx.expression()));
        return null;
    }

    private void assign(Variable target, Object value) {
        Class targetType = target.classType();

        Class valueType;
        if (value instanceof Variable) {
            valueType = ((Variable) value).classType();
        } else {
            valueType = value.getClass();
        }

        if (targetType != valueType) convert: {
            if (targetType == String.class) {
                value = target.invoke("valueOf", value);
                break convert;
            } else if (isNumber(targetType) && isNumber(valueType)) {
                break convert;
            }
            throw new IllegalArgumentException
                ("Cannot convert type " + valueType + " to " + targetType);
        }

        target.set(value);
    }

    private static boolean isNumber(Class type) {
        return type == double.class || type == int.class || Number.class.isAssignableFrom(type);
    }

    @Override
    public Object visitParExpression(ParExpressionContext ctx) {
        return visit(ctx.getChild(1)); // 0: '(',  1: <expr>,  2: ')'
    }

    @Override
    public Object visitVariableReference(VariableReferenceContext ctx) {
        String name = ctx.Identifier().getText();
        var local = scope.findLocal(name);
        if (local == null) {
            throw new IllegalArgumentException("Cannot read from undeclared variable: " + name);
        }
        return local;
    }

    @Override
    public Object visitToString(ToStringContext ctx) {
        return mm.var(String.class).invoke("valueOf", visit(ctx.parExpression()));
    }

    @Override
    public Object visitBinaryOperation(BinaryOperationContext ctx) {
        Variable left;
        {
            Object leftResult = visit(ctx.left);
            if (leftResult instanceof Variable) {
                left = (Variable) leftResult;
            } else {
                // Don't bother with constant folding or flipping things around.
                left = mm.var(leftResult.getClass()).set(leftResult);
            }
        }

        var right = visit(ctx.right);

        int op = ctx.op.getType();

        switch (op) {
        case MUL: return left.mul(right);
        case DIV: return left.div(right);
        case MOD: return left.rem(right);
        case MINUS: return left.sub(right);

        case LT: return left.lt(right);
        case GT: return left.gt(right);
        case LTEQ: return left.le(right);
        case GTEQ: return left.ge(right);

        case EQ: case NOTEQ:
            if (typeOf(left) == String.class && typeOf(right) == String.class) {
                var result = left.invoke("equals", right);
                if (op == NOTEQ) {
                    result = result.not();
                }
                return result;
            } else {
                return op == EQ ? left.eq(right) : left.ne(right);
            }

        case PLUS:
            if (typeOf(left) == String.class || typeOf(right) == String.class) {
                return mm.concat(left, right);
            } else {
                return left.add(right);
            }

            // Note: These aren't short-circuit operators, and they don't need to be.
        case AND: return left.and(right);
        case OR: return left.or(right);

        default:
            throw new AssertionError();
        }
    }

    private Class typeOf(Object value) {
        if (value instanceof Variable) {
            return ((Variable) value).classType();
        } else {
            return value.getClass();
        }
    }

    @Override
    public Object visitUnaryOperation(UnaryOperationContext ctx) {
        Variable value;
        {
            Object result = visit(ctx.expression());
            if (result instanceof Variable) {
                value = (Variable) result;
            } else {
                // Don't bother with constant folding.
                value = mm.var(result.getClass()).set(result);
            }
        }

        switch (ctx.op.getType()) {
        case MINUS: return value.neg();
        case NOT: return value.not();
        default:
            throw new AssertionError();
        }
    }

    @Override
    public Object visitIntType(IntTypeContext ctx) {
        return mm.var(int.class);
    }

    @Override
    public Object visitDoubleType(DoubleTypeContext ctx) {
        return mm.var(double.class);
    }

    @Override
    public Object visitBooleanType(BooleanTypeContext ctx) {
        return mm.var(boolean.class);
    }

    @Override
    public Object visitStringType(StringTypeContext ctx) {
        return mm.var(String.class);
    }

    @Override
    public Object visitInt(IntContext ctx) {
        return Integer.valueOf(ctx.getText());
    }

    @Override
    public Object visitFloat(FloatContext ctx) {
        return Double.valueOf(ctx.getText());
    }

    @Override
    public Object visitString(StringContext ctx) {
        String str = ctx.getText();
        return str.substring(1, str.length() - 1); // strip quotes
    }

    @Override
    public Object visitBoolean(BooleanContext ctx) {
        return mm.var(boolean.class).set(Boolean.valueOf(ctx.getText()));
    }

    private static class Scope {
        final Scope parent;
        final HashMap<String, Variable> locals = new HashMap<>();

        Label breakTarget;

        Scope(Scope parent) {
            this.parent = parent;
        }

        Variable findLocal(String name) {
            return findLocal(this, name);
        }

        static Variable findLocal(Scope scope, String name) {
            do {
                Variable var = scope.locals.get(name);
                if (var != null) {
                    return var;
                }
            } while ((scope = scope.parent) != null);
            return null;
        }

        Label breakOut() {
            return breakOut(this);
        }

        static Label breakOut(Scope scope) {
            do {
                if (scope.breakTarget != null) {
                    return scope.breakTarget;
                }
            } while ((scope = scope.parent) != null);
            return null;
        }
    }
}
