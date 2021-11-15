package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.prob.AbstractionGame.ChoiceNode
import hu.bme.mit.theta.prob.AbstractionGame.StateNode
import hu.bme.mit.theta.prob.ERGNode.WrappedChoiceNode
import hu.bme.mit.theta.prob.ERGNode.WrappedStateNode
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.min

enum class OptimType {
    MAX, MIN
}
fun <T> OptimType.select(m: Map<T, Double>): Double? =
    when(this) {
        OptimType.MAX -> m.maxBy { it.value }?.value
        OptimType.MIN -> m.minBy { it.value }?.value
    }
fun OptimType.select(l: List<Double>): Double? =
    when(this) {
        OptimType.MAX -> l.max()
        OptimType.MIN -> l.min()
    }

fun <T> OptimType.argSelect(m: Map<T, Double>): T?  =
    when(this) {
        OptimType.MAX -> m.maxBy { it.value }?.key
        OptimType.MIN -> m.minBy { it.value }?.key
    }


// TODO: this is wasteful
private sealed class ERGNode<S: State, LA, LC>() {
    data class WrappedStateNode<S: State, LA, LC>(
        val stateNode: StateNode<S, LA>): ERGNode<S, LA, LC>()
    data class WrappedChoiceNode<S: State, LA, LC>(
        val choiceNode: ChoiceNode<S, LC>): ERGNode<S, LA, LC>()
}

private class EdgeRelationGraph<S: State, LA, LC>(
    val nodes: List<ERGNode<S, LA, LC>>,
    val edgeList: MutableList<MutableList<Int>>
)

data class AbstractionGameCheckResult<S: State, LAbs, LConc>(
    val abstractionNodeValues: Map<StateNode<S, LAbs>, Double>,
    val concreteChoiceNodeValues: Map<ChoiceNode<S, LConc>, Double>
)

private typealias MECType<S, LA, LC> = Pair<Set<StateNode<S, LA>>, Set<ChoiceNode<S, LC>>>

