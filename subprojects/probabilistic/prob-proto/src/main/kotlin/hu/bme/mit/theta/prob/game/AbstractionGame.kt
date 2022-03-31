package hu.bme.mit.theta.prob.game

import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.core.stmt.Stmt
import hu.bme.mit.theta.prob.EnumeratedDistribution

class AbstractionGame<S: State, LAbs, LConc>(
    val stateNodes: MutableSet<StateNode<S, LAbs, LConc>> = hashSetOf(),
    val initNodes: MutableSet<StateNode<S, LAbs, LConc>> = hashSetOf(),
    val concreteChoiceNodes: MutableSet<ChoiceNode<S, LAbs, LConc>> = hashSetOf(),
    val abstractionChoiceEdges: MutableSet<AbstractionChoiceEdge<S, LAbs, LConc>> = hashSetOf(),
    val concreteChoiceEdges: MutableSet<ConcreteChoiceEdge<S, LAbs, LConc>> = hashSetOf()
) {

    init {
        require(stateNodes.containsAll(initNodes))
    }

    val stateNodeMap = hashMapOf<S, StateNode<S, LAbs, LConc>>()
    private val firstPredecessorForStateNode = hashMapOf<StateNode<S, LAbs, LConc>, ConcreteChoiceEdge<S, LAbs, LConc>>()
    private val firstPredecessorForChoiceNode = hashMapOf<ChoiceNode<S, LAbs, LConc>, AbstractionChoiceEdge<S, LAbs, LConc>>()

    /**
     * @param S The type representing abstract states
     * @param L The type used for labeling outgoing edges.
     */
    class StateNode<S: State, LAbs, LConc>(
        val state: S,
        val outgoingEdges: HashSet<AbstractionChoiceEdge<S, LAbs, LConc>> = hashSetOf(),
        val incomingEdges: HashSet<ConcreteChoiceEdge<S, LAbs, LConc>> = hashSetOf()
    ) {
        override fun toString(): String = state.toString()
    }

    /**
     * @param S The type representing abstract states
     * @param L The type used for labeling outgoing edges.
     */
    class ChoiceNode<S: State, LAbs, LConc>(
        val outgoingEdges: HashSet<ConcreteChoiceEdge<S, LAbs, LConc>> = hashSetOf(),
        val incomingEdges: HashSet<AbstractionChoiceEdge<S, LAbs, LConc>> = hashSetOf()
    ) {
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
    class AbstractionChoiceEdge<S: State, LAbs, LConc>(
        val start: StateNode<S, LAbs, LConc>,
        val end: ChoiceNode<S, LAbs, LConc>,
        val label: LAbs) {
        init {
            start.outgoingEdges.add(this)
        }
    }

    /**
     * Class for representing edges between concrete choice nodes and distributions
     */
    class ConcreteChoiceEdge<S: State, LAbs, LConc>(
        val start: ChoiceNode<S, LAbs, LConc>,
        val end: EnumeratedDistribution<StateNode<S, LAbs, LConc>, MutableList<Stmt>>,
        val label: LConc) {
        init {
            start.outgoingEdges.add(this)
        }
    }

    fun createStateNode(s: S, isInitial: Boolean = false): StateNode<S, LAbs, LConc> =
        StateNode<S, LAbs, LConc>(s).also {
            stateNodes.add(it)
            stateNodeMap[s] = it
            if(isInitial) initNodes.add(it)
        }
    fun createStateNodes(ss: Collection<S>, isInitial: Boolean): List<StateNode<S, LAbs, LConc>> =
        ss.map { createStateNode(it, isInitial) }

    fun createConcreteChoiceNode(): ChoiceNode<S, LAbs, LConc> = ChoiceNode<S, LAbs, LConc>().also { concreteChoiceNodes.add(it) }

    fun getOrCreateNodeWithChoices(
        choices: Set<Pair<
                EnumeratedDistribution<StateNode<S, LAbs, LConc>, MutableList<Stmt>>, LConc
                >>
    ): ChoiceNode<S, LAbs, LConc> {
        // TODO: make the search more efficient by pre-partitioning the list
        return concreteChoiceNodes.firstOrNull {
            it.outgoingEdges.size == choices.size &&
            it.outgoingEdges.all { edge-> choices.contains(Pair(edge.end, edge.label)) }
        } ?: createConcreteChoiceNode().also {
            choices.forEach { (end, lbl) -> connect(it, end, lbl) }
        }
    }

    fun connect(s: StateNode<S, LAbs, LConc>, concreteChoiceNode: ChoiceNode<S, LAbs, LConc>, label: LAbs) {
        require(s in stateNodes)
        val edge = AbstractionChoiceEdge(s, concreteChoiceNode, label)
        abstractionChoiceEdges.add(edge)
        firstPredecessorForChoiceNode.putIfAbsent(concreteChoiceNode, edge)
        concreteChoiceNode.incomingEdges.add(edge)
    }

    fun connect(
        concreteChoiceNode: ChoiceNode<S, LAbs, LConc>,
        distribution: EnumeratedDistribution<StateNode<S, LAbs, LConc>, MutableList<Stmt>>,
        label: LConc
    ) {
        val edge = ConcreteChoiceEdge(concreteChoiceNode, distribution, label)
        concreteChoiceEdges.add(edge)
        for (stateNode in distribution.pmf.keys) {
            firstPredecessorForStateNode.putIfAbsent(stateNode, edge)
            stateNode.incomingEdges.add(edge)
        }
    }

    fun getFirstPredecessorEdge(stateNode: StateNode<S, LAbs, LConc>) = firstPredecessorForStateNode[stateNode]
    fun getFirstPredecessorEdge(choiceNode: ChoiceNode<S, LAbs, LConc>) = firstPredecessorForChoiceNode[choiceNode]
}