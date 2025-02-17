package hu.bme.mit.theta.prob.analysis.pomdp

open class BeliefState<S>(val distribution: Distribution<S>) : State(distribution.toString())

open class BeliefTransition<S: State>(val sourceBeliefState: BeliefState<S>, val destinationBeliefState: BeliefState<S>, val probability: Double)
open class BeliefMDP<S: State, A: Action> (
    states: MutableSet<BeliefState<S>> = mutableSetOf(),
    actions: MutableSet<A> = mutableSetOf(),
    transitionRelation: HashMap<BeliefState<S>, HashMap<A, Distribution<BeliefState<S>>>> = hashMapOf(),
    initState: BeliefState<S>? = null
) : MDP<BeliefState<S>, A>(states, actions, transitionRelation, initState)