package hu.bme.mit.theta.prob.game

import hu.bme.mit.theta.common.visualization.EdgeAttributes
import hu.bme.mit.theta.common.visualization.Graph
import hu.bme.mit.theta.common.visualization.LineStyle
import hu.bme.mit.theta.common.visualization.NodeAttributes
import hu.bme.mit.theta.prob.game.analysis.OptimType
import hu.bme.mit.theta.prob.game.analysis.argSelect
import hu.bme.mit.theta.prob.game.analysis.select
import java.awt.Color
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.min


open class StochasticGame<SAbs, SConc, LAbs, LConc> {

    companion object {
        enum class Player {
            A, C
        }
    }

    // Nodes
    abstract inner class Node(var owner: Player, _isInit: Boolean = false) {
        abstract val outEdges: ArrayList<out Edge>
        abstract val inEdges: ArrayList<Edge>

        var isInit: Boolean = _isInit
            set(value) {
                if (value) {
                    field = true
                    if (this !in initNodes) initNodes.add(this)
                } else {
                    initNodes.remove(this)
                    field = false
                }
            }

        fun optimStep(n: Int, V: (Node) -> Double, optim: (Player) -> OptimType) =
            step(n) { it.valueOptimalChoice(V, optim) }

        fun step(n: Int, strat: (Node) -> Edge?): Map<Node, Double> {
            if (n == 0) return hashMapOf(this to 1.0)
            else return strat(this)?.end?.entries
                ?.flatMap {
                    it.key.step(n - 1, strat).map { (k, v) -> k to it.value * v }
                }?.toMap() ?: hashMapOf(this to 1.0)
        }

        fun valueOptimalChoice(V: (Node) -> Double, optim: (Player) -> OptimType): Edge? {
            return optim(this.owner).argSelect(
                outEdges.map {
                    it to it.end.entries.sumByDouble { it.value * V(it.key) }
                }.toMap()
            )
        }
    }

    inner class ANode(
        val s: SAbs,
        override val outEdges: ArrayList<AEdge> = arrayListOf(),
        override val inEdges: ArrayList<Edge> = arrayListOf(),
        isInit: Boolean = false
    ) : Node(Player.A, isInit) {
        init {
            aNodes.add(this)
            if (isInit) initNodes.add(this)
        }

        override fun toString(): String {
            return s.toString()
        }
    }

    inner class CNode(
        val s: SConc,
        override val outEdges: ArrayList<CEdge> = arrayListOf(),
        override val inEdges: ArrayList<Edge> = arrayListOf(),
        isInit: Boolean = false
    ) : Node(Player.C, isInit) {
        init {
            cNodes.add(this)
            if (isInit) initNodes.add(this)
        }

        override fun toString(): String {
            return s.toString()
        }
    }

    val aNodes: ArrayList<ANode> = arrayListOf()
    val cNodes: ArrayList<CNode> = arrayListOf()
    val allNodes get() = aNodes + cNodes
    val initNodes: ArrayList<Node> = arrayListOf()

    fun getCNodeWithChoices(s: SConc, edges: Set<Pair<LConc, Map<Node, Double>>>) =
        cNodes.firstOrNull {
            it.outEdges.size == edges.size &&
                    it.outEdges.all { Pair(it.lbl, it.end) in edges }
        } ?: run {
            val n = CNode(s)
            for ((lbl, end) in edges) {
                CEdge(lbl, n, end)
            }
            return@run n
        }


    // Edges
    abstract inner class Edge {
        abstract val start: Node
        abstract val end: Map<out Node, Double>
    }

    inner class AEdge(
        val lbl: LAbs?,
        override val start: ANode,
        override val end: Map<Node, Double>,
    ) : Edge() {
        init {
            aEdges.add(this)
            start.outEdges.add(this)
            end.keys.forEach {
                it.inEdges.add(this)
            }
        }

        override fun toString(): String {
            return "Edge: $lbl"
        }
    }

    inner class CEdge(
        val lbl: LConc?,
        override val start: CNode,
        override val end: Map<Node, Double>
    ) : Edge() {
        init {
            cEdges.add(this)
            start.outEdges.add(this)
            end.keys.forEach { it.inEdges.add(this) }
        }

        override fun toString(): String {
            return "Edge: $lbl"
        }
    }

    val aEdges: ArrayList<AEdge> = arrayListOf()
    val cEdges: ArrayList<CEdge> = arrayListOf()
    val allEdges get() = aEdges + cEdges

