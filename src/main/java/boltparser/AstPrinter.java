// AstPrinter.java
package boltparser;

import AbstractSyntax.Statements.*;
import AbstractSyntax.Expressions.*;
import AbstractSyntax.Types.*;
import AbstractSyntax.SizeParams.*;
import AbstractSyntax.Definitions.*;
import AbstractSyntax.Program.*;
import java.util.ArrayList;
import Lib.Pair;

/**
 * Pretty printer for BOLT Abstract Syntax Trees.
 * Supports printing complete Prog structures with chained function definitions.
 */
public class AstPrinter {
    private int indentLevel = 0;
    private StringBuilder sb = new StringBuilder();

    private void indent() {
        indentLevel += 2;
    }

    private void dedent() {
        indentLevel = Math.max(0, indentLevel - 2);
    }

    private void appendLine(String text) {
        for (int i = 0; i < indentLevel; i++) {
            sb.append(' ');
        }
        sb.append(text).append('\n');
    }

    /**
     * Print a complete BOLT program.
     * @param prog The program to print
     * @return Formatted string representation
     */
    public String printProgram(Prog prog) {
        sb = new StringBuilder();
        printProg(prog);
        return sb.toString();
    }

    /**
     * Legacy method for printing individual statements.
     * Kept for backward compatibility.
     * @param stmt The statement to print
     * @return Formatted string representation
     */
    public String print(Stmt stmt) {
        sb = new StringBuilder();
        printStmt(stmt);
        return sb.toString();
    }

    private void printProg(Prog prog) {
        if (prog == null) {
            appendLine("NULL PROGRAM");
            return;
        }

        appendLine("PROGRAM");
        indent();
        if (prog.func == null) {
            appendLine("No functions defined");
        } else {
            appendLine("Function chain:");
            indent();
            printFuncDef(prog.func);
            dedent();
        }
        dedent();
    }

    private void printFuncDef(FuncDef func) {
        if (func == null) {
            appendLine("NULL FUNCTION");
            return;
        }

        appendLine("FUNCTION DEFINITION: " + func.procname);
        indent();

        appendLine("Return type: " + printType(func.returnType));

        appendLine("Parameters:");
        indent();
        if (func.formalParams != null && !func.formalParams.isEmpty()) {
            for (Pair<Type, String> param : func.formalParams) {
                appendLine(printType(param.elem1) + " " + param.elem2);
            }
        } else {
            appendLine("None");
        }
        dedent();

        appendLine("Body:");
        indent();
        printStmt(func.funcBody);
        dedent();

        appendLine("Return expression:");
        indent();
        if (func.returnExpr != null) {
            printExpr(func.returnExpr);
        } else {
            appendLine("None (void function)");
        }
        dedent();

        dedent();

        // Print the next function in the chain
        if (func.nextFunc != null) {
            appendLine(""); // Empty line for readability
            printFuncDef(func.nextFunc);
        }
    }

    private void printStmt(Stmt stmt) {
        if (stmt == null) {
            appendLine("NULL STATEMENT");
            return;
        }

        if (stmt instanceof Comp) {
            Comp comp = (Comp) stmt;
            appendLine("COMPOUND STATEMENT");
            indent();
            appendLine("Statement 1:");
            indent();
            printStmt(comp.stmt1);
            dedent();
            appendLine("Statement 2:");
            indent();
            printStmt(comp.stmt2);
            dedent();
            dedent();
        } else if (stmt instanceof Declaration) {
            Declaration decl = (Declaration) stmt;
            appendLine("DECLARATION: " + decl.ident);
            indent();
            appendLine("Type: " + printType(decl.t));
            if (decl.expr != null) {
                appendLine("Initializer:");
                indent();
                printExpr(decl.expr);
                dedent();
            }
            dedent();
        } else if (stmt instanceof Assign) {
            Assign assign = (Assign) stmt;
            if (assign.isSimpleAssignment()) {
                appendLine("ASSIGNMENT: " + assign.getIdentifier());
            } else {
                appendLine("TENSOR ASSIGNMENT");
                indent();
                appendLine("Target:");
                indent();
                printExpr(assign.target);
                dedent();
                dedent();
            }
            indent();
            appendLine("Value:");
            indent();
            printExpr(assign.expr);
            dedent();
            dedent();
        } else if (stmt instanceof If) {
            If ifStmt = (If) stmt;
            appendLine("IF STATEMENT");
            indent();
            appendLine("Condition:");
            indent();
            printExpr(ifStmt.cond);
            dedent();
            appendLine("Then branch:");
            indent();
            printStmt(ifStmt.then);
            dedent();
            if (ifStmt.els != null) {
                appendLine("Else branch:");
                indent();
                printStmt(ifStmt.els);
                dedent();
            }
            dedent();
        } else if (stmt instanceof While) {
            While whileStmt = (While) stmt;
            appendLine("WHILE STATEMENT");
            indent();
            appendLine("Condition:");
            indent();
            printExpr(whileStmt.cond);
            dedent();
            appendLine("Body:");
            indent();
            printStmt(whileStmt.stmt);
            dedent();
            dedent();
        } else if (stmt instanceof Defer) {
            Defer defer = (Defer) stmt;
            appendLine("DEFER BLOCK");
            indent();
            if (defer.dim != null && !defer.dim.isEmpty()) {
                appendLine("Thread dimensions:");
                indent();
                for (Pair<String, SizeParam> dim : defer.dim) {
                    appendLine(dim.elem1 + ": " + printSizeParam(dim.elem2));
                }
                dedent();
            }
            appendLine("Body:");
            indent();
            printStmt(defer.stmt);
            dedent();
            dedent();
        } else {
            appendLine("UNKNOWN STATEMENT TYPE: " + stmt.getClass().getName());
        }
    }

