package TestFiles;

//import AbstractSyntax.Program.Prog;
import AbstractSyntax.Program.*;
import SemanticAnalysis.TypeChecker;
import boltparser.Parser; //For List<TypeError>
import boltparser.Scanner;
import java.util.List;

/**
 * 
 * BoltIntegrationTestRunner
 *
 * This class serves as the integration test runner for the BOLT language.
 * It executes all .bolt test files through the full pipeline: parsing,
 * AST construction, and type checking. Each file is expected to either 
 * pass or fail type checking, and this runner verifies that outcome.
 *
 * The results are printed to the console as [PASS], [FAIL], or [ERROR],
 * along with additional error messages when applicable.
 */



public class BoltIntegrationTestRunner {
    
    //Entry point for running all integration tests
    public static void main(String[] args) {
        System.out.println("=== Running TypeChecker Integration Tests ===");

        //Each runTest() call tests one .bolt file
        //The second argument indicates whether the file is expected to pass or fail type checking
        runTest("../bolt_tests/valid_program_1.bolt", true); //this should pass
        runTest("../bolt_tests/valid_return_stmt.bolt", true); //this should pass
        runTest("../bolt_tests/valid_defer_block.bolt", true); // this should pass

        runTest("../bolt_tests/func_arg_error.bolt", false); //this should fail
        runTest("../bolt_tests/tensor_shape_error.bolt", false); //this should fail
        runTest("../bolt_tests/func_missing_return.bolt", false); //this should fail
        runTest("../bolt_tests/defer_after_return.bolt", false); //this should fail


        runTest("../bolt_tests/undeclared_var.bolt", false); //this should fail
        runTest("../bolt_tests/tensor_shape_mismatch.bolt", false); //this should fail
        runTest("../bolt_tests/func_call_wrong_arg_count.bolt", false); //this should fail
        runTest("../bolt_tests/func_call_wrong_arg_type.bolt", false); //this should fail
        runTest("../bolt_tests/builtin_zeros_invalid_type.bolt", false); //this should fail
        runTest("../bolt_tests/defer_forbidden_id.bolt", false); //this should fail
        runTest("../bolt_tests/invalid_return_stmt_type.bolt", false); //this should fail
        runTest("../bolt_tests/if_stmt_type_mismatch.bolt", false); //this should fail
        runTest("../bolt_tests/while_stmt_type_mismatch.bolt", false); //this should fail
        
    }
     //This method runs a single test by parsing and type-checking the provided .bolt file
    public static void runTest(String filePath, boolean shouldPass) {
    System.out.println("Test: " + filePath);

    try {
        Scanner scanner = new Scanner(filePath);
        Parser parser = new Parser(scanner);

        //Parse the file, this builds the AST
        parser.Parse(); //This is the default Coco/R entry point
        Prog prog = parser.mainNode; //mainNode contains the constructed AST root node

        TypeChecker checker = new TypeChecker();
        checker.check(prog);
        List<String> errors = checker.getErrors(); 

        if (shouldPass && errors.isEmpty()) {
            System.out.println("  [PASS] " + filePath);
        } else if (!shouldPass && !errors.isEmpty()) {
            System.out.println("  [PASS] " + filePath + " (correctly failed)");
            for (String err : errors) {
                System.out.println("       → " + err);
            }
        } else {
            System.out.println("  [FAIL] " + filePath);
            if (!errors.isEmpty()) {
                for (String err : errors) {
                    System.out.println("       → " + err);
                }
            } else {
                System.out.println("       → Expected errors but none were found.");
            }
        }

    } catch (Exception e) {
        System.out.println("  [ERROR] " + filePath);
        System.out.println("       → Exception: " + e.getMessage());
        e.printStackTrace();
    }

    System.out.println(); //spacing

    }
}
