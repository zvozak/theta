package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.prob.AbstractionGame.*

enum class Player{
    Abstraction, Concrete
}

fun <S : ExprState> analyzeGame(
    game: AbstractionGame<CfaState<S>, CfaAction, Unit>,
    errorLoc: CFA.Loc,
    finalLoc: CFA.Loc,
    nonDetGoal: OptimType
): Pair<AbstractionGameCheckResult<CfaState<S>, CfaAction, Unit>, AbstractionGameCheckResult<CfaState<S>, CfaAction, Unit>> {
    val LAinit = hashMapOf(*game.stateNodes.map {
        it to if (it.state.loc == errorLoc) 1.0 else 0.0
    }.toTypedArray())
    val LCinit = hashMapOf(*game.concreteChoiceNodes.map { it to 0.0 }.toTypedArray())
    val UAinit = hashMapOf(*game.stateNodes.map {
        it to if (it.state.loc == finalLoc) 0.0 else 1.0
    }.toTypedArray())
    val UCinit = hashMapOf(*game.concreteChoiceNodes.map { it to 1.0 }.toTypedArray())

    val convergenceThreshold = 1e-6

    val minCheckResult = BVI(
        game,
        OptimType.MIN, nonDetGoal,
        convergenceThreshold,
        LAinit, LCinit,
        UAinit, UCinit,
        game.initNodes.toList(),
        collapseMecs = true
    )

    val maxCheckResult = BVI(
        game,
        OptimType.MAX, nonDetGoal,
        convergenceThreshold,
        LAinit, LCinit,
        UAinit, UCinit,
        game.initNodes.toList(),
        collapseMecs = true
    )
    return Pair(minCheckResult, maxCheckResult)
}