    private void printExpr(Expr expr) {
        if (expr == null) {
            appendLine("NULL EXPRESSION");
            return;
        }

        if (expr instanceof BinExpr) {
            BinExpr binExpr = (BinExpr) expr;
            appendLine("BINARY EXPRESSION: " + binExpr.op);
            indent();
            appendLine("Left:");
            indent();
            printExpr(binExpr.left);
            dedent();
            appendLine("Right:");
            indent();
            printExpr(binExpr.right);
            dedent();
            dedent();
        } else if (expr instanceof UnExpr) {
            UnExpr unExpr = (UnExpr) expr;
            appendLine("UNARY EXPRESSION: " + unExpr.op);
            indent();
            printExpr(unExpr.expr);
            dedent();
        } else if (expr instanceof Ident) {
            Ident ident = (Ident) expr;
            appendLine("IDENTIFIER: " + ident.name);
        } else if (expr instanceof IntVal) {
            IntVal intVal = (IntVal) expr;
            appendLine("INTEGER: " + intVal.value);
        } else if (expr instanceof DoubleVal) {
            DoubleVal doubleVal = (DoubleVal) expr;
            appendLine("DOUBLE: " + doubleVal.val);
        } else if (expr instanceof BoolVal) {
            BoolVal boolVal = (BoolVal) expr;
            appendLine("BOOLEAN: " + boolVal.value);
        } else if (expr instanceof CharVal) {
            CharVal charVal = (CharVal) expr;
            appendLine("CHAR: '" + charVal.val + "'");
        } else if (expr instanceof ParenExpr) {
            ParenExpr parenExpr = (ParenExpr) expr;
            appendLine("PARENTHESIZED EXPRESSION:");
            indent();
            printExpr(parenExpr.expr);
            dedent();
        } else if (expr instanceof FuncCallExpr) {
            FuncCallExpr funcCall = (FuncCallExpr) expr;
            appendLine("FUNCTION CALL: " + funcCall.name);
            indent();
            appendLine("Arguments:");
            indent();
            if (funcCall.actualParameters != null && !funcCall.actualParameters.isEmpty()) {
                for (Expr arg : funcCall.actualParameters) {
                    printExpr(arg);
                }
            } else {
                appendLine("None");
            }
            dedent();
            dedent();
        } else if (expr instanceof TensorDefExpr) {
            TensorDefExpr tensorExpr = (TensorDefExpr) expr;
            appendLine("TENSOR LITERAL");
            indent();
            appendLine("Elements:");
            indent();
            if (tensorExpr.exprs != null && !tensorExpr.exprs.isEmpty()) {
                for (Expr element : tensorExpr.exprs) {
                    printExpr(element);
                }
            } else {
                appendLine("Empty");
            }
            dedent();
            dedent();
        } else if (expr instanceof TensorAccessExpr) {
            TensorAccessExpr accessExpr = (TensorAccessExpr) expr;
            appendLine("TENSOR ACCESS");
            indent();
            appendLine("Base expression:");
            indent();
            printExpr(accessExpr.listExpr);
            dedent();
            appendLine("Indices:");
            indent();
            if (accessExpr.indices != null && !accessExpr.indices.isEmpty()) {
                for (Expr idx : accessExpr.indices) {
                    printExpr(idx);
                }
            }
            dedent();
            dedent();
        } else {
            appendLine("UNKNOWN EXPRESSION TYPE: " + expr.getClass().getName());
        }
    }

    private String printType(Type type) {
        if (type == null) {
            return "NULL TYPE";
        }

        if (type instanceof SimpleType) {
            SimpleType simpleType = (SimpleType) type;
            // Handle void type representation
            if (simpleType.type == SimpleTypesEnum.BOOL) {
                return "BOOL"; // Could be void in function context
            }
            return simpleType.type.toString();
        } else if (type instanceof TensorType) {
            TensorType tensorType = (TensorType) type;
            StringBuilder sb = new StringBuilder();

            if (tensorType.isVector()) {
                sb.append("vector[");
            } else if (tensorType.isMatrix()) {
                sb.append("matrix[");
            } else {
                sb.append("tensor[");
            }

            // Component type
            sb.append(printType(tensorType.componentType));

            // Dimensions
            for (SizeParam dim : tensorType.dimensions) {
                sb.append(", ");
                sb.append(printSizeParam(dim));
            }

            sb.append("]");
            return sb.toString();
        } else {
            return "UNKNOWN TYPE: " + type.getClass().getName();
        }
    }

    private String printSizeParam(SizeParam sizeParam) {
        if (sizeParam instanceof SPInt) {
            return String.valueOf(((SPInt) sizeParam).value);
        } else if (sizeParam instanceof SPIdent) {
            return ((SPIdent) sizeParam).ident;
        } else {
            return "UNKNOWN SIZE PARAM: " + sizeParam.getClass().getName();
        }
    }
}