package hu.bme.mit.theta.prob

import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

interface StochasticGamePass {
    fun <SAbs, SConc, LAbs, LConc>
            apply(game: StochasticGame<SAbs, SConc, LAbs, LConc>): PassResult<SAbs, SConc, LAbs, LConc>
}

data class PassResult<SAbs, SConc, LAbs, LConc>(
    val result: StochasticGame<SAbs, SConc, LAbs, LConc>,
    val nodeMap: Map<StochasticGame<SAbs, SConc, LAbs, LConc>.Node, StochasticGame<SAbs, SConc, LAbs, LConc>.Node>
)

class MergedGame<SAbs, SConc, LAbs, LConc>(): StochasticGame<
        List<StochasticGame<SAbs, SConc, LAbs, LConc>.Node>,
        List<StochasticGame<SAbs, SConc, LAbs, LConc>.Node>,
        List<StochasticGame<SAbs, SConc, LAbs, LConc>.Edge>,
        List<StochasticGame<SAbs, SConc, LAbs, LConc>.Edge>,
        >() {
    constructor(game: StochasticGame<SAbs, SConc, LAbs, LConc>): this() {
        val nodeMap = hashMapOf<StochasticGame<SAbs, SConc, LAbs, LConc>.Node, Node>()
        for (aNode in game.aNodes) {
            TODO()
        }
    }
}

private typealias MergedStochasticGame<SAbs, SConc, LAbs, LConc> =
        StochasticGame<
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Node>,
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Node>,
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Edge>,
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Edge>,
                >
private typealias MergedStochasticGameNode<SAbs, SConc, LAbs, LConc> =
        StochasticGame<
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Node>,
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Node>,
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Edge>,
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Edge>,
                >.Node
private typealias MergedStochasticGameANode<SAbs, SConc, LAbs, LConc> =
        StochasticGame<
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Node>,
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Node>,
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Edge>,
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Edge>,
                >.ANode
private typealias MergedStochasticGameCNode<SAbs, SConc, LAbs, LConc> =
        StochasticGame<
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Node>,
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Node>,
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Edge>,
                List<StochasticGame<SAbs, SConc, LAbs, LConc>.Edge>,
                >.CNode

/**
 * Replaces all snake patterns ->()->()->...->()->(head) with a single edge,
 * merging the nodes of the snake into the "head"
 */
fun <SAbs, SConc, LAbs, LConc> simplifySnakes(game: StochasticGame<SAbs, SConc, LAbs, LConc>): Pair<
        MergedStochasticGame<SAbs, SConc, LAbs, LConc>,
        HashMap<
                StochasticGame<SAbs, SConc, LAbs, LConc>.Node,
                MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>>
        > {
    val result = MergedStochasticGame<SAbs, SConc, LAbs, LConc>()
    val nodeMap = hashMapOf<
            StochasticGame<SAbs, SConc, LAbs, LConc>.Node,
            MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>
            >()


    fun isSimplifiable(node: StochasticGame<SAbs, SConc, LAbs, LConc>.Node): Boolean {
        return node.inEdges.size == 1 && node.outEdges.size == 1 &&
                node.outEdges.first().end.keys.size == 1 &&
                node.outEdges.first().end.keys.first().inEdges.size == 1
    }
    val nonSimplifiable = game.allNodes.filterNot(::isSimplifiable)

    // Creating nodes of the new game

    val q = ArrayDeque<StochasticGame<SAbs, SConc, LAbs, LConc>.Node>(nonSimplifiable.size)
    for (node in nonSimplifiable) {
        val res = when(node.owner) {
            StochasticGame.Companion.Player.A -> result.ANode(arrayListOf(node))
            StochasticGame.Companion.Player.C -> result.CNode(arrayListOf(node))
        }
        nodeMap[node] = res
        q.add(node)
    }

    while (!q.isEmpty()) {
        val curr = q.poll()
        if(curr.inEdges.size==1 && isSimplifiable(curr.inEdges.first().start)) {
            val nodeToMerge = curr.inEdges.first().start
            val merged = nodeMap[curr]!!
            nodeMap[nodeToMerge] = merged
            when(merged) {
                is StochasticGame.ANode -> (merged.s as ArrayList).add(0, nodeToMerge)
                is StochasticGame.CNode -> (merged.s as ArrayList).add(0, nodeToMerge)
            }
            q.add(nodeToMerge)
        }
    }

    // Filling in the edges
    for (edge in game.allEdges) {
        val start = nodeMap[edge.start]!!
        val end = edge.end.mapKeys { nodeMap[it.key]!! }
        if(end.size == 1 && start == end.keys.first()) continue
        // TODO: collect the original labels
        when(start) {
            is StochasticGame.ANode -> {result.AEdge(listOf(edge), start, end)}
            is StochasticGame.CNode -> {result.CEdge(listOf(edge), start, end)}
        }
    }

    return Pair(result, nodeMap)
}

