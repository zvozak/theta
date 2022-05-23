package hu.bme.mit.theta.prob.gbar

import hu.bme.mit.theta.analysis.State

interface AbstractRewardFunction<S> {
    /**
     * Returns the lowest reward obtainable in the abstract state,
     * i.e. the minimum reward among all concrete states abstracted by this state.
     */
    fun lowerReward(s: S): Double

    /**
     * Returns the highest reward obtainable in the abstract state,
     * i.e. the maximum reward among all concrete states abstracted by this state.
     */
    fun upperReward(s: S): Double

    /**
     * Returns a number that bounds the whole reward function from below.
     * If no safe upper bound is known a-priori before state-space exploration,
     * Double.POSITIVE_INFINITY is returned.
     */
    fun upperBound(): Double


    /**
     * Returns a number that bounds the whole reward function from above.
     * If no safe upper bound is known a-priori before state-space exploration,
     * Double.NEGATIVE_INFINITY is returned.
     */
    fun lowerBound(): Double
}