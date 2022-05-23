package hu.bme.mit.theta.prob.game.analysis

import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.prob.game.AbstractionGame.*
import hu.bme.mit.theta.prob.game.analysis.ERGNode.WrappedChoiceNode
import hu.bme.mit.theta.prob.game.analysis.ERGNode.WrappedStateNode
import hu.bme.mit.theta.prob.game.AbstractionGame
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class OptimType {
    MAX, MIN
}
fun <T> OptimType.select(m: Map<T, Double>): Double? =
    when(this) {
        OptimType.MAX -> m.maxByOrNull { it.value }?.value
        OptimType.MIN -> m.minByOrNull { it.value }?.value
    }
fun OptimType.select(l: List<Double>): Double? =
    when(this) {
        OptimType.MAX -> l.maxOrNull()
        OptimType.MIN -> l.minOrNull()
    }

fun <T> OptimType.argSelect(m: Map<T, Double>): T?  =
    when(this) {
        OptimType.MAX -> m.maxByOrNull { it.value }?.key
        OptimType.MIN -> m.minByOrNull { it.value }?.key
    }
fun <T> OptimType.argSelect(m: List<Pair<T, Double>>): T?  =
    when(this) {
        OptimType.MAX -> m.maxByOrNull { it.second }?.first
        OptimType.MIN -> m.minByOrNull { it.second }?.first
    }
fun OptimType.unitForProb(): Double =
    when (this) {
        OptimType.MAX -> 0.0
        OptimType.MIN -> 1.0
    }


// TODO: this is wasteful
private sealed class ERGNode<S: State, LA, LC>() {
    data class WrappedStateNode<S: State, LA, LC>(
        val stateNode: StateNode<S, LA, LC>): ERGNode<S, LA, LC>()
    data class WrappedChoiceNode<S: State, LA, LC>(
        val choiceNode: ChoiceNode<S, LA, LC>): ERGNode<S, LA, LC>()
}

private class EdgeRelationGraph<S: State, LA, LC>(
    val nodes: List<ERGNode<S, LA, LC>>,
    val edgeList: MutableList<MutableList<Int>>
)

data class AbstractionGameCheckResult<S: State, LAbs, LConc>(
    val abstractionNodeValues: Map<StateNode<S, LAbs, LConc>, Double>,
    val concreteChoiceNodeValues: Map<ChoiceNode<S, LAbs, LConc>, Double>
)

private typealias MECType<S, LA, LC> = Pair<Set<StateNode<S, LA, LC>>, Set<ChoiceNode<S, LA, LC>>>

