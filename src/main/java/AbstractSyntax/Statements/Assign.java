package AbstractSyntax.Statements;
import AbstractSyntax.Expressions.*;


public class Assign implements Stmt {
    public Expr target;  // ændret fra String ident to Expr target
    public Expr expr;    // right-hand side expression

    // simple assignments
    public Assign(String ident, Expr expr) {
        this.target = new Ident(ident);
        this.expr = expr;
    }

    // tensor assignments
    public Assign(Expr target, Expr expr) {
        this.target = target;
        this.expr = expr;
    }

    // checker, simple assignment
    public boolean isSimpleAssignment() {
        return target instanceof Ident;
    }

    // get identifier name for simple assignments
    public String getIdentifier() {
        if (target instanceof Ident) {
            return ((Ident) target).name;
        }
        return null;
    }
}