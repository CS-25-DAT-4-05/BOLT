package SemanticAnalysis;

import AbstractSyntax.Definitions.*;
import AbstractSyntax.Expressions.*;
import AbstractSyntax.Program.*;
import AbstractSyntax.SizeParams.*;
import AbstractSyntax.Statements.*;
import AbstractSyntax.Types.*;
import Lib.Pair;
import java.util.*;

//import javax.management.openmbean.SimpleType;

/*
TODO::
Add more built-in functions for tensor operations
 */

public class TypeChecker {
    private final List<TypeError> errors = new ArrayList<>();
    private TypeEnvironment globalEnv;
    private Map<String, FuncDef> functionDefinitions = new HashMap<>(); // stores function definitions

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

    public TypeEnvironment getGlobalEnvironment() {
        return globalEnv;
    }

    private TypeEnvironment buildFunctionEnvironment(Prog program) {
        TypeEnvironment env = new TypeEnvironment(); // Global scope
        FuncDef current = program.func;

        while (current != null) {
            if (env.lookup(current.procname) != null) {
                addError("Duplicate function definition", 0, current.procname);
            } else {
                // Store the return type in environment
                env.bind(current.procname, current.returnType);

                // also store the complete function definition for parameter checking
                functionDefinitions.put(current.procname, current);
            }
            current = current.nextFunc;
        }

        return env;
    }

    private void checkDefinitions(FuncDef funcDef) {
        if (funcDef == null) return;

        // create local environment for this function
        TypeEnvironment localEnv = globalEnv.copy();

        // adds parameters to local environment
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
                // Simple assignment: x = 5
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
                // Tensor assignment: mat[0, 1] = 99
                Type targetType = checkExpr(assign.target, env);
                Type exprType = checkExpr(assign.expr, env);

                if (targetType != null && exprType != null) {
                    if (!isCompatible(targetType, exprType)) {
                        addError("Type mismatch in tensor assignment", line,
                                "Cannot assign " + typeToString(exprType) +
                                        " to tensor element of type " + typeToString(targetType));
                    }
                }
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

            // check that thread dimensions are valid
            for (Pair<String, SizeParam> dim : defer.dim) {
                String threadVar = dim.elem1;
                SizeParam size = dim.elem2;

                // Validate size parameter
                if (size instanceof SPInt) {
                    SPInt intSize = (SPInt) size;
                    if (intSize.value <= 0) {
                        addError("Invalid thread dimension", line,
                                "Thread dimension '" + threadVar + "' must be positive, got " + intSize.value);
                    }
                } else if (size instanceof SPIdent) {
                    SPIdent identSize = (SPIdent) size;
                    Type sizeType = env.lookup(identSize.ident);
                    if (sizeType == null) {
                        addError("Undefined size parameter", line,
                                "Size parameter '" + identSize.ident + "' is not declared");
                    } else if (!isIntType(sizeType)) {
                        addError("Invalid size parameter type", line,
                                "Size parameter '" + identSize.ident + "' must be int, got " + typeToString(sizeType));
                    }
                }
            }

            // Create new scope with thread variables
            TypeEnvironment deferEnv = env.newScope();
            for (Pair<String, SizeParam> dim : defer.dim) {
                String threadVar = dim.elem1;
                deferEnv.bind(threadVar, new SimpleType(SimpleTypesEnum.INT));
            }

            // Check defer body in new scope
            checkStmt(defer.stmt, deferEnv, functionContext);
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

        // Handles TensorDefExpr (tensor literals like {1, 2, 3})
        else if (expr instanceof TensorDefExpr) {
            return checkTensorDefExpr((TensorDefExpr) expr, env);
        }

        // Handles TensorAccessExpr (tensor access like myTensor[0, 1])
        else if (expr instanceof TensorAccessExpr) {
            return checkTensorAccessExpr((TensorAccessExpr) expr, env);
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

            // Handle built-in functions FIRST
            if (funcCall.name.equals("zeros")) {
                // "zeros" must take EXACTLY 2 integer arguments
                if (funcCall.actualParameters.size() != 2) {
                    addError("Built-in function 'zeros' expects 2 integer arguments", getLineNumber(expr));
                    return null;
                }

                // Type check both arguments
                Type arg1 = checkExpr(funcCall.actualParameters.get(0), env);
                Type arg2 = checkExpr(funcCall.actualParameters.get(1), env);

                // Both arguments must be integers
                if (!isIntType(arg1) || !isIntType(arg2)) {
                    addError("'zeros' arguments must be integers", getLineNumber(expr));
                    return null;
                }

                // Create a 2D tensor of type int with unknown sizes
                ArrayList<SizeParam> dims = new ArrayList<>();
                dims.add(null); // Placeholder for dimension 1
                dims.add(null); // Placeholder for dimension 2

                return new TensorType(new SimpleType(SimpleTypesEnum.INT), dims);
            }

            if (funcCall.name.equals("ones")) {
                // Handle built-in 'ones' function that creates a 2D tensor filled with ones
                if (funcCall.actualParameters.size() != 2) {
                    addError("Built-in function 'ones' expects 2 integer arguments", getLineNumber(expr));
                    return null;
                }

                // Type check both arguments
                Type arg1 = checkExpr(funcCall.actualParameters.get(0), env);
                Type arg2 = checkExpr(funcCall.actualParameters.get(1), env);

                // Both arguments must be integers
                if (!isIntType(arg1) || !isIntType(arg2)) {
                    addError("'ones' arguments must be integers", getLineNumber(expr));
                    return null;
                }

                // Return a 2D tensor of ints
                ArrayList<SizeParam> dims = new ArrayList<>();
                dims.add(null); // Unknown actual size at typecheck time
                dims.add(null);

                return new TensorType(new SimpleType(SimpleTypesEnum.INT), dims);
            }

            // Look up function return type for regular functions
            Type funcType = env.lookup(funcCall.name);
            if (funcType == null) {
                addError("Undefined function", getLineNumber(expr),
                        "Function '" + funcCall.name + "' is not declared");
                return null;
            }

            // Look up function definition for parameter checking
            FuncDef funcDef = functionDefinitions.get(funcCall.name);
            if (funcDef == null) {
                // Could be a built-in function - for now just return the type
                // TODO: Add built-in function parameter checking later
                return funcType;
            }

            // Check argument count
            int expectedParams = funcDef.formalParams.size();
            int actualParams = funcCall.actualParameters.size();

            if (expectedParams != actualParams) {
                addError("Wrong number of arguments", getLineNumber(expr),
                        "Function '" + funcCall.name + "' expects " + expectedParams +
                                " arguments but got " + actualParams);
                return null;
            }

            // Check each parameter type
            for (int i = 0; i < expectedParams; i++) {
                Type expectedType = funcDef.formalParams.get(i).elem1;
                Type actualType = checkExpr(funcCall.actualParameters.get(i), env);

                if (actualType != null && !isCompatible(expectedType, actualType)) {
                    addError("Argument type mismatch", getLineNumber(expr),
                            "Parameter " + (i + 1) + " of function '" + funcCall.name +
                                    "' expects " + typeToString(expectedType) +
                                    " but got " + typeToString(actualType));
                    return null;
                }
            }

            return funcType;
        }

        return null;
    }

