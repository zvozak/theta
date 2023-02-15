package hu.bme.mit.theta.prob.analysis.menuabstraction

import hu.bme.mit.theta.analysis.Action
import hu.bme.mit.theta.analysis.InitFunc
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.probabilistic.EnumeratedDistribution
import hu.bme.mit.theta.probabilistic.ImplicitStochasticGame
import hu.bme.mit.theta.probabilistic.StochasticGame

data class ProbabilisticCommand<A: Action>(
    val guard: Expr<BoolType>,
    val result: EnumeratedDistribution<A>
)

interface ProbabilisticCommandLTS<S: State, A: Action> {
    fun getAvailableCommands(state: S): Collection<ProbabilisticCommand<A>>
    fun canFail(state: S, command: ProbabilisticCommand<A>): Boolean
}

interface ProbabilisticCommandTransFunc<S: State, A: Action, P: Prec> {
    /**
     * Computes a list of possible next state distributions after executing the given command in the given abstract state.
     * The result is projected to the precision in the argument.
     */
    fun getNextStates(state: S, command: ProbabilisticCommand<A>, prec: P): Collection<EnumeratedDistribution<S>>
}

const val P_ABSTRACTION = 0
const val P_CONCRETE = 1

class MenuGameAbstractor<S: State, A: Action, P: Prec>(
    val lts: ProbabilisticCommandLTS<S, A>,
    val init: InitFunc<S, P>,
    val transFunc: ProbabilisticCommandTransFunc<S, A, P>
) {

    sealed class MenuGameNode<S: State, A: Action>(val player: Int) {
        data class StateNode<S: State, A: Action>(val s: S): MenuGameNode<S, A>(P_CONCRETE)
        data class ResultNode<S: State, A: Action>(
            val s: S, val a: ProbabilisticCommand<A>
        ): MenuGameNode<S, A>(P_ABSTRACTION) {
//            companion object {var id = 0}
//            val _id = id++
//            override fun toString(): String {
//                return "ResultNode$id"
//            }
        }
    }

    sealed class MenuGameAction<S: State, A: Action>() {
        data class ChosenCommand<S: State, A: Action>(val command: ProbabilisticCommand<A>): MenuGameAction<S, A>()
        data class AbstractionDecision<S: State, A: Action>(val result: EnumeratedDistribution<S>): MenuGameAction<S, A>()
    }

    fun computeAbstraction(prec: P): StochasticGame<MenuGameNode<S, A>, MenuGameAction<S, A>> {
        return object : ImplicitStochasticGame<MenuGameNode<S, A>, MenuGameAction<S, A>>() {

            override val initialNode: MenuGameNode<S, A>
                get() = MenuGameNode.StateNode(init.getInitStates(prec).first()) // TODO: cannot handle multiple abstract inits yet

            override fun getPlayer(node: MenuGameNode<S, A>): Int = node.player

            override fun getResult(node: MenuGameNode<S, A>, action: MenuGameAction<S, A>): EnumeratedDistribution<MenuGameNode<S, A>> {
                return when(node) {
                    is MenuGameNode.StateNode -> when(action) {
                        is MenuGameAction.AbstractionDecision -> throw IllegalArgumentException("Result called for unavailable action $action on node $node")
                        is MenuGameAction.ChosenCommand -> EnumeratedDistribution.dirac(
                            MenuGameNode.ResultNode(node.s, action.command)
                        )
                    }
                    is MenuGameNode.ResultNode -> when(action) {
                        is MenuGameAction.AbstractionDecision -> action.result.transform { MenuGameNode.StateNode(it) }
                        is MenuGameAction.ChosenCommand -> throw IllegalArgumentException("Result called for unavailable action $action on node $node")
                    }
                }
            }

            override fun getAvailableActions(node: MenuGameNode<S, A>): Collection<MenuGameAction<S, A>> {
                return when(node) {
                    is MenuGameNode.StateNode ->
                        if(node.s.isBottom) listOf()
                        else lts.getAvailableCommands(node.s).map { MenuGameAction.ChosenCommand(it) }
                    is MenuGameNode.ResultNode ->
                        transFunc.getNextStates(node.s, node.a, prec).map { MenuGameAction.AbstractionDecision(it) }
                }
            }

        }
    }

}