import AbstractSyntax.Expressions.*;

import java.io.FileReader;
import java.io.Reader;
//import java.lang.reflect.Type;
import java.util.ArrayList;

//import javax.management.openmbean.SimpleType;

//import javax.management.openmbean.SimpleType;

import AbstractSyntax.Definitions.*;
import AbstractSyntax.SizeParams.*;
import AbstractSyntax.Types.*;
import Lib.Pair;
import Transpiler.Transpiler;
import AbstractSyntax.Program.*;
import AbstractSyntax.Statements.*;
import Semantic.TypeChecker;

import boltparser.Scanner;
import boltparser.Parser;


public class Test {


    public static void main(String[] args){
        Prog root;
        FuncDef func1;
        Assign assign1;
        BinExpr bexp;
        Ident returnExp;
        FuncDef mainFunc;
        Ident ident;
        Assign assign2;
        IntVal zero;
        Declaration dec1;

        /* Example program, see aEx.cu
        bexp = new BinExpr(new IntVal(1), new IntVal(2), Binoperator.ADD);
        zero = new IntVal(0);
        
        
        assign2 = new Assign("x", bexp);
        dec1 = new Declaration(new SimpleType(SimpleTypesEnum.INT), "x", new IntVal(3),assign2 );


        ArrayList<Pair<Type,String>> params = new ArrayList<Pair<Type,String>>();
        params.add(new Pair<Type,String>(new SimpleType(SimpleTypesEnum.INT),"n"));

        func1 = new FuncDef(new SimpleType(SimpleTypesEnum.DOUBLE), "foo", params, dec1, zero,null);

        mainFunc = new FuncDef(new SimpleType(SimpleTypesEnum.INT), "main",null, null, zero, func1);

        root = new Prog(mainFunc);
        */

        zero = new IntVal(0);

        ArrayList<SizeParam> tensorDimSP = new ArrayList<SizeParam>();
        tensorDimSP.add(new SPInt(3));
        tensorDimSP.add(new SPInt(2));
        tensorDimSP.add(new SPInt(2));
        TensorType decTensorType = new TensorType(new SimpleType(SimpleTypesEnum.INT), tensorDimSP);
        ArrayList<Expr> tensorDimExpr = new ArrayList<Expr>();
        tensorDimExpr.add(new IntVal(1));
        tensorDimExpr.add(new IntVal(2));
        tensorDimExpr.add(new IntVal(3));
        TensorDefExpr tensorDefExpr = new TensorDefExpr(tensorDimExpr);
        Declaration mainFuncBody = new Declaration(decTensorType, "testTensor", tensorDefExpr, null);

        mainFunc = new FuncDef(new SimpleType(SimpleTypesEnum.INT), "main", null, mainFuncBody, zero, null);
        
        root = new Prog(mainFunc);

        Transpiler.TranspileProg(null, root);

        /* Ikke forbundet med transpiler
        try {
        //Reader reader = new FileReader("CocoR/test.bolt"); //Works works?
        Reader reader = new FileReader("CocoR/test.bolt");
        boltparser.Scanner scanner = new boltparser.Scanner(reader);
        boltparser.Parser parser = new boltparser.Parser(scanner);


        Prog prog = parser.Program(); // Or parser.Parse(), if .Program() doesnt work
        //Prog prog = parser.Parse();  // Generate AST? Has to be a valid node. 
        runTypeCheckerTest(prog);     // Runs TestTypeChecker
        }
         catch (Exception e) {
        e.printStackTrace();
        }
        */

    }

    /* Ikke forbundet med transpiler
    public static void runTypeCheckerTest(Prog prog) {

        try {
            TypeChecker checker = new TypeChecker();
            checker.check(prog);

            System.out.println("Type checking passed!");
        } catch (RuntimeException e) {
            System.err.println("Type checking failed: " + e.getMessage());
        }
    }
    */

}




