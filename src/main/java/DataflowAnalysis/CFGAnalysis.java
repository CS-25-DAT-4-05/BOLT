package DataflowAnalysis;

import java.util.*;

public class CFGAnalysis {

    public static class Liveness {
        public Set<String> in = new HashSet<>();
        public Set<String> out = new HashSet<>();
    }

    public static Map<String, Set<CFGBuilder.CFGNode>> computeUseDefChains(List<CFGBuilder.CFGNode> cfgNodes) {
        Map<String, Set<CFGBuilder.CFGNode>> useDef = new HashMap<>();
        for (CFGBuilder.CFGNode node : cfgNodes) {
            for (String v : node.kill) {
                useDef.computeIfAbsent(v, k -> new HashSet<>()).add(node);
            }
        }
        return useDef;
    }

    public static Map<CFGBuilder.CFGNode, Liveness> performLiveness(List<CFGBuilder.CFGNode> allNodes) {
        Map<CFGBuilder.CFGNode, Liveness> liveness = new HashMap<>();
        for (CFGBuilder.CFGNode node : allNodes) {
            liveness.put(node, new Liveness());
        }

        boolean changed;
        do {
            changed = false;
            for (CFGBuilder.CFGNode node : allNodes) {
                Liveness lv = liveness.get(node);

                Set<String> newOut = new HashSet<>();
                for (CFGBuilder.CFGNode succ : node.successors) {
                    newOut.addAll(liveness.get(succ).in);
                }

                Set<String> newIn = new HashSet<>(node.gen);
                Set<String> outMinusKill = new HashSet<>(newOut);
                outMinusKill.removeAll(node.kill);
                newIn.addAll(outMinusKill);

                if (!newIn.equals(lv.in) || !newOut.equals(lv.out)) {
                    lv.in = newIn;
                    lv.out = newOut;
                    changed = true;
                }
            }
        } while (changed);

        return liveness;
    }

    public static List<CFGBuilder.CFGNode> eliminateDeadCode(List<CFGBuilder.CFGNode> allNodes, Map<CFGBuilder.CFGNode, Liveness> livenessMap) {
        List<CFGBuilder.CFGNode> optimized = new ArrayList<>();
        for (CFGBuilder.CFGNode node : allNodes) {
            boolean isDead = true;
            for (String defined : node.kill) {
                if (livenessMap.get(node).out.contains(defined)) {
                    isDead = false;
                    break;
                }
            }
            if (!isDead || node.kill.isEmpty()) {
                optimized.add(node);
            }
        }
        return optimized;
    }

    public static void insertMemoryTransfers(List<CFGBuilder.CFGNode> nodes) {
        CFGBuilder.CFGNode prev = null;
        for (CFGBuilder.CFGNode node : nodes) {
            if (prev != null && prev.inGPUContext != node.inGPUContext) {
                System.out.println("[MEM_TRANSFER] Between node " + prev.id + " and " + node.id +
                        ": " + (node.inGPUContext ? "CPU -> GPU" : "GPU -> CPU"));
            }
            prev = node;
        }
    }
}
