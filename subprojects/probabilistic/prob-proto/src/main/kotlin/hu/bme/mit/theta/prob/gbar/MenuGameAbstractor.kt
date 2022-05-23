package hu.bme.mit.theta.prob.gbar

import hu.bme.mit.theta.analysis.Action
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.prob.game.StochasticGame

class MenuGameAbstractor<S : State, A : Action, P : Prec> : SGAbstractor<S, A, P> {
    override fun computeAbstraction(prec: P): StochasticGame<S, Unit, Unit, A> {
        TODO("Not yet implemented")
    }
}