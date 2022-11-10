package hu.bme.mit.theta.probabilistic.gamesolvers.initializers

import hu.bme.mit.theta.probabilistic.Goal
import hu.bme.mit.theta.probabilistic.StochasticGame
import hu.bme.mit.theta.probabilistic.gamesolvers.SGSolutionInitilizer

/**
 * Provides an initial lower approximation for computing the probability of reaching a set of target states
 */
class TargetSetLowerInitializer<N, A>(
    val isTarget: (N) -> Boolean
): SGSolutionInitilizer<N, A>{
    override fun computeAllInitialValues(game: StochasticGame<N, A>, goal: (Int) -> Goal): Map<N, Double> =
        game.getAllNodes().associateWith { if(isTarget(it)) 1.0 else 0.0 }

    override fun getInitialValue(n: N, goal: (Int) -> Goal): Double = if(isTarget(n)) 1.0 else 0.0
}