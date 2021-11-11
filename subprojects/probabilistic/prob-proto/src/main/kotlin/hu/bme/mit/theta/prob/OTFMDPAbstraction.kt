package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.Action
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.reachedset.Partition
import hu.bme.mit.theta.analysis.waitlist.FifoWaitlist
import hu.bme.mit.theta.cfa.analysis.*
import hu.bme.mit.theta.cfa.analysis.lts.CfaSbeLts
import hu.bme.mit.theta.core.stmt.HavocStmt
import hu.bme.mit.theta.core.type.booltype.BoolExprs.*
import hu.bme.mit.theta.prob.EnumeratedDistribution.Companion.dirac

interface PLTS<S: State, A: Action> {
    fun getEnabledDistributions(state: S): List<EnumeratedDistribution<A>>
}


typealias PcfaStateNode<S> = AbstractionGame.StateNode<CfaState<S>, CfaAction>
fun <S: ExprState, P: Prec> checkPCFA(
    cfaTransFunc: CfaTransFunc<S, P>,
    transFunc: CfaGroupedTransferFunction<S, P>,
    lts: CfaSbeLts, // LBE not supported yet!
    init: CfaInitFunc<S, P>,
    initialPrec: CfaPrec<P>
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

    while(!waitlist.isEmpty) {
        val node = waitlist.remove()

        val s = node.state
        val actions = lts.getEnabledActionsFor(s)

        for (action in actions) {
            require(action.stmts.size == 1) // TODO: LBE not supported yet
            val stmt = action.stmts.first()
            val nextStates = transFunc.getSuccStates(s, action, currPrec)
            if(stmt is ProbStmt) {

            } else {
                for (nextStateSet in nextStates) {
                    val choiceNode = game.createConcreteChoiceNode()
                    game.connect(node, choiceNode, action)
                    for(nextState in nextStateSet) {

                    }
                }
            }
        }
    }
}

