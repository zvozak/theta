package hu.bme.mit.theta.probabilistic.gamesolvers.initializers

import hu.bme.mit.theta.probabilistic.Goal
import hu.bme.mit.theta.probabilistic.StochasticGame
import hu.bme.mit.theta.probabilistic.gamesolvers.SGSolutionInitilizer

/**
 * Propagates initial values based on almost sure reachability using graph-based computations.
 */
class AlmostSurePropagatorInitializer<N, A>(
    val baseInitializer: SGSolutionInitilizer<N, A>
) : SGSolutionInitilizer<N, A> {
    override fun getInitialValue(n: N, goal: (Int) -> Goal): Double {
        TODO("Not yet implemented")
    }

    override fun computeAllInitialValues(game: StochasticGame<N, A>, goal: (Int) -> Goal): Map<N, Double> {
        val inits = baseInitializer.computeAllInitialValues(game, goal)
        TODO("Not yet implemented")
    }
}