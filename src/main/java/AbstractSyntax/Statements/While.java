package AbstractSyntax.Statements;

import AbstractSyntax.Expressions.Expr;

/* Created 09-05-2025 by Nikolaj
 * 
 * 
 * 
 */

public class While implements Stmt {
    public Stmt stmt;
    public Expr cond;
    public While(Stmt stmt,Expr cond){
        this.stmt = stmt;
        this.cond = cond;
    }
}
