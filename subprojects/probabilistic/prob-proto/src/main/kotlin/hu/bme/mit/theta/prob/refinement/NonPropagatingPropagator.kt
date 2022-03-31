package hu.bme.mit.theta.prob.refinement

import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.analysis.pred.PredState
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.cfa.analysis.prec.LocalCfaPrec
import hu.bme.mit.theta.core.model.ImmutableValuation
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.ExprSimplifier
import hu.bme.mit.theta.prob.game.AbstractionGame

object nonPropagatingPropagator: PredicatePropagator {
    override fun <LAbs : StmtAction, LConc> propagate(
        game: AbstractionGame<CfaState<PredState>, LAbs, LConc>,
        refinedState: AbstractionGame.StateNode<CfaState<PredState>, LAbs, LConc>,
        origPrecision: LocalCfaPrec<PredPrec>,
        newPredicates: List<Expr<BoolType>>
    ): LocalCfaPrec<PredPrec> {
        val loc = refinedState.state.loc
        val origLocalPrec = origPrecision.getPrec(loc)
        val newLocalPrec = origLocalPrec.join(PredPrec.of(newPredicates.map {
            ExprSimplifier.simplify(it, ImmutableValuation.empty())
        }))
        return origPrecision.refine(loc, newLocalPrec)
    }
}