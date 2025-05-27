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

//Semantic Analysis
import SemanticAnalysis.TypeEnvironment;
import boltparser.FunctionCFGInfo;

//Java libraries
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

public class Transpiler {
    static boolean hasMain = false;
    static FnameGenerator fnameGenerator = new FnameGenerator();
    static TypeEnvironment globalTypeEnv;
    static Map<String, Type> currentFunctionTypes = new HashMap<>();
    static Map<String, FunctionCFGInfo> currentFunctionCFGs = new HashMap<>();
    static Set<String> declaredDeviceVariables = new HashSet<>();

    // Kernel information storage
    static class KernelInfo {
        public Defer deferBlock;
        public String kernelName;
        public Map<String, Type> externalVariables;

        public KernelInfo(Defer defer, String name, Map<String, Type> vars) {
            this.deferBlock = defer;
            this.kernelName = name;
            this.externalVariables = vars;
        }
    }

    static ArrayList<KernelInfo> kernelsToGenerate = new ArrayList<>();

    public static void TranspileProg(String fileName, Prog root, TypeEnvironment globalTypes, Map<String, FunctionCFGInfo> cfgInfo) {
        // Store the type environment and CFG info
        globalTypeEnv = globalTypes;
        currentFunctionCFGs = cfgInfo;

        // Clear any previous kernels
        kernelsToGenerate.clear();

        File outputFile;
        if(fileName == null){
            fileName = "a.cu";
        } else {
            fileName = fileName + ".cu";
        }
        outputFile = new File(fileName);

        try(FileWriter fWriter = new FileWriter(outputFile)){
            // Add includes
            fWriter.append("#include <cuda_runtime.h>\n");
            fWriter.append("#include <algorithm>\n");
            fWriter.append("#include <vector>\n");
            fWriter.append("#include \"tensor.h\"\n");
            fWriter.append("#include \"kernels.h\"\n\n");

            if(!(root instanceof Prog)){
                throw new Exception("Incorrect root for abstract syntax tree");
            }

            // Generate function prototypes
            addPrototype(fWriter, root.func);
            fWriter.write("\n");

            // Generate function definitions
            transpileDef(fWriter, root.func);
        } catch(Exception e){
            System.out.println("Error generating main CUDA file: " + e.getMessage());
            e.printStackTrace();
        }

        // Generate kernels.h file
        generateKernelsHeader();
    }

