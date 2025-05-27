# BOLT - Basic Optimized Language for Threads

A domain-specific language (DSL) designed to simplify GPU programming by providing high-level abstractions for CUDA development while maintaining familiar imperative syntax.


## Contributing

This project was developed as part of Aalborg University's 4th semester Computer Science program. The language specification and formal semantics are detailed in our project report.

**Note**: BOLT is an academic project demonstrating DSL design principles. While functional, it is primarily intended for educational purposes and research into GPU programming language design.

## Overview

BOLT addresses the complexity barrier in GPU programming by offering explicit but minimal abstractions for GPU execution. The language targets developers who are familiar with imperative programming but lack specialized knowledge in parallel computing or hardware-oriented programming.

### Key Features

- **Familiar Imperative Syntax**: C-like syntax that's accessible to developers with experience in languages like C++, Java, or Python
- **GPU Execution Abstractions**: Simple `defer` blocks for GPU computation offloading without manual thread management
- **Native Tensor Support**: Built-in support for vectors, matrices, and multi-dimensional tensors with compile-time type checking
- **Automatic Memory Management**: Compiler handles GPU memory allocation, data transfers, and synchronization
- **Static Type System**: Strong typing with tensor shape validation for compile-time safety

## Quick Start

### Prerequisites

- Java 8 or higher
- NVIDIA CUDA Toolkit (for compiling generated code)
- `tensor.h` header file (provided)

### Running the Compiler

1. **Compile your BOLT program:**
   ```bash
   java -cp out/production/BOLT boltparser.Main your_program.bolt
   ```

2. **Compile the generated CUDA code:**
   ```bash
   nvcc -o your_program your_program.cu
   ```

3. **Run the executable:**
   ```bash
   ./your_program
   ```

## Language Syntax

### Basic Structure
```bolt
func: return_type function_name() {
    // variable declarations
    // defer blocks for GPU computation
    return value;
}
```

### Tensor Types
- **Vectors**: `vector[int, 5]` - 1D arrays
- **Matrices**: `matrix[int, 3, 3]` - 2D arrays  
- **Tensors**: `tensor[int, 2, 3, 4]` - Multi-dimensional arrays

### GPU Computation with `defer`
```bolt
defer[(thread_var, size)] {
    // GPU code here
}

defer[(i, rows), (j, cols)] {
    // 2D GPU code here
}
```

## Example Programs

### 1. Vector Addition
```bolt
func: int main() {
    vector[int, 4] a = {1, 2, 3, 4};
    vector[int, 4] b = {5, 6, 7, 8};
    int multiplier = 10;

    defer[(i, 4)] {
        a[i] = a[i] + b[i] + multiplier;
    }

    return 0;
}
```

### 2. Matrix Scaling
```bolt
func: int main() {
    matrix[int, 3, 3] mat = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    int factor = 2;
    
    defer[(i, 3), (j, 3)] {
        mat[i, j] = mat[i, j] * factor;
    }
    
    return 0;
}
```

### 3. Conditional Processing
```bolt
func: int main() {
    matrix[int, 4, 4] mat = {{1, 2, 3, 4}, {5, 6, 7, 8}, {9, 10, 11, 12}, {13, 14, 15, 16}};
    int threshold = 8;
    
    defer[(i, 4), (j, 4)] {
        if (mat[i, j] > threshold) {
            mat[i, j] = mat[i, j] * 2;
        } else {
            mat[i, j] = 0;
        }
    }
    
    return 0;
}
```

### 4. Loops in GPU Code
```bolt
func: int main() {
    vector[int, 6] data = {1, 4, 9, 16, 25, 36};
    int limit = 20;
    
    defer[(i, 6)] {
        int value = data[i];
        while (value > limit) do {
            value = value - 5;
        }
        data[i] = value;
    }
    
    return 0;
}
```

## Language Features

### Control Flow
- **Conditionals**: `if (condition) { } else { }`
- **Loops**: `while (condition) do { }`
- **Assignments**: `variable = expression;`
- **Declarations**: `type variable = initial_value;`

### Operators
- **Arithmetic**: `+`, `-`, `*`, `/`, `%`
- **Comparison**: `==`, `!=`, `<`, `<=`, `>`, `>=`
- **Logical**: `&&`, `||`, `!`

### Data Types
- **Simple**: `int`, `double`, `char`, `bool`
- **Tensor**: `vector[type, size]`, `matrix[type, rows, cols]`, `tensor[type, dim1, dim2, ...]`

## Generated Output

The BOLT compiler generates:
- `program_name.cu` - Main CUDA file with host code
- `kernels.h` - GPU kernel definitions
- Automatic memory management (malloc, memcpy, free)
- Proper thread configuration for 1D, 2D, and 3D grids

## Architecture

### Compiler Pipeline
1. **Lexical & Syntax Analysis** (Coco/R)
2. **AST Construction** 
3. **Type Checking** with tensor shape validation
4. **Control Flow Analysis** for memory transfer optimization
5. **CUDA Code Generation** (Transpiler)

### Memory Management
- Automatic CPU ↔ GPU data transfers
- Efficient memory allocation and cleanup
- No manual CUDA memory management required

## Project Status

**Current Status**: Functional prototype with core features implemented

**Working Features**:
- Vector, matrix, and tensor operations
- 1D, 2D, and 3D defer blocks
- Control flow (if-else, while loops)
- Automatic memory management
- Type checking and validation
- CUDA code generation


For the complete grammar specification, see the project report


