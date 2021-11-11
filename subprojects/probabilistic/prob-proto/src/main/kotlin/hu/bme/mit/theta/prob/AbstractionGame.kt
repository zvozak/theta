package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.State

class AbstractionGame<S: State, LAbs, LConc>(
    val stateNodes: MutableSet<StateNode<S, LAbs>> = hashSetOf(),
    val concreteChoiceNodes: MutableSet<ChoiceNode<S, LConc>> = hashSetOf(),
    val abstractionChoiceEdges: MutableSet<AbstractionChoiceEdge<S, LAbs>> = hashSetOf(),
    val concreteChoiceEdges: MutableSet<ConcreteChoiceEdge<S, LConc>> = hashSetOf()
) {

    val stateNodeMap = hashMapOf<S, StateNode<S, LAbs>>()

    /**
     * @param S The type representing abstract states
     * @param L The type used for labeling outgoing edges.
     */
    class StateNode<S: State, L>(val state: S, val outgoingEdges: HashSet<AbstractionChoiceEdge<S, L>> = hashSetOf()) {}

    /**
     * @param S The type representing abstract states
     * @param L The type used for labeling outgoing edges.
     */
    class ChoiceNode<S: State, L>(val outgoingEdges: HashSet<ConcreteChoiceEdge<S, L>> = hashSetOf()) {}

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
    class ConcreteChoiceEdge<S: State, L>(val start: ChoiceNode<S, L>, val end: EnumeratedDistribution<S>, val label: L) {
        init {
            start.outgoingEdges.add(this)
        }
    }

    fun createStateNode(s: S): StateNode<S, LAbs> = StateNode<S, LAbs>(s).also {stateNodes.add(it); stateNodeMap.put(s, it)}
    fun createStateNodes(ss: Collection<S>): List<StateNode<S, LAbs>> = ss.map { createStateNode(it) }

    fun createConcreteChoiceNode(): ChoiceNode<S, LConc> = ChoiceNode<S, LConc>().also { concreteChoiceNodes.add(it) }

    fun connect(s: StateNode<S, LAbs>, concreteChoiceNode: ChoiceNode<S, LConc>, label: LAbs) {
        // TODO: meaningful exception handling
        val edge = AbstractionChoiceEdge<S, LAbs>(s, concreteChoiceNode, label)
        abstractionChoiceEdges.add(edge)
    }

    fun connect(concreteChoiceNode: ChoiceNode<S, LConc>, distribution: EnumeratedDistribution<S>, label: LConc) {
        val edge = ConcreteChoiceEdge<S, LConc>(concreteChoiceNode, distribution, label)
        concreteChoiceEdges.add(edge)
    }
}