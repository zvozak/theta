package hu.bme.mit.theta.prob

class EnumeratedDistribution<D>(pairs: List<Pair<D, Double>>) {
    val pmf = pairs.toMap()

    companion object {
        fun <D> dirac(d: D) = EnumeratedDistribution(listOf(d to 1.0))
    }

    fun isDirac() = pmf.size == 1
}