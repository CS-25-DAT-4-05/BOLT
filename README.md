# BOLT - Basic Optimized Language for Threads

A domain-specific language (DSL) designed to simplify GPU programming by providing high-level abstractions for CUDA development while maintaining familiar imperative syntax.

## Overview

BOLT addresses the complexity barrier in GPU programming by offering explicit but minimal abstractions for GPU execution. The language targets developers who are familiar with imperative programming but lack specialized knowledge in parallel computing or hardware-oriented programming.

### Key Features

- **Familiar Imperative Syntax**: C-like syntax that's accessible to developers with experience in languages like C++, Java, or Python
- **GPU Execution Abstractions**: Simple `defer` blocks for GPU computation offloading without manual thread management
- **Native Tensor Support**: Built-in support for vectors, matrices, and multi-dimensional tensors with compile-time type checking
- **Automatic Memory Management**: Compiler handles GPU memory allocation, data transfers, and synchronization
- **Static Type System**: Strong typing with parameterized tensor types for compile-time safety

## Language Design

BOLT follows a transpiler architecture that converts BOLT source code into CUDA C++, allowing programs to leverage NVIDIA's mature compiler optimizations while providing a simplified programming interface.

### Core Abstractions

#### GPU Execution with `defer`
```bolt
func: int vectorAdd(tensor[int, 1000] A, tensor[int, 1000] B) {
    tensor[int, 1000] result;
    
    defer[(i, 1000)] {
        result[i] = A[i] + B[i];
    }
    
    return sum(result);
}
```

#### Tensor Types
- **Vectors**: `vector[type, size]`
- **Matrices**: `matrix[type, rows, cols]` 
- **Tensors**: `tensor[type, dim1, dim2, ...]`

#### Parametric Types
```bolt
func: tensor[int, n] scale(tensor[int, n] input, int factor) {
    tensor[int, n] result;
    defer[(i, n)] {
        result[i] = input[i] * factor;
    }
    return result;
}
```

## Architecture

### Compiler Pipeline

1. **Frontend (Coco/R)**
   - Lexical analysis and parsing
   - AST construction with embedded semantic actions
   - LL(1) grammar for predictable parsing

2. **Semantic Analysis**
   - Type checking with tensor shape validation
   - Symbol table management
   - Static analysis for GPU/CPU data placement

3. **Backend (Transpiler)**
   - CUDA C++ code generation
   - Memory management insertion
   - Thread configuration automation

### Type System

BOLT implements a strong, static type system with:
- Simple types: `int`, `double`, `char`, `bool`
- Complex types.


## Getting Started

### Prerequisites

- Java 8 or higher
- NVIDIA CUDA Toolkit (for generated code compilation)
- Coco/R parser generator

### Building BOLT

### Running the Compiler

```bash
java -cp ".:lib/*" boltparser.Main examples/vector_add.bolt
```

## Example Programs

### Simple Vector Addition
```bolt
func: int main() {
    vector[int, 10] a = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    vector[int, 10] b = {10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
    vector[int, 10] result;
    
    defer[(i, 10)] {
        result[i] = a[i] + b[i];
    }
    
    return 0;
}
```

### Matrix Multiplication
```bolt
func: matrix[int, m, n] matmul(matrix[int, m, k] A, matrix[int, k, n] B) {
    matrix[int, m, n] result;
    
    defer[(i, m), (j, n)] {
        int sum = 0;
        for (int idx = 0; idx < k; idx = idx + 1) {
            sum = sum + A[i, idx] * B[idx, j];
        }
        result[i, j] = sum;
    }
    
    return result;
}
```


## Language Specification

### Grammar (EBNF)

```ebnf
Program = {FunctionDefinition}

FunctionDefinition = 
    "func:" Type IDENT "(" [FormalParameter {"," FormalParameter}] ")"
    "{" FunctionBody "return" [Expression] ";" "}"

Type = SimpleType | ComplexType

SimpleType = "double" | "int" | "char" | "bool" | "void"

ComplexType = 
    "vector" "[" SimpleType "," SizeExpression "]" |
    "matrix" "[" SimpleType "," SizeExpression "," SizeExpression "]" |
    "tensor" "[" SimpleType "," SizeExpression {"," SizeExpression} "]"
```

For the complete grammar specification, see the project report


## Contributing

This project was developed as part of Aalborg University's 4th semester Computer Science program. The language specification and formal semantics are detailed in our project report.

### Development Team


**Note**: BOLT is an academic project demonstrating DSL design principles. While functional, it is primarily intended for educational purposes and research into GPU programming language design.
