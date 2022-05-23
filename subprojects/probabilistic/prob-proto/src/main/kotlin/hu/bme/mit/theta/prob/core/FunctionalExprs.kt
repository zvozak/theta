package hu.bme.mit.theta.prob.core

import hu.bme.mit.theta.core.model.Valuation
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.LitExpr
import hu.bme.mit.theta.core.type.Type
import hu.bme.mit.theta.core.type.UnaryExpr
import hu.bme.mit.theta.core.type.inttype.IntExprs.Int
import hu.bme.mit.theta.core.type.inttype.IntLitExpr
import hu.bme.mit.theta.core.type.inttype.IntType
import hu.bme.mit.theta.core.type.rattype.RatLitExpr
import hu.bme.mit.theta.core.type.rattype.RatType
import java.math.BigInteger

abstract class FloorExpr<T: Type>(op: Expr<T>): UnaryExpr<T, IntType>(op) {

}

class RatFloorExpr(op: Expr<RatType>): FloorExpr<RatType>(op) {
    private val HASH_SEED = 3656
    private val OPERATOR_LABEL = "FLOOR"

    override fun getType(): IntType = Int()

    override fun eval(`val`: Valuation?): LitExpr<IntType> {
        val opVal = op.eval(`val`) as RatLitExpr
        val num = opVal.num.longValueExact()
        val denom = opVal.denom.longValueExact()
        val longVal = Math.floorDiv(num, denom)
        return IntLitExpr.of(BigInteger.valueOf(longVal))
    }

    override fun getHashSeed() = HASH_SEED

    override fun getOperatorLabel() = OPERATOR_LABEL

    override fun with(op: Expr<RatType>): UnaryExpr<RatType, IntType> {
        return RatFloorExpr(op)
    }
}


class IntFloorExpr(op: Expr<IntType>): FloorExpr<IntType>(op) {
    private val HASH_SEED = 3656
    private val OPERATOR_LABEL = "FLOOR"

    override fun getType(): IntType = Int()

    override fun eval(`val`: Valuation?): LitExpr<IntType> {
        return op.eval(`val`)
    }

    override fun getHashSeed() = HASH_SEED

    override fun getOperatorLabel() = OPERATOR_LABEL

    override fun with(op: Expr<IntType>): UnaryExpr<IntType, IntType> {
        return IntFloorExpr(op)
    }
}

abstract class CeilExpr<T: Type>(op: Expr<T>): UnaryExpr<T, IntType>(op) {

}

class RatCeilExpr(op: Expr<RatType>): CeilExpr<RatType>(op) {
    private val HASH_SEED = 7912
    private val OPERATOR_LABEL = "CEIL"

    override fun getType(): IntType = Int()

    override fun eval(`val`: Valuation?): LitExpr<IntType> {
        val opVal = op.eval(`val`) as RatLitExpr
        val num = opVal.num.longValueExact()
        val denom = opVal.denom.longValueExact()
        val longVal = Math.floorDiv(-num, denom)
        return IntLitExpr.of(BigInteger.valueOf(longVal))
    }

    override fun getHashSeed() = HASH_SEED

    override fun getOperatorLabel() = OPERATOR_LABEL

    override fun with(op: Expr<RatType>): UnaryExpr<RatType, IntType> {
        return RatFloorExpr(op)
    }
}

class IntCeilExpr(op: Expr<IntType>): CeilExpr<IntType>(op) {
    private val HASH_SEED = 3656
    private val OPERATOR_LABEL = "FLOOR"

    override fun getType(): IntType = Int()

    override fun eval(`val`: Valuation?): LitExpr<IntType> {
        return op.eval(`val`)
    }

    override fun getHashSeed() = HASH_SEED

    override fun getOperatorLabel() = OPERATOR_LABEL

    override fun with(op: Expr<IntType>): UnaryExpr<IntType, IntType> {
        return IntFloorExpr(op)
    }
}