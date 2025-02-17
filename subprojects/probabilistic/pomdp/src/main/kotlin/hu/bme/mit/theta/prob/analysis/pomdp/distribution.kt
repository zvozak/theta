package hu.bme.mit.theta.prob.analysis.pomdp

class Distribution<D>(val pmf: HashMap<D, Double>) : Iterable<Map.Entry<D, Double>> {
    init {
        // Checking that the probability mass function sums to one, while keeping double precision in mind
        require(Math.abs(pmf.entries.sumOf { it.value } - 1.0) < 1e-4)
        // Checking that none of the probabilities is negative
        require(pmf.values.none { it < 0.0 })
    }

    operator fun get(d: D): Double {
        require(pmf.containsKey(d))
        return pmf[d]!!
    }

    override fun toString(): String {
        val stringBuilder = StringBuilder("[")

        for ((key, prob) in pmf){
            stringBuilder.append(prob.toString())
            stringBuilder.append(", ")
        }

        stringBuilder.trimEnd(',', ' ')

        stringBuilder.append("]")

        return stringBuilder.toString()
    }

    companion object {
        fun <D> createUniformDistribution(keys: Set<D>): Distribution<D> {
            val probability: Double = 1.0 / keys.size

            var map = hashMapOf<D, Double>()
            for (key in keys) {
                map.put(key, probability)
            }
            return Distribution(map)
        }

        fun <D> createIdentityDistribution(startkey: D, keys: Set<D>): Distribution<D> {
            var map = hashMapOf<D, Double>()
            for (key in keys) {
                map.put(key, 0.0)
            }
            map.set(startkey, 1.0)

            return Distribution(map)
        }

        fun <D> normalizeProbabilities(map: HashMap<D, Double>): Distribution<D> {
            val sum = map.values.sum()
            for ((k,p) in map){
                map[k] = p/sum
            }

            return Distribution(map)
        }
    }

    override fun iterator(): Iterator<Map.Entry<D, Double>> {
        return pmf.iterator()
    }

    fun addMap(other: HashMap<D, Double> ): HashMap<D, Double> {
        val newMap = HashMap(other)
        for (k in this.pmf.keys){
            newMap[k] = this[k]!! + newMap[k]!!
        }
        return newMap
    }

    private operator fun set(k: D, value: Double) {
        pmf[k] = value
    }
}