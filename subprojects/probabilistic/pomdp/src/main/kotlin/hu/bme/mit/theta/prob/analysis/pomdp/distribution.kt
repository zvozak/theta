package hu.bme.mit.theta.prob.analysis.pomdp

class Distribution<D>(val pmf: Map<D, Double>) {
    init {
        // Checking that the probability mass function sums to one, while keeping double precision in mind
        require(Math.abs(pmf.entries.sumOf { it.value } - 1.0) < 1e-10)
        // Checking that none of the probabilities is negative
        require(pmf.values.none { it < 0.0 })
    }

    companion object {
        fun <D> createUniformDistribution(keys: Set<D>): Distribution<D> {
            val probability: Double = 1.0 / keys.size

            return Distribution(buildMap {
                for (key in keys) {
                    put(key, probability)
                }
            })
        }

        fun <D> createIdentityDistribution(startkey: D, keys: Set<D>): Distribution<D> {
            return Distribution(buildMap {
                for (key in keys) {
                    put(key, 0.0)
                }
                set(startkey, 1.0)
            })
        }
    }
}