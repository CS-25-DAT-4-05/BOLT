package boltparser;

import AbstractSyntax.Program.*;
import SemanticAnalysis.TypeChecker;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <input-file>");
            return;
        }

        String filename = args[0];
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                        BOLT Compiler                         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println("Working Directory: " + System.getProperty("user.dir"));
        System.out.println("Input file: " + filename);

        try {
            File file = new File(filename);
            if (!file.exists()) {
                System.err.println("Error: File does not exist: " + filename);
                return;
            }

            // ═══════════════════════════════════════════════════════════════
            // LEXICAL & SYNTACTIC ANALYSIS
            // ═══════════════════════════════════════════════════════════════
            System.out.println("\nStarting lexical and syntactic analysis...");

            Scanner scanner = new Scanner(filename);
            Parser parser = new Parser(scanner);
            parser.Parse();

            if (parser.hasErrors()) {
                System.out.println("Parsing failed!");
                return;
            }

            Prog ast = parser.mainNode;
            if (ast == null) {
                System.out.println("No AST generated (empty program)");
                return;
            }

            System.out.println("Parsing completed successfully");

            // ═══════════════════════════════════════════════════════════════
            // AST VISUALIZATION
            // ═══════════════════════════════════════════════════════════════
            System.out.println("\nAbstract Syntax Tree:");
            System.out.println("══════════════════════════════════════════════════════════════");
            AstPrinter printer = new AstPrinter();
            System.out.println(printer.printProgram(ast));

            // ═══════════════════════════════════════════════════════════════
            // SEMANTIC ANALYSIS (TYPE CHECKING)
            // ═══════════════════════════════════════════════════════════════
            System.out.println("\nStarting semantic analysis...");

            TypeChecker typeChecker = new TypeChecker();
            boolean typeCheckPassed = false;

            try {
                typeChecker.check(ast);
                System.out.println("Type checking passed!");
                typeCheckPassed = true;
            } catch (RuntimeException e) {
                System.out.println("Type checking failed!");
                System.out.println("\nType Errors Found:");
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                int errorCount = 0;
                for (String error : typeChecker.getErrors()) {
                    errorCount++;
                    System.err.println("  " + errorCount + ". " + error);
                }

                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.err.println("Total errors: " + errorCount);
            }

            // ═══════════════════════════════════════════════════════════════
            // COMPILATION SUMMARY
            // ═══════════════════════════════════════════════════════════════
            System.out.println("\n Compilation Pipeline Status:");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("Lexical Analysis - Check");
            System.out.println("Syntax Analysis - Check");
            System.out.println(typeCheckPassed ? "Semantic Analysis - Check" : "Semantic Analysis - Not Complete");

            if (typeCheckPassed) {
                System.out.println("Ready for code generation...");
            } else {
                System.out.println("Fix type errors before proceeding to code generation");
            }

        } catch (Exception e) {
            System.err.println("Fatal error during compilation:");
            System.err.println("   " + e.getMessage());
            if (args.length > 1 && args[1].equals("--debug")) {
                e.printStackTrace();
            }
        }
    }
}
