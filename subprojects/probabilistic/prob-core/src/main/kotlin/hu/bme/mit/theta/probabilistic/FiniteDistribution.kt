package hu.bme.mit.theta.probabilistic

class FiniteDistribution<D>(
    _pmf: Map<D, Double>
) {
    init {
        require(_pmf.entries.sumOf { it.value }.equals(1.0, 1e-10))
        require(_pmf.entries.any { it.value >= 0.0 })
    }

    private val pmf = _pmf.filter { it.value > 0.0 }
    constructor(vararg components: Pair<D, Double>): this(components.toMap())

    operator fun get(v: D) = pmf.getOrDefault(v, 0.0)
    val support get() = pmf.keys

    fun expectedValue(f: (D)->Double) = pmf.entries.sumByDouble { it.value*f(it.key) }
    fun <E> transform(f: (D)->E): FiniteDistribution<E> {
        val result = hashMapOf<E, Double>()
        for ((k, v) in pmf) {
            val kk = f(k)
            result[kk] = result.getOrDefault(kk, 0.0) + v
        }
        return FiniteDistribution(result)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FiniteDistribution<*>

        if (pmf != other.pmf) return false

        return true
    }

    override fun hashCode(): Int {
        return pmf.hashCode()
    }

    override fun toString(): String {
        return "D$($pmf)"
    }

    companion object {
        fun <D> dirac(d: D) = FiniteDistribution(d to 1.0)
    }
}