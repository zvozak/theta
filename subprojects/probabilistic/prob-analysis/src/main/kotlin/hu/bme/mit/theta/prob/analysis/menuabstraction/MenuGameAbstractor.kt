package hu.bme.mit.theta.prob.analysis.menuabstraction

import hu.bme.mit.theta.analysis.Action
import hu.bme.mit.theta.analysis.InitFunc
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.inttype.IntExprs.Int
import hu.bme.mit.theta.core.type.inttype.IntType
import hu.bme.mit.theta.prob.analysis.ProbabilisticCommand
import hu.bme.mit.theta.probabilistic.FiniteDistribution
import hu.bme.mit.theta.probabilistic.GameRewardFunction
import hu.bme.mit.theta.probabilistic.ImplicitStochasticGame
import hu.bme.mit.theta.probabilistic.StochasticGame

interface ProbabilisticCommandLTS<S: State, A: Action> {
    fun getAvailableCommands(state: S): Collection<ProbabilisticCommand<A>>
    fun canFail(state: S, command: ProbabilisticCommand<A>): Boolean
}

interface ProbabilisticCommandTransFunc<S: State, A: Action, P: Prec> {
    /**
     * Computes a list of possible next state distributions after executing the given command in the given abstract state.
     * The result is projected to the precision in the argument.
     */
    fun getNextStates(state: S, command: ProbabilisticCommand<A>, prec: P): Collection<FiniteDistribution<S>>
}

const val P_ABSTRACTION = 0
const val P_CONCRETE = 1

class MenuGameAbstractor<S: State, A: Action, P: Prec>(
    val lts: ProbabilisticCommandLTS<S, A>,
    val init: InitFunc<S, P>,
    val transFunc: ProbabilisticCommandTransFunc<S, A, P>,
    val mayBeTarget: (S) -> Boolean,
    val mustBeTarget: (S) -> Boolean
) {

    sealed class MenuGameNode<S: State, A: Action>(val player: Int) {
        data class StateNode<S: State, A: Action>(val s: S): MenuGameNode<S, A>(P_CONCRETE)
        data class ResultNode<S: State, A: Action>(
            val s: S, val a: ProbabilisticCommand<A>
        ): MenuGameNode<S, A>(P_ABSTRACTION)
    }

    sealed class MenuGameAction<S: State, A: Action>() {
        data class ChosenCommand<S: State, A: Action>(val command: ProbabilisticCommand<A>): MenuGameAction<S, A>()
        data class AbstractionDecision<S: State, A: Action>(
            val result: FiniteDistribution<S>,
            val rewardExpression: Expr<IntType> = Int(0),
            val rewardValue: Int = 0
        ): MenuGameAction<S, A>()
    }

    fun computeAbstraction(prec: P): StochasticGame<MenuGameNode<S, A>, MenuGameAction<S, A>> {

        val rewardForMax: (MenuGameNode<S, A>)->Double = { n: MenuGameNode<S, A> ->
            when(n) {
                is MenuGameNode.StateNode<S, A> ->
                    if(n.s.isBottom) 0.0
                    else TODO()
                is MenuGameNode.ResultNode -> 0.0
            }
        }

        val rewardFunMax = object : GameRewardFunction<MenuGameNode<S, A>, MenuGameAction<S, A>> {
            override fun getStateReward(n: MenuGameNode<S, A>): Double {
                TODO("Not yet implemented")
            }

            override fun getEdgeReward(
                source: MenuGameNode<S, A>,
                action: MenuGameAction<S, A>,
                target: MenuGameNode<S, A>
            ): Double {
                TODO("Not yet implemented")
            }

        }

        val rewardingEdges = listOf<MenuGameAction<S, A>>()

        return object : ImplicitStochasticGame<MenuGameNode<S, A>, MenuGameAction<S, A>>() {

            override val initialNode: MenuGameNode<S, A>
                get() = MenuGameNode.StateNode(init.getInitStates(prec).first()) // TODO: cannot handle multiple abstract inits yet

            override fun getPlayer(node: MenuGameNode<S, A>): Int = node.player

            override fun getResult(node: MenuGameNode<S, A>, action: MenuGameAction<S, A>): FiniteDistribution<MenuGameNode<S, A>> {
                return when(node) {
                    is MenuGameNode.StateNode -> when(action) {
                        is MenuGameAction.AbstractionDecision -> throw IllegalArgumentException("Result called for unavailable action $action on node $node")
                        is MenuGameAction.ChosenCommand -> FiniteDistribution.dirac(
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