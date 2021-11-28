package hu.bme.mit.theta.prob

import hu.bme.mit.theta.core.stmt.Stmt

// TODO: better solution for storing the statements leading to the state instead of a metadata field
data class EnumeratedDistribution<D, M>(
    val pmf: Map<D, Double>,
    val metadata: Map<D, M> = mapOf()
    ) {
    constructor(pairs: List<Pair<D, Double>>): this(pairs.toMap())

    companion object {
        fun <D, M> dirac(d: D) = EnumeratedDistribution<D, M>(mapOf(d to 1.0))
        fun <D, M> dirac(d: D, m: M) = EnumeratedDistribution<D, M>(mapOf(d to 1.0), mapOf(d to m))
    }

    fun isDirac() = pmf.size == 1

    override fun toString(): String {
        return "[${pmf.map { "${it.value}:${it.key}" }.joinToString(", ")}]"
    }

}