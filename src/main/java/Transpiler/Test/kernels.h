__global__ void A(){
	int xAxis = blockIdx.x * blockDim.x + threadIdx.x;
	if(xAxis > 3){ return; }
}