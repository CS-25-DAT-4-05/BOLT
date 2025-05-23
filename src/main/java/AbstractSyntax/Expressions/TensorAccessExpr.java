package AbstractSyntax.Expressions;
import java.util.ArrayList;
public class TensorAccessExpr implements Expr {
    public Expr listExpr;
    public ArrayList<Expr> indices;

    public TensorAccessExpr(Expr listExpr, ArrayList<Expr> indices) {
        this.listExpr = listExpr;
        this.indices = new ArrayList<>();
        if (indices != null) {
            this.indices.addAll(indices);
        }
    }
}