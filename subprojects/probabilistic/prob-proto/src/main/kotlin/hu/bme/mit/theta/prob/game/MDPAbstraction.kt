package hu.bme.mit.theta.prob.game

import com.google.common.base.Stopwatch
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.waitlist.FifoWaitlist
import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.*
import hu.bme.mit.theta.cfa.analysis.lts.CfaLts
import hu.bme.mit.theta.cfa.analysis.lts.CfaSbeLts
import hu.bme.mit.theta.core.stmt.SequenceStmt
import hu.bme.mit.theta.core.stmt.Stmt
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.prob.*
import hu.bme.mit.theta.prob.game.AbstractionGame.ChoiceNode
import hu.bme.mit.theta.prob.game.AbstractionGame.StateNode
import hu.bme.mit.theta.prob.EnumeratedDistribution.Companion.dirac
import hu.bme.mit.theta.prob.game.analysis.AbstractionGameAnalysis
import hu.bme.mit.theta.prob.game.analysis.OptimType
import hu.bme.mit.theta.prob.game.analysis.argSelect
import hu.bme.mit.theta.prob.game.analysis.select
import hu.bme.mit.theta.prob.pcfa.ProbStmt
import hu.bme.mit.theta.prob.refinement.PrecRefiner
import hu.bme.mit.theta.prob.refinement.RefinableStateSelector
import hu.bme.mit.theta.prob.transfunc.CfaGroupedTransFunc
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

fun <S : State, LAbs, LConc> strategyFromValues(
    VA: Map<StateNode<S, LAbs, LConc>, Double>,
    VC: Map<ChoiceNode<S, LAbs, LConc>, Double>,
    playerAGoal: OptimType
): Map<StateNode<S, LAbs, LConc>, ChoiceNode<S, LAbs, LConc>?> {
    val strat = VA.keys.map {
        val values = it.outgoingEdges.map {
            val node = it.end
            val value = VC[node]!!
            node to value
        }
        it to playerAGoal.argSelect(values)
    }
    return strat.toMap()
}

const val doubleEquivalenceThreshold = 1e-6
fun doubleEquals(a: Double, b: Double) = Math.abs(a - b) < doubleEquivalenceThreshold

enum class ThresholdType(val check: (threshold: Double, v: Double) -> Boolean) {
    LESS_THAN({ threshold, v -> v < threshold }),
    GREATER_THAN({ threshold, v -> v > threshold })
}

data class PCFACheckResult(
    val propertySatisfied: Boolean,
    val lastMin: Double,
    val lastMax: Double
)

typealias PcfaStateNode<S> = StateNode<CfaState<S>, CfaAction, Unit>
typealias PCFANode<S> = StochasticGame<CfaState<S>, Unit, CfaAction, Unit>.ANode


class MDPAbstractionAnalyzer : KoinComponent {

    private val abstractionGameAnalyzer: AbstractionGameAnalysis by inject()
    private val dataCollector: DataCollector by inject()

    fun <S : ExprState, SubP : Prec, P : CfaPrec<SubP>> analyze(
        transFunc: CfaGroupedTransFunc<S, SubP>,
        lts: CfaLts,
        init: CfaInitFunc<S, SubP>,
        initialPrec: P,
        errorLoc: CFA.Loc, finalLoc: CFA.Loc,
        nonDetGoal: OptimType,
        stopCheck: (min: Double, max: Double) -> Boolean,
        result: (min: Double, max: Double) -> PCFACheckResult,
        precRefiner: PrecRefiner<P, CfaState<S>, CfaAction, Unit>,
        refinableStateSelector: RefinableStateSelector,
        useBVI: Boolean, useTVI: Boolean
    ): PCFACheckResult {
        var currPrec = initialPrec
        var iters = 0
        var prevPrec: Prec? = null

        while (true) {
            if (prevPrec == currPrec)
                throw java.lang.RuntimeException(
                    "Abstraction refinement stuck :-(, " +
                            "use a configuration with more precise post operator or better refinement"
                )
            prevPrec = currPrec

            iters++
            println("Iter $iters: ")
            dataCollector.setIterationNumber(iters)
            val game = computeGameAbstraction(init, lts, transFunc, currPrec)

            val (minCheckResult, maxCheckResult) =
                abstractionGameAnalyzer.analyzeGame(game, errorLoc, finalLoc, nonDetGoal, useBVI, useTVI)

            val max = nonDetGoal.select(game.initNodes.map { maxCheckResult.abstractionNodeValues[it] ?: 0.0 })!!
            val min = nonDetGoal.select(game.initNodes.map { minCheckResult.abstractionNodeValues[it] ?: 0.0 })!!

            println("    $currPrec")
            println("    Abstraction size: ${game.stateNodes.size}")
            println("    [$min, $max]")
            println("    [$min, $max]")

            if (stopCheck(min, max)) {
                return result(min, max)
            } else {
                // Perform refinement
                val stateToRefine = refinableStateSelector.select(
                    game,
                    minCheckResult.abstractionNodeValues, maxCheckResult.abstractionNodeValues,
                    minCheckResult.concreteChoiceNodeValues, maxCheckResult.concreteChoiceNodeValues,
                )!!
                currPrec = precRefiner.refine(
                    game, stateToRefine, currPrec,
                    minCheckResult.abstractionNodeValues, maxCheckResult.abstractionNodeValues,
                    minCheckResult.concreteChoiceNodeValues, maxCheckResult.concreteChoiceNodeValues
                )
            }
        }
    }

