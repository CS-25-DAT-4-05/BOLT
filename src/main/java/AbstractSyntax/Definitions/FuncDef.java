package AbstractSyntax.Definitions;
import java.util.ArrayList;

import AbstractSyntax.Statements.Stmt;
import AbstractSyntax.Types.Type;
import AbstractSyntax.Expressions.Expr;
import Lib.Pair;

 // func: T0 p(T1 x1, ..., Tn xn) S return e

public class FuncDef {
    public Type returnType;
    public String procname;
    public ArrayList<Pair<Type, String>> formalParams;
    public Stmt funcBody;
    public Expr returnExpr;
    public FuncDef nextFunc;  // chaining function definitions

    public FuncDef(Type returnType, String procname, ArrayList<Pair<Type, String>> formalParams,
                   Stmt funcBody, Expr returnExpr, FuncDef nextFunc) {
        this.returnType = returnType;
        this.procname = procname;
        this.formalParams = new ArrayList<>();
        if (formalParams != null) {
            this.formalParams.addAll(formalParams);
        }
        this.funcBody = funcBody;
        this.returnExpr = returnExpr;
        this.nextFunc = nextFunc;
    }
}