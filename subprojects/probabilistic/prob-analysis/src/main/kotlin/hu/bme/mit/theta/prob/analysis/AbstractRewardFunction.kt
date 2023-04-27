package hu.bme.mit.theta.prob.analysis

import hu.bme.mit.theta.analysis.State

interface AbstractRewardFunction<S: State> {
    fun getUpperReward(s: S): Double
    fun getLowerReward(s: S): Double
    val lowerLimit: Double
    val upperLimit: Double
}