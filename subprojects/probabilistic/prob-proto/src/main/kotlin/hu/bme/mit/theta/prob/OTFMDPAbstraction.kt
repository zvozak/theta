package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.Action
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.waitlist.FifoWaitlist
import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.*
import hu.bme.mit.theta.cfa.analysis.lts.CfaSbeLts
import hu.bme.mit.theta.prob.AbstractionGame.StateNode
import hu.bme.mit.theta.prob.EnumeratedDistribution.Companion.dirac

typealias PcfaStateNode<S> = StateNode<CfaState<S>, CfaAction>
fun <S: ExprState, P: Prec> checkPCFA(
    transFunc: CfaGroupedTransferFunction<S, P>,
    lts: CfaSbeLts, // LBE not supported yet!
    init: CfaInitFunc<S, P>,
    initialPrec: CfaPrec<P>,
    errorLoc: CFA.Loc, finalLoc: CFA.Loc,
    nonDetGoal: OptimType
) {
    val sInit = init.getInitStates(initialPrec)

    val game = AbstractionGame<CfaState<S>, CfaAction, Unit>()

    val waitlist = FifoWaitlist.create<PcfaStateNode<S>>()
//    val visited = Partition.of { node: PcfaStateNode<S> -> node.state.loc }

    val initNodes = game.createStateNodes(sInit)
    waitlist.addAll(initNodes)
//    visited.addAll(initNodes)
    val stateNodeMap = hashMapOf<CfaState<S>, PcfaStateNode<S>>()

    var currPrec = initialPrec

    fun getOrCreateNode(s: CfaState<S>): PcfaStateNode<S> =
        stateNodeMap.getOrElse(s) {
            val newNode = game.createStateNode(s)
            stateNodeMap[s] = newNode
            waitlist.add(newNode)
            return@getOrElse newNode
        }

    // Computing the abstraction
    while(!waitlist.isEmpty) {
        val node = waitlist.remove()

        val s = node.state
        val actions = lts.getEnabledActionsFor(s)

        for (action in actions) {
            require(action.stmts.size == 1) // TODO: LBE not supported yet
            val stmt = action.stmts.first()
            val nextStates = transFunc.getSuccStates(s, action, currPrec)
            if(stmt is ProbStmt) {
                val substmts = stmt.stmts
                for(nextStateSet in nextStates) {
                    // It might be better to label the returned states with the stmt that led to it instead of
                    // relying on the list orders
                    require(nextStateSet.size == substmts.size)
                    val nextStateDistr= EnumeratedDistribution(
                        substmts.indices.map {
                            // TODO: get rid of that cast
                            val nextStateNode =
                                getOrCreateNode(nextStateSet[it]) as StateNode<CfaState<S>, *>
                            nextStateNode to (stmt.distr.pmf[substmts[it]] ?: 0.0)
                        }
                    )
                    val choiceNode = game.createConcreteChoiceNode()
                    game.connect(node, choiceNode, action)
                    game.connect(choiceNode, nextStateDistr, Unit)

                    // TODO: merge next state distributions if possible
                }
            } else {
                for (nextStateSet in nextStates) {
                    val choiceNode = game.createConcreteChoiceNode()
                    game.connect(node, choiceNode, action)
                    for(nextState in nextStateSet) {
                        // Will connecting to the state instead of the state node work?
                        game.connect(choiceNode, dirac(getOrCreateNode(nextState)), Unit)
                    }
                }
            }
        }
    }

    // Computing the approximation for the property under check for the abstraction

    val LAinit = hashMapOf(*stateNodeMap.entries
        .map { it.value to if(it.key.loc == errorLoc) 1.0 else 0.0 }.toTypedArray())
    val LCinit = hashMapOf(*game.concreteChoiceNodes.map { it to 0.0 }.toTypedArray())
    val UAinit = hashMapOf(*stateNodeMap.entries
        .map { it.value to if(it.key.loc == finalLoc) 0.0 else 1.0 }.toTypedArray())
    val UCinit = hashMapOf(*game.concreteChoiceNodes.map { it to 1.0 }.toTypedArray())

    val convergenceThreshold = 1e-6

    val maxCheckResult = BVI(
        game,
        OptimType.MAX, nonDetGoal,
        convergenceThreshold,
        LAinit, LCinit,
        UAinit, UCinit,
        initNodes
    )

    val minCheckResult = BVI(
        game,
        OptimType.MIN, nonDetGoal,
        convergenceThreshold,
        LAinit, LCinit,
        UAinit, UCinit,
        initNodes
    )

    val doubleEquivalenceThreshold = 1e-6
    fun doubleEquals(a: Double, b: Double) = Math.abs(a-b) < doubleEquivalenceThreshold
    val max = initNodes.sumByDouble { maxCheckResult.abstractionNodeValues[it] ?: 0.0 }
    val min = initNodes.sumByDouble { minCheckResult.abstractionNodeValues[it] ?: 0.0 }

    if(doubleEquals(max, min)) {

    } else {
        TODO("refinement")
    }


}

