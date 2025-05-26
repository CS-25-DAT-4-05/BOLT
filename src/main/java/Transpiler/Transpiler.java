package Transpiler;

//Abstract syntax
import AbstractSyntax.Expressions.*;
import AbstractSyntax.Definitions.*;
import AbstractSyntax.SizeParams.*;
import AbstractSyntax.Types.*;
import AbstractSyntax.Program.*;
import AbstractSyntax.Statements.*;

//Helper libraries
import Lib.*;

//Java libraries
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;


public class Transpiler {
    static boolean hasMain = false;

    static HashMap<String,Defer> deferMap;

    static boolean isRoot = false;

    static FnameGenerator fnameGenerator = new FnameGenerator();

    static ArrayList<Pair<Defer, String>> defersToBeWrittenInKernel = new ArrayList<>();

    public static void TranspileProg(String fileName, Prog root){
        //Initialization
        Ftable ftable = new Ftable();
        File outputFile;

        if(fileName == null){
            fileName = "a.cu";
        }else{
            fileName = fileName + ".cu";
        }
        outputFile = new File(fileName);

        try(FileWriter fWriter = new FileWriter(outputFile)){
            fWriter.append("#include \"tensor.h\"\n#include \"kernels.h\"\n");

            if(!(root instanceof Prog)){
                throw new Exception("Incorrect root for abstract syntax tree");
            }
            addPrototype(fWriter, root.func);
            fWriter.write("\n");
            transpileDef(fWriter, root.func);
        } catch(Exception e){
            System.out.println(e.getMessage());
        }

        //Generate the kernels.h file
        ArrayList<String> dimensionalCudaAxis = new ArrayList<>();
        dimensionalCudaAxis.add("x");
        dimensionalCudaAxis.add("y");
        dimensionalCudaAxis.add("z"); 
        try(FileWriter kfWriter = new FileWriter(new File("kernels.h"))) {
            for (Pair<Defer, String> deferBlock : defersToBeWrittenInKernel) {
                //Writing kernel definition/head
                kfWriter.append("__global__ void " + deferBlock.elem2 + "(){\n");

                //Get the names of the kernelThreadVarBindings
                ArrayList<String> kernelThreadVarBindList = new ArrayList<>();
                for (Pair<String, SizeParam> dimension : deferBlock.elem1.dim) {
                    String kernelThreadVarIdent = dimension.elem1;
                    kernelThreadVarBindList.add(kernelThreadVarIdent);
                }

                //Write out the dimensionalAxisBindings in CUDA C++
                for (String threadVarBinding : kernelThreadVarBindList) {
                    String cudaAxis = dimensionalCudaAxis.get(kernelThreadVarBindList.indexOf(threadVarBinding));
                    kfWriter.append("\tint " + threadVarBinding + " = blockIdx." + cudaAxis + " * blockDim." + cudaAxis + " + threadIdx." + cudaAxis + ";\n");

                    //Inserting the check for threadIds on each dimension, so that threads dont work out of scope
                    String dimLimit = transpileSizeParameters(deferBlock.elem1.dim.get(kernelThreadVarBindList.indexOf(threadVarBinding)).elem2);
                    kfWriter.append("\tif(" + threadVarBinding + " > " + dimLimit + "){ return; }");
                }

                

                //Transpile the statements in the Defer block inside the Kernel
                transpileStmt(kfWriter, null, kernelThreadVarBindList);

            }


        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

    }

    static void transpileDef(FileWriter fileWriter,FuncDef f) throws Exception{
        if(f == null){
            return;
        }
        printFunctionHeader(fileWriter, f);
        fileWriter.append("{\n");
        transpileStmt(fileWriter, f.funcBody, null);
        String returnExpr = transpileExpr(f.returnExpr, null);

        fileWriter.append("return "+returnExpr + ";\n");
        fileWriter.append("}\n");
        transpileDef(fileWriter, f.nextFunc);
    }

    static void transpileStmt(FileWriter fWriter,Stmt s, ArrayList<String> forbiddenIdentifiersInDefer) throws Exception{
        String ident;
        String expr;
        String cond;
        switch (s) {
            case null:
                fWriter.append("");
                break;
            case Assign asgn:
                ident = asgn.getIdentifier();
                expr = transpileExpr(asgn.expr, null);

                // Check LHS for illegal assignment in Defer
                if (forbiddenIdentifiersInDefer != null && forbiddenIdentifiersInDefer.contains(ident)) {
                    throw new Exception("[ERROR] Assignment to forbidden identifier '" + ident + "' inside defer block. (Multiple Versioning Error)");
                }

                // Check RHS for forbidden identifiers (simple string containment as a start)
                for (String forbidden : forbiddenIdentifiersInDefer) {
                    if (expr.contains(forbidden)) {
                        throw new Exception("[ERROR] Assignment with forbidden identifier '" + ident + "' inside defer block. (Multiple Versioning Error)");
                    }
                }

                fWriter.append(ident + " = " + expr + ";\n");
                break;
            case Declaration dec:
                String type = boltToCudaTypeConverter(dec.t);
                ident = dec.ident;
                expr = transpileExpr(dec.expr, dec.t);

                // Check LHS for illegal assignment in Defer
                if (forbiddenIdentifiersInDefer != null && forbiddenIdentifiersInDefer.contains(ident)) {
                    throw new Exception("[ERROR] Assignment to forbidden identifier '" + ident + "' inside defer block. (Multiple Versioning Error)");
                }

                // Check RHS for forbidden identifiers (simple string containment as a start)
                for (String forbidden : forbiddenIdentifiersInDefer) {
                    if (expr.contains(forbidden)) {
                        throw new Exception("[ERROR] Assignment with forbidden identifier '" + ident + "' inside defer block. (Multiple Versioning Error)");
                    }
                }


                fWriter.append(type + " " + ident + " = " + expr + ";\n");
                transpileStmt(fWriter, dec.stmt, null);
                break;
            case Comp cmp:
                transpileStmt(fWriter, cmp.stmt1, null);
                transpileStmt(fWriter, cmp.stmt2, null);
                break;
            case If ife:
                cond = transpileExpr(ife.cond, null);
                fWriter.append("if(" + cond + "){\n");
                transpileStmt(fWriter, ife.then, null);
                fWriter.append("}\n");
                if(ife.els != null){
                    fWriter.append("else{\n");
                    transpileStmt(fWriter, ife.els, null);
                    fWriter.append("}\n");
                }
                break;
            case While wh:
                cond = transpileExpr(wh.cond, null);
                fWriter.append("while(" + cond + "){\n");
                transpileStmt(fWriter, wh.stmt, null);
                fWriter.append("}\n");
                break;
            case Defer df:
                if (forbiddenIdentifiersInDefer == null) {
                    //Generate a name for the kernel
                    String kernelNameString = fnameGenerator.generateFunctionName();

                    //Write the kernel dimension calculation in CUDA C++
                    StringBuilder sbKernelCall = new StringBuilder();
                    writeKernelDimensions(df.dim, sbKernelCall);

                    //Write the kernel call in C++
                    sbKernelCall.append(kernelNameString + "<<<<amountOfBlocks, blockShape>>>();\n");
                    sbKernelCall.append("cudaDeviceSynchronize();\n");

                    //Write the actual kernel in a header file called kernels.h
                    defersToBeWrittenInKernel.add(new Pair<Defer,String>(df, kernelNameString));
                }
                else{
                    throw new Exception("[ERROR] Defer in Defer not allowed!");
                }

                break;
            default:  
                break;
        }
    }
    
    static void writeKernelDimensions(ArrayList<Pair<String, SizeParam>> dim, StringBuilder sbObj){
        String size1 = "";
        String size2 = "";
        String size3 = "";

        switch (dim.size()) {
            case 1:
                size1 = transpileSizeParameters(dim.get(0).elem2);

                sbObj.append("dim3 blockShape(341, 341, 341);\n");
                sbObj.append("dim3 amountOfBlocks((" + size1 + " / 341) + 1, 1,1);\n");
                break;
            
            case 2:
                size1 = transpileSizeParameters(dim.get(0).elem2);
                size2 = transpileSizeParameters(dim.get(1).elem2);

                sbObj.append("dim3 blockShape(341, 341, 341);\n");
                sbObj.append("dim3 amountOfBlocks((" + size1 + " / 341) + 1," + " (" + size1 + " / 341) + 1,1);\n");
                break;

            case 3:
                size1 = transpileSizeParameters(dim.get(0).elem2);
                size2 = transpileSizeParameters(dim.get(1).elem2);
                size3 = transpileSizeParameters(dim.get(2).elem2);

                sbObj.append("dim3 blockShape(341, 341, 341);\n");
                sbObj.append("dim3 amountOfBlocks((" + size1 + " / 341) + 1," + " (" + size2 + " / 341) + 1," + " (" + size3 + " / 341) + 1);\n");
                break;
        
            default:
                break;
        }
    }

    static String transpileExpr(Expr e, Type optionalTypeObject) throws Exception{
        StringBuilder sb = new StringBuilder();
        switch (e) {
            case BinExpr be:
                String e1 = transpileExpr(be.left, null);
                String e2 = transpileExpr(be.left, null);
                return e1 +  getBinOp(be.op) + e2;
            case IntVal iv:
                return "" + iv.value;
            case BoolVal bv:
                return "" + bv.value;
            case CharVal cv:
                return "" + cv.val;
            case DoubleVal dv:
                return "" + dv.val;
            case Ident id:
                return id.name;
            case ParenExpr pe:
                return "("+ transpileExpr(pe.expr, null)+")";
            case UnExpr ue:
                return getUnOp(ue.op) + transpileExpr(ue.expr, null);
            case FuncCallExpr func: //Mangler at tage højde for parametriske tensorer
                String params = "";
                if(func.actualParameters != null){
                    for (Expr exp : func.actualParameters) {
                        if(sb.length() != 0){
                            sb.append(",");
                        }
                        sb.append(transpileExpr(exp, null));
                    }
                    params = sb.toString();
                }
                return func.name + "(" + params + ")";
            case TensorAccessExpr tae:
                for(Expr exp: tae.indices){
                    if(sb.length() != 0){
                        sb.append(',');
                    }
                    sb.append(transpileExpr(exp, null));
                }
                String indices = sb.toString();
                return transpileExpr(tae.listExpr, null) + ".access({" + indices + "})";
            case TensorDefExpr tde: //Mangler type information fra typecheck 
                //Getting dimensions from the Tensor declaration (type?)
                ArrayList<String> dimensions = new ArrayList<>();
                getDimensions(optionalTypeObject, dimensions);
                //sbDim = Tensor dimensions for Tensor declaration in C++
                StringBuilder sbDim = new StringBuilder("{");
                for (String dim : dimensions) {
                    if (sbDim.length() != 1) {
                        sbDim.append(",");
                    }
                    sbDim.append(dim);
                }
                sbDim.append("}");
            
                //Getting the components to be assigned to the Tensor
                ArrayList<String> components = new ArrayList<>();
                getTensorComponents(tde, components);
                //sbComponents = Tensor Components for Tensor declaration in C++
                StringBuilder sbComponents = new StringBuilder("{");
                for (String comp : components) {
                    if(sbComponents.length()!=1){
                        sbComponents.append(",");
                    }
                    sbComponents.append(comp);
                }
                sbComponents.append("}");
                
                return "new " + boltToCudaTypeConverter(optionalTypeObject) + "(" + sbComponents.toString() + ", " + sbDim.toString() + ")";
            default:
                return "";
        }
    }

    static void getDimensions(Type tensorTypeObj, ArrayList<String> dimensions){
        switch (tensorTypeObj) {
            case TensorType typeObj:
                for (SizeParam sp : typeObj.dimensions) {
                    dimensions.add(transpileSizeParameters(sp));
                } 
                break;
        
            default:
                break;
        }
    }

    static String transpileSizeParameters(SizeParam sp){
        switch (sp) {
            case SPIdent spIdent:
                return spIdent.ident;
            case SPInt spInt:
                int value = spInt.value;
                return Integer.toString(value);
            default:
                return "";
        }
    }

    static void getTensorComponents(Expr e,ArrayList<String> components) throws Exception{
        switch (e) {
            case TensorDefExpr tde:
                for (Expr expression : tde.exprs) {
                    try {
                        //Transpiling the expressions given as dimension parameters for the Tensor declaration
                        String transpiledExpression = transpileExpr(expression, null);
                        components.add(transpiledExpression);
                    } catch (Exception ex) {
                        System.out.println("Short description of Exception: " + ex.toString());
                        System.out.println("Message from Exception: " + ex.getMessage());
                        throw new Exception("[ERROR] Failed at transpiling the expressions during a tensor declaration!");
                    }
                    
                }
                //dim.add(tde.exprs.size());
                //getDim(tde.exprs.get(0), dim);
                break;
            default:
                return;
        }
    }    

    
    //#### Auxiliary functions ########################################
    //Mangler at tage højde for parametriske tensorer
    static void printFunctionHeader(FileWriter fWriter, FuncDef f) throws Exception{
        String rtype = boltToCudaTypeConverter(f.returnType);
        String procName = f.procname;
        String paramters = "";
        StringBuilder sb = new StringBuilder();
        if(f.formalParams != null){
            for (Pair<Type,String> p : f.formalParams) {
                if(!(sb.length() == 0)){
                    sb.append(',');
                }
                sb.append(boltToCudaTypeConverter(p.elem1));
                sb.append(" ");
                sb.append(p.elem2);
            }
            paramters = sb.toString();
        }
        String writeString = rtype + " " + procName + "(" + paramters +  ")";
        fWriter.append(writeString);
    }


    static void addPrototype(FileWriter fWriter,FuncDef f) throws Exception{
        if(f == null){
            return;
        }

        if(f.procname.equals("main")){
            hasMain = true;
            addPrototype(fWriter,f.nextFunc);
        }
        else{
            printFunctionHeader(fWriter, f);
            fWriter.append(";\n");
            addPrototype(fWriter, f.nextFunc);
        }
    }

    static String boltToCudaTypeConverter(Type t) throws Exception{
        switch (t) {
            case SimpleType st:
                switch (st.type) {
                    case INT:
                        return "int";
                    case BOOL:
                        return "bool";
                    case CHAR:
                        return "char";
                    case DOUBLE:
                        return "double";
                    default:
                        throw new Exception("Unrecognized type");
                }
            case TensorType ct:
                switch (ct.componentType.type) {
                    case INT:
                        return "IntTensor";
                    case DOUBLE:
                        return "DoubleTensor";
                    default:
                        throw new Exception("Unrecognized type");
                }
            default:
                throw new Exception("Unrecognized type");
        }

    }

    static String getBinOp(Binoperator bin) throws Exception{
        switch (bin) {
            case ADD:
                return "+";
            case MINUS:
                return "-";
            case TIMES:
                return "*";
            case MODULO:
                return "%";
            case EQUAL:
                return "==";
            case NEQUAL:
                return "!=";
            case DIV:
                return "/";
            case LEQ:
                return "<=";
            case LT:
                return "<";
            case GT:
                return ">";
            case GEQ:
                return ">=";
            case OR:
                return "||";
            case AND:
                return "&&";
            case ELMULT:
                return "<<";    
            default:
                throw new Exception("invalid operator");
        }
    }

    static char getUnOp(Unaryoperator op) throws Exception{
        switch (op) {
            case NOT:
                return '!';
            case NEG:
                return '-';
            default:
                throw new Exception("invalid operator");
        }
    }
    

}