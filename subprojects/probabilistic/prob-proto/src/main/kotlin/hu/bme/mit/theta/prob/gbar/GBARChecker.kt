package hu.bme.mit.theta.prob.gbar

import hu.bme.mit.theta.analysis.Action
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.prob.game.analysis.OptimType
import hu.bme.mit.theta.prob.game.analysis.select

class GBARChecker<S: State, A: Action, P: Prec, L>(
        val abstractor: SGAbstractor<S, L, P>,
        val refiner: SGRefiner<S, Unit, L, P>,
        val sgSolver: SGSolver,
        val rewardFunction: AbstractRewardFunction<S>
) {
    fun <R> check(initPrec: P, query: ExpectedRewardQuery<R>): R {
        var currPrec = initPrec
        while (true) {
            val game = abstractor.computeAbstraction(currPrec)
            val minValues = sgSolver.computeValues(
                    game, rewardFunction, setGoal(OptimType.MIN, query.optim)
            )
            val maxValues = sgSolver.computeValues(
                    game, rewardFunction, setGoal(OptimType.MAX, query.optim)
            )
            currPrec = refiner.refine(game, currPrec, minValues, maxValues)
            val lower = query.optim.select(minValues) ?: rewardFunction.lowerBound()
            val upper = query.optim.select(maxValues) ?: rewardFunction.upperBound()
            if(query.canStop(lower, upper)) return query.createResult(lower, upper)
        }
    }
}