package SemanticAnalysis;

import AbstractSyntax.Statements.*;
import AbstractSyntax.Expressions.*;
import AbstractSyntax.Types.*;
import AbstractSyntax.Definitions.*;
import AbstractSyntax.Program.*;
import Lib.Pair;
import java.util.*;

/*
TODO::
Function call parameter checking (verify argument count and types)
Add more built-in functions for tensor operations
Implement the y-mapping system for parameterized tensors from formal type rules
 */


public class TypeChecker {
    private final List<TypeError> errors = new ArrayList<>();
    private TypeEnvironment globalEnv;

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<String> getErrors() {
        List<String> errorMessages = new ArrayList<>();
        for (TypeError error : errors) {
            errorMessages.add(error.toString());
        }
        return errorMessages;
    }

    public void check(Prog program) {
        if (program == null) {
            addError("Program is null", 0);
            return;
        }

        // 1: build global function environment
        globalEnv = buildFunctionEnvironment(program);

        // 2: check all function definitions
        checkDefinitions(program.func);

        if (hasErrors()) {
            throw new RuntimeException("Type checking failed with " + errors.size() + " error(s)");
        }
    }

    private TypeEnvironment buildFunctionEnvironment(Prog program) {
        TypeEnvironment env = new TypeEnvironment(); // Global scope
        FuncDef current = program.func;

        while (current != null) {
            if (env.lookup(current.procname) != null) {
                addError("Duplicate function definition", 0, current.procname);
            } else {
                // Simplified function type representation, not complete function type
                env.bind(current.procname, current.returnType);
            }
            current = current.nextFunc;
        }

        return env;
    }

    private void checkDefinitions(FuncDef funcDef) {
        if (funcDef == null) return;

        // Create local environment for this function
        TypeEnvironment localEnv = globalEnv.copy();

        // Add parameters to local environment
        for (Pair<Type, String> param : funcDef.formalParams) {
            if (localEnv.isLocal(param.elem2)) {
                addError("Duplicate parameter name", 0, param.elem2);
            } else {
                localEnv.bind(param.elem2, param.elem1);
            }
        }

        // Check function body
        checkStmt(funcDef.funcBody, localEnv, funcDef.procname);

        // Check return expression
        if (funcDef.returnExpr != null) {
            Type returnType = checkExpr(funcDef.returnExpr, localEnv);
            if (returnType != null && !isCompatible(returnType, funcDef.returnType)) {
                addError("Return type mismatch", 0,
                        "Expected " + typeToString(funcDef.returnType) +
                                " but got " + typeToString(returnType) +
                                " in function '" + funcDef.procname + "'");
            }
        } else if (!isVoidType(funcDef.returnType)) {
            addError("Missing return expression", 0,
                    "Non-void function '" + funcDef.procname + "' must have a return expression");
        }

        // Continue with next function
        checkDefinitions(funcDef.nextFunc);
    }

