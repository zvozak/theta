package hu.bme.mit.theta.prob.core

import hu.bme.mit.theta.core.model.Valuation
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.LitExpr
import hu.bme.mit.theta.core.type.Type
import hu.bme.mit.theta.core.type.UnaryExpr
import hu.bme.mit.theta.core.type.abstracttype.PosExpr
import hu.bme.mit.theta.core.type.inttype.IntType

class RealPosExpr(op: Expr<RealType>): PosExpr<RealType>(op) {
    private val HASH_SEED = 6853
    private val OPERATOR_LABEL = "+"

    override fun getType(): RealType {
        TODO("Not yet implemented")
    }

    override fun eval(`val`: Valuation?): LitExpr<RealType> {
        TODO("Not yet implemented")
    }

    override fun getHashSeed(): Int {
        TODO("Not yet implemented")
    }

    override fun getOperatorLabel(): String {
        TODO("Not yet implemented")
    }

    override fun with(op: Expr<RealType>?): UnaryExpr<RealType, RealType> {
        TODO("Not yet implemented")
    }

}