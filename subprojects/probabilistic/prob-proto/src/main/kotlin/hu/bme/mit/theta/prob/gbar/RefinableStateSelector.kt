package hu.bme.mit.theta.prob.gbar

import hu.bme.mit.theta.prob.game.StochasticGame

interface RefinableStateSelector {
    /**
     * Chooses one of the refinable states to refine. Returns null if no refinable state exists in the game.
     */
    fun <SA, SC, LA, LC> select(game: StochasticGame<SA, SC, LA, LC>): StochasticGame<SA, SC, LA, LC>.Node?
}