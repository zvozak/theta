package hu.bme.mit.theta.prob.gbar

import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.cfa.CFA.Loc
import hu.bme.mit.theta.cfa.analysis.CfaState

/**
 * Used to encode a probabilistic reachability query for a CFA location as an abstract reward function, by
 * assigning reward 1.0 to states with the correct location and 0.0 to all other states.
 * It works only with CfaStates, so the location is always explicitly tracked, meaning that the lower and upper
 * rewards will coincide in each abstract state.
 */
class LocReachabilityRewardFunction<S: ExprState>(
        val errorLoc: Loc
): AbstractRewardFunction<CfaState<S>> {
    override fun lowerReward(s: CfaState<S>): Double =
            if(s.loc == errorLoc) 1.0
            else 0.0

    override fun upperReward(s: CfaState<S>): Double =
            if(s.loc == errorLoc) 1.0
            else 0.0

    override fun upperBound(): Double = 1.0

    override fun lowerBound(): Double = 0.0

}