package AbstractSyntax.Expressions;

/* Created 09-05-2025 by Nikolaj
 * 
 * 
 * 
 */

public class ParenExpr implements Expr{
    public Expr expr;
    public ParenExpr(Expr expr){
        this.expr = expr;
    }
}
