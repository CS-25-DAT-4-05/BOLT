package TestFiles.UnitTests;

import AbstractSyntax.Expressions.*;
import AbstractSyntax.Types.*;
import SemanticAnalysis.TypeChecker;
import SemanticAnalysis.TypeEnvironment;
import java.util.ArrayList;

/*
 * Unit tests for verifying type checking of individual expression nodes.
 *
 * This includes:
 * - Integer literals
 * - Binary expressions like addition
 * - Tensor literals
 * - Invalid binary operations (int + tensor)
 * - Usage of undeclared variables
 *
 * Each test ensures the type checker returns correct types or appropriate errors.
 */

public class TestExprChecker {

    public static void main(String[] args) {
        System.out.println(" Running TestExprChecker...");

        testIntegerLiteral();        //Simple integer value
        testBinaryAddition();        //int + int
        testTensorLiteral();         //tensor literal expression
        testInvalidAddition();       //int + tensor (should fail)
        testUndeclaredVariable();    //variable "x" not in environment (this should fail)
    }
    //Tests if an IntVal expression is correctly recognized as INT type
    static void testIntegerLiteral() {
        Expr expr = new IntVal(42);
        TypeChecker checker = new TypeChecker();
        Type result = checker.checkExpr(expr, new TypeEnvironment());

        if (!(result instanceof SimpleType) || ((SimpleType) result).type != SimpleTypesEnum.INT) {
            System.out.println(" testIntegerLiteral failed — expected IntType, got: " + result);
        } else {
            System.out.println(" testIntegerLiteral passed");
        }
    }
    //Tests binary expression: 3 + 5
    static void testBinaryAddition() {
        Expr expr = new BinExpr(
            new IntVal(3),
            new IntVal(5),
            Binoperator.ADD
        );
        TypeChecker checker = new TypeChecker();
        Type result = checker.checkExpr(expr, new TypeEnvironment());

        if (!(result instanceof SimpleType) || ((SimpleType) result).type != SimpleTypesEnum.INT) {
            System.out.println(" testBinaryAddition failed — expected IntType, got: " + result);
        } else {
            System.out.println(" testBinaryAddition passed");
        }
    }
    //Tests tensor literal, expects TensorType in return
    static void testTensorLiteral() {
        Expr expr = new TensorDefExpr(makeTensorLiteral(new int[][]{{1, 2}, {3, 4}}));
        TypeChecker checker = new TypeChecker();
        Type result = checker.checkExpr(expr, new TypeEnvironment());

        if (!(result instanceof TensorType)) {
            System.out.println(" testTensorLiteral failed — expected TensorType, got: " + result);
        } else {
            System.out.println(" testTensorLiteral passed");
        }
    }
    //Tests invalid expression: int + tensor — should trigger type error
    static void testInvalidAddition() {
        Expr expr = new BinExpr(
            new IntVal(7),
            new TensorDefExpr(makeTensorLiteral(new int[][]{{1}})),
            Binoperator.ADD
        );
        TypeChecker checker = new TypeChecker();

        try {
            checker.checkExpr(expr, new TypeEnvironment());
            System.out.println(" testInvalidAddition failed — expected type error, but none occurred");
        } catch (Exception e) {
            System.out.println(" testInvalidAddition passed — caught expected error: " + e.getMessage());
        }
    }
     //Tests undeclared variable "x" — should fail with undeclared identifier error
    static void testUndeclaredVariable() {
        Expr expr = new Ident("x");  //not declared in the TypeEnvironment (again, should fail)
        TypeChecker checker = new TypeChecker();

        try {
            checker.checkExpr(expr, new TypeEnvironment());
            System.out.println(" testUndeclaredVariable failed — expected error, but none occurred");
        } catch (Exception e) {
            System.out.println(" testUndeclaredVariable passed — caught expected error: " + e.getMessage());
        }
    }

    //Helper method to build TensorDefExpr from raw int matrix.
    private static ArrayList<Expr> makeTensorLiteral(int[][] values) {
        ArrayList<Expr> outer = new ArrayList<>();
        for (int[] row : values) {
            ArrayList<Expr> inner = new ArrayList<>();
            for (int val : row) {
                inner.add(new IntVal(val));
            }
            outer.add(new TensorDefExpr(inner));
        }
            return outer;
    }
}