    private void checkStmt(Stmt stmt, TypeEnvironment env, String functionContext) {
        if (stmt == null) return;

        if (stmt instanceof Declaration) {
            Declaration decl = (Declaration) stmt;
            int line = getLineNumber(stmt);

            if (env.isLocal(decl.ident)) {
                addError("Variable redeclaration", line,
                        "Variable '" + decl.ident + "' is already declared in this scope");
                return;
            }

            if (decl.expr != null) {
                Type exprType = checkExpr(decl.expr, env);
                if (exprType != null && !isCompatible(decl.t, exprType)) {
                    addError("Type mismatch in declaration", line,
                            "Variable '" + decl.ident + "' declared as " + typeToString(decl.t) +
                                    " but initialized with " + typeToString(exprType));
                }
            }

            env.bind(decl.ident, decl.t);
        }

        else if (stmt instanceof Assign) {
            Assign assign = (Assign) stmt;
            int line = getLineNumber(stmt);

            if (assign.isSimpleAssignment()) {
                String identifier = assign.getIdentifier();
                Type varType = env.lookup(identifier);

                if (varType == null) {
                    addError("Undefined variable", line,
                            "Cannot assign to undeclared variable '" + identifier + "'");
                    return;
                }

                Type exprType = checkExpr(assign.expr, env);
                if (exprType != null && !isCompatible(varType, exprType)) {
                    addError("Type mismatch in assignment", line,
                            "Variable '" + identifier + "' has type " + typeToString(varType) +
                                    " but assigned value of type " + typeToString(exprType));
                }
            } else {
                // TODO: Handle tensor assignments
                Type targetType = checkExpr(assign.target, env);
                Type exprType = checkExpr(assign.expr, env);
                addError("Tensor assignment not yet implemented", line, "");
            }
        }

        else if (stmt instanceof Comp) {
            Comp comp = (Comp) stmt;
            checkStmt(comp.stmt1, env, functionContext);
            checkStmt(comp.stmt2, env, functionContext);
        }

        else if (stmt instanceof If) {
            If ifStmt = (If) stmt;
            int line = getLineNumber(stmt);

            Type condType = checkExpr(ifStmt.cond, env);
            if (condType != null && !isBoolType(condType)) {
                addError("Invalid if condition", line,
                        "If condition must be of type bool, got " + typeToString(condType));
            }

            checkStmt(ifStmt.then, env.newScope(), functionContext);
            if (ifStmt.els != null) {
                checkStmt(ifStmt.els, env.newScope(), functionContext);
            }
        }

        else if (stmt instanceof While) {
            While whileStmt = (While) stmt;
            int line = getLineNumber(stmt);

            Type condType = checkExpr(whileStmt.cond, env);
            if (condType != null && !isBoolType(condType)) {
                addError("Invalid while condition", line,
                        "While condition must be of type bool, got " + typeToString(condType));
            }

            checkStmt(whileStmt.stmt, env.newScope(), functionContext);
        }

        else if (stmt instanceof Defer) {
            Defer defer = (Defer) stmt;
            int line = getLineNumber(stmt);

            // TODO: Add defer-specific type checking for GPU blocks
            addError("Defer blocks not yet implemented", line, "GPU defer blocks need special handling");
            checkStmt(defer.stmt, env.newScope(), functionContext);
        }
    }

    private Type checkExpr(Expr expr, TypeEnvironment env) {
        if (expr == null) return null;

        if (expr instanceof Ident) {
            Ident ident = (Ident) expr;
            Type type = env.lookup(ident.name);
            if (type == null) {
                addError("Undefined variable", getLineNumber(expr),
                        "Variable '" + ident.name + "' is not declared");
            }
            return type;
        }

        else if (expr instanceof IntVal) {
            return new SimpleType(SimpleTypesEnum.INT);
        }

        else if (expr instanceof DoubleVal) {
            return new SimpleType(SimpleTypesEnum.DOUBLE);
        }

        else if (expr instanceof BoolVal) {
            return new SimpleType(SimpleTypesEnum.BOOL);
        }

        else if (expr instanceof CharVal) {
            return new SimpleType(SimpleTypesEnum.CHAR);
        }

        else if (expr instanceof BinExpr) {
            BinExpr binExpr = (BinExpr) expr;
            Type leftType = checkExpr(binExpr.left, env);
            Type rightType = checkExpr(binExpr.right, env);

            return checkBinaryOperation(binExpr.op, leftType, rightType, getLineNumber(expr));
        }

        else if (expr instanceof UnExpr) {
            UnExpr unExpr = (UnExpr) expr;
            Type operandType = checkExpr(unExpr.expr, env);

            return checkUnaryOperation(unExpr.op, operandType, getLineNumber(expr));
        }

        else if (expr instanceof FuncCallExpr) {
            FuncCallExpr funcCall = (FuncCallExpr) expr;
            Type funcType = env.lookup(funcCall.name);

            if (funcType == null) {
                addError("Undefined function", getLineNumber(expr),
                        "Function '" + funcCall.name + "' is not declared");
                return null;
            }

            // TODO: Check parameter types and count
            return funcType;
        }

        // TODO: Add other expression types (TensorDefExpr, TensorAccessExpr, etc.)

        return null;
    }

