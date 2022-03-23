package hu.bme.mit.theta.prob.refinement

import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.analysis.pred.PredState
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.cfa.analysis.prec.LocalCfaPrec
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.prob.AbstractionGame

interface PredicatePropagator {
    fun <LAbs: StmtAction, LConc> propagate(
        game: AbstractionGame<CfaState<PredState>, LAbs, LConc>,
        refinedState: AbstractionGame.StateNode<CfaState<PredState>, LAbs, LConc>,
        origPrecision: LocalCfaPrec<PredPrec>,
        newPredicates: List<Expr<BoolType>>
    ): LocalCfaPrec<PredPrec>
}