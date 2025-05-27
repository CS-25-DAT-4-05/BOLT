package boltparser;

import DataflowAnalysis.CFGBuilder;
import DataflowAnalysis.CFGAnalysis;
import java.util.*;

// Shared class to hold CFG analysis results for a function
public class FunctionCFGInfo {
    public String functionName;
    public List<CFGBuilder.CFGNode> allNodes;
    public Map<CFGBuilder.CFGNode, CFGAnalysis.Liveness> liveness;
    public Map<String, Set<CFGBuilder.CFGNode>> useDef;
    public List<CFGBuilder.CFGNode> optimized;

    public FunctionCFGInfo(String name) {
        this.functionName = name;
        this.allNodes = new ArrayList<>();
    }
}