    private Type checkBinaryOperation(Binoperator op, Type leftType, Type rightType, int line) {
        if (leftType == null || rightType == null) return null;

        switch (op) {
            case ADD:
            case MINUS:
            case TIMES:
            case DIV:
                if (isNumericType(leftType) && isNumericType(rightType)) {
                    // OBS: Return the "wider" type (double if either is double, int otherwise)
                    if (isDoubleType(leftType) || isDoubleType(rightType)) {
                        return new SimpleType(SimpleTypesEnum.DOUBLE);
                    }
                    return new SimpleType(SimpleTypesEnum.INT);
                }
                addError("Invalid arithmetic operation", line,
                        "Operator '" + op + "' requires numeric operands, got " +
                                typeToString(leftType) + " and " + typeToString(rightType));
                return null;

            case MODULO:
                if (isIntType(leftType) && isIntType(rightType)) {
                    return new SimpleType(SimpleTypesEnum.INT);
                }
                addError("Invalid modulo operation", line,
                        "Modulo operator requires int operands, got " +
                                typeToString(leftType) + " and " + typeToString(rightType));
                return null;

            case EQUAL:
            case NEQUAL:
                if (isCompatible(leftType, rightType)) {
                    return new SimpleType(SimpleTypesEnum.BOOL);
                }
                addError("Invalid equality comparison", line,
                        "Cannot compare " + typeToString(leftType) + " with " + typeToString(rightType));
                return null;

            case LT:
            case GT:
            case LEQ:
            case GEQ:
                if (isNumericType(leftType) && isNumericType(rightType)) {
                    return new SimpleType(SimpleTypesEnum.BOOL);
                }
                addError("Invalid comparison", line,
                        "Comparison operators require numeric operands, got " +
                                typeToString(leftType) + " and " + typeToString(rightType));
                return null;

            case AND:
            case OR:
                if (isBoolType(leftType) && isBoolType(rightType)) {
                    return new SimpleType(SimpleTypesEnum.BOOL);
                }
                addError("Invalid logical operation", line,
                        "Logical operators require bool operands, got " +
                                typeToString(leftType) + " and " + typeToString(rightType));
                return null;

            default:
                addError("Unknown binary operator", line, "Operator: " + op);
                return null;
        }
    }

    private Type checkUnaryOperation(Unaryoperator op, Type operandType, int line) {
        if (operandType == null) return null;

        switch (op) {
            case NEG:
                if (isNumericType(operandType)) {
                    return operandType;
                }
                addError("Invalid negation", line,
                        "Negation requires numeric operand, got " + typeToString(operandType));
                return null;

            case NOT:
                if (isBoolType(operandType)) {
                    return new SimpleType(SimpleTypesEnum.BOOL);
                }
                addError("Invalid logical NOT", line,
                        "Logical NOT requires bool operand, got " + typeToString(operandType));
                return null;

            default:
                addError("Unknown unary operator", line, "Operator: " + op);
                return null;
        }
    }

    // Helper methods
    private void addError(String message, int line) {
        errors.add(new TypeError(message, line, ""));
    }

    private void addError(String message, int line, String details) {
        errors.add(new TypeError(message, line, details));
    }

    private boolean isCompatible(Type t1, Type t2) {
        if (t1 == null || t2 == null) return false;

        if (t1 instanceof SimpleType && t2 instanceof SimpleType) {
            SimpleType s1 = (SimpleType) t1;
            SimpleType s2 = (SimpleType) t2;
            return s1.type == s2.type;
        }

        // TODO: Add tensor type compatibility
        return false;
    }

    private boolean isNumericType(Type type) {
        return isIntType(type) || isDoubleType(type);
    }

    private boolean isIntType(Type type) {
        return type instanceof SimpleType &&
                ((SimpleType) type).type == SimpleTypesEnum.INT;
    }

    private boolean isDoubleType(Type type) {
        return type instanceof SimpleType &&
                ((SimpleType) type).type == SimpleTypesEnum.DOUBLE;
    }

    private boolean isBoolType(Type type) {
        return type instanceof SimpleType &&
                ((SimpleType) type).type == SimpleTypesEnum.BOOL;
    }

    private boolean isVoidType(Type type) {
        // Er void ikke Bool?
        return type instanceof SimpleType &&
                ((SimpleType) type).type == SimpleTypesEnum.BOOL;
    }

    private String typeToString(Type type) {
        if (type == null) return "null";

        if (type instanceof SimpleType) {
            SimpleType simpleType = (SimpleType) type;
            switch (simpleType.type) {
                case INT: return "int";
                case DOUBLE: return "double";
                case BOOL: return "bool";
                case CHAR: return "char";
                default: return "unknown";
            }
        }

        if (type instanceof TensorType) {
            TensorType tensorType = (TensorType) type;
            StringBuilder sb = new StringBuilder();

            if (tensorType.isVector()) {
                sb.append("vector[");
            } else if (tensorType.isMatrix()) {
                sb.append("matrix[");
            } else {
                sb.append("tensor[");
            }

            sb.append(typeToString(tensorType.componentType));
            // TODO: Add dimension information
            sb.append("]");
            return sb.toString();
        }

        return type.getClass().getSimpleName();
    }

    private int getLineNumber(Object node) {
        // TODO: Add line number extraction from AST nodes
        // For now, return 0 as placeholder
        return 0;
    }
}