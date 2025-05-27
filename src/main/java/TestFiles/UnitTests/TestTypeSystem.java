package TestFiles.UnitTests;

import AbstractSyntax.SizeParams.*;
import AbstractSyntax.Types.*;
import java.util.ArrayList;

/*
 * Unit tests for verifying type equality in the type system
 *
 * This includes:
 * - Simple type equality (INT == INT)
 * - Tensor type equality (same dimensions and base type)
 * - TensorType string conversion
 *
 * Each test checks logical correctness and edge cases in type comparisons
 */

public class TestTypeSystem {
    public static void main(String[] args) {
        System.out.println(" Running TestTypeSystem....");

        testIntTypeEquality();       //Test equality between two INT types
        testTensorTypeEquality();    //Test equality of tensor types with same dimensions
        testTensorTypeToString();    //Test toString formatting for tensor types
    }

    //Tests equality of two identical SimpleTypes, for example; they are both INT
    static void testIntTypeEquality() {
        Type t1 = new SimpleType(SimpleTypesEnum.INT);
        Type t2 = new SimpleType(SimpleTypesEnum.INT);

        if (!(t1 instanceof SimpleType) || !(t2 instanceof SimpleType)) {
            System.out.println(" testIntTypeEquality failed | not instances of SimpleType");
        } else if (((SimpleType) t1).type != ((SimpleType) t2).type) {
            System.out.println(" testIntTypeEquality failed | SimpleType enums not equal");
        } else {
            System.out.println(" testIntTypeEquality passed");
        }
    }

    //Tests equality of two TensorTypes with the same base type and dimensions
    static void testTensorTypeEquality() {
    SimpleType baseType = new SimpleType(SimpleTypesEnum.INT);

    ArrayList<SizeParam> dims1 = new ArrayList<>();
    dims1.add(new SPIdent("m"));
    dims1.add(new SPIdent("n"));

    ArrayList<SizeParam> dims2 = new ArrayList<>();
    dims2.add(new SPIdent("m"));
    dims2.add(new SPIdent("n"));

    TensorType t1 = new TensorType(baseType, dims1);
    TensorType t2 = new TensorType(baseType, dims2);

    //Fallback: compare toString representations
    if (!t1.toString().equals(t2.toString())) {
        System.out.println(" testTensorTypeEquality failed | tensor string mismatch");
    } else {
        System.out.println(" testTensorTypeEquality passed");
    }
    }


    //Tests string representation of a TensorType
    static void testTensorTypeToString() {
        ArrayList<SizeParam> dims = new ArrayList<>();
        dims.add(new SPIdent("m"));
        dims.add(new SPIdent("n"));

        TensorType t = new TensorType(new SimpleType(SimpleTypesEnum.INT), dims);

        String actual = t.toString();
        //Note: .toString() (might not work...)
        if (!actual.contains("m") || !actual.contains("n")) {
            System.out.println(" testTensorTypeToString failed | unexpected format: " + actual);
        } else {
            System.out.println(" testTensorTypeToString passed");
        }
    }
}