    private static void generateKernelsHeader() {
        try(FileWriter kfWriter = new FileWriter(new File("kernels.h"))) {
            kfWriter.append("#include <cuda_runtime.h>\n");
            kfWriter.append("#include <algorithm>\n");
            kfWriter.append("#include \"tensor.h\"\n\n");

            for (KernelInfo kernelInfo : kernelsToGenerate) {
                generateKernelFunction(kfWriter, kernelInfo);
            }
        } catch (Exception e) {
            System.out.println("Error generating kernels.h: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void generateKernelFunction(FileWriter kfWriter, KernelInfo kernelInfo) throws Exception {
        // Generate kernel signature
        kfWriter.append("__global__ void " + kernelInfo.kernelName + "(");

        // Generate parameters based on actual types
        boolean first = true;
        for (Map.Entry<String, Type> entry : kernelInfo.externalVariables.entrySet()) {
            String var = entry.getKey();
            Type type = entry.getValue();

            if (!first) kfWriter.append(", ");

            if (type instanceof TensorType) {
                // For tensors, pass data array and dimensions separately
                TensorType tensorType = (TensorType) type;
                String dataType = getCudaType(tensorType.componentType);
                kfWriter.append(dataType + "* " + var + "_data, int* " + var + "_dims");
            } else if (type instanceof SimpleType) {
                SimpleType simpleType = (SimpleType) type;
                kfWriter.append(getCudaType(simpleType) + " " + var);
            }
            first = false;
        }
        kfWriter.append(") {\n");

        // Generate thread index calculations
        generateThreadIndexing(kfWriter, kernelInfo);

        // Generate kernel body
        transpileStmt(kfWriter, kernelInfo.deferBlock.stmt, null, true, "kernel");

        kfWriter.append("}\n\n");
    }

    private static void generateThreadIndexing(FileWriter kfWriter, KernelInfo kernelInfo) throws Exception {
        String[] cudaAxes = {"x", "y", "z"};

        int dimIndex = 0;
        for (Pair<String, SizeParam> dim : kernelInfo.deferBlock.dim) {
            String threadVar = dim.elem1;
            String axis = cudaAxes[dimIndex];

            kfWriter.append("\tint " + threadVar + " = blockIdx." + axis + " * blockDim." + axis + " + threadIdx." + axis + ";\n");

            // Add bounds check
            String dimLimit = transpileSizeParameters(dim.elem2);
            kfWriter.append("\tif(" + threadVar + " >= " + dimLimit + ") return;\n");

            dimIndex++;
        }
    }

    private static String getCudaType(SimpleType simpleType) {
        switch (simpleType.type) {
            case INT: return "int";
            case DOUBLE: return "double";
            case BOOL: return "bool";
            case CHAR: return "char";
            default: return "int";
        }
    }

    // Analyze external variables and their types
    private static Map<String, Type> analyzeExternalVariables(Defer df, Set<String> threadVars) {
        Set<String> usedVars = new HashSet<>();
        Set<String> declaredVars = new HashSet<>();

        // Collect variables from the defer block
        collectVariablesFromStmt(df.stmt, usedVars, declaredVars);

        // External vars = used - declared - thread vars
        Set<String> externalVars = new HashSet<>(usedVars);
        externalVars.removeAll(declaredVars);
        externalVars.removeAll(threadVars);

        System.out.println("[DEBUG] Used vars: " + usedVars);
        System.out.println("[DEBUG] Declared vars: " + declaredVars);
        System.out.println("[DEBUG] Thread vars: " + threadVars);
        System.out.println("[DEBUG] External vars (before type lookup): " + externalVars);

        // Map to types using the function-local type map
        Map<String, Type> externalVarsWithTypes = new HashMap<>();
        for (String var : externalVars) {
            Type varType = currentFunctionTypes.get(var);
            if (varType != null) {
                externalVarsWithTypes.put(var, varType);
                System.out.println("[DEBUG] Found external var: " + var + " of type " + getTypeString(varType));
            } else {
                // Try the global environment as fallback
                Type globalType = globalTypeEnv.lookup(var);
                if (globalType != null) {
                    externalVarsWithTypes.put(var, globalType);
                    System.out.println("[DEBUG] Found external var in global env: " + var + " of type " + getTypeString(globalType));
                } else {
                    System.out.println("[DEBUG] Could not find type for variable: " + var);
                }
            }
        }

        return externalVarsWithTypes;
    }

    private static String getTypeString(Type type) {
        if (type instanceof SimpleType) {
            return ((SimpleType) type).type.toString();
        } else if (type instanceof TensorType) {
            return "TensorType";
        }
        return "Unknown";
    }

    private static void collectVariablesFromStmt(Stmt stmt, Set<String> used, Set<String> declared) {
        if (stmt == null) return;

        switch (stmt) {
            case Declaration decl:
                declared.add(decl.ident);
                if (decl.expr != null) {
                    collectVariablesFromExpr(decl.expr, used);
                }
                collectVariablesFromStmt(decl.stmt, used, declared);
                break;

            case Assign assign:
                collectVariablesFromExpr(assign.expr, used);
                if (assign.target instanceof Ident) {
                    String targetName = ((Ident) assign.target).name;
                    if (!declared.contains(targetName)) {
                        used.add(targetName);
                    }
                } else {
                    collectVariablesFromExpr(assign.target, used);
                }
                break;

            case Comp comp:
                collectVariablesFromStmt(comp.stmt1, used, declared);
                collectVariablesFromStmt(comp.stmt2, used, declared);
                break;

            case If ifStmt:
                collectVariablesFromExpr(ifStmt.cond, used);
                collectVariablesFromStmt(ifStmt.then, used, declared);
                if (ifStmt.els != null) {
                    collectVariablesFromStmt(ifStmt.els, used, declared);
                }
                break;

            case While whileStmt:
                collectVariablesFromExpr(whileStmt.cond, used);
                collectVariablesFromStmt(whileStmt.stmt, used, declared);
                break;

            case Defer defer:
                collectVariablesFromStmt(defer.stmt, used, declared);
                break;

            default:
                break;
        }
    }

    private static void collectVariablesFromExpr(Expr expr, Set<String> used) {
        if (expr == null) return;

        switch (expr) {
            case Ident ident:
                used.add(ident.name);
                break;

            case BinExpr binExpr:
                collectVariablesFromExpr(binExpr.left, used);
                collectVariablesFromExpr(binExpr.right, used);
                break;

            case UnExpr unExpr:
                collectVariablesFromExpr(unExpr.expr, used);
                break;

            case FuncCallExpr funcCall:
                for (Expr param : funcCall.actualParameters) {
                    collectVariablesFromExpr(param, used);
                }
                break;

            case TensorAccessExpr tensorAccess:
                collectVariablesFromExpr(tensorAccess.listExpr, used);
                for (Expr index : tensorAccess.indices) {
                    collectVariablesFromExpr(index, used);
                }
                break;

            case ParenExpr parenExpr:
                collectVariablesFromExpr(parenExpr.expr, used);
                break;

            case TensorDefExpr tensorDef:
                for (Expr element : tensorDef.exprs) {
                    collectVariablesFromExpr(element, used);
                }
                break;

            default:
                break;
        }
    }

    static void transpileDef(FileWriter fileWriter, FuncDef f) throws Exception {
        if (f == null) return;

        // Clear types for each new function
        currentFunctionTypes.clear();
        declaredDeviceVariables.clear();
        System.out.println("[DEBUG] Starting function: " + f.procname);

        printFunctionHeader(fileWriter, f);
        fileWriter.append(" {\n");

        // Pass function name to transpileStmt for CFG lookup
        transpileStmt(fileWriter, f.funcBody, null, false, f.procname);

        String returnExpr = transpileExpr(f.returnExpr, null, false);
        fileWriter.append("return " + returnExpr + ";\n");
        fileWriter.append("}\n\n");

        transpileDef(fileWriter, f.nextFunc);
    }

    static void transpileStmt(FileWriter fWriter, Stmt s, ArrayList<String> forbiddenIdentifiers, boolean inKernel, String functionName) throws Exception {
        if (s == null) return;

        switch (s) {
            case Assign asgn:
                transpileAssignment(fWriter, asgn, inKernel);
                break;

            case Declaration decl:
                transpileDeclaration(fWriter, decl, inKernel, functionName);
                break;

            case Comp comp:
                transpileStmt(fWriter, comp.stmt1, forbiddenIdentifiers, inKernel, functionName);
                transpileStmt(fWriter, comp.stmt2, forbiddenIdentifiers, inKernel, functionName);
                break;

            case If ifStmt:
                transpileIf(fWriter, ifStmt, forbiddenIdentifiers, inKernel, functionName);
                break;

            case While whileStmt:
                transpileWhile(fWriter, whileStmt, forbiddenIdentifiers, inKernel, functionName);
                break;

            case Defer defer:
                if (forbiddenIdentifiers == null) {
                    transpileDefer(fWriter, defer, functionName);
                } else {
                    throw new Exception("[ERROR] Defer in Defer not allowed!");
                }
                break;

            default:
                break;
        }
    }

    private static void transpileAssignment(FileWriter fWriter, Assign asgn, boolean inKernel) throws Exception {
        String expr = transpileExpr(asgn.expr, null, inKernel);

        if (asgn.isSimpleAssignment()) {
            // Simple assignment: x = value
            String ident = asgn.getIdentifier();
            fWriter.append(ident + " = " + expr + ";\n");
        } else {
            // Tensor assignment: tensor[i,j] = value
            if (asgn.target instanceof TensorAccessExpr) {
                TensorAccessExpr tae = (TensorAccessExpr) asgn.target;
                String baseExpr = transpileExpr(tae.listExpr, null, inKernel);

                if (inKernel) {
                    // Inside kernel - use device methods
                    if (tae.indices.size() == 1) {
                        String index = transpileExpr(tae.indices.get(0), null, inKernel);
                        fWriter.append("tensor_setAt_1d(" + baseExpr + "_data, " + index + ", " + expr + ");\n");
                    } else if (tae.indices.size() == 2) {
                        String index1 = transpileExpr(tae.indices.get(0), null, inKernel);
                        String index2 = transpileExpr(tae.indices.get(1), null, inKernel);
                        fWriter.append("tensor_setAt_2d(" + baseExpr + "_data, " + index1 + ", " + index2 + ", " + baseExpr + "_dims, " + expr + ");\n");
                    } else if (tae.indices.size() == 3) {
                        String index1 = transpileExpr(tae.indices.get(0), null, inKernel);
                        String index2 = transpileExpr(tae.indices.get(1), null, inKernel);
                        String index3 = transpileExpr(tae.indices.get(2), null, inKernel);
                        fWriter.append("tensor_setAt_3d(" + baseExpr + "_data, " + index1 + ", " + index2 + ", " + index3 + ", " + baseExpr + "_dims, " + expr + ");\n");
                    }
                } else {
                    // Host code - use host methods
                    StringBuilder indices = new StringBuilder();
                    for (int i = 0; i < tae.indices.size(); i++) {
                        if (i > 0) indices.append(", ");
                        indices.append(transpileExpr(tae.indices.get(i), null, inKernel));
                    }
                    fWriter.append(baseExpr + ".setAt({" + indices + "}, " + expr + ");\n");
                }
            }
        }
    }

    private static void transpileDeclaration(FileWriter fWriter, Declaration decl, boolean inKernel, String functionName) throws Exception {
        String type = boltToCudaTypeConverter(decl.t);
        String ident = decl.ident;

        // Store the type for later use
        currentFunctionTypes.put(ident, decl.t);
        System.out.println("[DEBUG] Stored type for " + ident + ": " + getTypeString(decl.t));

        if (decl.expr != null) {
            String expr = transpileExpr(decl.expr, decl.t, inKernel);
            fWriter.append(type + " " + ident + " = " + expr + ";\n");
        } else {
            fWriter.append(type + " " + ident + ";\n");
        }

        transpileStmt(fWriter, decl.stmt, null, inKernel, functionName);
    }

    private static void transpileIf(FileWriter fWriter, If ifStmt, ArrayList<String> forbiddenIdentifiers, boolean inKernel, String functionName) throws Exception {
        String cond = transpileExpr(ifStmt.cond, null, inKernel);
        fWriter.append("if(" + cond + ") {\n");
        transpileStmt(fWriter, ifStmt.then, forbiddenIdentifiers, inKernel, functionName);
        fWriter.append("}\n");

        if (ifStmt.els != null) {
            fWriter.append("else {\n");
            transpileStmt(fWriter, ifStmt.els, forbiddenIdentifiers, inKernel, functionName);
            fWriter.append("}\n");
        }
    }

    private static void transpileWhile(FileWriter fWriter, While whileStmt, ArrayList<String> forbiddenIdentifiers, boolean inKernel, String functionName) throws Exception {
        String cond = transpileExpr(whileStmt.cond, null, inKernel);
        fWriter.append("while(" + cond + ") {\n");
        transpileStmt(fWriter, whileStmt.stmt, forbiddenIdentifiers, inKernel, functionName);
        fWriter.append("}\n");
    }

    private static void transpileDefer(FileWriter fWriter, Defer defer, String functionName) throws Exception {
        // Generate kernel name
        String kernelName = fnameGenerator.generateFunctionName();
        System.out.println("[DEBUG] Generated kernel name: " + kernelName);

        // Get thread variables
        Set<String> threadVars = new HashSet<>();
        for (Pair<String, SizeParam> dim : defer.dim) {
            threadVars.add(dim.elem1);
        }

        // Analyze external variables with types
        Map<String, Type> externalVars = analyzeExternalVariables(defer, threadVars);
        System.out.println("[DEBUG] External vars with types: " + externalVars);

        // Generate memory transfers using CFG information
        generateMemoryTransfers(fWriter, externalVars, kernelName, functionName, true); // CPU -> GPU

        // Generate kernel launch configuration
        generateKernelLaunch(fWriter, defer, kernelName);

        // Generate kernel call with device pointers
        generateKernelCallWithDevicePointers(fWriter, kernelName, externalVars);

        // Generate memory transfers back
        generateMemoryTransfers(fWriter, externalVars, kernelName, functionName, false); // GPU -> CPU

        // Store kernel info for later generation
        kernelsToGenerate.add(new KernelInfo(defer, kernelName, externalVars));
    }

    // Method to generate memory transfers based on CFG analysis
    private static void generateMemoryTransfers(FileWriter fWriter, Map<String, Type> externalVars,
                                                String kernelName, String functionName, boolean cpuToGpu) throws Exception {

        System.out.println("[DEBUG] Generating memory transfers - CPU to GPU: " + cpuToGpu);

        // Get CFG info for this function
        FunctionCFGInfo cfgInfo = currentFunctionCFGs.get(functionName);
        if (cfgInfo == null) {
            System.out.println("[DEBUG] No CFG info found for function: " + functionName);
            return;
        }

        if (cpuToGpu) {
            fWriter.append("// Memory transfer: CPU -> GPU\n");
            for (Map.Entry<String, Type> entry : externalVars.entrySet()) {
                String varName = entry.getKey();
                Type varType = entry.getValue();

                if (varType instanceof TensorType) {
                    generateTensorCpuToGpu(fWriter, varName, kernelName, varType);
                }
                // For scalar types, no explicit transfer needed - passed by value
            }
        } else {
            fWriter.append("// Memory transfer: GPU -> CPU\n");
            for (Map.Entry<String, Type> entry : externalVars.entrySet()) {
                String varName = entry.getKey();
                Type varType = entry.getValue();

                if (varType instanceof TensorType) {
                    generateTensorGpuToCpu(fWriter, varName, kernelName, varType);
                }
            }
        }
    }

    // generate CPU to GPU tensor transfer
    private static void generateTensorCpuToGpu(FileWriter fWriter, String varName, String kernelName, Type tensorType) throws Exception {
        if (tensorType instanceof TensorType) {
            TensorType tt = (TensorType) tensorType;
            String dataType = getCudaType(tt.componentType);
            String sizeOfType = "sizeof(" + dataType + ")";

            String deviceDataName = "device_" + varName + "_data";
            String deviceDimsName = "device_" + varName + "_dims";

            // Only declare if not already declared
            if (!declaredDeviceVariables.contains(deviceDataName)) {
                fWriter.append(dataType + "* " + deviceDataName + ";\n");
                fWriter.append("int* " + deviceDimsName + ";\n");
                declaredDeviceVariables.add(deviceDataName);
                declaredDeviceVariables.add(deviceDimsName);
            }

            // Always allocate and copy (in case it was freed earlier)
            fWriter.append("cudaMalloc(&" + deviceDataName + ", " + varName + ".components.size() * " + sizeOfType + ");\n");
            fWriter.append("cudaMalloc(&" + deviceDimsName + ", " + varName + ".dimensions.size() * sizeof(int));\n");
            fWriter.append("cudaMemcpy(" + deviceDataName + ", " + varName + ".getData(), " + varName + ".components.size() * " + sizeOfType + ", cudaMemcpyHostToDevice);\n");
            fWriter.append("cudaMemcpy(" + deviceDimsName + ", " + varName + ".getDims(), " + varName + ".dimensions.size() * sizeof(int), cudaMemcpyHostToDevice);\n");
        }
    }
    // generate GPU to CPU tensor transfer
    private static void generateTensorGpuToCpu(FileWriter fWriter, String varName, String kernelName, Type tensorType) throws Exception {
        if (tensorType instanceof TensorType) {
            TensorType tt = (TensorType) tensorType;
            String dataType = getCudaType(tt.componentType);
            String sizeOfType = "sizeof(" + dataType + ")";

            fWriter.append("cudaMemcpy(" + varName + ".getData(), device_" + varName + "_data, " + varName + ".components.size() * " + sizeOfType + ", cudaMemcpyDeviceToHost);\n");
            fWriter.append("cudaFree(device_" + varName + "_data);\n");
            fWriter.append("cudaFree(device_" + varName + "_dims);\n");
        }
    }

    private static void generateKernelLaunch(FileWriter fWriter, Defer defer, String kernelName) throws Exception {
        int dimCount = defer.dim.size();

        switch (dimCount) {
            case 1:
                String size1 = transpileSizeParameters(defer.dim.get(0).elem2);
                fWriter.append("int blockSize_" + kernelName + " = (256 < " + size1 + ") ? 256 : " + size1 + ";\n");
                fWriter.append("dim3 blockShape_" + kernelName + "(blockSize_" + kernelName + ");\n");
                fWriter.append("dim3 amountOfBlocks_" + kernelName + "((" + size1 + " + blockSize_" + kernelName + " - 1) / blockSize_" + kernelName + ");\n");
                break;

            case 2:
                String sizeX = transpileSizeParameters(defer.dim.get(0).elem2);
                String sizeY = transpileSizeParameters(defer.dim.get(1).elem2);
                fWriter.append("int blockX_" + kernelName + " = (16 < " + sizeX + ") ? 16 : " + sizeX + ";\n");
                fWriter.append("int blockY_" + kernelName + " = (16 < " + sizeY + ") ? 16 : " + sizeY + ";\n");
                fWriter.append("dim3 blockShape_" + kernelName + "(blockX_" + kernelName + ", blockY_" + kernelName + ");\n");
                fWriter.append("dim3 amountOfBlocks_" + kernelName + "((" + sizeX + " + blockX_" + kernelName + " - 1) / blockX_" + kernelName + ", (" + sizeY + " + blockY_" + kernelName + " - 1) / blockY_" + kernelName + ");\n");
                break;

            case 3:
                String sizeX3 = transpileSizeParameters(defer.dim.get(0).elem2);
                String sizeY3 = transpileSizeParameters(defer.dim.get(1).elem2);
                String sizeZ3 = transpileSizeParameters(defer.dim.get(2).elem2);
                fWriter.append("int blockX_" + kernelName + " = (8 < " + sizeX3 + ") ? 8 : " + sizeX3 + ";\n");
                fWriter.append("int blockY_" + kernelName + " = (8 < " + sizeY3 + ") ? 8 : " + sizeY3 + ";\n");
                fWriter.append("int blockZ_" + kernelName + " = (4 < " + sizeZ3 + ") ? 4 : " + sizeZ3 + ";\n");
                fWriter.append("dim3 blockShape_" + kernelName + "(blockX_" + kernelName + ", blockY_" + kernelName + ", blockZ_" + kernelName + ");\n");
                fWriter.append("dim3 amountOfBlocks_" + kernelName + "((" + sizeX3 + " + blockX_" + kernelName + " - 1) / blockX_" + kernelName + ", (" + sizeY3 + " + blockY_" + kernelName + " - 1) / blockY_" + kernelName + ", (" + sizeZ3 + " + blockZ_" + kernelName + " - 1) / blockZ_" + kernelName + ");\n");
                break;
        }
    }

    // generate kernel call with device pointers instead of host pointers
    private static void generateKernelCallWithDevicePointers(FileWriter fWriter, String kernelName, Map<String, Type> externalVars) throws Exception {
        fWriter.append(kernelName + "<<<amountOfBlocks_" + kernelName + ", blockShape_" + kernelName + ">>>(");

        boolean first = true;
        for (Map.Entry<String, Type> entry : externalVars.entrySet()) {
            String var = entry.getKey();
            Type type = entry.getValue();

            if (!first) fWriter.append(", ");

            if (type instanceof TensorType) {
                // Use device pointers instead of host pointers
                fWriter.append("device_" + var + "_data, device_" + var + "_dims");
            } else {
                // Scalar types passed by value
                fWriter.append(var);
            }
            first = false;
        }

        fWriter.append(");\n");
        fWriter.append("cudaDeviceSynchronize();\n");
    }

    static String transpileExpr(Expr e, Type optionalTypeObject, boolean inKernel) throws Exception {
        if (e == null) return "";

        switch (e) {
            case BinExpr be:
                String e1 = transpileExpr(be.left, null, inKernel);
                String e2 = transpileExpr(be.right, null, inKernel);
                return e1 + " " + getBinOp(be.op) + " " + e2;

            case IntVal iv:
                return String.valueOf(iv.value);

            case BoolVal bv:
                return String.valueOf(bv.value);

            case CharVal cv:
                return "'" + cv.val + "'";

            case DoubleVal dv:
                return String.valueOf(dv.val);

            case Ident id:
                return id.name;

            case ParenExpr pe:
                return "(" + transpileExpr(pe.expr, null, inKernel) + ")";

            case UnExpr ue:
                return getUnOp(ue.op) + transpileExpr(ue.expr, null, inKernel);

            case FuncCallExpr func:
                if (func.name.equals("zeros")) {
                    String rows = transpileExpr(func.actualParameters.get(0), null, inKernel);
                    String cols = transpileExpr(func.actualParameters.get(1), null, inKernel);
                    return "IntTensor(std::vector<int>(" + rows + " * " + cols + ", 0), {" + rows + ", " + cols + "})";
                } else if (func.name.equals("ones")) {
                    String rows = transpileExpr(func.actualParameters.get(0), null, inKernel);
                    String cols = transpileExpr(func.actualParameters.get(1), null, inKernel);
                    return "IntTensor(std::vector<int>(" + rows + " * " + cols + ", 1), {" + rows + ", " + cols + "})";
                }
                StringBuilder params = new StringBuilder();
                if (func.actualParameters != null) {
                    for (int i = 0; i < func.actualParameters.size(); i++) {
                        if (i > 0) params.append(", ");
                        params.append(transpileExpr(func.actualParameters.get(i), null, inKernel));
                    }
                }
                return func.name + "(" + params + ")";

            case TensorAccessExpr tae:
                String baseExpr = transpileExpr(tae.listExpr, null, inKernel);

                if (inKernel) {
                    // Inside kernel - use device methods
                    if (tae.indices.size() == 1) {
                        String index = transpileExpr(tae.indices.get(0), null, inKernel);
                        return "tensor_access_1d(" + baseExpr + "_data, " + index + ")";
                    } else if (tae.indices.size() == 2) {
                        String index1 = transpileExpr(tae.indices.get(0), null, inKernel);
                        String index2 = transpileExpr(tae.indices.get(1), null, inKernel);
                        return "tensor_access_2d(" + baseExpr + "_data, " + index1 + ", " + index2 + ", " + baseExpr + "_dims)";
                    } else if (tae.indices.size() == 3) {
                        String index1 = transpileExpr(tae.indices.get(0), null, inKernel);
                        String index2 = transpileExpr(tae.indices.get(1), null, inKernel);
                        String index3 = transpileExpr(tae.indices.get(2), null, inKernel);
                        return "tensor_access_3d(" + baseExpr + "_data, " + index1 + ", " + index2 + ", " + index3 + ", " + baseExpr + "_dims)";
                    }
                } else {
                    // Host code - use host methods
                    StringBuilder indices = new StringBuilder();
                    for (int i = 0; i < tae.indices.size(); i++) {
                        if (i > 0) indices.append(", ");
                        indices.append(transpileExpr(tae.indices.get(i), null, inKernel));
                    }
                    return baseExpr + ".access({" + indices + "})";
                }
                break;

            case TensorDefExpr tde:
                return transpileTensorDef(tde, optionalTypeObject, inKernel);

            default:
                return "";
        }
        return "";
    }

    private static String transpileTensorDef(TensorDefExpr tde, Type optionalTypeObject, boolean inKernel) throws Exception {
        // Get dimensions
        ArrayList<String> dimensions = new ArrayList<>();
        if (optionalTypeObject instanceof TensorType) {
            TensorType tensorType = (TensorType) optionalTypeObject;
            for (SizeParam sp : tensorType.dimensions) {
                dimensions.add(transpileSizeParameters(sp));
            }
        }

        StringBuilder sbDim = new StringBuilder("{");
        for (int i = 0; i < dimensions.size(); i++) {
            if (i > 0) sbDim.append(", ");
            sbDim.append(dimensions.get(i));
        }
        sbDim.append("}");

        // Get components
        ArrayList<String> components = new ArrayList<>();
        getTensorComponents(tde, components, inKernel);

        StringBuilder sbComponents = new StringBuilder("{");
        for (int i = 0; i < components.size(); i++) {
            if (i > 0) sbComponents.append(", ");
            sbComponents.append(components.get(i));
        }
        sbComponents.append("}");

        return boltToCudaTypeConverter(optionalTypeObject) + "(" + sbComponents + ", " + sbDim + ")";
    }

    static void getTensorComponents(TensorDefExpr tde, ArrayList<String> components, boolean inKernel) throws Exception {
        for (Expr expression : tde.exprs) {
            if (expression instanceof TensorDefExpr) {
                getTensorComponents((TensorDefExpr) expression, components, inKernel);
            } else {
                String transpiledExpression = transpileExpr(expression, null, inKernel);
                components.add(transpiledExpression);
            }
        }
    }

    static String transpileSizeParameters(SizeParam sp) {
        switch (sp) {
            case SPIdent spIdent:
                return spIdent.ident;
            case SPInt spInt:
                return String.valueOf(spInt.value);
            default:
                return "";
        }
    }

    // Helper methods
    static void printFunctionHeader(FileWriter fWriter, FuncDef f) throws Exception {
        String rtype = boltToCudaTypeConverter(f.returnType);
        String procName = f.procname;
        StringBuilder params = new StringBuilder();

        if (f.formalParams != null) {
            for (int i = 0; i < f.formalParams.size(); i++) {
                if (i > 0) params.append(", ");
                Pair<Type, String> p = f.formalParams.get(i);
                params.append(boltToCudaTypeConverter(p.elem1)).append(" ").append(p.elem2);
            }
        }

        fWriter.append(rtype + " " + procName + "(" + params + ")");
    }

    static void addPrototype(FileWriter fWriter, FuncDef f) throws Exception {
        if (f == null) return;

        if (f.procname.equals("main")) {
            hasMain = true;
        } else {
            printFunctionHeader(fWriter, f);
            fWriter.append(";\n");
        }

        addPrototype(fWriter, f.nextFunc);
    }

    static String boltToCudaTypeConverter(Type t) throws Exception {
        switch (t) {
            case SimpleType st:
                switch (st.type) {
                    case INT: return "int";
                    case BOOL: return "bool";
                    case CHAR: return "char";
                    case DOUBLE: return "double";
                    default: throw new Exception("Unrecognized simple type");
                }
            case TensorType ct:
                switch (ct.componentType.type) {
                    case INT: return "IntTensor";
                    case DOUBLE: return "DoubleTensor";
                    default: throw new Exception("Unrecognized tensor component type");
                }
            default:
                throw new Exception("Unrecognized type");
        }
    }

    static String getBinOp(Binoperator bin) throws Exception {
        switch (bin) {
            case ADD: return "+";
            case MINUS: return "-";
            case TIMES: return "*";
            case MODULO: return "%";
            case EQUAL: return "==";
            case NEQUAL: return "!=";
            case DIV: return "/";
            case LEQ: return "<=";
            case LT: return "<";
            case GT: return ">";
            case GEQ: return ">=";
            case OR: return "||";
            case AND: return "&&";
            case ELMULT: return ".*";
            default: throw new Exception("Invalid binary operator");
        }
    }

    static char getUnOp(Unaryoperator op) throws Exception {
        switch (op) {
            case NOT: return '!';
            case NEG: return '-';
            default: throw new Exception("Invalid unary operator");
        }
    }
}