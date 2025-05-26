package TestFiles;

import AbstractSyntax.Program.Prog;
import SemanticAnalysis.TypeChecker;
import boltparser.Parser;
import boltparser.Scanner;
import java.io.*;

public class TestTypeChecker {
    
    // Entry point for running all integration tests
    public static void main(String[] args) {
        System.out.println("=== Running TypeChecker Integration Tests ===");
        // Each runTest() call tests one .bolt file
        // The second argument indicates whether the file is expected to pass or fail type checking
        runTest("bolt_tests/valid_program_1.bolt", true); //Expected to pass
        runTest("bolt_tests/undeclared_var.bolt", false); //Should fail
        runTest("bolt_tests/tensor_shape_error.bolt", false); //Should fail
        runTest("bolt_tests/func_arg_error.bolt", false); //Should fail

        System.out.println("\n All integration tests completed.");
    }
     //This method runs a single test by parsing and type-checking the provided .bolt file
    private static void runTest(String filePath, boolean shouldPass) {
        System.out.print("Test: " + filePath + " — ");
        try {
            //Open input file
            FileInputStream in = new FileInputStream("TestFiles/" + filePath);

            //Create Scanner and Parser
            Scanner scanner = new Scanner(in);
            Parser parser = new Parser(scanner);
            
            //Parse the file, this builds the AST
            parser.Parse(); //This is the default Coco/R entry point
            Prog prog = parser.mainNode; //Mainnode contains the constructed AST root node


            //Run Typechecking on AST
            new TypeChecker().check(prog);

            //If the test was expected to pass and no exception was thrown, we passed
            if (shouldPass) {
                System.out.println("Passed");
            } else {
                System.out.println("Failed — expected type error, but none occurred"); //If it should have failed, but didn’t — this is a mistake
            }

        } catch (Exception e) {
             //If the program failed during parsing or type checking..
            if (shouldPass) {
                System.out.println("Failed — unexpected error: " + e.getMessage());  //...but was expected to pass — we failed the test
            } else {
                System.out.println("Correctly failed: " + e.getMessage());  //...and was expected to fail — we passed the test
            }
        }
    }
}
