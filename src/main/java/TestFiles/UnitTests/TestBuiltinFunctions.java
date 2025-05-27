package TestFiles.UnitTests;

import AbstractSyntax.Expressions.DoubleVal;
import AbstractSyntax.Expressions.Expr;
import AbstractSyntax.Expressions.FuncCallExpr;
import AbstractSyntax.Expressions.IntVal;
import AbstractSyntax.Types.SimpleType;
import AbstractSyntax.Types.SimpleTypesEnum;
import AbstractSyntax.Types.TensorType;
import SemanticAnalysis.TypeChecker;
import SemanticAnalysis.TypeEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestBuiltinFunctions {
    public static void main(String[] args) {
        System.out.println("✔️  Running TestBuiltinFunctions...");
        testZerosCorrectUsage();
        testZerosWrongArity();
        testZerosNonIntArguments();
    }

    static void testZerosCorrectUsage() {
        List<Expr> args = Arrays.asList(
            new IntVal(3),
            new IntVal(4)
        );
        FuncCallExpr call = new FuncCallExpr("zeros", new ArrayList<>(args));

        TypeChecker checker = new TypeChecker();
        var result = checker.checkExpr(call, new TypeEnvironment());

        if (result instanceof TensorType tensor &&
            tensor.componentType instanceof SimpleType st &&
            st.type == SimpleTypesEnum.INT &&
            tensor.isMatrix()) {

            System.out.println("✔️  testZerosCorrectUsage passed");
        } else {
            System.out.println("❌ testZerosCorrectUsage failed – expected 2D Int Tensor, got: " + result);
        }
    }

    static void testZerosWrongArity() {
        List<Expr> args = Arrays.asList(
            new IntVal(3) // Only one argument
        );
        FuncCallExpr call = new FuncCallExpr("zeros", new ArrayList<>(args));

        TypeChecker checker = new TypeChecker();

        try {
            checker.checkExpr(call, new TypeEnvironment());
            System.out.println("❌ testZerosWrongArity failed – expected error for wrong arity");
        } catch (Exception e) {
            System.out.println("✔️  testZerosWrongArity passed – caught error: " + e.getMessage());
        }
    }

    static void testZerosNonIntArguments() {
        List<Expr> args = Arrays.asList(
            new DoubleVal(3.5),
            new IntVal(2)
        );
        FuncCallExpr call = new FuncCallExpr("zeros", new ArrayList<>(args));

        TypeChecker checker = new TypeChecker();

        try {
            checker.checkExpr(call, new TypeEnvironment());
            System.out.println("❌ testZerosNonIntArguments failed – expected error for non-int arg");
        } catch (Exception e) {
            System.out.println("✔️  testZerosNonIntArguments passed – caught error: " + e.getMessage());
        }
    }
}
