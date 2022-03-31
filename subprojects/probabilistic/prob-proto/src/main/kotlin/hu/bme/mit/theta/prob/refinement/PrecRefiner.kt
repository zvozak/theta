package hu.bme.mit.theta.prob.refinement

import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.prob.game.AbstractionGame

interface PrecRefiner<P: Prec, S: State, LAbs, LConc> {
    fun refine(
        game: AbstractionGame<S, LAbs, LConc>,
        stateToRefine: AbstractionGame.StateNode<S, LAbs, LConc>,
        origPrecision: P,
        VAmin: StateNodeValues<S, LAbs, LConc>, VAmax: StateNodeValues<S, LAbs, LConc>,
        VCmin: ChoiceNodeValues<S, LAbs, LConc>, VCmax: ChoiceNodeValues<S, LAbs, LConc>
    ): P
}