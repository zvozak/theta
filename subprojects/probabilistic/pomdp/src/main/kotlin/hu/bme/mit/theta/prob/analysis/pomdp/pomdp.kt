package hu.bme.mit.theta.prob.analysis.pomdp

import hu.bme.mit.theta.common.visualization.EdgeAttributes
import hu.bme.mit.theta.common.visualization.NodeAttributes
import hu.bme.mit.theta.common.visualization.Shape
import hu.bme.mit.theta.common.visualization.writer.GraphvizWriter
import java.awt.Color

interface IPOMDP<S, A, O> {
    fun getUnderlyingMDP(): IMDP<S, A>
    fun getObservations(s: S, a: A): Distribution<O>
    //fun computeBeliefMDP(numSteps: Int): MDP<BeliefState<S>, A>
    fun visualiseUnderlyingMDP(filename: String, withTransitionNodes: Boolean)
    fun visualiseBeliefMDP(filename: String, numSteps: Int)
    fun visualise(filename: String)
}
/*
class BeliefState<S : IState>(val d: Distribution<S>) {
    fun getNextBeliefState(
        distributionOfObservationOverStates: Distribution<S>,
        observation: O,
        action: IAction<S>
    ): BeliefState<S> {
        val newDistribution = d.pmf.toMap()
        newDistribution.forEach { (state, probability) ->
            distributionOfObservationOverStates.pmf[state] * action.getStateTransitions().entries.sumOf { (prevState, distr) -> distr.pmf[state]!! }
        }
    }
}*/

abstract class POMDPImpl<S, A, O>(val mdp: IMDP<S, A>, open val observationFunction: Map<Pair<S, A>, Distribution<O>>) : IPOMDP<S, A, O> {

    override fun getUnderlyingMDP(): IMDP<S, A> = mdp
    override fun visualiseUnderlyingMDP(filename: String, withTransitionNodes: Boolean) {
        mdp.visualize(filename, withTransitionNodes)
    }

    override fun getObservations(s: S, a: A): Distribution<O> =
        observationFunction[Pair(s, a)] ?: throw IllegalArgumentException("State or action is unkown.")
    /* TODO
        override fun computeBeliefMDP(numSteps: Int): MDP<BeliefState<S>, A> {
            val beliefMDP: MDP<BeliefState<S>, A>
        }*/
}

open class SimplePomdp(
    mdp: SimpleMDP,
    observationFunction: Map<Pair<State, Action>, Distribution<Observation>>,
    val initBeliefState: Distribution<State>?,
    ) : POMDPImpl<State, Action, Observation>(
    mdp,
    observationFunction,
) {
    companion object{
        fun readFromFile(filename: String): SimplePomdp {
            return PomdpDslManager.createPOMDP(filename)
        }
    }

    override fun visualiseBeliefMDP(filename: String, numSteps: Int) {
        TODO("Not yet implemented")
    }

    override fun visualise(filename: String) {
        var graph = mdp.buildGraph()
        val observationAttr = NodeAttributes.builder().shape(Shape.RECTANGLE).fillColor(Color.yellow) // TODO need less vivid colours..
        var edgeAttr = EdgeAttributes.builder() // this will have different labels showing probabilites

        for (s in mdp.states){
            for (a in mdp.actions){
                var distribution = observationFunction[Pair(s, a)] ?: continue
                for ((o, p) in distribution.pmf){
                    if (graph.nodes.any { n -> n.id == o.name}.not()){
                        graph.addNode(o.name, observationAttr.label(o.name).build())
                    }
                    graph.addEdge(s.name, o.name, edgeAttr.label(a.name + "\n" + p.toString()).build())
                }
            }
        }

        GraphvizWriter.getInstance().writeFileAutoConvert(graph, filename)
    }
}