    /**
     * Computes the set of states from which player forPlayer can be sure of entering the set nodes in one round,
     * regardless of the move chosen by the other player, if the players choose only from permittedEdge.
     */
    private fun pre(forPlayer: Player, nodes: Set<Node>, permittedEdges: Set<Edge>): Set<Node> {
        addSelfLoops()
        val potentialPreNodes =
            nodes.flatMap { it.inEdges }.intersect(permittedEdges).map { it.start }.toSet()
                .union(nodes.filter { it.outEdges.isEmpty() })
        return potentialPreNodes.filter {
            if (it.owner == forPlayer) {
                it.outEdges.intersect(permittedEdges).any { nodes.containsAll(it.end.keys) }
            } else {
                it.outEdges.intersect(permittedEdges).all { nodes.containsAll(it.end.keys) }
            }
        }.toSet()
    }

    /**
     * Computes the largest subset of targetSet, that the specified player can be sure of not leaving
     * at any time in the future, regardless of the moves chosen by the other player
     */
    private fun safe(forPlayer: Player, targetSet: Set<Node>, permittedEdges: Set<Edge>): Set<Node> {
        // TODO: this could be implemented to run in linear time with an appropriate data structure,
        //      or as nested fixed-point iteration (saturation?)
        var currResult = targetSet
        do {
            val prevSize = currResult.size
            currResult = currResult.intersect(pre(forPlayer, currResult, permittedEdges))
        } while (currResult.size != prevSize)
        return currResult
    }

    /**
     * Computes the largest subset of the forPlayer-edges in permittedEdges that guarantees
     * that the game stays in targetSet for at least one round. The resulting edge set keeps
     * all edges belonging to the other player in permittedEdges (this method is used as a
     * subprocedure of computing almost sure reachability, and this decision makes the iterations
     * of that method simpler).
     */
    private fun stay(forPlayer: Player, targetSet: Set<Node>, permittedEdges: Set<Edge>): Set<Edge> {
        return permittedEdges.filter {
            it.start.owner != forPlayer || // keeping all edges for the other player, see doc
                    targetSet.containsAll(it.end.keys)
        }.toSet()
    }

    /**
     * Computes the set of nodes from where forPlayer can play in a way that guarantees that the game will
     * almost surely enter the target set of nodes, regardless of the other player's moves.
     */
    fun almostSure(forPlayer: Player, target: Set<Node>): Set<Node> {
        // See "de Alfaro et. al.: Concurrent reachability games" for description of the algorithm
        var result = allNodes.toSet()
        var permittedEdges = allEdges.toSet()
        val otherPlayer = when (forPlayer) {
            Player.A -> Player.C
            Player.C -> Player.A
        }

        do {
            val prevSize = result.size
            val C = safe(otherPlayer, result - target, permittedEdges)
            result = safe(forPlayer, result - C, permittedEdges)
            permittedEdges = stay(forPlayer, result, permittedEdges)
        } while (result.size != prevSize)

        println("Almost sure result size: " + result.size)
        return result
    }

    fun visualize(): Graph {
        val result = Graph("StochasticGame", "StochasticGame")
        val nodes = allNodes
        val indexMap = hashMapOf<Node, Int>()
        for ((index, node) in nodes.withIndex()) {
            var attr = NodeAttributes.builder()
                .label(
                    when (node) {
                        is ANode -> node.s.toString()
                        is CNode -> node.s.toString()
                        else -> "Error"
                    }
                )
            if (node.owner == Player.A) attr = attr.lineStyle(LineStyle.DASHED)
            result.addNode(index.toString(), attr.build())
            indexMap[node] = index
        }

        for ((index, edge) in allEdges.withIndex()) {
            for ((endNode, prob) in edge.end) {
                val attr = EdgeAttributes.builder()
                    .label(
                        when (edge) {
                            is AEdge -> edge.lbl.toString()
                            is CEdge -> edge.lbl.toString()
                            else -> "Error"
                        }
                    )
                result.addEdge(indexMap[edge.start].toString(), indexMap[endNode].toString(), attr.build())
            }
        }

        return result
    }

    fun visualizeWithValueIntervals(
        lower: Map<Node, Double>,
        upper: Map<Node, Double>,
        mecIdx: (Node) -> Int = { -1 },
        convThreshold: Double = 1e-5
    ): Graph {
        val mecColors = listOf(
            Color.GREEN, Color.BLUE, Color.RED, Color.MAGENTA, Color.ORANGE, Color.YELLOW,
            Color.LIGHT_GRAY
        )
        val converged = allNodes.filter { upper[it]!! - lower[it]!! < convThreshold }.toSet()

        val result = Graph("StochasticGame", "StochasticGame")
        val nodes = allNodes
        val indexMap = hashMapOf<Node, Int>()
        for ((index, node) in nodes.withIndex()) {
            var attr = NodeAttributes.builder()
                .label("${lower[node]} - ${upper[node]}")
            if (node.owner == Player.A) attr = attr.lineStyle(LineStyle.DASHED)
            attr =
                if (upper[node]!! - lower[node]!! < convThreshold)
                    attr.fillColor(Color.CYAN)
                else
                    attr.fillColor(Color.pink)
            if (mecIdx(node) != -1) {
                attr = attr.lineColor(mecColors[mecIdx(node) % mecColors.size])
            }
            result.addNode(index.toString(), attr.build())
            indexMap[node] = index
        }

        for (edge in allEdges) {
            for ((endNode, prob) in edge.end) {
                val attr = EdgeAttributes.builder()
                    .label(
                        "$prob : ${
                            when (edge) {
                                is AEdge -> edge.lbl.toString()
                                is CEdge -> edge.lbl.toString()
                                else -> "Error"
                            }
                        }"
                    ).color(if (prob != 1.0) Color.GREEN else Color.BLACK)
                result.addEdge(indexMap[edge.start].toString(), indexMap[endNode].toString(), attr.build())
            }
        }

