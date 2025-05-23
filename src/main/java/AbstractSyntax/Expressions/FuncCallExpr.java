package AbstractSyntax.Expressions;
import java.util.ArrayList;

public class FuncCallExpr implements Expr {
    public ArrayList<Expr> actualParameters;
    public String name;

    public FuncCallExpr(String name, ArrayList<Expr> actualParameters) {
        this.name = name;
        this.actualParameters = new ArrayList<>();
        if (actualParameters != null) {
            this.actualParameters.addAll(actualParameters);
        }
    }
}