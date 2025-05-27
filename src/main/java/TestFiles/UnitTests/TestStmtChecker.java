package TestFiles.UnitTests;

import AbstractSyntax.Expressions.Expr;
import AbstractSyntax.Expressions.Ident;
import AbstractSyntax.Expressions.IntVal;
import AbstractSyntax.Statements.Assign;
import AbstractSyntax.Statements.Declaration;
import AbstractSyntax.Statements.Stmt;
import AbstractSyntax.Types.SimpleType;
import AbstractSyntax.Types.SimpleTypesEnum;
import SemanticAnalysis.TypeChecker;
import SemanticAnalysis.TypeEnvironment;

/*
 * Unit tests for verifying type checking of statement nodes.
 *
 * This includes:
 * - Declarations with valid initializers
 * - Assignments to undeclared identifiers (should fail)
 *
 * Each test ensures the type checker behaves correctly
 * by either passing valid statements or catching semantic errors.
 */

public class TestStmtChecker {

    public static void main(String[] args) {
        System.out.println(" Running TestStmtChecker...");

        testValidDeclaration();              //int x = 5; (should succeed)
        testUndeclaredVariableAssignment();  //x = 3; (x not declared, so should fail)
    }

    //Test: Declaration with initializer
    //Simulates "int x = 5;" — should typecheck successfully
    static void testValidDeclaration() {
        Expr init = new IntVal(5);
        Stmt decl = new Declaration(
            new SimpleType(SimpleTypesEnum.INT),  //declared type
            "x", //variable name
            init, //initializer
            null //no following statement
        );

        TypeChecker checker = new TypeChecker();
        TypeEnvironment env = new TypeEnvironment();

        try {
            checker.checkStmt(decl, env, null);  //null = no enclosing function
            System.out.println(" testValidDeclaration passed");
        } catch (Exception e) {
            System.out.println(" testValidDeclaration failed — " + e.getMessage());
        }
    }

    //Test: Assignment to undeclared variable
    //Simulates "x = 3;" — x is not in the environment, (should fail)
    static void testUndeclaredVariableAssignment() {
        Expr rhs = new IntVal(3);
        Expr lhs = new Ident("x");
        Stmt assign = new Assign(lhs, rhs);

        TypeChecker checker = new TypeChecker();
        TypeEnvironment env = new TypeEnvironment();  //x is undeclared

        try {
            checker.checkStmt(assign, env, null);
            System.out.println(" testUndeclaredVariableAssignment failed — expected error, but none thrown");
        } catch (Exception e) {
            System.out.println(" testUndeclaredVariableAssignment passed — caught expected error: " + e.getMessage());
        }
    }
}
