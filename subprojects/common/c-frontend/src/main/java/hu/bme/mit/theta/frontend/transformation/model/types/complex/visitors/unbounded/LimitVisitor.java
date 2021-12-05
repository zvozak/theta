package hu.bme.mit.theta.frontend.transformation.model.types.complex.visitors.unbounded;

import hu.bme.mit.theta.core.stmt.AssumeStmt;
import hu.bme.mit.theta.core.stmt.Stmts;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.abstracttype.AbstractExprs;
import hu.bme.mit.theta.core.type.booltype.BoolExprs;
import hu.bme.mit.theta.frontend.transformation.model.types.complex.CComplexType;
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

import static hu.bme.mit.theta.core.stmt.Stmts.Assume;
import static hu.bme.mit.theta.core.type.abstracttype.AbstractExprs.Geq;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.True;
import static hu.bme.mit.theta.core.type.inttype.IntExprs.Int;

public class LimitVisitor extends hu.bme.mit.theta.frontend.transformation.model.types.complex.visitors.integer.LimitVisitor {
    @Override
    public AssumeStmt visit(CSignedShort type, Expr<?> param) {
        return AssumeStmt.of(True());
    }

    @Override
    public AssumeStmt visit(CUnsignedShort type, Expr<?> param) {
        return Assume(Geq(param, Int(0)));
    }

    @Override
    public AssumeStmt visit(CSignedLongLong type, Expr<?> param) {
        return AssumeStmt.of(True());
    }

    @Override
    public AssumeStmt visit(CUnsignedLongLong type, Expr<?> param) {
        return Assume(Geq(param, Int(0)));
    }

    @Override
    public AssumeStmt visit(CUnsignedLong type, Expr<?> param) {
        return Assume(Geq(param, Int(0)));
    }

    @Override
    public AssumeStmt visit(CSignedLong type, Expr<?> param) {
        return AssumeStmt.of(True());
    }

    @Override
    public AssumeStmt visit(CSignedInt type, Expr<?> param) {
        return AssumeStmt.of(True());
    }

    @Override
    public AssumeStmt visit(CUnsignedInt type, Expr<?> param) {
        return Assume(Geq(param, Int(0)));
    }

    @Override
    public AssumeStmt visit(CSignedChar type, Expr<?> param) {
        return AssumeStmt.of(True());
    }

    @Override
    public AssumeStmt visit(CUnsignedChar type, Expr<?> param) {
        return Assume(Geq(param, Int(0)));
    }
}
