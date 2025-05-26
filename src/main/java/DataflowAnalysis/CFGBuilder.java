package DataflowAnalysis;

import AbstractSyntax.Definitions.*;
import AbstractSyntax.Expressions.*;
import AbstractSyntax.Statements.*;
import java.util.*;

public class CFGBuilder {

    public static class CFGNode {
        public int id;
        public Stmt astNode;               
        public Set<String> gen;            
        public Set<String> kill;           
        public boolean inGPUContext;       
        public List<CFGNode> successors;   

        public CFGNode(int id, Stmt astNode, boolean inGPU) {
            this.id = id;
            this.astNode = astNode;
            this.inGPUContext = inGPU;
            this.gen = new HashSet<>();
            this.kill = new HashSet<>();
            this.successors = new ArrayList<>();
        }
    }

    private int nextId = 0;
    private boolean inGPUContext = false;

    private static class BuildResult {
        public CFGNode entry;
        public List<CFGNode> tails;
        public BuildResult(CFGNode entry, List<CFGNode> tails) {
            this.entry = entry;
            this.tails = tails;
        }
    }

    private BuildResult buildStmt(Stmt s) {
        if (s == null) {
            // No statement (empty block)
            return new BuildResult(null, new ArrayList<>());
        }
        if (s instanceof Comp) {
            
            Comp comp = (Comp) s;
            BuildResult firstRes = buildStmt(comp.stmt1);
            BuildResult secondRes = buildStmt(comp.stmt2);
            
            if (firstRes.entry != null && secondRes.entry != null) {
                for (CFGNode tail : firstRes.tails) {
                    tail.successors.add(secondRes.entry);
                }
            }
            
            CFGNode entryNode = (firstRes.entry != null ? firstRes.entry : secondRes.entry);
            List<CFGNode> tailNodes = (secondRes.entry != null ? secondRes.tails : firstRes.tails);
            return new BuildResult(entryNode, tailNodes);
        }
        if (s instanceof If) {
            
            If ifStmt = (If) s;
            
            CFGNode ifNode = new CFGNode(nextId++, ifStmt, inGPUContext);
            
            collectUses(ifStmt.cond, ifNode.gen);
            
            BuildResult thenRes = buildStmt(ifStmt.then);
            
            BuildResult elseRes = buildStmt(ifStmt.els);
            
            if (thenRes.entry != null) {
                ifNode.successors.add(thenRes.entry);
            }
            
            if (elseRes.entry != null) {
                ifNode.successors.add(elseRes.entry);
            }
            
            List<CFGNode> tailNodes = new ArrayList<>();
            if (thenRes.entry != null) {
                
                tailNodes.addAll(thenRes.tails);
            } else {
                
                tailNodes.add(ifNode);
            }
            if (elseRes.entry != null) {
                
                tailNodes.addAll(elseRes.tails);
            } else {
                
                tailNodes.add(ifNode);
            }
            return new BuildResult(ifNode, tailNodes);
        }
        if (s instanceof While) {
            
            While whileStmt = (While) s;
            
            CFGNode whileNode = new CFGNode(nextId++, whileStmt, inGPUContext);
            
            collectUses(whileStmt.cond, whileNode.gen);
            
            BuildResult bodyRes = buildStmt(whileStmt.stmt);
            
            if (bodyRes.entry != null) {
                whileNode.successors.add(bodyRes.entry);
            } else {
                
                whileNode.successors.add(whileNode);
            }
            
            if (bodyRes.entry != null) {
                for (CFGNode tail : bodyRes.tails) {
                    tail.successors.add(whileNode);
                }
            }
           
            List<CFGNode> tailNodes = new ArrayList<>();
            tailNodes.add(whileNode);
            return new BuildResult(whileNode, tailNodes);
        }
        if (s instanceof Defer) {
            
            Defer deferStmt = (Defer) s;
            
            boolean prevContext = inGPUContext;
            inGPUContext = true;
            BuildResult bodyRes = buildStmt(deferStmt.stmt);  
            
            inGPUContext = prevContext;
            
            return bodyRes;
        }
        if (s instanceof Declaration) {
            
            Declaration decl = (Declaration) s;
            CFGNode node = new CFGNode(nextId++, decl, inGPUContext);
           
            String varName = decl.ident;  
            node.kill.add(varName);
            
            if (decl.expr != null) {
                collectUses(decl.expr, node.gen);
            }
            return new BuildResult(node, Collections.singletonList(node));
        }
        if (s instanceof Assign) {
            
            Assign assign = (Assign) s;
            CFGNode node = new CFGNode(nextId++, assign, inGPUContext);
            
            if (assign.target instanceof Ident) {
                Ident targetId = (Ident) assign.target;
                node.kill.add(targetId.name);
            } else if (assign.target instanceof TensorAccessExpr) {
                TensorAccessExpr ta = (TensorAccessExpr) assign.target;
                
                if (ta.listExpr instanceof Ident) {
                    Ident baseId = (Ident) ta.listExpr;
                    node.kill.add(baseId.name);
                }
                
                for (Expr idx : ta.indices) {
                    collectUses(idx, node.gen);
                }
            }
            
            collectUses(assign.expr, node.gen);
            return new BuildResult(node, Collections.singletonList(node));
        }
        
        CFGNode node = new CFGNode(nextId++, s, inGPUContext);
        
        return new BuildResult(node, Collections.singletonList(node));
    }

    private void collectUses(Expr expr, Set<String> uses) {
        if (expr == null) return;
        if (expr instanceof Ident) {
            Ident idExpr = (Ident) expr;
            uses.add(idExpr.name);
        } else if (expr instanceof BinExpr) {
            BinExpr bin = (BinExpr) expr;
            collectUses(bin.left, uses);
            collectUses(bin.right, uses);
        } else if (expr instanceof UnExpr) {
            UnExpr un = (UnExpr) expr;
            collectUses(un.expr, uses);
        } else if (expr instanceof FuncCallExpr) {
            FuncCallExpr call = (FuncCallExpr) expr;
            for (Expr arg : call.actualParameters) {
                collectUses(arg, uses);
            }
        } else if (expr instanceof TensorAccessExpr) {
            TensorAccessExpr ta = (TensorAccessExpr) expr;
            collectUses(ta.listExpr, uses);
            for (Expr idx : ta.indices) {
                collectUses(idx, uses);
            }
        } else if (expr instanceof TensorDefExpr) {
            TensorDefExpr td = (TensorDefExpr) expr;
            for (Expr val : td.exprs) {
                collectUses(val, uses);
            }
        } else if (expr instanceof ParenExpr) {
            ParenExpr paren = (ParenExpr) expr;
            collectUses(paren.expr, uses);
        }
    }

    public CFGNode buildFunctionCFG(FuncDef funcDef) {
        nextId = 0;
        inGPUContext = false;

        BuildResult bodyRes = buildStmt(funcDef.funcBody);
        
        CFGNode returnNode = new CFGNode(nextId++, null, false);
        
        if (funcDef.returnExpr != null) {
            collectUses(funcDef.returnExpr, returnNode.gen);
        }
        
        if (bodyRes.entry != null) {
            for (CFGNode tail : bodyRes.tails) {
                tail.successors.add(returnNode);
            }
        } else {
            
            return returnNode;
        }
        return bodyRes.entry; 
    }
}