    fun <S : ExprState, SubP : Prec, P : CfaPrec<SubP>> checkThresholdProperty(
        transFunc: CfaGroupedTransFunc<S, SubP>,
        lts: CfaLts,
        init: CfaInitFunc<S, SubP>,
        initialPrec: P,
        errorLoc: CFA.Loc, finalLoc: CFA.Loc,
        nonDetGoal: OptimType, propertyThreshold: Double, thresholdType: ThresholdType,
        precRefiner: PrecRefiner<P, CfaState<S>, CfaAction, Unit>,
        refinableStateSelector: RefinableStateSelector,
        useBVI: Boolean, useTVI: Boolean
    ): PCFACheckResult {
        return analyze(
            transFunc,
            lts,
            init,
            initialPrec,
            errorLoc, finalLoc,
            nonDetGoal,
            { min, max ->
                val minSatisfies = thresholdType.check(propertyThreshold, min)
                val maxSatisfies = thresholdType.check(propertyThreshold, max)
                (minSatisfies && maxSatisfies) || (!minSatisfies && !maxSatisfies)
            },
            { min, max -> PCFACheckResult(
                thresholdType.check(propertyThreshold, min) && thresholdType.check(propertyThreshold, max),
                min, max)
            },
            precRefiner,
            refinableStateSelector,
            useBVI, useTVI
        )
    }

    fun <S : ExprState, SubP : Prec, P : CfaPrec<SubP>> computeProb(
        transFunc: CfaGroupedTransFunc<S, SubP>,
        lts: CfaLts,
        init: CfaInitFunc<S, SubP>,
        initialPrec: P,
        errorLoc: CFA.Loc, finalLoc: CFA.Loc,
        nonDetGoal: OptimType, tolerance: Double,
        precRefiner: PrecRefiner<P, CfaState<S>, CfaAction, Unit>,
        refinableStateSelector: RefinableStateSelector,
        useBVI: Boolean, useTVI: Boolean
    ): PCFACheckResult {
        return analyze(
            transFunc,
            lts,
            init,
            initialPrec,
            errorLoc, finalLoc,
            nonDetGoal,
            { min, max ->
                abs(max - min) < tolerance
            },
            { min, max -> PCFACheckResult(true, min, max)
            },
            precRefiner,
            refinableStateSelector,
            useBVI, useTVI
        )
    }

    @Serializable
    data class MDPAbstractionStats(
        val abstractionSize: Int,
        val precisionUsed: String,
        val abstractionBuildingTime: Long,
        val timeUnit: TimeUnit
    ) {
        override fun toString(): String {
            return Json.encodeToString(this)
        }
    }

    fun <P : Prec, S : ExprState> computeSGAbstraction(
        init: CfaInitFunc<S, P>,
        lts: CfaLts,
        transFunc: CfaGroupedTransFunc<S, P>,
        currPrec: CfaPrec<P>
    ): StochasticGame<CfaState<S>, Unit, CfaAction, Unit> {
        val stopwatch = Stopwatch.createStarted()

        val sInit = init.getInitStates(currPrec).toSet()

        val game = StochasticGame<CfaState<S>, Unit, CfaAction, Unit>()

        val waitlist = FifoWaitlist.create<PCFANode<S>>()

        val stateNodeMap = hashMapOf<CfaState<S>, PCFANode<S>>()

        fun getOrCreateNode(s: CfaState<S>, isInitial: Boolean = false): PCFANode<S> =
            stateNodeMap.getOrElse(s) {
                val newNode = game.ANode(s, isInit = isInitial)
                stateNodeMap[s] = newNode
                waitlist.add(newNode)
                return@getOrElse newNode
            }

        for (it in sInit) getOrCreateNode(it, true)

        // Computing the abstraction
        while (!waitlist.isEmpty) {
            val node = waitlist.remove()

            val s = node.s
            val actions = lts.getEnabledActionsFor(s)

            for (action in actions) {
                val stmt = if (action.stmts.size == 1) action.stmts.first() else SequenceStmt.of(action.stmts)
                val nextStates = transFunc.getSuccStates(s, action, currPrec)
                if (stmt is ProbStmt) {
                    val substmts = stmt.stmts
                    for (nextStateSet in nextStates) {
                        // It might be better to label the returned states with the stmt that led to it instead of
                        // relying on the list orders
                        require(nextStateSet.size == substmts.size)
                        val nextStatePMF = hashMapOf<PCFANode<S>, Double>()
                        val metadata = hashMapOf<PCFANode<S>, MutableList<Stmt>>()
                        for (idx in substmts.indices) {
                            val nextStateNode = getOrCreateNode(nextStateSet[idx])
                            metadata.getOrPut(nextStateNode) { arrayListOf() }.add(substmts[idx])
                            nextStatePMF[nextStateNode] =
                                (nextStatePMF[nextStateNode] ?: 0.0) + (stmt.distr.pmf[substmts[idx]] ?: 0.0)
                        }

                        TODO()
//                        val nextStateDistr = EnumeratedDistribution(nextStatePMF, metadata)
//
//                        val choiceNode = game.getCNodeWithChoices(Unit, setOf(nextStateDistr to Unit))
//                        game.connect(node, choiceNode, action)
                    }
                } else {
                    TODO()
//                    for (nextStateSet in nextStates) {
//                        val choices = nextStateSet.map { nextState ->
//                            dirac(getOrCreateNode(nextState), mutableListOf(stmt)) to Unit
//                        }.toSet()
//                        val choiceNode = game.getCNodeWithChoices(Unit, choices)
//                        game.connect(node, choiceNode, action)
//                    }
                }
            }
        }

        stopwatch.stop()

        dataCollector.logIterationData(MDPAbstractionStats(
            abstractionSize = game.allNodes.size,
            precisionUsed = currPrec.toString(),
            abstractionBuildingTime = stopwatch.elapsed(TimeUnit.MILLISECONDS),
            timeUnit = TimeUnit.MILLISECONDS
        ))

        return game
    }