fun <SAbs, SConc, LAbs, LConc> toMergedGame(game: StochasticGame<SAbs, SConc, LAbs, LConc>): Pair<
        MergedStochasticGame<SAbs, SConc, LAbs, LConc>,
        Map<
                StochasticGame<SAbs, SConc, LAbs, LConc>.Node,
                MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>>
        > {
    val res = MergedStochasticGame<SAbs, SConc, LAbs, LConc>()
    val aMap = game.aNodes.associateWith { res.ANode(listOf(it), isInit = it.isInit) }
    val cMap = game.cNodes.associateWith { res.CNode(listOf(it), isInit = it.isInit) }
    val nodeMap = aMap + cMap
    for (edge in game.allEdges) {
        val resEnd = edge.end.mapKeys { nodeMap[it.key]!! }
        when(edge) {
            is StochasticGame.AEdge -> res.AEdge(listOf(edge), aMap[edge.start]!!, resEnd)
            is StochasticGame.CEdge -> res.CEdge(listOf(edge), cMap[edge.start]!!, resEnd)
        }
    }
    return Pair(res, nodeMap)
}

object simplifySnakesPass {
    fun <SAbs, SConc, LAbs, LConc> apply(game: MergedStochasticGame<SAbs, SConc, LAbs, LConc>): Pair<
            MergedStochasticGame<SAbs, SConc, LAbs, LConc>,
            Map<
                    MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>,
                    MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>>
            > {
        game.addSelfLoops()
        val result = MergedStochasticGame<SAbs, SConc, LAbs, LConc>()
        val nodeMap = hashMapOf<
                MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>,
                MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>
                >()


        fun isSimplifiable(node: MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>): Boolean {
            return node.inEdges.size == 1 && node.outEdges.size == 1 &&
                    node.outEdges.first().end.keys.size == 1 &&
                    node.outEdges.first().end.keys.first().inEdges.size == 1
        }
        val nonSimplifiable = game.allNodes.filterNot(::isSimplifiable)

        // Creating nodes of the new game

        val q = ArrayDeque<MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>>(nonSimplifiable.size)
        for (node in nonSimplifiable) {
            val s = when(node) {
                is StochasticGame.ANode -> node.s
                is StochasticGame.CNode -> node.s
                else -> throw Exception()
            }
            val res = when(node.owner) {
                StochasticGame.Companion.Player.A -> result.ANode(s.toMutableList())
                StochasticGame.Companion.Player.C -> result.CNode(s.toMutableList())
            }
            nodeMap[node] = res
            q.add(node)
        }

        while (!q.isEmpty()) {
            val curr = q.poll()
            if(curr.inEdges.size==1 && isSimplifiable(curr.inEdges.first().start)) {
                val nodeToMerge = curr.inEdges.first().start
                val merged = nodeMap[curr]!!
                nodeMap[nodeToMerge] = merged
                val s = when(nodeToMerge) {
                    is StochasticGame.ANode -> nodeToMerge.s.toMutableList()
                    is StochasticGame.CNode -> nodeToMerge.s.toMutableList()
                    else -> throw Exception()
                }
                when(merged) {
                    is StochasticGame.ANode -> (merged.s as MutableList).addAll(0, s)
                    is StochasticGame.CNode -> (merged.s as MutableList).addAll(0, s)
                }
                q.add(nodeToMerge)
            }
        }

        // Filling in the edges
        for (edge in game.allEdges) {
            val start = nodeMap[edge.start]!!
            val end = edge.end.mapKeys { nodeMap[it.key]!! }
            if(end.size == 1 && start == end.keys.first()) continue
            // TODO: collect the original labels
            val l = when(edge) {
                is StochasticGame.AEdge -> edge.lbl
                is StochasticGame.CEdge -> edge.lbl
                else -> throw Exception()
            }
            when(start) {
                is StochasticGame.ANode -> {result.AEdge(l, start, end)}
                is StochasticGame.CNode -> {result.CEdge(l, start, end)}
            }
        }

        return Pair(result, nodeMap)
    }
}

object simplifySameOutsPass {
    fun <SAbs, SConc, LAbs, LConc> apply(game: MergedStochasticGame<SAbs, SConc, LAbs, LConc>): Pair<
            MergedStochasticGame<SAbs, SConc, LAbs, LConc>,
            Map<
                    MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>,
                    MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>>
            > {
        game.addSelfLoops()
        val result = MergedStochasticGame<SAbs, SConc, LAbs, LConc>()
        val nodeMap = hashMapOf<
                MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>,
                MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>
                >()

        val grouping = game.allNodes.groupBy { it.owner to it.outEdges.map { it.end }.toSet() }
        for ((key, nodes) in grouping) {
            val (owner, _) = key
            fun getSubnodes(node: MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>) =
                when(node) {
                    is StochasticGame.ANode -> node.s
                    is StochasticGame.CNode -> node.s
                    else -> throw Exception()
                }
            val resultNode = when(owner) {
                StochasticGame.Companion.Player.A ->
                    result.ANode(nodes.flatMap(::getSubnodes), isInit = nodes.any{it.isInit})
                StochasticGame.Companion.Player.C ->
                    result.CNode(nodes.flatMap(::getSubnodes), isInit = nodes.any{it.isInit})
            }
            nodes.forEach { nodeMap[it] = resultNode }
        }

        for ((key, nodes) in grouping) {
            val (_, ends) = key
            val resStart = nodeMap[nodes.first()]!!
            for (end in ends) {
                val resEnd = end.entries.map { (k,v) -> nodeMap[k]!! to v }.groupingBy { it.first }.fold(0.0) {
                        acc, elem -> acc + elem.second
                }
                if(resStart.outEdges.any {it.end == resEnd}) continue
                when(resStart) {
                    is StochasticGame.ANode -> result.AEdge(listOf(), resStart, resEnd)
                    is StochasticGame.CNode -> result.CEdge(listOf(), resStart, resEnd)
                }
            }
        }

        return Pair(result, nodeMap)
    }

}

