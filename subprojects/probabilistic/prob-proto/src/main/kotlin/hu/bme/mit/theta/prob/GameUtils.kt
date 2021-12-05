package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.State

fun <S: State, LAbs, LConc> step(node: AbstractionGame.StateNode<S, LAbs>,
                          strategyA: Map<AbstractionGame.StateNode<S, LAbs>, AbstractionGame.ChoiceNode<S, LConc>>
): List<AbstractionGame.StateNode<S, LAbs>> {
    TODO()
}