    fun <P : Prec, S : ExprState> computeGameAbstraction(
        init: CfaInitFunc<S, P>,
        lts: CfaLts,
        transFunc: CfaGroupedTransFunc<S, P>,
        currPrec: CfaPrec<P>
    ): AbstractionGame<CfaState<S>, CfaAction, Unit> {
        val stopwatch = Stopwatch.createStarted()

        val sInit = init.getInitStates(currPrec).toSet()

        val game = AbstractionGame<CfaState<S>, CfaAction, Unit>()

        val waitlist = FifoWaitlist.create<PcfaStateNode<S>>()

        val stateNodeMap = hashMapOf<CfaState<S>, PcfaStateNode<S>>()

        fun getOrCreateNode(s: CfaState<S>, isInitial: Boolean = false): PcfaStateNode<S> =
            stateNodeMap.getOrElse(s) {
                val newNode = game.createStateNode(s, isInitial)
                stateNodeMap[s] = newNode
                waitlist.add(newNode)
                return@getOrElse newNode
            }

        val initNodes = sInit.map { getOrCreateNode(it, true) }

        // Computing the abstraction
        while (!waitlist.isEmpty) {
            val node = waitlist.remove()

            val s = node.state
            val actions = lts.getEnabledActionsFor(s)

            for (action in actions) {
                val stmt = if (action.stmts.size == 1) action.stmts.first() else SequenceStmt.of(action.stmts)
                val nextStates = transFunc.getSuccStates(s, action, currPrec)
                if (stmt is ProbStmt) {
                    val substmts = stmt.stmts
                    for (nextStateSet in nextStates) {
                        // It might be better to label the returned states with the stmt that led to it instead of
                        // relying on the list orders
                        require(nextStateSet.size == substmts.size)
                        val nextStatePMF = hashMapOf<StateNode<CfaState<S>, CfaAction, Unit>, Double>()
                        val metadata = hashMapOf<StateNode<CfaState<S>, CfaAction, Unit>, MutableList<Stmt>>()
                        for (idx in substmts.indices) {
                            val nextStateNode = getOrCreateNode(nextStateSet[idx])
                            metadata.getOrPut(nextStateNode) { arrayListOf() }.add(substmts[idx])
                            nextStatePMF[nextStateNode] =
                                (nextStatePMF[nextStateNode] ?: 0.0) + (stmt.distr.pmf[substmts[idx]] ?: 0.0)
                        }

                        val nextStateDistr = EnumeratedDistribution(nextStatePMF, metadata)

                        val choiceNode = game.getOrCreateNodeWithChoices(setOf(nextStateDistr to Unit))
                        game.connect(node, choiceNode, action)
                    }
                } else {
                    for (nextStateSet in nextStates) {
                        val choices = nextStateSet.map { nextState ->
                            dirac(getOrCreateNode(nextState), mutableListOf(stmt)) to Unit
                        }.toSet()
                        val choiceNode = game.getOrCreateNodeWithChoices(choices)
                        game.connect(node, choiceNode, action)
                    }
                }
            }
        }

        stopwatch.stop()

        dataCollector.logIterationData(MDPAbstractionStats(
            abstractionSize = game.stateNodes.size + game.concreteChoiceNodes.size,
            precisionUsed = currPrec.toString(),
            abstractionBuildingTime = stopwatch.elapsed(TimeUnit.MILLISECONDS),
            timeUnit = TimeUnit.MILLISECONDS
        ))

        return game
    }
}
