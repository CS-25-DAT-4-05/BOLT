package AbstractSyntax.Statements;

import java.util.ArrayList;

import AbstractSyntax.SizeParams.SizeParam;
import Lib.Pair;

/* Created 09-05-2025 by Nikolaj
 *
 *
 *
 */

public class Defer implements Stmt {
    public ArrayList<Pair<String, SizeParam>> dim; // for explicit giving params to defer, not implemented
    public Stmt stmt; // statements inside defer body

    public Defer(ArrayList<Pair<String, SizeParam>> dim, Stmt stmt) {
        this.dim = new ArrayList<>();
        if (dim != null) {
            for (Pair<String, SizeParam> pair : dim) {
                this.dim.add(pair);
            }
        }
        this.stmt = stmt;
    }
}