    // Type check tensor literals {1, 2, 3} or {{1, 2}, {3, 4}}
    private Type checkTensorDefExpr(TensorDefExpr tensorExpr, TypeEnvironment env) {
        if (tensorExpr.exprs == null || tensorExpr.exprs.isEmpty()) {
            addError("Empty tensor literal", getLineNumber(tensorExpr), "Tensor cannot be empty");
            return null;
        }

        // Check the type of the first element
        Type firstElementType = checkExpr(tensorExpr.exprs.get(0), env);
        if (firstElementType == null) return null;

        // Check all elements have the same type
        for (int i = 1; i < tensorExpr.exprs.size(); i++) {
            Type elementType = checkExpr(tensorExpr.exprs.get(i), env);
            if (!isCompatible(firstElementType, elementType)) {
                addError("Inconsistent tensor element types", getLineNumber(tensorExpr),
                        "Expected " + typeToString(firstElementType) +
                                " but got " + typeToString(elementType) + " at index " + i);
                return null;
            }
        }

        // Determine tensor dimensions
        ArrayList<SizeParam> dimensions = new ArrayList<>();
        dimensions.add(new SPInt(tensorExpr.exprs.size())); // First dimension is the count

        // If elements are tensors themselves, add their dimensions
        if (firstElementType instanceof TensorType) {
            TensorType innerTensor = (TensorType) firstElementType;
            dimensions.addAll(innerTensor.dimensions);
            return new TensorType(innerTensor.componentType, dimensions);
        }
        // If elements are simple types, this is a 1D tensor
        else if (firstElementType instanceof SimpleType) {
            return new TensorType((SimpleType) firstElementType, dimensions);
        }

        addError("Invalid tensor element type", getLineNumber(tensorExpr),
                "Tensor elements must be simple types or other tensors");
        return null;
    }

