package hu.bme.mit.theta.prob

class StochasticGame<SAbs, SConc, LAbs, LConc> {

    companion object {
        enum class Player {
            A, C
        }
    }

    // Nodes
    abstract inner class Node(val owner: Player) {
        abstract val outEdges: List<Edge>
        abstract val inEdges: List<Edge>
    }
    inner class ANode(
        val s: SAbs,
        override val outEdges: ArrayList<AEdge> = arrayListOf(),
        override val inEdges: ArrayList<CEdge> = arrayListOf()
    ) : Node(Player.A) {
        init {
            aNodes.add(this)
        }
    }
    inner class CNode(
        val s: SConc,
        override val outEdges: ArrayList<CEdge> = arrayListOf(),
        override val inEdges: ArrayList<AEdge> = arrayListOf()
    ) : Node(Player.C) {
        init {
            cNodes.add(this)
        }
    }

    val aNodes: ArrayList<ANode> = arrayListOf()
    val cNodes: ArrayList<CNode> = arrayListOf()
    val allNodes get() = aNodes + cNodes

    // Edges
    abstract inner class Edge {
        abstract val start: Node
        abstract val end: Map<out Node, Double>
    }
    inner class AEdge(
        val lbl: LAbs,
        override val start: ANode,
        override val end: Map<CNode, Double>
    ): Edge() {
        init {
            aEdges.add(this)
            start.outEdges.add(this)
            end.keys.forEach { it.inEdges.add(this) }
        }
    }
    inner class CEdge(
        val lbl: LConc,
        override val start: CNode,
        override val end: Map<ANode, Double>
    ): Edge() {
        init {
            cEdges.add(this)
            start.outEdges.add(this)
            end.keys.forEach { it.inEdges.add(this) }
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
        val potentialPreNodes =
            nodes.flatMap {it.inEdges}.intersect(permittedEdges).map {it.start}.toSet()
        return potentialPreNodes.filter {
            if(it.owner == forPlayer) {
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
        val otherPlayer = when(forPlayer) {
            Player.A -> Player.C
            Player.C -> Player.A
        }

        do {
            val prevSize = result.size
            val C = safe(otherPlayer, result - target, permittedEdges)
            result = safe(forPlayer, result-C, permittedEdges)
            permittedEdges = stay(forPlayer, result, permittedEdges)
        } while (result.size != prevSize)

        return result
    }
}