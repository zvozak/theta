package hu.bme.mit.theta.probabilistic.gamesolvers

import hu.bme.mit.theta.probabilistic.StochasticGameSolver
import hu.bme.mit.theta.probabilistic.AnalysisTask

/**
 * Implementation of standard Value Iteration.
 * Assumes all nodes to have at least one available action (absorbing states should have self-loops).
 * Only usable for finite games.
 */
class VISolver<N, A>(
    val tolerance: Double,
    val initializer: SGSolutionInitilizer<N, A>,
    val useGS: Boolean = false
): StochasticGameSolver<N, A> {
    override fun solve(analysisTask: AnalysisTask<N, A>): Map<N, Double> {
        val game = analysisTask.game
        val goal = analysisTask.goal

        // This should result in an exception if the game is infinite
        val allNodes = game.getAllNodes()
        val init = initializer.computeAllInitialValues(game, goal)
        var curr = init
        do {
            val stepResult =
                if(useGS) bellmanStepGS(game, curr, goal, analysisTask.discountFactor)
                else bellmanStep(game, curr, goal, analysisTask.discountFactor)
            val maxChange = stepResult.maxChange
            curr = stepResult.result
        } while (maxChange > tolerance)
        return curr
    }
}