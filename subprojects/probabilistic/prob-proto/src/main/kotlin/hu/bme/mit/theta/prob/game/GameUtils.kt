package hu.bme.mit.theta.prob.game

import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.prob.refinement.ChoiceNodeValues
import hu.bme.mit.theta.prob.refinement.StateNodeValues

data class GameConversionResult<S: State, LAbs, LConc>(
    val result: StochasticGame<S, Unit, LAbs, LConc>,
    val aMap: Map<AbstractionGame.StateNode<S, LAbs, LConc>, StochasticGame<S, Unit, LAbs, LConc>.ANode >,
    val cMap: Map<AbstractionGame.ChoiceNode<S, LAbs, LConc>, StochasticGame<S, Unit, LAbs, LConc>.CNode>
)

fun <S: State, LAbs, LConc> AbstractionGame<S, LAbs, LConc>.toStochasticGame():
        GameConversionResult<S, LAbs, LConc> {
    val result = StochasticGame<S, Unit, LAbs, LConc>()
    val aNodeMap = hashMapOf<AbstractionGame.StateNode<S, LAbs, LConc>,
                                StochasticGame<S, Unit, LAbs, LConc>.ANode>()
    val cNodeMap = hashMapOf<AbstractionGame.ChoiceNode<S, LAbs, LConc>,
            StochasticGame<S, Unit, LAbs, LConc>.CNode>()
    for (stateNode in stateNodes) {
        aNodeMap[stateNode] = result.ANode(stateNode.state, isInit = stateNode in this.initNodes)
    }
    for (choiceNode in concreteChoiceNodes) {
        cNodeMap[choiceNode] = result.CNode(Unit)
    }

    for (edge in this.abstractionChoiceEdges) {
        result.AEdge(edge.label, aNodeMap[edge.start]!!, hashMapOf(cNodeMap[edge.end]!! to 1.0))
    }
    for(edge in this.concreteChoiceEdges) {
        val end = edge.end.pmf.entries.associate {
            aNodeMap[it.key]!! as StochasticGame<S, Unit, LAbs, LConc>.Node to it.value
        }
        result.CEdge(edge.label, cNodeMap[edge.start]!!, end)
    }

    return GameConversionResult(result, aNodeMap, cNodeMap)
}

fun <K, V> Map<K,V>.inverse() = this.entries.associate { it.value to it.key }
fun <K, V> Map<K, V>.inverseImage(): Map<V, List<K>> {
    val res = this.values.toSet().map { it to arrayListOf<K>() }.toMap()
    for (k in keys) {
        res[this[k]]?.add(k)
    }
    return res
}
infix fun <K, T, V> Map<T, V>.compose(other: Map<K, T>): Map<K, V> =
    other.mapValues { (_, v) ->
        this[v] ?:
        throw java.lang.IllegalArgumentException("Non-composable maps: the value for $v is missing")
    }

fun <S: State, C, LAbs, LConc> sgValsFromAgVals(
    VA: StateNodeValues<S, LAbs, LConc>,
    VC: ChoiceNodeValues<S, LAbs, LConc>,
    aMap: Map<AbstractionGame.StateNode<S, LAbs, LConc>, StochasticGame<S, C, LAbs, LConc>.Node>,
    cMap: Map<AbstractionGame.ChoiceNode<S, LAbs, LConc>, StochasticGame<S, C, LAbs, LConc>.Node>,
) = (VA.entries.map { (k, v) -> aMap[k]!! to v } + VC.entries.map { (k, v) -> cMap[k]!! to v }).toMap()