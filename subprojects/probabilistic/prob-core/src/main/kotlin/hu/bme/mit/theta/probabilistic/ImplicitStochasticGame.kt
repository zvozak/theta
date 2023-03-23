package hu.bme.mit.theta.probabilistic

import java.util.ArrayDeque


abstract class ImplicitStochasticGame<N, A>: StochasticGame<N, A> {
    private var explored = false
    private val exploredNodes = hashSetOf<N>()

    abstract override val initialNode: N

    fun explore() {
        if (explored) return
        val q = ArrayDeque<N>()
        q.push(initialNode)
        while (q.isNotEmpty()) {
            val curr = q.pop()
            exploredNodes.add(curr)
            val acts = getAvailableActions(curr)
            for (a in acts) {
                val resultNodes = getResult(curr, a).support
                for (n in resultNodes) {
                    if(exploredNodes.add(n))
                        q.push(n)
                }
            }
        }

        explored = true
    }

    override fun getAllNodes(): Collection<N> {
        explore()
        return exploredNodes
    }

    abstract override fun getPlayer(node: N): Int

    abstract override fun getResult(node: N, action: A): FiniteDistribution<N>

    abstract override fun getAvailableActions(node: N): Collection<A>

}