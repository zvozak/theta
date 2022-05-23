package hu.bme.mit.theta.prob.gbar

import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Not
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.utils.WithPushPop

class ExpressionReachabilityRewardFunction<S : ExprState>(
        val target: Expr<BoolType>,
        val solver: Solver
) : AbstractRewardFunction<S> {
    override fun lowerReward(s: S): Double {
        WithPushPop(solver).use {
            solver.add(s.toExpr())
            solver.add(Not(target))
            if(solver.status.isSat) return 0.0
            else return 1.0
        }
    }

    override fun upperReward(s: S): Double {
        WithPushPop(solver).use {
            solver.add(s.toExpr())
            solver.add(target)
            if(solver.status.isSat) return 1.0
            else return 0.0
        }
    }

    override fun upperBound(): Double = 1.0
    override fun lowerBound(): Double = 0.0
}