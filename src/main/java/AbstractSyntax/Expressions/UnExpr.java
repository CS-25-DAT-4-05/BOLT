package AbstractSyntax.Expressions;
public class UnExpr implements Expr{
   public Unaryoperator op;
   public Expr expr;

    public UnExpr(Expr expr, Unaryoperator op){
        this.op = op;
        this.expr = expr;
    }
}
