package SemanticAnalysis;

import AbstractSyntax.Definitions.*;
import AbstractSyntax.Expressions.*;
import AbstractSyntax.Program.*;
import AbstractSyntax.SizeParams.SizeParam;
import AbstractSyntax.Statements.*;
import AbstractSyntax.Types.*;
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
    private FuncDef rootFuncDef; //Reference to the root of the function definitions linked list

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

        this.rootFuncDef = program.func; //We store the top-level function list for later lookup (We use this when type checking function calls)

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
            //We handle built-in functions manually 
            //Check if this is a call to the built-in 'zeros' function
            if (funcCall.name.equals("zeros")) {
                //"zeros" must take EXACTLY 2 integer arguments, example; zeros(3, 4)
                if (funcCall.actualParameters.size() != 2) {
                    addError("Built-in function 'zeros' expects 2 integer arguments", getLineNumber(expr));
                    return null;
                }

                //We type check both arguments
                Type arg1 = checkExpr(funcCall.actualParameters.get(0), env);
                Type arg2 = checkExpr(funcCall.actualParameters.get(1), env);

                //Both arguments must be integers.
                if (!isIntType(arg1) || !isIntType(arg2)) {
                    addError("'zeros' arguments most be integers", getLineNumber(expr));

                    return null;
                }

                //Create a 2D tensor of type int with unknown sizes (null as the placeholders.)
                ArrayList<SizeParam> dims = new ArrayList<>();
                dims.add(null); //Placeholder for dimension 1
                dims.add(null); //Placeholder for dimension 2

                return new TensorType(new SimpleType(SimpleTypesEnum.INT), dims);
            }
            //Same structure as "zeros". Now for the built in "ones" function
            if (funcCall.name.equals("ones")) {
                //Handle built-in 'ones' function that creates a 2D tensor filled with ones
                //Check that exactly two arguments are provided
                if (funcCall.actualParameters.size() != 2) {
                    addError("Built-in function 'ones' expects 2 integer arguments ", getLineNumber(expr));
                    return null;
                }

                //We type check both arguments
                Type arg1 = checkExpr(funcCall.actualParameters.get(0), env);
                Type arg2 = checkExpr(funcCall.actualParameters.get(1), env);

                //Both arguments must be integers, example; ones(3, 4)
                if (!isIntType(arg1) || !isIntType(arg2)) {
                    addError("'ones' arguments must be integers", getLineNumber(expr));
                    return null;
                }

                //Return a 2D tensor of the ints
                ArrayList<SizeParam> dims = new ArrayList<>();
                dims.add(null); //Unknown actual size at typecheck time
                dims.add(null);

                return new TensorType(new SimpleType(SimpleTypesEnum.INT), dims);
            }

            //This function checks function types and count
            // Retrieve actual arguments from the function call
            List<Expr> actualArgs = funcCall.actualParameters;

            //We lookup the expected return type (we have already done this with env.lookup)
            //Now we need to manually fetch the function definition to get parameter info
            FuncDef calledFunc = null;
            FuncDef scan = rootFuncDef; // Or however you're storing function definitions
            while (scan != null) {
                if (scan.procname.equals(funcCall.name)) {
                    calledFunc = scan;
                    break;
                }
                scan = scan.nextFunc;
            }

            if (calledFunc == null) {
                addError("Internal error", getLineNumber(expr),
                    "Function '" + funcCall.name + "' not found in global environment.");
                return null;
            }

            List<Pair<Type, String>> formalParams = calledFunc.formalParams;

            //1. Check parameter count
            if (actualArgs.size() != formalParams.size()) {
                addError("Function call error", getLineNumber(expr),
                    "Function '" + funcCall.name + "' expects " + formalParams.size() +
                    " arguments, but got " + actualArgs.size());
                return null;
            }
            

            //2. Check argument types
            for (int i = 0; i < actualArgs.size(); i++) {
                Type expectedType = formalParams.get(i).elem1;
                Type actualType = checkExpr(actualArgs.get(i), env);
                if (!isCompatible(expectedType, actualType))  {
                    addError("Function call type mismatch", getLineNumber(expr),
                        "Argument " + (i + 1) + " to function '" + funcCall.name + "' expected " +
                         typeToString(expectedType) + " but got " + typeToString(actualType));
                    return null;
                }
            }
            return funcType;
        }
        
        //Type checks tensor element acess expressions; A[i][j]) for correctness.
        else if (expr instanceof TensorAccessExpr) {
            //Downcast the generic expression to a TensorAccessExpr
            TensorAccessExpr access = (TensorAccessExpr) expr;
            //We recursively type check the base expression, the tensor being accessed. Example; if its [i][j], this gets the type of "A"
            Type baseType = checkExpr(access.listExpr, env);
            //Check that the expression being indexed is actually a tensor
            if (!(baseType instanceof TensorType)){
                addError("Tensor acess error", getLineNumber(expr),
                    "Attempted to index a non-tensor expression of type: " + typeToString(baseType));
                return null;
            }

            
            //We can cast the baseType to TensorType since we've checked it above
            TensorType tensor = (TensorType) baseType;
            //Get the list of index expressions (the [i][j] part(s))
            List<Expr> indices = access.indices;
             //Check that number of indices matches the tensors number of dimensions
            if (indices.size() != tensor.dimensions.size()){
                addError("Tensor access error", getLineNumber(expr),
                    "Expected  " + tensor.dimensions.size() + " indices, but we got " + indices.size());
                return null;
            }
            //We check that each index expression is of type int
            for (Expr indexExpr : indices){
                Type indexType = checkExpr(indexExpr, env);
                if (!isIntType(indexType)) {
                    addError("Tensor index type error", getLineNumber(expr),
                        "Tensor indices must be of type int, but  got: " + typeToString(indexType));
                    return null;
                }
            }
            
        //If everything checks out, then we return the type of the element(s) inside the tensor. For example, if the tensor holds type "double", the result type is "double"
        return tensor.componentType;
        }
        return null;
        }  
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

        //SimpleType; check if same kind, for example; INT == INT
        if (t1 instanceof SimpleType && t2 instanceof SimpleType) {
            SimpleType s1 = (SimpleType) t1;
            SimpleType s2 = (SimpleType) t2;
            return s1.type == s2.type;
        }
        //TensorType; check structure, not actual SizeParams
        if (t1 instanceof TensorType && t2 instanceof TensorType){
            TensorType tensor1 = (TensorType) t1;
            TensorType tensor2 = (TensorType) t2;

        //We recursilvely check element type
        if (!isCompatible(tensor1.componentType, tensor2.componentType)) return false;

        //Same number of dimensions required
        if (tensor1.dimensions.size() != tensor2.dimensions.size())return false;

        return true;

    }
        //Other combinations are not compatible
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
     /* 
    //Uncomment when public int line field to core AST nodes, Expr, Stmt...
    private int getLineNumber(Object node) {
    if (node instanceof Expr) {
        return ((Expr) node).line;
    } else if (node instanceof Stmt) {
        return ((Stmt) node).line;
    } else if (node instanceof FuncDef) {
        return ((FuncDef) node).line;
    } else {
        return 0;
    }
    */
}