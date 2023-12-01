package hu.bme.mit.theta.prob.analysis.pomdp

interface IMDP<S, A> {
    fun getInitState(): S
    fun getAvailableActions(s: S): Collection<A>
    fun getNextStateDistribution(s: S, a: A): distribution<S>
    fun addState(s: S)
    fun addTransition(s: S, result: distribution<S>, action: A)
    fun setInitState(s: S)
    fun setDiscount(discount: Double)
    fun visualize(filename: String)
}