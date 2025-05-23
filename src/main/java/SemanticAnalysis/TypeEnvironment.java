package SemanticAnalysis;
import AbstractSyntax.Types.Type;
import java.util.*;

public class TypeEnvironment {
    private final TypeEnvironment parent;
    private final Map<String, Type> bindings;

    public TypeEnvironment() {
        this(null);
    }

    private TypeEnvironment(TypeEnvironment parent) {
        this.parent = parent;
        this.bindings = new HashMap<>();
    }

    public TypeEnvironment newScope() {
        return new TypeEnvironment(this);
    }

    public void bind(String variable, Type type) {
        if (isLocal(variable)) {
            throw new RuntimeException("Variable '" + variable + "' already bound in local scope");
        }
        bindings.put(variable, type);
    }

    public Type lookup(String variable) {
        Type type = bindings.get(variable);
        if (type != null) return type;
        if (parent != null) return parent.lookup(variable);
        return null;
    }

    public boolean isLocal(String variable) {
        return bindings.containsKey(variable);
    }

    public TypeEnvironment copy() {
        TypeEnvironment copy = new TypeEnvironment(parent != null ? parent.copy() : null);
        copy.bindings.putAll(this.bindings);
        return copy;
    }
}