fun <S : State, LAbs, LConc> BVI(
    game: AbstractionGame<S, LAbs, LConc>,
    playerAGoal: OptimType, playerCGoal: OptimType,
    threshold: Double,
    LAinit: Map<StateNode<S, LAbs>, Double>,
    LCinit: Map<ChoiceNode<S, LConc>, Double>,
    UAinit: Map<StateNode<S, LAbs>, Double>,
    UCinit: Map<ChoiceNode<S, LConc>, Double>,
    checkedStateNodes: List<StateNode<S, LAbs>>
): AbstractionGameCheckResult<S, LAbs, LConc> {
    // TODO: check performance with array conversion
    var LA = HashMap(LAinit) // Lower approximation for Abstraction node values
    var LC = HashMap(LCinit) // Lower approximation for Concrete choice node values
    var UA = HashMap(UAinit) // Upper approximation for Abstraction node values
    var UC = HashMap(UCinit) // Upper approximation for Concrete choice node values

    do {
        // Bellman updates
        val LAnext = HashMap(LA)
        val LCnext = HashMap(LC)
        val UAnext = HashMap(UA)
        val UCnext = HashMap(UC)

        for (stateNode in LA.keys) {
            val lEdgeValues = stateNode.outgoingEdges.map { edge ->
                LC[edge.end]!!
            }
            LAnext[stateNode] = playerAGoal.select(lEdgeValues)

            val uEdgeValues = stateNode.outgoingEdges.map { edge ->
                UC[edge.end]!!
            }
            UAnext[stateNode] = playerAGoal.select(uEdgeValues)
        }

        for (choiceNode in LC.keys) {
            val lEdgeValues = choiceNode.outgoingEdges.map { edge ->
                edge.end.pmf.entries.sumByDouble { it.value * LA[it.key]!! }
            }
            LCnext[choiceNode] = playerCGoal.select(lEdgeValues)

            val uEdgeValues = choiceNode.outgoingEdges.map { edge ->
                edge.end.pmf.entries.sumByDouble { it.value * UA[it.key]!! }
            }
            UCnext[choiceNode] = playerCGoal.select(uEdgeValues)
        }

        // Computing MSECs for deflation
        val stateNodeIndices = hashMapOf<StateNode<S, LAbs>, Int>()
        var i = 0
        val wrappedStateNodes = game.stateNodes.map {
            stateNodeIndices[it] = i++
            WrappedStateNode<S, LAbs, LConc>(it)
        }
        val choiceNodeIndices = hashMapOf<ChoiceNode<S, LConc>, Int>()
        val wrappedChoiceNodes = game.concreteChoiceNodes.map {
            choiceNodeIndices[it] = i++
            WrappedChoiceNode<S, LAbs, LConc>(it)
        }
        val ergNodes = wrappedStateNodes + wrappedChoiceNodes

        // Filtered for MSEC computation: only optimal edges
        val stateNodeEdges = wrappedStateNodes.map { wrappedNode ->
            var filteredEdges = wrappedNode.stateNode.outgoingEdges.toList()
            if(playerAGoal == OptimType.MIN)
                filteredEdges = filteredEdges.filterNot {
                    val edgeValue = LC[it.end]!!
                    // TODO: check if relaxed comparison (double equality) is needed
                    return@filterNot edgeValue > LA[wrappedNode.stateNode]!!
                }
            filteredEdges.map { choiceNodeIndices[it.end]!! }.toMutableList()
        }
        val choiceNodeEdges = wrappedChoiceNodes.map { wrappedNode ->
            var filteredEdges = wrappedNode.choiceNode.outgoingEdges.toList()
            if(playerCGoal == OptimType.MIN) {
                filteredEdges = filteredEdges.filterNot { edge ->
                    val edgeValue = edge.end.pmf.entries
                        .sumByDouble { (stateNode, prob) ->
                            prob * (LA[stateNode]!!)
                        }
                    return@filterNot edgeValue > LC[wrappedNode.choiceNode]!!
                }
            }
            val pre = filteredEdges.flatMap {
                it.end.pmf.keys.map { stateNodeIndices[it]!! }
            }
            pre.toSet().toMutableList()
        }
        val ergEdges = stateNodeEdges + choiceNodeEdges

        val ERG = EdgeRelationGraph(ergNodes, ergEdges.toMutableList())
        // As non-optimal minimizer actions have been removed, the computed MECs are MSECs
        // See Kelmendi et. al.: Value Iteration for Simple Stochastic Games..., Lemma 2.
        val msecs = computeMECs(ERG, stateNodeIndices, choiceNodeIndices)

        // Deflation
        for (msec in msecs) {
            if(playerAGoal == OptimType.MAX) {
                val bestExit = msec.first.flatMap {
                    it.outgoingEdges.map { UC[it.end]!! }
                }.max() ?: 0.0
                for (stateNode in msec.first) {
                    UA[stateNode] = min(UA[stateNode]!!, bestExit)
                }
            }
            if(playerCGoal == OptimType.MAX) {
                val bestExit = msec.second.flatMap {
                    it.outgoingEdges.map {
                        it.end.pmf.entries.sumByDouble { (stateNode, prob) ->
                            prob * (LA[stateNode]!!)
                        }
                    }
                }.max() ?: 0.0
                for (choiceNode in msec.second) {
                    UC[choiceNode] = min(UC[choiceNode]!!, bestExit)
                }
            }
        }

        val errorOnCheckedNodes = checkedStateNodes.sumByDouble { Math.abs(UA[it]!! - LA[it]!!) }
    } while(errorOnCheckedNodes > threshold)

    return AbstractionGameCheckResult(avgMap(LA, UA), avgMap(LC, UC))
}