fun <S : State, LAbs, LConc> BVI(
    game: AbstractionGame<S, LAbs, LConc>,
    playerAGoal: OptimType, playerCGoal: OptimType,
    threshold: Double,
    LAinit: Map<StateNode<S, LAbs, LConc>, Double>,
    LCinit: Map<ChoiceNode<S, LAbs, LConc>, Double>,
    UAinit: Map<StateNode<S, LAbs, LConc>, Double>,
    UCinit: Map<ChoiceNode<S, LAbs, LConc>, Double>,
    checkedStateNodes: List<StateNode<S, LAbs, LConc>>,
    collapseMecs: Boolean = false // Uses collapsing instead of deflation if the playerA and playerC goals coincide
): AbstractionGameCheckResult<S, LAbs, LConc> {

    val collapseMecs = collapseMecs && playerAGoal == playerCGoal
    val converged = hashSetOf<StateNode<S, LAbs, LConc>>()

    fun precomputeMecs(): List<Pair<Set<StateNode<S, LAbs, LConc>>, Set<ChoiceNode<S, LAbs, LConc>>>> {
        val stateNodeIndices = hashMapOf<StateNode<S, LAbs, LConc>, Int>()
        var i = 0
        val wrappedStateNodes = game.stateNodes.map {
            stateNodeIndices[it] = i++
            WrappedStateNode<S, LAbs, LConc>(it)
        }
        val choiceNodeIndices = hashMapOf<ChoiceNode<S, LAbs, LConc>, Int>()
        val wrappedChoiceNodes = game.concreteChoiceNodes.map {
            choiceNodeIndices[it] = i++
            WrappedChoiceNode<S, LAbs, LConc>(it)
        }
        val ergNodes = wrappedStateNodes + wrappedChoiceNodes

        // Filtered for MSEC computation: only optimal edges
        val stateNodeEdges = wrappedStateNodes.map { wrappedNode ->
            wrappedNode.stateNode.outgoingEdges.toList().map { choiceNodeIndices[it.end]!! }.toMutableList()
        }
        val choiceNodeEdges = wrappedChoiceNodes.map { wrappedNode ->
            val pre = wrappedNode.choiceNode.outgoingEdges.toList().flatMap {
                it.end.pmf.keys.map { stateNodeIndices[it]!! }
            }
            pre.toSet().toMutableList()
        }
        val ergEdges = stateNodeEdges + choiceNodeEdges

        val ERG = EdgeRelationGraph(ergNodes, ergEdges.toMutableList())
        // As non-optimal minimizer actions have been removed, the computed MECs are MSECs
        // See Kelmendi et. al.: Value Iteration for Simple Stochastic Games..., Lemma 2.
        return computeMECs(ERG, stateNodeIndices, choiceNodeIndices)
    }
    val fullMecs = precomputeMecs()

    val mecExitingEdgesState = hashMapOf<StateNode<S, LAbs, LConc>, List<AbstractionChoiceEdge<S, LAbs, LConc>>>()
    val mecExitingEdgesChoice = hashMapOf<ChoiceNode<S, LAbs, LConc>, List<ConcreteChoiceEdge<S, LAbs, LConc>>>()
    for (mec in fullMecs) {
        val anyExiting =
            mec.first.any { it.outgoingEdges.any { it.end !in mec.second } } ||
            mec.second.any { it.outgoingEdges.any { it.end.pmf.keys.any {it !in mec.first} } }
        if(!anyExiting) continue
        for (stateNode in mec.first) {
            mecExitingEdgesState[stateNode] = stateNode.outgoingEdges.filter { it.end !in mec.second }
        }
        for (choiceNode in mec.second) {
            mecExitingEdgesChoice[choiceNode] = choiceNode.outgoingEdges.filter { it.end.pmf.keys.any {it !in mec.first} }
        }
    }

    var LA: HashMap<StateNode<S, LAbs, LConc>, Double> = HashMap(LAinit) // Lower approximation for Abstraction node values
    var LC: HashMap<ChoiceNode<S, LAbs, LConc>, Double> = HashMap(LCinit) // Lower approximation for Concrete choice node values
    var UA: HashMap<StateNode<S, LAbs, LConc>, Double> = HashMap(UAinit) // Upper approximation for Abstraction node values
    var UC: HashMap<ChoiceNode<S, LAbs, LConc>, Double> = HashMap(UCinit) // Upper approximation for Concrete choice node values
    var iters = 0
    do {
        iters++
        // Bellman updates
        val LAnext = HashMap(LA)
        val LCnext = HashMap(LC)
        val UAnext = HashMap(UA)
        val UCnext = HashMap(UC)

        for (stateNode in LA.keys) {
            val lEdgeValues =
                if (collapseMecs) {
                    var pre = (mecExitingEdgesState[stateNode] ?: stateNode.outgoingEdges).map { edge ->
                        LC[edge.end]!!
                    }
                    if(mecExitingEdgesState[stateNode] != null)
                        pre = pre + listOf(if (playerAGoal == OptimType.MAX) 0.0 else 1.0)
                    pre
                } else stateNode.outgoingEdges.map { edge ->
                    LC[edge.end]!!
                }
            LAnext[stateNode] = playerAGoal.select(lEdgeValues) ?: LAnext[stateNode]

            val uEdgeValues =
                if (collapseMecs) {
                    var pre = (mecExitingEdgesState[stateNode] ?: stateNode.outgoingEdges).map { edge ->
                        UC[edge.end]!!
                    }
                    if(mecExitingEdgesState[stateNode] != null)
                        pre = pre + listOf(if (playerAGoal == OptimType.MAX) 0.0 else 1.0)
                    pre
                } else stateNode.outgoingEdges.map { edge ->
                    UC[edge.end]!!
                }
            UAnext[stateNode] = playerAGoal.select(uEdgeValues) ?: UAnext[stateNode]
        }

        for (choiceNode in LC.keys) {
            val lEdgeValues =
                if (collapseMecs) (mecExitingEdgesChoice[choiceNode] ?: choiceNode.outgoingEdges).map { edge ->
                    edge.end.pmf.entries.sumByDouble { it.value * LA[it.key]!! }
                } + listOf(if (playerAGoal == OptimType.MAX) 0.0 else 1.0)
                else choiceNode.outgoingEdges.map { edge ->
                    edge.end.pmf.entries.sumByDouble { it.value * LA[it.key]!! }
                }
            LCnext[choiceNode] = playerCGoal.select(lEdgeValues)

            val uEdgeValues =
                if (collapseMecs) (mecExitingEdgesChoice[choiceNode] ?: choiceNode.outgoingEdges).map { edge ->
                    edge.end.pmf.entries.sumByDouble { it.value * UA[it.key]!! }
                } + listOf(if (playerAGoal == OptimType.MAX) 0.0 else 1.0)
                else choiceNode.outgoingEdges.map { edge ->
                    edge.end.pmf.entries.sumByDouble { it.value * UA[it.key]!! }
                }
            UCnext[choiceNode] = playerCGoal.select(uEdgeValues)
        }

        LA = LAnext
        LC = LCnext
        UA = UAnext
        UC = UCnext

        // Computing MSECs for deflation
        if (collapseMecs) {
            // TODO: this doesn't seem right
            for (mec in fullMecs) {
                val LOptim = playerAGoal.select(mec.first.map { LA[it]!! }+mec.second.map { LC[it]!! })
                val UOptim = playerAGoal.select(mec.first.map { UA[it]!! }+mec.second.map { UC[it]!! })
                for (stateNode in mec.first) {
                    LA[stateNode] = LOptim!!
                    UA[stateNode] = UOptim!!
                }
                for (choiceNode in mec.second) {
                    LC[choiceNode] = LOptim!!
                    UC[choiceNode] = UOptim!!
                }
            }
        } else {
            val stateNodeIndices = hashMapOf<StateNode<S, LAbs, LConc>, Int>()
            var i = 0
            val wrappedStateNodes = game.stateNodes.map {
                stateNodeIndices[it] = i++
                WrappedStateNode<S, LAbs, LConc>(it)
            }
            val choiceNodeIndices = hashMapOf<ChoiceNode<S, LAbs, LConc>, Int>()
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
                var bestExit = 0.0
                if(playerAGoal == OptimType.MAX) {
                    bestExit = msec.first.flatMap {
                        it.outgoingEdges.filter { it.end !in msec.second }.map { UC[it.end]!! }
                    }.maxOrNull() ?: 0.0
                }
                if(playerCGoal == OptimType.MAX) {
                    val bestAExit = msec.second.flatMap {
                        it.outgoingEdges
                            .filter { it.end.pmf.keys.any { it !in msec.first } }
                            .map {
                                it.end.pmf.entries.sumByDouble { (stateNode, prob) ->
                                    prob * (UA[stateNode]!!)
                                }
                            }
                    }.maxOrNull() ?: 0.0
                    if(bestAExit > bestExit) bestExit = bestAExit
                }

                for (stateNode in msec.first) {
                    UA[stateNode] = min(UA[stateNode]!!, bestExit)
                }

                for (choiceNode in msec.second) {
                    UC[choiceNode] = min(UC[choiceNode]!!, bestExit)
                }
            }
        }

        val errorOnCheckedNodes = checkedStateNodes.sumByDouble { abs(UA[it]!! - LA[it]!!) }
        val largestStateError = game.stateNodes.map { UA[it]!!-LA[it]!! }.maxOrNull() ?: 0.0
        val largestChoiceError = game.concreteChoiceNodes.map { UC[it]!!-LC[it]!! }.maxOrNull() ?: 0.0
        val largestError = max(largestStateError, largestChoiceError)
    } while(largestError > threshold)
