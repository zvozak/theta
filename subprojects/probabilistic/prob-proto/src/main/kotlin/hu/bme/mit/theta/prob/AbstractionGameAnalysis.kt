package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.prob.OptimType.MAX
import hu.bme.mit.theta.prob.OptimType.MIN
import hu.bme.mit.theta.prob.StochasticGame.Companion.Player.C

enum class Player{
    Abstraction, Concrete
}

fun <S : ExprState> analyzeGame(
    game: AbstractionGame<CfaState<S>, CfaAction, Unit>,
    errorLoc: CFA.Loc,
    finalLoc: CFA.Loc,
    nonDetGoal: OptimType,
    useBVI: Boolean = false
): Pair<AbstractionGameCheckResult<CfaState<S>, CfaAction, Unit>, AbstractionGameCheckResult<CfaState<S>, CfaAction, Unit>> {
    val (stochGame, aNodeMap, cNodeMap) = game.toStochasticGame()
    val targets =
        game.stateNodes.filter { it.state.loc == errorLoc }.map(aNodeMap::get).filterNotNull().toSet()
    val almostSureTarget = stochGame.almostSure(C, targets)

    val LAinit = hashMapOf(*game.stateNodes.map {
        val isTarget =
            it.state.loc == errorLoc || (nonDetGoal == MAX && almostSureTarget.contains(aNodeMap[it]!!))
        it to if (isTarget) 1.0 else 0.0
    }.toTypedArray())
    val LCinit = hashMapOf(*game.concreteChoiceNodes.map {
        it to if(cNodeMap[it]!! in almostSureTarget) 1.0 else 0.0
    }.toTypedArray())

    val UAinit = hashMapOf(*game.stateNodes.map {
        it to if (it.state.loc == finalLoc) 0.0 else 1.0
    }.toTypedArray())
    val UCinit = hashMapOf(*game.concreteChoiceNodes.map { it to 1.0 }.toTypedArray())

    val convergenceThreshold = 1e-8

    stochGame.addSelfLoops()
    val linit = stochGame.allNodes.associateWith { 0.0 }.toMutableMap()
    targets.forEach { linit[it] = 1.0 }
    val fin = stochGame.aNodes.filter { it.s.loc == finalLoc }
    val uinit = stochGame.allNodes.associateWith { 1.0 }.toMutableMap()
    fin.forEach { uinit[it] = 0.0 }

    val minCheckV =
        if (useBVI) stochGame.BVI(
            { if (it == C) nonDetGoal else MIN },
            uinit,
            linit,
            convergenceThreshold,
        ) else stochGame.VI(
            { if (it == C) nonDetGoal else MIN },
            linit,
            convergenceThreshold,
        )
    val minCheckResult = nonDetGoal.select(stochGame.initNodes.map { minCheckV[it]!! })!!

    stochGame.allNodes.forEach { it.owner = C } // TODO: unhack this
    val almostSureTarget2 = stochGame.almostSure(C, targets.toSet())
    val linitAS = stochGame.allNodes.associateWith {
        if(it in targets || it in almostSureTarget2) 1.0 else 0.0
    }

    val (embed, embedMap) = toMergedGame(stochGame)
    val invEmbedMap = embedMap.inverse()
    val (simplifiedGame, simplificationMap) = iterativeSimplificationPass.apply(embed)
    val invMap = simplificationMap.inverseImage()
    val linit2 = simplifiedGame.allNodes.associateWith { invMap[it]!!.map { linitAS[invEmbedMap[it]]!! }.max()!!}
    val uinit2 = simplifiedGame.allNodes.associateWith { invMap[it]!!.map { uinit[invEmbedMap[it]]!! }.min()!!}
    val simplifiedCheck =
        if (useBVI) simplifiedGame.BVI(
            MAX,
            uinit2,
            linit2,
            convergenceThreshold
        ) else simplifiedGame.VI(
            { if (it == C) nonDetGoal else MAX },
            linit2,
            convergenceThreshold
        )
    val maxCheckV = stochGame.allNodes.associateWith { simplifiedCheck[simplificationMap[embedMap[it]]] }
    val maxCheckResult = nonDetGoal.select(stochGame.initNodes.map { maxCheckV[it]!! })!!

    val LA = game.stateNodes.associateWith { minCheckV[aNodeMap[it]!!]!! }
    val UA = game.stateNodes.associateWith { maxCheckV[aNodeMap[it]!!]!! }
    val LC = game.concreteChoiceNodes.associateWith { minCheckV[cNodeMap[it]!!]!! }
    val UC = game.concreteChoiceNodes.associateWith { maxCheckV[cNodeMap[it]!!]!! }

    return Pair(AbstractionGameCheckResult(LA, LC), AbstractionGameCheckResult(UA, UC))
}
