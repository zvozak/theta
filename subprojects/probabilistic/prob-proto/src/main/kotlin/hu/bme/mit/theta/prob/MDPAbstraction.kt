package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.waitlist.FifoWaitlist
import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.*
import hu.bme.mit.theta.cfa.analysis.lts.CfaSbeLts
import hu.bme.mit.theta.core.stmt.Stmt
import hu.bme.mit.theta.prob.AbstractionGame.ChoiceNode
import hu.bme.mit.theta.prob.AbstractionGame.StateNode
import hu.bme.mit.theta.prob.EnumeratedDistribution.Companion.dirac
import hu.bme.mit.theta.prob.TransferFunctions.CfaGroupedTransferFunction

fun <S: State, LAbs, LConc> strategyFromValues(
    VA: Map<StateNode<S, LAbs>, Double>,
    VC: Map<ChoiceNode<S, LConc>, Double>,
    playerAGoal: OptimType
): Map<StateNode<S, LAbs>, ChoiceNode<S, *>?> {
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
fun doubleEquals(a: Double, b: Double) = Math.abs(a-b) < doubleEquivalenceThreshold

enum class ThresholdType(val check: (threshold: Double, v: Double) -> Boolean) {
    LESS_THAN({threshold, v -> v < threshold}),
    GREATER_THAN({threshold, v -> v > threshold})
}

data class PCFACheckResult(
    val propertySatisfied: Boolean,
    val lastMin: Double,
    val lastMax: Double
)

typealias PcfaStateNode<S> = StateNode<CfaState<S>, CfaAction>
fun <S: ExprState, SubP: Prec, P: CfaPrec<SubP>> checkThresholdProperty(
    transFunc: CfaGroupedTransferFunction<S, SubP>,
    lts: CfaSbeLts, // LBE not supported yet!
    init: CfaInitFunc<S, SubP>,
    initialPrec: P,
    errorLoc: CFA.Loc, finalLoc: CFA.Loc,
    nonDetGoal: OptimType, propertyThreshold: Double, thresholdType: ThresholdType,
    precRefiner: PrecRefiner<P, CfaState<S>, CfaAction, Unit>,
    refinableStateSelector: RefinableStateSelector
): PCFACheckResult {
    var currPrec = initialPrec
    var iters = 0

    while (true) {
        iters++
        val game = computeGameAbstraction(init, lts, transFunc, currPrec)

        // Computing the approximation for the property under check for the abstraction

        val (minCheckResult, maxCheckResult) = analyzeGame(game, errorLoc, finalLoc, nonDetGoal)

        val max = nonDetGoal.select(game.initNodes.map { maxCheckResult.abstractionNodeValues[it] ?: 0.0 })!!
        val min = nonDetGoal.select(game.initNodes.map { minCheckResult.abstractionNodeValues[it] ?: 0.0 })!!

        val maxSatisfies = thresholdType.check(propertyThreshold, max)
        val minSatisfies = thresholdType.check(propertyThreshold, min)

        println("Iter $iters: ")
        println("    $currPrec")
        println("    [$min, $max]")

        if (maxSatisfies && minSatisfies) {
            return PCFACheckResult(true, min, max)
        } else if (!maxSatisfies && !minSatisfies) {
            return PCFACheckResult(false, min, max)
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

fun <S: ExprState, SubP: Prec, P: CfaPrec<SubP>> computeProb(
    transFunc: CfaGroupedTransferFunction<S, SubP>,
    lts: CfaSbeLts, // LBE not supported yet!
    init: CfaInitFunc<S, SubP>,
    initialPrec: P,
    errorLoc: CFA.Loc, finalLoc: CFA.Loc,
    nonDetGoal: OptimType, tolerance: Double,
    precRefiner: PrecRefiner<P, CfaState<S>, CfaAction, Unit>,
    refinableStateSelector: RefinableStateSelector
): PCFACheckResult {
    var currPrec = initialPrec
    var iters = 0

    while (true) {
        iters++
        val game = computeGameAbstraction(init, lts, transFunc, currPrec)

        // Computing the approximation for the property under check for the abstraction

        val (minCheckResult, maxCheckResult) = analyzeGame(game, errorLoc, finalLoc, nonDetGoal)

        val max = nonDetGoal.select(game.initNodes.map { maxCheckResult.abstractionNodeValues[it] ?: 0.0 })!!
        val min = nonDetGoal.select(game.initNodes.map { minCheckResult.abstractionNodeValues[it] ?: 0.0 })!!

        println("Iter $iters: ")
        println("    $currPrec")
        println("    [$min, $max]")

        if (max-min < tolerance) {
            return PCFACheckResult(true, min, max)
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

private fun <S : ExprState> analyzeGame(
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

fun <P : Prec, S : ExprState> computeGameAbstraction(
    init: CfaInitFunc<S, P>,
    lts: CfaSbeLts,
    transFunc: CfaGroupedTransferFunction<S, P>,
    currPrec: CfaPrec<P>
): AbstractionGame<CfaState<S>, CfaAction, Unit> {
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
            require(action.stmts.size == 1) // TODO: action-based LBE not supported yet
            val stmt = action.stmts.first()
            val nextStates = transFunc.getSuccStates(s, action, currPrec)
            if (stmt is ProbStmt) {
                val substmts = stmt.stmts
                for (nextStateSet in nextStates) {
                    // It might be better to label the returned states with the stmt that led to it instead of
                    // relying on the list orders
                    require(nextStateSet.size == substmts.size)
                    val nextStatePMF = hashMapOf<StateNode<CfaState<S>, *>, Double>()
                    val metadata = hashMapOf<StateNode<CfaState<S>, *>, MutableList<Stmt>>()
                    for (idx in substmts.indices) {
                        val nextStateNode = getOrCreateNode(nextStateSet[idx]) as StateNode<CfaState<S>, *>
                        metadata.getOrPut(nextStateNode) { arrayListOf() }.add(substmts[idx])
                        nextStatePMF[nextStateNode] =
                            (nextStatePMF[nextStateNode] ?: 0.0) + (stmt.distr.pmf[substmts[idx]] ?: 0.0)
                    }

                    val nextStateDistr = EnumeratedDistribution(nextStatePMF, metadata)

//                    val choiceNode = game.createConcreteChoiceNode()
//                    game.connect(choiceNode, nextStateDistr, Unit)
                    val choiceNode = game.getOrCreateNodeWithChoices(setOf(nextStateDistr to Unit))
                    game.connect(node, choiceNode, action)

                    // TODO: merge next state distributions if possible
                }
            } else {
                for (nextStateSet in nextStates) {
                    val choices = nextStateSet.map { nextState ->
                        // TODO: get rid of that cast
                        dirac(getOrCreateNode(nextState) as StateNode<CfaState<S>, *>, mutableListOf(stmt)) to Unit
                    }.toSet()
                    val choiceNode = game.getOrCreateNodeWithChoices(choices)
//                    val choiceNode = game.createConcreteChoiceNode()
                    game.connect(node, choiceNode, action)
//                    for (nextState in nextStateSet) {
//                        game.connect(choiceNode, dirac(getOrCreateNode(nextState), arrayListOf(stmt)), Unit)
//                    }
                }
            }
        }
    }
    return game
}

