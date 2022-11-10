package hu.bme.mit.theta.probabilistic.gamesolvers

import hu.bme.mit.theta.probabilistic.Goal
import hu.bme.mit.theta.probabilistic.StochasticGame

interface SGSolutionInitilizer<N, A> {
    fun getInitialValue(n: N, goal: (Int) -> Goal): Double
    fun computeAllInitialValues(game: StochasticGame<N, A>, goal: (Int) -> Goal): Map<N, Double>
}