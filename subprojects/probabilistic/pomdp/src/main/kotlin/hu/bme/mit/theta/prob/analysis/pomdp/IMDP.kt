package hu.bme.mit.theta.prob.analysis.pomdp

import hu.bme.mit.theta.common.visualization.Graph

interface IMDP<S, A> {
    //val discount: Double
    //val values: Values
    val states: MutableSet<S>
    val actions: MutableSet<A>
    val transitionRelation: HashMap<S, HashMap<A, Distribution<S>>>
    var initState: S?

    fun getAvailableActions(s: S): Collection<A>
    fun isActionAvailableFrom(a: A, s: S): Boolean
    fun getNextStateDistribution(s: S, a: A): Distribution<S>
    fun addState(s: S)
    fun addTransition(s: S, result: Distribution<S>, action: A)
    fun visualize(filename: String, withTransitionNodes: Boolean = false)
    fun buildGraph(): Graph
    fun addAction(newAction: A)
}

enum class Values(val v: String) { REWARD("REWARD"), COST("COST") }