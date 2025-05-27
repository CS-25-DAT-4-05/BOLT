#include <vector>
#include <iostream>
#include <cuda_runtime.h>

// Device-compatible tensor access functions
__device__ __host__ inline int tensor_access_1d(int* data, int index) {
    return data[index];
}

__device__ __host__ inline int tensor_access_2d(int* data, int row, int col, int* dims) {
    return data[row * dims[1] + col];
}

__device__ __host__ inline void tensor_setAt_1d(int* data, int index, int value) {
    data[index] = value;
}

__device__ __host__ inline void tensor_setAt_2d(int* data, int row, int col, int* dims, int value) {
    data[row * dims[1] + col] = value;
}

__device__ __host__ inline int tensor_access_3d(int* data, int x, int y, int z, int* dims) {
    return data[x * dims[1] * dims[2] + y * dims[2] + z];
}

__device__ __host__ inline void tensor_setAt_3d(int* data, int x, int y, int z, int* dims, int value) {
    data[x * dims[1] * dims[2] + y * dims[2] + z] = value;
}

class IntTensor{
    public:
        std::vector<int> components;
        std::vector<int> dimensions;

        IntTensor(std::vector<int> comp, std::vector<int> dim){
            components = comp;
            dimensions = dim;
        }

        IntTensor(){

        }

        // Add getData() and getDims() methods for kernel parameter passing
        int* getData() { return components.data(); }
        int* getDims() { return dimensions.data(); }

        int access(std::vector<int> indices){
            int realIndex = indices.back();
            for(int i = indices.size() - 2; i >= 0; i--){
                realIndex += indices[i] * dimensions[i+1];
            }
            return components[realIndex];
        }

        IntTensor operator+(IntTensor const& tensor){
            IntTensor res;
            res.dimensions = dimensions;
            for(int i = 0; i < components.size(); i++){
                res.components.push_back(tensor.components[i] + components[i]);
            }
            return res;
        }

        IntTensor operator-(IntTensor const& tensor){
            IntTensor res;
            res.dimensions = dimensions;
            for(int i = 0; i < components.size(); i++){
                res.components.push_back(components[i] - tensor.components[i]);
            }
            return res;
        }

        IntTensor operator<<(IntTensor const& tensor){
            IntTensor res;
            res.dimensions = dimensions;
            for(int i = 0; i < components.size(); i++){
                res.components.push_back(tensor.components[i] * components[i]);
            }
            return res;
        }

        void setAt(std::vector<int> indices, int value) {
            int realIndex = indices.back();
            for(int i = indices.size() - 2; i >= 0; i--) {
                realIndex += indices[i] * dimensions[i+1];
            }
            components[realIndex] = value;
        }
};

//Scalar multiplication overloading
IntTensor operator*(int scalar, IntTensor const& tensor){
    IntTensor res;
    res.dimensions = tensor.dimensions;
    for(int i = 0; i < tensor.components.size(); i++){
        res.components.push_back(scalar * tensor.components[i]);
    }
    return res;
}

class DoubleTensor{
    public:
        std::vector<double> components;
        std::vector<int> dimensions;

        DoubleTensor(std::vector<double> comp, std::vector<int> dim){
            components = comp;
            dimensions = dim;
        }

        DoubleTensor(){

        }

        // Add getData() and getDims() methods for kernel parameter passing
        double* getData() { return components.data(); }
        int* getDims() { return dimensions.data(); }

        double access(std::vector<int> indices){
            int realIndex = indices.back();
            for(int i = indices.size() - 2; i >= 0; i--){
                realIndex += indices[i] * dimensions[i+1];
            }
            return components[realIndex];
        }

        DoubleTensor operator+(const DoubleTensor &tensor){
            DoubleTensor res;
            res.dimensions = dimensions;
            for(int i = 0; i < tensor.components.size(); i++){
                res.components.push_back(tensor.components[i] + components[i]);
            }
            return res;
        }

        DoubleTensor operator-(const DoubleTensor &tensor){
            DoubleTensor res;
            res.dimensions = dimensions;
            for(int i = 0; i < tensor.components.size(); i++){
                res.components.push_back(components[i] - tensor.components[i]);
            }
            return res;
        }

        DoubleTensor operator<<(const DoubleTensor &tensor){
            DoubleTensor res;
            res.dimensions = dimensions;
            for(int i = 0; i < tensor.components.size(); i++){
                res.components.push_back(tensor.components[i] * components[i]);
            }
            return res;
        }

        void setAt(std::vector<int> indices, double value) {
            int realIndex = indices.back();
            for(int i = indices.size() - 2; i >= 0; i--) {
                realIndex += indices[i] * dimensions[i+1];
            }
            components[realIndex] = value;
        }
};

DoubleTensor operator*(double scalar, DoubleTensor const& tensor){
    DoubleTensor res;
    res.dimensions = tensor.dimensions;
    for(int i = 0; i < tensor.components.size(); i++){
        res.components.push_back(scalar * tensor.components[i]);
    }
    return res;
}