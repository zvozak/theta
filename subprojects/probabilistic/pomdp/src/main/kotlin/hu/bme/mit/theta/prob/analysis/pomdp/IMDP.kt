package hu.bme.mit.theta.prob.analysis.pomdp

interface IMDP<S, A> {
    val discount: Double
    val values: Values
    val states: MutableSet<S>
    val actions: MutableSet<A>
    val transitionRelation: HashMap<S, MutableMap<A, Distribution<S>>>
    var initState: State?

    fun getAvailableActions(s: S): Collection<A>
    fun getNextStateDistribution(s: S, a: A): Distribution<S>
    fun addState(s: S)
    fun addTransition(s: S, result: Distribution<S>, action: A)
    fun visualize(filename: String)
}

enum class Values(val v: String) { REWARD("REWARD"), COST("COST") }