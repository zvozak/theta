package hu.bme.mit.theta.prob.game.analysis

import com.google.common.base.Stopwatch
import com.google.common.math.LongMath
import hu.bme.mit.theta.prob.DataCollector
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.prob.game.analysis.OptimType.MAX
import hu.bme.mit.theta.prob.game.analysis.OptimType.MIN
import hu.bme.mit.theta.prob.game.StochasticGame.Companion.Player
import hu.bme.mit.theta.prob.game.StochasticGame.Companion.Player.C
import hu.bme.mit.theta.prob.game.*
import hu.bme.mit.theta.prob.game.StochasticGame.Companion.Player.A
import javafx.scene.paint.Stop
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class AbstractionGameAnalysis : KoinComponent {
    val dataCollector: DataCollector by inject()

    @Serializable
    data class GameAnalysisStats(
        val analysisTime: Long,
        val timeUnit: TimeUnit,
        val analyzedGameSize: Int,
        val goalA: OptimType,
        val goalC: OptimType
    ) {
        override fun toString(): String {
            return Json.encodeToString(this)
        }
    }

    fun <S : ExprState> analyzeGame(
        game: AbstractionGame<CfaState<S>, CfaAction, Unit>,
        errorLoc: CFA.Loc,
        finalLoc: CFA.Loc,
        nonDetGoal: OptimType,
        useBVI: Boolean
    ): Pair<AbstractionGameCheckResult<CfaState<S>, CfaAction, Unit>, AbstractionGameCheckResult<CfaState<S>, CfaAction, Unit>> {

        val (stochGame, aNodeMap, cNodeMap) = game.toStochasticGame()
        val errors =
            game.stateNodes.filter { it.state.loc == errorLoc }.mapNotNull(aNodeMap::get).toSet()
        val almostSureTarget = stochGame.almostSure(C, errors)

        val convergenceThreshold = 1e-8

        stochGame.addSelfLoops()
        val l0 = stochGame.allNodes.associateWith { 0.0 }.toMutableMap()
        errors.forEach { l0[it] = 1.0 }
        almostSureTarget.forEach { l0[it] = 1.0 }
        val finals = stochGame.aNodes.filter { it.s.loc == finalLoc }
        val u0 = stochGame.allNodes.associateWith { 1.0 }.toMutableMap()
        finals.forEach { u0[it] = 0.0 }

        val minCheckV = checkWithSimplification(
            stochGame,
            { if (it == C) nonDetGoal else MIN },
            l0, u0,
            convergenceThreshold, useBVI
        )
        val minCheckResult = nonDetGoal.select(stochGame.initNodes.map { minCheckV[it]!! })!!

        stochGame.allNodes.forEach { it.owner = C } // TODO: unhack this
        val almostSureTarget2 = stochGame.almostSure(C, errors.toSet())
        val linitAS = stochGame.allNodes.associateWith {
            if (it in errors || it in almostSureTarget2) 1.0 else 0.0
        }

        val maxCheckV = checkWithSimplification(
            stochGame,
            { if (it == C) nonDetGoal else MAX },
            linitAS, u0,
            convergenceThreshold, useBVI
        )
        val maxCheckResult = nonDetGoal.select(stochGame.initNodes.map { maxCheckV[it]!! })!!

        val LA = game.stateNodes.associateWith { minCheckV[aNodeMap[it]!!]!! }
        val UA = game.stateNodes.associateWith { maxCheckV[aNodeMap[it]!!]!! }
        val LC = game.concreteChoiceNodes.associateWith { minCheckV[cNodeMap[it]!!]!! }
        val UC = game.concreteChoiceNodes.associateWith { maxCheckV[cNodeMap[it]!!]!! }


        return Pair(AbstractionGameCheckResult(LA, LC), AbstractionGameCheckResult(UA, UC))
    }

    private fun <SA, SC, LA, LC> checkWithSimplification(
        game: StochasticGame<SA, SC, LA, LC>,
        goal: (Player) -> OptimType,
        L0: Map<StochasticGame<SA, SC, LA, LC>.Node, Double>,
        U0: Map<StochasticGame<SA, SC, LA, LC>.Node, Double>,
        convergenceThreshold: Double,
        useBVI: Boolean
    ): Map<StochasticGame<SA, SC, LA, LC>.Node, Double> {
        val stopwatch = Stopwatch.createStarted()

        val (embed, embedMap) = toMergedGame(game)
        val invEmbedMap = embedMap.inverse()
        val (simplifiedGame, simplificationMap) = iterativeSimplificationPass.apply(embed)
        val invMap = simplificationMap.inverseImage()
        val l0 = simplifiedGame.allNodes.associateWith { invMap[it]!!.maxOf { L0[invEmbedMap[it]]!! } }
        val u0 = simplifiedGame.allNodes.associateWith { invMap[it]!!.minOf { U0[invEmbedMap[it]]!! } }
        val VIResult =
            if (useBVI) simplifiedGame.BVI(goal, u0, l0, convergenceThreshold)
            else simplifiedGame.VI(goal, l0, convergenceThreshold)
        val map = simplificationMap compose embedMap

        dataCollector.logIterationData(
            GameAnalysisStats(
                stopwatch.elapsed(TimeUnit.MILLISECONDS),
                TimeUnit.MILLISECONDS,
                simplifiedGame.allNodes.size,
                goalA = goal(A),
                goalC = goal(C)
            )
        )
        return game.allNodes.associateWith { VIResult[map[it]]!! }
    }
}