package hu.bme.mit.theta.prob.gbar

import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.prob.game.StochasticGame
import hu.bme.mit.theta.prob.game.StochasticGame.Companion.Player
import hu.bme.mit.theta.prob.game.analysis.OptimType

fun setGoal(A: OptimType, C: OptimType): (Player)->OptimType = {
    if (it == Player.A) A else C
}

interface SGSolver {
    /**
     * Computes the value function, i.e. the expected value of the accumulated reward when starting from each state,
     * given that the players optimize as specified by the goal parameter.
     */
    fun <SA, SC, LA, LC> computeValues(
            game: StochasticGame<SA, SC, LA, LC>,
            rewards: AbstractRewardFunction<SA>,
            goal: (Player) -> OptimType
    ): Map<StochasticGame<SA, SC, LA, LC>.Node, Double>
}