private fun <T> avgMap(map1: Map<T, Double>, map2: Map<T, Double>): HashMap<T, Double> {
    val keys = map1.keys.union(map2.keys)
    val res = hashMapOf<T, Double>()
    for (key in keys) {
        res[key] = (map1.getOrDefault(key, 0.0) + map2.getOrDefault(key, 0.0)) / 2.0
    }
    return res
}

private fun <S: State, LAbs, LConc> computeMECs(
    ERG: EdgeRelationGraph<S, LAbs, LConc>,
    stateNodeIndices: Map<StateNode<S, LAbs>, Int>,
    choiceNodeIndices: Map<ChoiceNode<S, LConc>, Int>
): List<MECType<S, LAbs, LConc>>
{
    var sccs: Set<Set<Int>>
    do {
        var modified = false
        sccs = computeSCCs(ERG)
        for (scc in sccs) {
            for(nodeIdx in scc) {
                val node = ERG.nodes[nodeIdx]
                when(node) {
                    is WrappedStateNode<S, LAbs, LConc> -> {
                        if(ERG.edgeList[nodeIdx].retainAll(scc))
                            modified = true
                    }
                    is WrappedChoiceNode<S, LAbs, LConc> -> {
                         val newNeighbors = node.choiceNode.outgoingEdges
                            .asSequence()
                            .map { it.end.pmf.keys.map { stateNodeIndices[it]!! } }
                            .filter { it.all(scc::contains) }
                            .flatten().toSet().toMutableList()
                        if(ERG.edgeList[nodeIdx].size != newNeighbors.size)
                            modified = true
                        ERG.edgeList[nodeIdx] = newNeighbors
                    }
                }
            }
        }
    } while (modified)

    val nodeSCCs = sccs.map { it: Set<Int> ->
        val nodes = it.map { ERG.nodes[it] }
        val stateNodes = nodes
            .filterIsInstance<WrappedStateNode<S, LAbs, LConc>>()
            .map(WrappedStateNode<S, LAbs, LConc>::stateNode)
        val choiceNodes = nodes
            .filterIsInstance<WrappedChoiceNode<S, LAbs, LConc>>()
            .map(WrappedChoiceNode<S, LAbs, LConc>::choiceNode)
        return@map Pair(stateNodes.toSet(), choiceNodes.toSet())
    }.filter { it.first.size + it.second.size > 1 }

    return nodeSCCs
}

private fun <S:State, LA, LC> computeSCCs(ERG: EdgeRelationGraph<S, LA, LC>
): Set<Set<Int>>
{
    // The computation uses the Kosaraju algorithm
    // Everything can be treated through its index in ERG.nodes as an Int

    val L = Stack<Int>()

    //DFS to push every vertex onto L in their DFS *completion* order
    val E = ERG.edgeList

    val dfsstack = Stack<Int>()
    val visited = Array(E.size) {false}
    for(i in E.indices) {
        if(visited[i]) continue
        dfsstack.push(i)
        while (!dfsstack.empty()) {
            val u = dfsstack.peek()
            visited[u] = true
            var completed = true
            for (v in E[u]) {
                if(!visited[v]) {
                    completed = false
                    dfsstack.push(v)
                }
            }
            if(completed) {
                dfsstack.pop()
                L.push(u)
            }
        }
    }

    val EInv = Array(E.size) {ArrayList<Int>()}
    for ((u, list) in E.withIndex()) {
        for (v in list) {
            EInv[v].add(u)
        }
    }

    val q: Queue<Int> = ArrayDeque<Int>()
    val assigned = Array(E.size) {false}
    val SCCs: HashSet<Set<Int>> = HashSet<Set<Int>>()
    while (!L.empty()) {
        val u=L.pop()
        if(assigned[u]) continue
        val scc = HashSet<Int>()
        q.add(u)
        while (!q.isEmpty()) {
            val v = q.poll()
            scc.add(v)
            assigned[v] = true
            for (w in EInv[v]) {
                if(!assigned[w]) q.add(w)
            }
        }
        SCCs.add(scc)
    }

    return SCCs
}