//    } while(largestError > threshold)

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
    stateNodeIndices: Map<StateNode<S, LAbs, LConc>, Int>,
    choiceNodeIndices: Map<ChoiceNode<S, LAbs, LConc>, Int>
): List<MECType<S, LAbs, LConc>>
{
    var sccs: List<Set<Int>>
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
//                            .asSequence()
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
): List<Set<Int>>
{
    // The computation uses the Kosaraju algorithm
    // Everything can be treated through its index in ERG.nodes as an Int

    val L = Stack<Int>()
    L.ensureCapacity(ERG.nodes.size)

    //DFS to push every vertex onto L in their DFS *completion* order
    val E = ERG.edgeList

    val dfsstack = Stack<Int>()
    dfsstack.ensureCapacity(ERG.nodes.size)

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
    val SCCs: ArrayList<Set<Int>> = arrayListOf()
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

// Debug code:
// val (sg, am, cm) = game.toStochasticGame()
//val (ssg, map) = simplifySnakes(sg)
//val mapInv = map.inverseImage()
//val amInv = am.inverse()
//val cmInv = cm.inverse()
//val l = sgValsFromAgVals(LA, LC, am, cm)
//val u = sgValsFromAgVals(UA, UC, am, cm)
//val sl = l.mapKeys { (k,_) -> map[k]!! }
//val su = u.mapKeys { (k,_) -> map[k]!! }
//val graph = ssg.visualizeWithValueIntervals(sl, su, { n->
//    fullMecs.indexOfFirst { mec -> mapInv[n].any { amInv[it] in mec.first || cmInv[it] in mec.second } }
//})
//GraphvizWriter.getInstance().writeFile(graph, """E:\egyetem\dipterv\probabilistic-theta\debug_out\ssg.dot""")