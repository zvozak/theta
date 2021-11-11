package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.State

enum class OptimType {
    MAX, MIN
}
fun <T> OptimType.select(m: Map<T, Double>): Double? =
    when(this) {
        OptimType.MAX -> m.maxBy { it.value }?.value
        OptimType.MIN -> m.minBy { it.value }?.value
    }

fun <T> OptimType.argSelect(m: Map<T, Double>): T?  =
    when(this) {
        OptimType.MAX -> m.maxBy { it.value }?.key
        OptimType.MIN -> m.minBy { it.value }?.key
    }

fun <S : State, LAbs, LConc> BVI(
    game: AbstractionGame<S, LAbs, LConc>,
    playerAGoal: OptimType, playerCGoal: OptimType,
    threshold: Double,
    LAinit: List<Double>, LCinit: List<Double>,
    UAinit: List<Double>, UCinit: List<Double>
) {
    val NA = game.stateNodes.size
    val NC = game.concreteChoiceNodes.size


}