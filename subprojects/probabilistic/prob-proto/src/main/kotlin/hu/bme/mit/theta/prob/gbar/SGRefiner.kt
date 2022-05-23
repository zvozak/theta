package hu.bme.mit.theta.prob.gbar

import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.prob.game.StochasticGame

interface SGRefiner<S: State, LAbs, LConc, P: Prec> {
    /**
     * Computes a refined precision based on the given abstraction game, the precision used to create that game,
     * and the node values when the abstraction player minimizes/maximizes the reward.
     */
    fun <SC> refine(
            sg: StochasticGame<S, SC, LAbs, LConc>,
            prevPrec: P,
            minValues: Map<StochasticGame<S, SC, LAbs, LConc>.Node, Double>,
            maxValues: Map<StochasticGame<S, SC, LAbs, LConc>.Node, Double>
    ): P
}