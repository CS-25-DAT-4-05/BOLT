package Lib;

public class Pair<T1,T2> {
    public T1 elem1;  // Changed to public
    public T2 elem2;  // Changed to public

    public Pair(T1 elem1, T2 elem2) {
        this.elem1 = elem1;
        this.elem2 = elem2;
    }
}