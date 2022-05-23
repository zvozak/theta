package hu.bme.mit.theta.prob.core

import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.Type
import hu.bme.mit.theta.core.type.abstracttype.*
import hu.bme.mit.theta.core.type.fptype.FpType

class RealType: Type, Equational<RealType>, Additive<RealType>, Multiplicative<RealType>,
    Ordered<RealType> {
    override fun Add(ops: MutableIterable<Expr<RealType>>?): AddExpr<RealType> {
        TODO("Not yet implemented")
    }

    override fun Sub(leftOp: Expr<RealType>?, rightOp: Expr<RealType>?): SubExpr<RealType> {
        TODO("Not yet implemented")
    }

    override fun Pos(op: Expr<RealType>?): PosExpr<RealType> {
        TODO("Not yet implemented")
    }

    override fun Neg(op: Expr<RealType>?): NegExpr<RealType> {
        TODO("Not yet implemented")
    }

    override fun Eq(leftOp: Expr<RealType>?, rightOp: Expr<RealType>?): EqExpr<RealType> {
        TODO("Not yet implemented")
    }

    override fun Neq(leftOp: Expr<RealType>?, rightOp: Expr<RealType>?): NeqExpr<RealType> {
        TODO("Not yet implemented")
    }

    override fun Mul(ops: MutableIterable<Expr<RealType>>?): MulExpr<RealType> {
        TODO("Not yet implemented")
    }

    override fun Div(leftOp: Expr<RealType>?, rightOp: Expr<RealType>?): DivExpr<RealType> {
        TODO("Not yet implemented")
    }

    override fun Lt(leftOp: Expr<RealType>?, rightOp: Expr<RealType>?): LtExpr<RealType> {
        TODO("Not yet implemented")
    }

    override fun Leq(leftOp: Expr<RealType>?, rightOp: Expr<RealType>?): LeqExpr<RealType> {
        TODO("Not yet implemented")
    }

    override fun Gt(leftOp: Expr<RealType>?, rightOp: Expr<RealType>?): GtExpr<RealType> {
        TODO("Not yet implemented")
    }

    override fun Geq(leftOp: Expr<RealType>?, rightOp: Expr<RealType>?): GeqExpr<RealType> {
        TODO("Not yet implemented")
    }
}

