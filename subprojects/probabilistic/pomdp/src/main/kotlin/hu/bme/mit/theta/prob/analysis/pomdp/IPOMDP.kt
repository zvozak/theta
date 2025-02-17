package hu.bme.mit.theta.prob.analysis.pomdp

import java.util.*

interface IPOMDP<S, A, O> {
    fun getUnderlyingMDP(): IMDP<S, A>
    fun getStates(): Set<S>
    fun getActions(): Set<A>
    fun isActionAvailableFrom(action: A, state: S): Boolean
    fun getTransition(sourceState: S, action: A): Distribution<S>
    //fun getObservations(state: S): Distribution<O>

    fun getObservations(state: S, action: A): Distribution<O>
    fun computeBeliefMDP(numSteps: Int, fromBelief: BeliefState<S>): IMDP<BeliefState<S>, A>
    fun visualiseUnderlyingMDP(filename: String, withTransitionNodes: Boolean = false)
    fun visualiseBeliefMDP(filename: String, numSteps: Int, withTransitionNode: Boolean = false, fromBelief: BeliefState<S>)
    fun visualise(filename: String)
}