    // Type check tensor access myTensor[0] or myMatrix[1, 2]
    private Type checkTensorAccessExpr(TensorAccessExpr accessExpr, TypeEnvironment env) {
        // Check the base expression (hvad vi indexer ind i)
        Type baseType = checkExpr(accessExpr.listExpr, env);
        if (baseType == null) return null;

        if (!(baseType instanceof TensorType)) {
            addError("Invalid tensor access", getLineNumber(accessExpr),
                    "Cannot index into non-tensor type " + typeToString(baseType));
            return null;
        }

        TensorType tensorType = (TensorType) baseType;

        // Check that all indices are integers
        for (Expr indexExpr : accessExpr.indices) {
            Type indexType = checkExpr(indexExpr, env);
            if (!isIntType(indexType)) {
                addError("Invalid tensor index", getLineNumber(accessExpr),
                        "Tensor indices must be integers, got " + typeToString(indexType));
                return null;
            }
        }

        // Check dimension count matches
        if (accessExpr.indices.size() != tensorType.dimensions.size()) {
            addError("Wrong number of tensor indices", getLineNumber(accessExpr),
                    "Tensor has " + tensorType.dimensions.size() + " dimensions but got " +
                            accessExpr.indices.size() + " indices");
            return null;
        }

        // Accessing all dimensions returns the component type
        return tensorType.componentType;
    }

    private Type checkBinaryOperation(Binoperator op, Type leftType, Type rightType, int line) {
        if (leftType == null || rightType == null) return null;

        switch (op) {
            case ADD:
            case MINUS:
            case TIMES:
            case DIV:
                if (isNumericType(leftType) && isNumericType(rightType)) {
                    // Return the "wider" type (double if either is double, int otherwise)
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

        if (t1 instanceof TensorType && t2 instanceof TensorType) {
            TensorType tensor1 = (TensorType) t1;
            TensorType tensor2 = (TensorType) t2;

            // Check component types match
            if (!isCompatible(tensor1.componentType, tensor2.componentType)) {
                return false;
            }

            // Check dimensions match
            if (tensor1.dimensions.size() != tensor2.dimensions.size()) {
                return false;
            }

            // Check each dimension size matches
            for (int i = 0; i < tensor1.dimensions.size(); i++) {
                SizeParam dim1 = tensor1.dimensions.get(i);
                SizeParam dim2 = tensor2.dimensions.get(i);

                // For now, only check if both are integer literals
                if (dim1 instanceof SPInt && dim2 instanceof SPInt) {
                    SPInt spInt1 = (SPInt) dim1;
                    SPInt spInt2 = (SPInt) dim2;
                    if (spInt1.value != spInt2.value) {
                        return false;
                    }
                }
                // TODO: Handle parametric dimensions (SPIdent)
            }

            return true;
        }

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
        // Note: void is represented as BOOL in your current implementation
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
    // NOT USED, ded code:
    //This function maps the formal parameters to actual arguments (used for the parameterised tensor dimensions.)
    private Map<String, Integer> buildYMapping(List<Pair<Type, String>> formalParams, List<Expr> actualArgs, TypeEnvironment env) {
        //We create a mapping from the formal parameter names (like "m", "n") to the actual constant integer values that are passed during the function call
        Map<String, Integer> yMap = new HashMap<>();
        //Iterate over each formal parameter to match it with the coresponding actual argument
        for (int i = 0; i < formalParams.size(); i++) {
            String formalName = formalParams.get(i).elem2;
            Expr actualExpr = actualArgs.get(i);

            //Evaluate the actual expression if it's a known constant integer, example;  3, 4...
            if (actualExpr instanceof IntVal) {
                int value = ((IntVal) actualExpr).value;
                yMap.put(formalName, value);
            } else  {
                addError("Cannot resolve y-mapping for non-constant argument", getLineNumber(actualExpr),
                    "Parameter '" + formalName + "' must be a constant intager");
            }
    }
    //We return our finished mapping of the formal dimension names to the actual integer values 
    return yMap;
    }

    private int getLineNumber(Object node) {
        // TODO: Add line number extraction from AST nodes
        // For now, return 0 as placeholder
        return 0;
    }
}