package hu.bme.mit.theta.core.decl;

import hu.bme.mit.theta.core.type.Type;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

public class MultiIndexedConstDecl<DeclType extends Type> extends ConstDecl<DeclType> {
    private final VarDecl<DeclType> varDecl;
    private final List<Integer> indices;

    MultiIndexedConstDecl(final VarDecl<DeclType> varDecl, final List<Integer> indices) {
        super(createName(varDecl, indices), varDecl.getType());
        checkArgument(indices.stream().allMatch((i) -> i >= 0));
        this.varDecl = varDecl;
        this.indices = indices;
    }

    private static String createName(final VarDecl<?> varDecl, final List<Integer> indices) {
        StringBuilder name = new StringBuilder("_").append(varDecl.getName());
        for (int idx : indices) {
            name.append(":").append(idx);
        }
        return name.toString();
    }

    public VarDecl<DeclType> getVarDecl() {
        return varDecl;
    }

    public List<Integer> getIndices() {
        return indices;
    }

}
