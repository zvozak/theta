package hu.bme.mit.theta.prob.gbar

import hu.bme.mit.theta.prob.game.ThresholdType
import hu.bme.mit.theta.prob.game.analysis.OptimType

sealed class ExpectedRewardQuery<out TResult>(val optim: OptimType) {

    class ThresholdQuery(
            val threshold: Double,
            val comparison: ThresholdType,
            optim: OptimType
    ): ExpectedRewardQuery<Boolean>(optim) {
        override fun canStop(lower: Double, upper: Double): Boolean {
            val lowerSats = comparison.check(threshold, lower)
            val upperSats = comparison.check(threshold, lower)
            if((lowerSats && upperSats) || (!lowerSats && !upperSats)) return true
            return false
        }

        override fun createResult(lower: Double, upper: Double): Boolean {
            val lowerSats = comparison.check(threshold, lower)
            val upperSats = comparison.check(threshold, lower)
            return lowerSats && upperSats
        }
    }

    class ValueQuery(val tolerance: Double, optim: OptimType) : ExpectedRewardQuery<Interval>(optim) {
        override fun canStop(lower: Double, upper: Double): Boolean {
            return Math.abs(upper - lower) < tolerance
        }

        override fun createResult(lower: Double, upper: Double): Interval {
            return Interval(lower, upper)
        }
    }

    /**
     * Checks whether the abstract result is precise enough to answer the query.
     */
    abstract fun canStop(lower: Double, upper: Double): Boolean

    /**
     * Creates an answer for the query using the lower and upper abstraction result.
     * Precondition: canStop(lower, upper)
     */
    abstract fun createResult(lower: Double, upper: Double): TResult
}