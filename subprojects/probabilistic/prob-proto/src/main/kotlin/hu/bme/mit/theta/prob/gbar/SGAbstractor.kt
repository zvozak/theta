package hu.bme.mit.theta.prob.gbar

import hu.bme.mit.theta.analysis.Action
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.prob.game.StochasticGame

interface SGAbstractor<S: State, L, P: Prec> {
    /**
     * Creates an abstraction game with the specified precision.
     */
    fun computeAbstraction(prec: P): StochasticGame<S, Unit, Unit, L>
}