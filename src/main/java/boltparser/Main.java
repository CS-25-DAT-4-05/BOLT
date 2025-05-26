package boltparser;

import AbstractSyntax.Definitions.FuncDef;
import AbstractSyntax.Program.*;
import DataflowAnalysis.CFGAnalysis;
import DataflowAnalysis.CFGBuilder;
import java.io.File;
import java.util.*;

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
            for (FuncDef func = ast.func; func != null; func = func.nextFunc) {

                
                System.out.println("\n\n\n-----------------------------------------------------------");
                System.out.println("Function: " + func.procname);
                CFGBuilder.CFGNode entry = builder.buildFunctionCFG(func);
                
                // Collect all nodes reachable from entry
                Set<CFGBuilder.CFGNode> visited = new HashSet<>();
                List<CFGBuilder.CFGNode> allNodes = new ArrayList<>();
                collectAllNodes(entry, visited, allNodes);
                
                // Print raw CFG
                printCFG(entry, new HashSet<>());
                
                // Run analysis
                
                Map<CFGBuilder.CFGNode, CFGAnalysis.Liveness> liveness = CFGAnalysis.performLiveness(allNodes);
                Map<String, Set<CFGBuilder.CFGNode>> useDef = CFGAnalysis.computeUseDefChains(allNodes);
                List<CFGBuilder.CFGNode> optimized = CFGAnalysis.eliminateDeadCode(allNodes, liveness);
                
                // Output analyses
                System.out.println("\n-- Liveness Info --");
                for (CFGBuilder.CFGNode node : allNodes) {
                    CFGAnalysis.Liveness lv = liveness.get(node);
                    System.out.println("Node " + node.id + " IN: " + lv.in + " OUT: " + lv.out);
                }

                System.out.println("\n-- Use-Def Chains --");
                for (String var : useDef.keySet()) {
                    System.out.print(var + " defined at nodes: ");
                    for (CFGBuilder.CFGNode def : useDef.get(var)) {
                        System.out.print(def.id + " ");
                    }
                    System.out.println();
                }

                System.out.println("\n-- Optimized CFG (Dead Code Eliminated) --");
                for (CFGBuilder.CFGNode node : optimized) {
                    System.out.println("Node ID: " + node.id + ", GEN: " + node.gen + ", KILL: " + node.kill);
                }

                System.out.println("\n-- Memory Transfers --");
                CFGAnalysis.insertMemoryTransfers(allNodes);
                System.out.println("-----------------------------------------------------------");
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
