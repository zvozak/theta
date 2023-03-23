package hu.bme.mit.theta.probabilistic

interface GameRewardFunction<N, A> {
    fun getStateReward(n: N): Double
    fun getEdgeReward(source: N, action: A, target: N): Double

    operator fun invoke(n: N) = getStateReward(n)
    operator fun invoke(source: N, action: A, target: N) = getEdgeReward(source, action, target)
}