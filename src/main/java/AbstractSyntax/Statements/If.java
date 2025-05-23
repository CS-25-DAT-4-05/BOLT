package AbstractSyntax.Statements;

import AbstractSyntax.Expressions.Expr;

/* Created 09-05-2025 by Nikolaj
 * 
 * 
 * 
 */

public class If implements Stmt{
    public Stmt then;
    public Stmt els;
    public Expr cond;
    public If(Stmt then, Stmt els, Expr cond){
        this.then = then;
        this.els = els;
        this.cond = cond;
    }
}