object iterativeSimplificationPass {
    fun <SAbs, SConc, LAbs, LConc> apply(game: MergedStochasticGame<SAbs, SConc, LAbs, LConc>): Pair<
            MergedStochasticGame<SAbs, SConc, LAbs, LConc>,
            Map<
                    MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>,
                    MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>>
            > {
        var res = game
        var map = game.allNodes.associateWith { it } // Identity map
        do {
            val resSize = game.allNodes.size
            val (next, nextMap) = simplifySnakesPass.apply(res)
            map = map.mapValues { nextMap[it.value]!! }
            val (next2, nextMap2) = simplifySameOutsPass.apply(next)
            map = map.mapValues { nextMap2[it.value]!! }
            res = next2
        } while (resSize == res.allNodes.size)
        return Pair(res, map)
    }
}

fun <SAbs, SConc, LAbs, LConc> simplifySameOuts(game: StochasticGame<SAbs, SConc, LAbs, LConc>): Pair<
        MergedStochasticGame<SAbs, SConc, LAbs, LConc>,
        HashMap<
                StochasticGame<SAbs, SConc, LAbs, LConc>.Node,
                MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>>
        > {
    val result = MergedStochasticGame<SAbs, SConc, LAbs, LConc>()
    val nodeMap = hashMapOf<
            StochasticGame<SAbs, SConc, LAbs, LConc>.Node,
            MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>
            >()

    val grouping = game.allNodes.groupBy { it.owner to it.outEdges.map { it.end }.toSet() }
    for ((key, nodes) in grouping) {
        val (owner, _) = key
        val resultNode = when(owner) {
            StochasticGame.Companion.Player.A -> result.ANode(nodes, isInit = nodes.any{it.isInit})
            StochasticGame.Companion.Player.C -> result.CNode(nodes, isInit = nodes.any{it.isInit})
        }
        nodes.forEach { nodeMap[it] = resultNode }
    }

    for ((key, nodes) in grouping) {
        val (_, ends) = key
        val resStart = nodeMap[nodes.first()]!!
        for (end in ends) {
            val resEnd = end.entries.map { (k,v) -> nodeMap[k]!! to v }.groupingBy { it.first }.fold(0.0) {
                    acc, elem -> acc + elem.second
            }
            if(resStart.outEdges.any {it.end == resEnd}) continue
            when(resStart) {
                is StochasticGame.ANode -> result.AEdge(listOf(), resStart, resEnd)
                is StochasticGame.CNode -> result.CEdge(listOf(), resStart, resEnd)
            }
        }
    }

    return Pair(result, nodeMap)
}

/**
 * Creates a new stochastic game with collapsed maximal end components. The resulting game is
 * actually an MDP, as all nodes are ANodes in the game (MEC collapsing only makes sense when
 * a game is treated as an MDP).
 */
fun <SAbs, SConc, LAbs, LConc> collapseMecs(game: StochasticGame<SAbs, SConc, LAbs, LConc>): Pair<
        MergedStochasticGame<SAbs, SConc, LAbs, LConc>,
        HashMap<
                StochasticGame<SAbs, SConc, LAbs, LConc>.Node,
                MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>>
        > {
    val result = MergedStochasticGame<SAbs, SConc, LAbs, LConc>()
    val nodeMap = hashMapOf<
            StochasticGame<SAbs, SConc, LAbs, LConc>.Node,
            MergedStochasticGameNode<SAbs, SConc, LAbs, LConc>
            >()

    for (mec in game.computeMECs()) {
        val isInit = mec.any { it.isInit }
        val mergedNode = result.ANode(mec.toList(), isInit = isInit)
        mec.forEach { nodeMap[it] = mergedNode }
    }

    for (node in game.allNodes) {
        nodeMap.computeIfAbsent(node) { result.ANode(listOf(node), isInit = node.isInit) }
    }

    // Fill in the edges
    for (edge in game.allEdges) {
        val resultStart = nodeMap[edge.start]!! as StochasticGame.ANode
        val resultEnd = edge.end.entries.map { (k,v) -> nodeMap[k]!! to v }.groupingBy { it.first }.fold(0.0) {
            acc, elem -> acc + elem.second
        }
        if(resultEnd.size == 1 && resultStart == resultEnd.keys.first()) continue
        result.AEdge(listOf(edge), resultStart, resultEnd)
    }

    result.addSelfLoops()

    return Pair(result, nodeMap)
}


