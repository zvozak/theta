package hu.bme.mit.theta.prob.gbar

import hu.bme.mit.theta.analysis.InitFunc
import hu.bme.mit.theta.analysis.LTS
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.waitlist.FifoWaitlist
import hu.bme.mit.theta.core.stmt.SequenceStmt
import hu.bme.mit.theta.core.stmt.Stmt
import hu.bme.mit.theta.prob.game.StochasticGame
import hu.bme.mit.theta.prob.pcfa.ProbStmt
import hu.bme.mit.theta.prob.transfunc.GroupedTransFunc

private typealias AbstractionNode<S, A> = StochasticGame<S, Unit, Unit, A>.ANode

class StandardMDPAbstractor<S: State, A: StmtAction, P: Prec>(
    val init: InitFunc<S, P>,
    val lts: LTS<S, A>,
    val transFunc: GroupedTransFunc<S, A, P>,
): SGAbstractor<S, Stmt, P> {
    override fun computeAbstraction(prec: P): StochasticGame<S, Unit, Unit, Stmt> {
        val sInit = init.getInitStates(prec).toSet()

        val game = StochasticGame<S, Unit, Unit, Stmt>()

        val waitlist = FifoWaitlist.create<AbstractionNode<S, Stmt>>()

        val stateNodeMap = hashMapOf<S, AbstractionNode<S, Stmt>>()

        fun getOrCreateNode(s: S, isInitial: Boolean = false): AbstractionNode<S, Stmt> =
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
                val stmtResult = transFunc.getSuccStatesWithStmt(s, action, prec)
                if (stmt is ProbStmt) {
                    for (nextStateSet in stmtResult) {
                        val nextStatePMF = hashMapOf<AbstractionNode<S, Stmt>, Double>()
                        for ((subStmt, nextStates) in nextStateSet) {
                            val nextStateNode = getOrCreateNode(nextStates)
                            nextStatePMF[nextStateNode] =
                                    (nextStatePMF[nextStateNode] ?: 0.0) + (stmt.distr.pmf[subStmt] ?: 0.0)
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
        TODO()
    }
}