package TestFiles.UnitTests;

import AbstractSyntax.Expressions.*;
import AbstractSyntax.Types.*;
import SemanticAnalysis.TypeChecker;
import SemanticAnalysis.TypeEnvironment;
import java.util.ArrayList;

/*
 * Unit tests for verifying special handling of built-in functions
 * like "zeros" and "ones" during type checking.
 *
 * These functions are not defined by the user but recognized by the type checker.
 * 
 * This includes:
 * - Valid usage: correct number and type of arguments
 * - Invalid usage: wrong arity, wrong argument types
 */

public class TestBuiltinFunctions {

    public static void main(String[] args) {
        System.out.println(" Running TestBuiltinFunctions...");

        testValidZerosCall();       //zeros(2, 3) — should succeed
        testZerosWrongArity();      //zeros(1) — should fail
        testZerosWithFloat();       //zeros(2.0, 3) — should fail
        testUnknownBuiltin();       //foobar(3, 4) — not a builtin
    }

    //Test: zeros(2, 3) should return a 2D tensor of int type
    static void testValidZerosCall() {
        ArrayList<Expr> args = new ArrayList<>();
        args.add(new IntVal(2));
        args.add(new IntVal(3));

        FuncCallExpr expr = new FuncCallExpr("zeros", args);
        TypeChecker checker = new TypeChecker();

        try {
            Type result = checker.checkExpr(expr, new TypeEnvironment());
            if (result instanceof TensorType) {
                System.out.println(" testValidZerosCall passed");
            } else {
                System.out.println(" testValidZerosCall failed — expected TensorType, got: " + result);
            }
        } catch (Exception e) {
            System.out.println(" testValidZerosCall failed — unexpected error: " + e.getMessage());
        }
    }

    //Test: zeros with 1 argument, should throw error due to wrong arity
    static void testZerosWrongArity() {
        ArrayList<Expr> args = new ArrayList<>();
        args.add(new IntVal(1));

        FuncCallExpr expr = new FuncCallExpr("zeros", args);
        TypeChecker checker = new TypeChecker();

        try {
            checker.checkExpr(expr, new TypeEnvironment());
            System.out.println(" testZerosWrongArity failed — expected error, but none occurred");
        } catch (Exception e) {
            System.out.println(" testZerosWrongArity passed — caught expected error: " + e.getMessage());
        }
    }

    //Test: zeros(2.0, 3), first argument is a float, should fail
    static void testZerosWithFloat() {
        ArrayList<Expr> args = new ArrayList<>();
        args.add(new DoubleVal(2.0));
        args.add(new IntVal(3));

        FuncCallExpr expr = new FuncCallExpr("zeros", args);
        TypeChecker checker = new TypeChecker();

        try {
            checker.checkExpr(expr, new TypeEnvironment());
            System.out.println(" testZerosWithFloat failed — expected error, but none occurred");
        } catch (Exception e) {
            System.out.println(" testZerosWithFloat passed — caught expected error: " + e.getMessage());
        }
    }

    //Test: foobar(3, 4), not a built-in, and no user-defined function context provided
    static void testUnknownBuiltin() {
        ArrayList<Expr> args = new ArrayList<>();
        args.add(new IntVal(3));
        args.add(new IntVal(4));

        FuncCallExpr expr = new FuncCallExpr("foobar", args);
        TypeChecker checker = new TypeChecker();

        try {
            checker.checkExpr(expr, new TypeEnvironment());
            System.out.println(" testUnknownBuiltin failed — expected error, but none occurred");
        } catch (Exception e) {
            System.out.println(" testUnknownBuiltin passed — caught expected error: " + e.getMessage());
        }
    }
}
