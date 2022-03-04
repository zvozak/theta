package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.State

data class GameConversionResult<S: State, LAbs, LConc>(
    val result: StochasticGame<S, Unit, LAbs, LConc>,
    val aMap: Map<AbstractionGame.StateNode<S, LAbs, LConc>, StochasticGame<S, Unit, LAbs, LConc>.ANode >,
    val cMap: Map<AbstractionGame.ChoiceNode<S, LAbs, LConc>, StochasticGame<S, Unit, LAbs, LConc>.CNode>
)

fun <S: State, LAbs, LConc> AbstractionGame<S, LAbs, LConc>.toStochasticGame():
 GameConversionResult<S, LAbs, LConc>{
    val result = StochasticGame<S, Unit, LAbs, LConc>()
    val aNodeMap = hashMapOf<AbstractionGame.StateNode<S, LAbs, LConc>,
                                StochasticGame<S, Unit, LAbs, LConc>.ANode>()
    val cNodeMap = hashMapOf<AbstractionGame.ChoiceNode<S, LAbs, LConc>,
            StochasticGame<S, Unit, LAbs, LConc>.CNode>()
    for (stateNode in stateNodes) {
        aNodeMap[stateNode] = result.ANode(stateNode.state)
    }
    for (choiceNode in concreteChoiceNodes) {
        cNodeMap[choiceNode] = result.CNode(Unit)
    }

    for (edge in this.abstractionChoiceEdges) {
        result.AEdge(edge.label, aNodeMap[edge.start]!!, hashMapOf(cNodeMap[edge.end]!! to 1.0))
    }
    for(edge in this.concreteChoiceEdges) {
        val end = edge.end.pmf.entries.map { aNodeMap[it.key]!! to it.value }.toMap()
        result.CEdge(edge.label, cNodeMap[edge.start]!!, end)
    }

    return GameConversionResult(result, aNodeMap, cNodeMap)
}

fun <K, V> Map<K,V>.inverse() = this.entries.map { it.value to it.key }.toMap()