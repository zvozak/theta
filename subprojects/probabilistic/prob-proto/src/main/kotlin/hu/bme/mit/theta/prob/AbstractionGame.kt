package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.core.stmt.Stmt

class AbstractionGame<S: State, LAbs, LConc>(
    val stateNodes: MutableSet<StateNode<S, LAbs>> = hashSetOf(),
    val initNodes: MutableSet<StateNode<S, LAbs>> = hashSetOf(),
    val concreteChoiceNodes: MutableSet<ChoiceNode<S, LConc>> = hashSetOf(),
    val abstractionChoiceEdges: MutableSet<AbstractionChoiceEdge<S, LAbs>> = hashSetOf(),
    val concreteChoiceEdges: MutableSet<ConcreteChoiceEdge<S, LConc>> = hashSetOf()
) {

    init {
        require(stateNodes.containsAll(initNodes))
    }

    val stateNodeMap = hashMapOf<S, StateNode<S, LAbs>>()
    private val firstPredecessorForStateNode = hashMapOf<StateNode<S, LAbs>, ConcreteChoiceEdge<S, LConc>>()
    private val firstPredecessorForChoiceNode = hashMapOf<ChoiceNode<S, LConc>, AbstractionChoiceEdge<S, LAbs>>()

    /**
     * @param S The type representing abstract states
     * @param L The type used for labeling outgoing edges.
     */
    class StateNode<S: State, L>(val state: S, val outgoingEdges: HashSet<AbstractionChoiceEdge<S, L>> = hashSetOf()) {
        override fun toString(): String = state.toString()
    }

    /**
     * @param S The type representing abstract states
     * @param L The type used for labeling outgoing edges.
     */
    class ChoiceNode<S: State, L>(val outgoingEdges: HashSet<ConcreteChoiceEdge<S, L>> = hashSetOf()) {
        override fun toString(): String {
            return "{${outgoingEdges.map { it.end.toString() }.joinToString(", ")}}"
        }
    }

    /**
     * Class for representing edges between abstract states and choice nodes of the _concrete_ non-determinism.
     * The name comes from the fact that the abstraction player is supposed to choose from these edges.
     * @property start The abstract state this edge leaves.
     * @property end The concrete choice node this edge enters.
     * @property label The label of the edge, mostly an action whose application leads to going through this edge.
     */
    class AbstractionChoiceEdge<S: State, L>(val start: StateNode<S, L>, val end: ChoiceNode<S, *>, val label: L) {
        init {
            start.outgoingEdges.add(this)
        }
    }

    /**
     * Class for representing edges between concrete choice nodes and distributions
     */
    class ConcreteChoiceEdge<S: State, L>(val start: ChoiceNode<S, L>, val end: EnumeratedDistribution<StateNode<S, *>, MutableList<Stmt>>, val label: L) {
        init {
            start.outgoingEdges.add(this)
        }
    }

    fun createStateNode(s: S, isInitial: Boolean = false): StateNode<S, LAbs> =
        StateNode<S, LAbs>(s).also {
            stateNodes.add(it)
            stateNodeMap[s] = it
            if(isInitial) initNodes.add(it)
        }
    fun createStateNodes(ss: Collection<S>, isInitial: Boolean): List<StateNode<S, LAbs>> =
        ss.map { createStateNode(it, isInitial) }

    fun createConcreteChoiceNode(): ChoiceNode<S, LConc> = ChoiceNode<S, LConc>().also { concreteChoiceNodes.add(it) }

    fun getOrCreateNodeWithChoices(
        choices: Set<Pair<
                    EnumeratedDistribution<StateNode<S, *>, MutableList<Stmt>>, LConc
                >>
    ): ChoiceNode<S, LConc> {
        // TODO: make the search more efficient by pre-partitioning the list
        return concreteChoiceNodes.firstOrNull {
            it.outgoingEdges.size == choices.size &&
            it.outgoingEdges.all { edge-> choices.contains(Pair(edge.end, edge.label)) }
        } ?: createConcreteChoiceNode().also {
            choices.forEach { (end, lbl) -> connect(it, end, lbl) }
        }
    }

    fun connect(s: StateNode<S, LAbs>, concreteChoiceNode: ChoiceNode<S, LConc>, label: LAbs) {
        require(s in stateNodes)
        val edge = AbstractionChoiceEdge(s, concreteChoiceNode, label)
        abstractionChoiceEdges.add(edge)
        firstPredecessorForChoiceNode.putIfAbsent(concreteChoiceNode, edge)
    }

    fun connect(
        concreteChoiceNode: ChoiceNode<S, LConc>,
        distribution: EnumeratedDistribution<StateNode<S, *>, MutableList<Stmt>>,
        label: LConc
    ) {
        val edge = ConcreteChoiceEdge<S, LConc>(concreteChoiceNode, distribution, label)
        concreteChoiceEdges.add(edge)
        for (stateNode in distribution.pmf.keys) {
            // TODO: get rid of that cast
            firstPredecessorForStateNode.putIfAbsent(stateNode as StateNode<S, LAbs>, edge)
        }
    }

    fun getFirstPredecessorEdge(stateNode: StateNode<S, LAbs>) = firstPredecessorForStateNode[stateNode]
    fun getFirstPredecessorEdge(choiceNode: ChoiceNode<S, LConc>) = firstPredecessorForChoiceNode[choiceNode]
}