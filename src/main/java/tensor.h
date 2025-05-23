#include <vector>
#include <iostream>


class IntTensor{
    public:
        std::vector<int> components;
        std::vector<int> dimensions;

        IntTensor(std::vector<int> comp,std::vector<int> dim){
            components = comp;
            dimensions = dim;
        }

        IntTensor(){

        }

        int access(std::vector<int> indices){
            int realIndex = indices.back();
            for(int i = indices.size() - 2;i >= 0 ;i--){
                realIndex += indices[i]*dimensions[i+1];
            }
            return components[realIndex];
        }

        IntTensor operator+(IntTensor const& tensor){
            IntTensor res;
            res.dimensions = dimensions;
            for(int i = 0;i < components.size();i++){
                res.components.push_back(tensor.components[i] + components[i]);
            }
            return res;
        }

        IntTensor operator-(IntTensor const& tensor){
            IntTensor res;
            res.dimensions = dimensions;
            for(int i = 0;i < components.size();i++){
                res.components.push_back(components[i] - tensor.components[i]);
            }
            return res;
        }

        IntTensor operator<<(IntTensor const& tensor){
            IntTensor res;
            res.dimensions = dimensions;
            for(int i = 0;i < components.size();i++){
                res.components.push_back(tensor.components[i] * components[i]);
            }
            return res;
        }

};

//Scalar multiplication overloading
IntTensor operator*(int scalar, IntTensor const& tensor){
    IntTensor res;
    res.dimensions = tensor.dimensions;
    for(int i = 0;i < tensor.components.size();i++){
        res.components.push_back(scalar * tensor.components[i]);
    }
    return res;
}



class DoubleTensor{
    public:
        std::vector<double> components;
        std::vector<int> dimensions;

        DoubleTensor(std::vector<double> comp,std::vector<int> dim){
            components = comp;
            dimensions = dim;
        }

        DoubleTensor(){

        }

        double access(std::vector<int> indices){
            
            int realIndex = indices.back();
            for(int i = indices.size() - 2;i >= 0 ;i--){
                realIndex += indices[i]*dimensions[i+1];
            }
            return components[realIndex];
        }

        DoubleTensor operator+(DoubleTensor &const tensor){
            DoubleTensor res;
            res.dimensions = dimensions;
            for(int i = 0;i < tensor.components.size();i++){
                res.components[i] = tensor.components[i] + components[i];
            }
            return res;
        }

        DoubleTensor operator-(DoubleTensor &const tensor){
            DoubleTensor res;
            res.dimensions = dimensions;
            for(int i = 0;i < tensor.components.size();i++){
                res.components[i] = tensor.components[i] - components[i];
            }
            return res;
        }

        DoubleTensor operator<<(DoubleTensor &const tensor){
            DoubleTensor res;
            res.dimensions = dimensions;
            for(int i = 0;i < tensor.components.size();i++){
                res.components[i] = tensor.components[i] * components[i];
            }
            return res;
        }


};

DoubleTensor operator*(double scalar, DoubleTensor const& tensor){
    DoubleTensor res;
    res.dimensions = tensor.dimensions;
    for(int i = 0;i < tensor.components.size();i++){
        res.components.push_back(scalar * tensor.components[i]);
    }
    return res;
}