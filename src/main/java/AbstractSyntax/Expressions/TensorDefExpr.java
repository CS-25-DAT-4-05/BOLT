package AbstractSyntax.Expressions;

import java.util.ArrayList;

public class TensorDefExpr implements Expr {
    public ArrayList<Expr> exprs;

    public TensorDefExpr(ArrayList<Expr> exprs) {
        this.exprs = new ArrayList<>();
        if (exprs != null) {
            this.exprs.addAll(exprs);
        }
    }
}