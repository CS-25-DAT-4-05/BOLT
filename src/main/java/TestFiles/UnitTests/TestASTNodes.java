package TestFiles.UnitTests;

import AbstractSyntax.Expressions.*;
import java.util.ArrayList;

/*
 * Unit tests for checking correctness of expression-related AST node construction and behavior
 * This includes tests for;
 * - Binary expressions (2 * 3)
 * - Tensor literals (2d arrays)
 * - Function call expressions (zeros(10, 20))
 * 
 * Each test checks if fields are set correctly and that expression structures behave as expected
 */

public class TestASTNodes {

    public static void main(String[] args) {
        System.out.println(" Running TestASTNodes...");

        testBinExprConstruction(); //Test binary structure
        testTensorLiteralConstruction(); //Test tensor literal values 
        testFuncCallExprConstruction(); //Test function call Expression structure
    }
    //Tests the construction of a binary expression, for example; 2 * 3
    //Verifies that the left, right operands and operator are stored correctly.
    static void testBinExprConstruction() {
        Expr left = new IntVal(2);
        Expr right = new IntVal(3);
        BinExpr expr = new BinExpr(left, right, Binoperator.TIMES);

        if (!(expr.left == left && expr.right == right && expr.op == Binoperator.TIMES)) {
            System.out.println(" testBinExprConstruction failed | fields not set correctly!");
        } else {
            System.out.println(" testBinExprConstruction passed");
        }
    }
    //Tests construction of a TensorDefExpr using a 2x2 literal
    //Verifies correct number of rows and columns
    static void testTensorLiteralConstruction() {
        //ArrayList<Expr> tensorRows = new ArrayList<>();

        // Build a 2x2 tensor: [[1, 2], [3, 4]]
        ArrayList<Expr> row1 = new ArrayList<>();
        row1.add(new IntVal(1));
        row1.add(new IntVal(2));

        ArrayList<Expr> row2 = new ArrayList<>();
        row2.add(new IntVal(3));
        row2.add(new IntVal(4));

        ArrayList<Expr> tensorRows = new ArrayList<>();
        tensorRows.add(new TensorDefExpr(row1));
        tensorRows.add(new TensorDefExpr(row2));

        TensorDefExpr tensor = new TensorDefExpr(tensorRows);

        //Check number of rows
        if (tensorRows.size() != 2) {
            System.out.println(" testTensorLiteralConstruction failed | incorrect number of rows");
            return;
        }

        // Check number of elements in each row
        if (row1.size() != 2 || row2.size() != 2) {
            System.out.println(" testTensorLiteralConstruction failed | row length mismatch");
            return;
        }

        System.out.println(" testTensorLiteralConstruction passed");

    }

    //Tests construction of a function call expression like, for example; "zeros(10, 20)"
    //We verify the name and number of arguments
    static void testFuncCallExprConstruction() {
        ArrayList<Expr> args = new ArrayList<>();
        args.add(new IntVal(10));
        args.add(new IntVal(20));

        FuncCallExpr call = new FuncCallExpr("zeros", args);

        if (!call.name.equals("zeros") || call.actualParameters.size() != 2) {
            System.out.println(" testFuncCallExprConstruction failed | name or parameters not set correctly");
        } else {
            System.out.println(" testFuncCallExprConstruction passed");
        }
    }

}
