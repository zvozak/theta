package hu.bme.mit.theta.frontend.transformation.model.types.complex.visitors.unbounded;

import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.frontend.transformation.model.types.complex.integer.cchar.CSignedChar;
import hu.bme.mit.theta.frontend.transformation.model.types.complex.integer.cchar.CUnsignedChar;
import hu.bme.mit.theta.frontend.transformation.model.types.complex.integer.cint.CSignedInt;
import hu.bme.mit.theta.frontend.transformation.model.types.complex.integer.cint.CUnsignedInt;
import hu.bme.mit.theta.frontend.transformation.model.types.complex.integer.clong.CSignedLong;
import hu.bme.mit.theta.frontend.transformation.model.types.complex.integer.clong.CUnsignedLong;
import hu.bme.mit.theta.frontend.transformation.model.types.complex.integer.clonglong.CSignedLongLong;
import hu.bme.mit.theta.frontend.transformation.model.types.complex.integer.clonglong.CUnsignedLongLong;
import hu.bme.mit.theta.frontend.transformation.model.types.complex.integer.cshort.CSignedShort;
import hu.bme.mit.theta.frontend.transformation.model.types.complex.integer.cshort.CUnsignedShort;

public class CastVisitor extends hu.bme.mit.theta.frontend.transformation.model.types.complex.visitors.integer.CastVisitor {
    @Override
    public Expr<?> visit(CSignedShort type, Expr<?> param) {
        return param;
    }

    @Override
    public Expr<?> visit(CUnsignedShort type, Expr<?> param) {
        return param;
    }

    @Override
    public Expr<?> visit(CSignedLongLong type, Expr<?> param) {
        return param;
    }

    @Override
    public Expr<?> visit(CUnsignedLongLong type, Expr<?> param) {
        return param;
    }

    @Override
    public Expr<?> visit(CUnsignedLong type, Expr<?> param) {
        return param;
    }

    @Override
    public Expr<?> visit(CSignedLong type, Expr<?> param) {
        return param;
    }

    @Override
    public Expr<?> visit(CSignedInt type, Expr<?> param) {
        return param;
    }

    @Override
    public Expr<?> visit(CUnsignedInt type, Expr<?> param) {
        return param;
    }

    @Override
    public Expr<?> visit(CSignedChar type, Expr<?> param) {
        return param;
    }

    @Override
    public Expr<?> visit(CUnsignedChar type, Expr<?> param) {
        return param;
    }
}
