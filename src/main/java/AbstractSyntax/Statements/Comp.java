package AbstractSyntax.Statements;

/* Created 09-05-2025 by Nikolaj
 * 
 * 
 * 
 */

public class Comp implements Stmt{
    public Stmt stmt1;
    public Stmt stmt2;
    public Comp(Stmt stmt1, Stmt stmt2){
        this.stmt1 = stmt1;
        this.stmt2 = stmt2;
    }
}