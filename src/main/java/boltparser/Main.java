package boltparser;
import Transpiler.Transpiler;
import AbstractSyntax.Definitions.FuncDef;
import AbstractSyntax.Program.*;
import DataflowAnalysis.CFGBuilder;
import DataflowAnalysis.CFGAnalysis;
import java.io.File;
import java.util.*;
import SemanticAnalysis.TypeChecker;
import SemanticAnalysis.TypeEnvironment;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <input-file>");
            return;
        }

        String filename = args[0];
        System.out.println("Working Directory: " + System.getProperty("user.dir"));
        System.out.println("Attempting to parse file: " + filename);

        try {
            File file = new File(filename);
            if (!file.exists()) {
                System.err.println("Error: File does not exist: " + filename);
                return;
            }

            Scanner scanner = new Scanner(filename);
            System.out.println("Scanner created successfully");

            Parser parser = new Parser(scanner);
            System.out.println("Parser created successfully");

            System.out.println("Starting parsing...");
            parser.Parse();
            System.out.println("Parsing completed");

            if (parser.hasErrors()) {
                System.out.println("Errors occurred during parsing!");
                return;
            }

            System.out.println("\n=== Abstract Syntax Tree - Program Structure ===\n");
            Prog ast = parser.mainNode;
            if (ast == null) {
                System.out.println("No AST generated (empty program)");
            } else {
                AstPrinter printer = new AstPrinter();
                System.out.println(printer.printProgram(ast));
            }

            System.out.println("\n=== Control Flow Graph ===\n");
            CFGBuilder builder = new CFGBuilder();

            // NEW: Collect CFG analysis for all functions
            Map<String, FunctionCFGInfo> allFunctionCFGs = new HashMap<>();

            for (FuncDef func = ast.func; func != null; func = func.nextFunc) {
                System.out.println("\n\n\n-----------------------------------------------------------");
                System.out.println("Function: " + func.procname);

                // NEW: Create CFG info for this function
                FunctionCFGInfo cfgInfo = new FunctionCFGInfo(func.procname);

                CFGBuilder.CFGNode entry = builder.buildFunctionCFG(func);

                // Collect all nodes reachable from entry
                Set<CFGBuilder.CFGNode> visited = new HashSet<>();
                collectAllNodes(entry, visited, cfgInfo.allNodes);

                // Print raw CFG
                printCFG(entry, new HashSet<>());

                // Run analysis
                cfgInfo.liveness = CFGAnalysis.performLiveness(cfgInfo.allNodes);
                cfgInfo.useDef = CFGAnalysis.computeUseDefChains(cfgInfo.allNodes);
                cfgInfo.optimized = CFGAnalysis.eliminateDeadCode(cfgInfo.allNodes, cfgInfo.liveness);

                // Output analyses
                System.out.println("\n-- Liveness Info --");
                for (CFGBuilder.CFGNode node : cfgInfo.allNodes) {
                    CFGAnalysis.Liveness lv = cfgInfo.liveness.get(node);
                    System.out.println("Node " + node.id + " IN: " + lv.in + " OUT: " + lv.out);
                }

                System.out.println("\n-- Use-Def Chains --");
                for (String var : cfgInfo.useDef.keySet()) {
                    System.out.print(var + " defined at nodes: ");
                    for (CFGBuilder.CFGNode def : cfgInfo.useDef.get(var)) {
                        System.out.print(def.id + " ");
                    }
                    System.out.println();
                }

                System.out.println("\n-- Optimized CFG (Dead Code Eliminated) --");
                for (CFGBuilder.CFGNode node : cfgInfo.optimized) {
                    System.out.println("Node ID: " + node.id + ", GEN: " + node.gen + ", KILL: " + node.kill);
                }

                System.out.println("\n-- Memory Transfers --");
                CFGAnalysis.insertMemoryTransfers(cfgInfo.allNodes);

                // NEW: Store CFG info for this function
                allFunctionCFGs.put(func.procname, cfgInfo);

                System.out.println("-----------------------------------------------------------");
            }

            System.out.println("\n=== Type Checking ===\n");
            TypeChecker typeChecker = new TypeChecker();
            try {
                typeChecker.check(ast);
                System.out.println("Type checking completed successfully");

                // Get the global type environment
                TypeEnvironment globalTypes = typeChecker.getGlobalEnvironment();

                System.out.println("\n=== Transpilation ===\n");
                try {
                    String baseFilename;
                    if (filename.contains(".")) {
                        baseFilename = filename.substring(0, filename.lastIndexOf('.'));
                    } else {
                        baseFilename = filename;
                    }

                    // NEW: Pass CFG analysis to transpiler
                    Transpiler.TranspileProg(baseFilename, ast, globalTypes, allFunctionCFGs);

                } catch (Exception transpilerError) {
                    System.err.println("Error during transpilation: " + transpilerError.getMessage());
                    transpilerError.printStackTrace();
                }

            } catch (RuntimeException typeError) {
                System.err.println("Type checking failed: " + typeError.getMessage());

                // Print detailed error information
                if (typeChecker.hasErrors()) {
                    List<String> errors = typeChecker.getErrors();
                    System.err.println("\nDetailed type checking errors:");
                    for (int i = 0; i < errors.size(); i++) {
                        System.err.println("  Error " + (i + 1) + ": " + errors.get(i));
                    }
                } else {
                    System.err.println("No detailed error information available");
                }

                // Print the exception stack trace for debugging
                System.err.println("\nException details:");
                typeError.printStackTrace();
            }

        } catch (Exception e) {
            System.err.println("Error during parsing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printCFG(CFGBuilder.CFGNode node, Set<Integer> visited) {
        if (node == null || visited.contains(node.id)) return;
        visited.add(node.id);
        System.out.println("Node ID: " + node.id +
                ", GPU: " + node.inGPUContext +
                ", GEN: " + node.gen +
                ", KILL: " + node.kill);
        for (CFGBuilder.CFGNode succ : node.successors) {
            System.out.println("  -> " + succ.id);
        }
        for (CFGBuilder.CFGNode succ : node.successors) {
            printCFG(succ, visited);
        }
    }

    private static void collectAllNodes(CFGBuilder.CFGNode node, Set<CFGBuilder.CFGNode> visited, List<CFGBuilder.CFGNode> result) {
        if (node == null || visited.contains(node)) return;
        visited.add(node);
        result.add(node);
        for (CFGBuilder.CFGNode succ : node.successors) {
            collectAllNodes(succ, visited, result);
        }
    }
}