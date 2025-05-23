package AbstractSyntax.Expressions;

public class BinExpr implements Expr {
    public Expr left, right;
    public Binoperator op;

    public BinExpr(Expr left, Expr right, Binoperator op) {
        this.left = left;
        this.right = right;
        this.op = op;
    }
}