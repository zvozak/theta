package hu.bme.mit.theta.prob.analysis.pomdp

import hu.bme.mit.theta.common.visualization.EdgeAttributes
import hu.bme.mit.theta.common.visualization.NodeAttributes
import hu.bme.mit.theta.common.visualization.Shape
import hu.bme.mit.theta.common.visualization.writer.GraphvizWriter
import java.awt.Color

open class NamedElement(val name: String) {
    companion object {
        fun isValidName(name: String): Boolean {
            val specialCharacters = ";.?!+*-()=%'\"&#@<>\\|$"
            return name.all {
                it.isWhitespace().not()
                        && specialCharacters.contains(it).not()
            }
        }

        inline fun <reified T : NamedElement> createElement(name: String): T {
            require(isValidName(name))
            return T::class.constructors.first().call(name)
        }

        inline fun <reified T : NamedElement> createElement(number: Int): T {
            require(number >= 0) {
                "ID must be a positive integer."
            }
            val id: String =  /*T::class.simpleName + "_" +*/ number.toString()
            return T::class.constructors.first().call(id)
        }

        inline fun <reified T : NamedElement> createNumberedElements(numberOfStates: Int): Set<T> {
            return buildSet {
                for (id in 0..numberOfStates-1) {
                    add(createElement<T>(id))
                }
            }
        }
    }
}

class Action(name: String) : NamedElement(name){
    override fun equals(other: Any?): Boolean {
        return other is Action && other.name.equals(this.name)
    }
}
class State(name: String) : NamedElement(name){
    override fun equals(other: Any?): Boolean {
        return other is State && other.name.equals(this.name)
    }
}
class Observation(name: String) : NamedElement(name){
    override fun equals(other: Any?): Boolean {
        return other is Observation && other.name.equals(this.name)
    }
}


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
                    graph.addEdge(s.name, o.name, edgeAttr.label(a.name + "  " + o.name).build())
                }
            }
        }

        GraphvizWriter.getInstance().writeFileAutoConvert(graph, filename)
    }
}