        return result
    }

    fun addSelfLoops() {
        for (aNode in aNodes.filter { it.outEdges.size == 0 }) {
            AEdge(null, aNode, mapOf(aNode to 1.0))
        }
        for (cNode in cNodes.filter { it.outEdges.size == 0 }) {
            CEdge(null, cNode, mapOf(cNode to 1.0))
        }
    }

    fun computeMECs() =
        computeMECs(allNodes.associateWith { it.outEdges })

    fun computeMECs(
        allowedEdges: Map<Node, List<Edge>>
    ): List<Set<Node>> {
        val nodes = allNodes
        val nodeIndices = allNodes.withIndex().associate { it.value to it.index }
        val ergEdgeList = nodes.map {
            allowedEdges[it]!!.flatMap { it.end.keys.mapNotNull(nodeIndices::get) }.toSet().toList()
        }.toMutableList()

        var sccs: List<Set<Int>>
        do {
            var modified = false
            sccs = computeSCCs(nodes, ergEdgeList)
            for (scc in sccs) {
                for (nodeIdx in scc) {
                    val node = nodes[nodeIdx]
                    val newNeighbors = (allowedEdges[node] ?: arrayListOf())
                        .map { it.end.keys.map { nodeIndices[it]!! } }
                        .filter { it.all(scc::contains) }
                        .flatten().toSet().toList()
                    if (ergEdgeList[nodeIdx].size != newNeighbors.size)
                        modified = true
                    ergEdgeList[nodeIdx] = newNeighbors
                }
            }
        } while (modified)

        val nodeSCCs = sccs.filter { it.size > 1 }.map { it.map(nodes::get).toSet() }

        return nodeSCCs
    }

    private fun computeSCCs(
        nodes: List<Node>,
        ergEdgeList: List<List<Int>>
    ): List<Set<Int>> {
        // The computation uses the Kosaraju algorithm
        // Using Ints from the node indices instead of the node objects

        val L = Stack<Int>()
        L.ensureCapacity(nodes.size)

        //DFS to push every vertex onto L in their DFS *completion* order
        val E = ergEdgeList

        val dfsstack = Stack<Int>()
        dfsstack.ensureCapacity(nodes.size)

        val visited = Array(E.size) { false }
        for (i in E.indices) {
            if (visited[i]) continue
            dfsstack.push(i)
            while (!dfsstack.empty()) {
                val u = dfsstack.peek()
                visited[u] = true
                var completed = true
                for (v in E[u]) {
                    if (!visited[v]) {
                        completed = false
                        dfsstack.push(v)
                    }
                }
                if (completed) {
                    dfsstack.pop()
                    L.push(u)
                }
            }
        }

        val EInv = Array(E.size) { ArrayList<Int>() }
        for ((u, list) in E.withIndex()) {
            for (v in list) {
                EInv[v].add(u)
            }
        }

        val q: Queue<Int> = ArrayDeque<Int>()
        val assigned = Array(E.size) { false }
        val SCCs: ArrayList<Set<Int>> = arrayListOf()
        while (!L.empty()) {
            val u = L.pop()
            if (assigned[u]) continue
            val scc = HashSet<Int>()
            q.add(u)
            while (!q.isEmpty()) {
                val v = q.poll()
                scc.add(v)
                assigned[v] = true
                for (w in EInv[v]) {
                    if (!assigned[w]) q.add(w)
                }
            }
            SCCs.add(scc)
        }

        return SCCs
    }

    /**
     * Bounded Value iteration for stochastic game. The goals of the player might be opposing.
     * Uses deflation to make the upper approximation converge.
     */
    fun BVI(
        goal: (Player) -> OptimType,
        UInit: Map<Node, Double>,
        LInit: Map<Node, Double>,
        convThreshold: Double,
        msecOptimalityThreshold: Double = 1e-10,
        checkOnlyInits: Boolean = false
    ): Map<Node, Double> {
        if(goal(Player.C) == goal(Player.A)) {
            return BVI(goal(Player.A), UInit, LInit, convThreshold, checkOnlyInits)
        }
        this.addSelfLoops()
        var U = UInit
        var L = LInit
        do {
            U = bellmanStep(goal, U)
            L = bellmanStep(goal, L)

            val optimalEdges = allNodes.associateWith {
                if (goal(it.owner) == OptimType.MAX) it.outEdges
                val edgeVals = it.outEdges.associateWith { computeEdgeValue(it, L) }
                val optim = edgeVals.values.min()
                    ?: throw Exception("No out edges on node $it - use self loops for absorbing states!")
                it.outEdges.filter { edgeVals[it]!! - optim < msecOptimalityThreshold }
            }

            val msecs = computeMECs(optimalEdges)
            val UDefl = U.toMutableMap()
            for (msec in msecs) {
                val bestExit = msec.filter { goal(it.owner) == OptimType.MAX }.flatMap {
                    it.outEdges.map { computeEdgeValue(it, U) }
                }.max() ?: 0.0
                for (node in msec) {
                    UDefl[node] = min(U[node]!!, bestExit)
                }
            }
            U = UDefl

            val maxError = (if (checkOnlyInits) initNodes else allNodes).map { U[it]!! - L[it]!! }.max()!!
        } while (maxError > convThreshold)

        return allNodes.associateWith { (U[it]!! + L[it]!!) / 2 }
    }

    /**
     * BVI for MDPs. Treats the game as an MDP by using the same goal for the two players.
     * Uses MEC collapsing to make the upper approximation converge.
     */
    fun BVI(
        goal: OptimType,
        UInit: Map<Node, Double>,
        LInit: Map<Node, Double>,
        convThreshold: Double,
        checkOnlyInits: Boolean = false
    ): Map<Node, Double> {
        this.addSelfLoops()
        val (collapsedGame, nodeMap) = collapseMecs(this)

        // Maps a value function on the original game to one on the collapsed game
        // The value of a collapsed MEC is the best value among the nodes of the MEC
        fun convertValFunction(V: Map<Node, Double>) =
            V.entries.groupBy(
                { nodeMap[it.key]!! }, { it.value }
            ).mapValues { goal.select(it.value)!! }

        var U = convertValFunction(UInit)
        var L = convertValFunction(LInit)
        do {
            L = collapsedGame.bellmanStep({ goal }, L)
            U = collapsedGame.bellmanStep({ goal }, U)
            val maxError = (if (checkOnlyInits) collapsedGame.initNodes else collapsedGame.allNodes)
                .map { U[it]!! - L[it]!! }.max()!!
        } while (maxError > convThreshold)
        val result = allNodes.associateWith { (U[nodeMap[it]]!! + L[nodeMap[it]]!!) / 2 }
        return result
    }

    private fun computeEdgeValue(e: Edge, V: Map<Node, Double>) =
        e.end.entries.sumByDouble { (node, prob) ->
            prob * (V[node] ?: throw Exception("Unknown value for node ${node}"))
        }

    private fun bellmanStep(
        goal: (Player) -> OptimType,
        V: Map<Node, Double>
    ): Map<Node, Double> {
//        val VNew = hashMapOf<Node, Double>()
        val VNew = V.toMutableMap()
        for (node in allNodes) {
            val edgeValues = node.outEdges.map { computeEdgeValue(it, VNew) }
            VNew[node] = goal(node.owner).select(edgeValues)
                ?: throw Exception("No out edges on node $node - use self loops for absorbing states!")
        }

        return VNew
    }

    public fun VI(
        goal: (Player) -> OptimType,
        VInit: Map<Node, Double>,
        convThreshold: Double,
        checkOnlyInits: Boolean = false
    ): Map<Node, Double> {
        if (goal(Player.A) == goal(Player.C)) {
            val goal = goal(Player.A)
            val (collapsed, nodeMap) = collapseMecs(this)

            var V = VInit.entries
                .groupBy({ nodeMap[it.key]!! }, { it.value })
                .mapValues { goal.select(it.value)!! }
            do {
                val VNext = collapsed.bellmanStep({goal}, V)
                val maxChange =
                    (if(checkOnlyInits) collapsed.initNodes else collapsed.allNodes)
                        .map { abs(VNext[it]!!-V[it]!!) }.max()!!
                V = VNext
            } while (maxChange > convThreshold)

            val VRes = allNodes.associateWith { V[nodeMap[it]]!! }
            return VRes
        } else {
            var V = VInit
            do {
                val VNext = bellmanStep(goal, V)
                val maxChange =
                    (if (checkOnlyInits) initNodes else allNodes)
                        .map { abs(VNext[it]!!-V[it]!!) }.max()!!
                V = VNext
            } while (maxChange > convThreshold)
            return V
        }
    }
}
