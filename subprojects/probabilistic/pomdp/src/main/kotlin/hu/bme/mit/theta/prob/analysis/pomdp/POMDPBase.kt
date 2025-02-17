package hu.bme.mit.theta.prob.analysis.pomdp

import hu.bme.mit.theta.common.visualization.EdgeAttributes
import hu.bme.mit.theta.common.visualization.NodeAttributes
import hu.bme.mit.theta.common.visualization.Shape
import hu.bme.mit.theta.common.visualization.writer.GraphvizWriter
import java.awt.Color

abstract class POMDPBase<S: State, A: Action, O: Observation>(
    open val mdp: IMDP<S, A>,
    open val observations: Set<O>,
    open val observationFunction: HashMap<Pair<A,S>, Distribution<O>>,
    open val initBeliefState: BeliefState<S>? = null,
) : IPOMDP<S, A, O> {

    override fun getUnderlyingMDP(): IMDP<S, A> = mdp

    override fun getStates() = mdp.states
    override fun getActions() = mdp.actions

    override fun isActionAvailableFrom(action: A, state: S): Boolean = mdp.isActionAvailableFrom(action, state)
    override fun getTransition(sourceState: S, action: A): Distribution<S> {
        require(isActionAvailableFrom(action, sourceState))
        return mdp.transitionRelation[sourceState] !![action]!!
    }
    override fun visualiseUnderlyingMDP(filename: String, withTransitionNodes: Boolean) {
        mdp.visualize(filename, withTransitionNodes)
    }

    val states = mdp.states

    override fun visualise(filename: String) {
        var graph = mdp.buildGraph()
        val observationAttr =
            NodeAttributes.builder().shape(Shape.RECTANGLE).fillColor(Color.yellow) // TODO need less vivid colours..
        var edgeAttr = EdgeAttributes.builder() // this will have different labels showing probabilites

        for (o in observations){
            graph.addNode(o.name, observationAttr.label(o.name).build())
        }

        for (s in mdp.states) {
            for (a in mdp.actions) {
                if (!isActionAvailableFrom(a, s)){
                    continue
                }
                var distribution = getObservations(s, a)
                for ((o, p) in distribution) {
                    if (p > 0.0){
                        graph.addEdge(o.name, s.name, edgeAttr.label(a.name + "\n" + p.toString()).build())
                    }
                }
            }
        }

        GraphvizWriter.getInstance().writeFileAutoConvert(graph, filename)
    }
    override fun getObservations(state: S, action: A): Distribution<O> = observationFunction[Pair(action, state)] ?: throw IllegalArgumentException("$state.name state or acton ${action.name} is unkown.")

    fun computeBeliefTransition(belief: BeliefState<S>, action: A, observation: O) : BeliefTransition<S>? {
        require(isActionAvailable(action, belief))

        val newBelief = HashMap(belief.distribution.pmf)
        for ((destinationState, _) in newBelief){
            newBelief[destinationState] = 0.0
            if (getObservations(destinationState, action)[observation] > 0.0){
                for (sourceState in getStates()){
                    newBelief[destinationState] = newBelief[destinationState]!! + getTransition(sourceState, action)[destinationState] * belief.distribution[sourceState]
                }
                newBelief[destinationState] = newBelief[destinationState]!! * getObservations(destinationState, action)[observation]
            }
        }

        return if(newBelief.any{ (_, prob) -> prob > 0.0}){
            val sum = newBelief.values.sum()
            for ((k,p) in newBelief){
                newBelief[k] = p/sum
            }

            BeliefTransition(belief, BeliefState(Distribution(newBelief)), sum)
        } else {
            null
        }
    }

    fun isActionAvailable(action: A, belief: BeliefState<S>): Boolean = belief.distribution.any { (s, p) -> p  > 0.0 && isActionAvailableFrom(action,s) }

    fun computeBeliefMDP(numSteps: Int, beliefsToProcess: Set<BeliefState<S>>, beliefMDP: BeliefMDP<S,A>) : BeliefMDP<S, A>{
        if (beliefsToProcess.isEmpty()){
            return beliefMDP
        }
        val newBeliefs = mutableSetOf<BeliefState<S>>()

        for (fromBelief in beliefsToProcess){
            for (action in getActions()){
                if (!isActionAvailable(action, fromBelief)){
                    continue
                }
                beliefMDP.addAction(action)
                val transitions = hashMapOf<BeliefState<S>, Double>()
                for (observation in observations){
                    val beliefTransition = computeBeliefTransition(fromBelief, action, observation)
                    if (beliefTransition != null) {
                        val newBeliefState = beliefTransition.destinationBeliefState
                        beliefMDP.addState(newBeliefState)
                        newBeliefs.add(newBeliefState)

                        if (transitions.containsKey(newBeliefState)){
                            transitions[newBeliefState] = transitions[newBeliefState]!! + beliefTransition.probability
                        } else {
                            transitions.put(newBeliefState, beliefTransition.probability)
                        }
                    }
                }
                beliefMDP.addTransition(fromBelief, Distribution.normalizeProbabilities(transitions), action)
            }
        }

        return computeBeliefMDP(numSteps - 1, newBeliefs, beliefMDP)
    }
    override fun computeBeliefMDP(numSteps: Int, fromBelief: BeliefState<S>) : BeliefMDP<S,A>{
        val beliefMDP = BeliefMDP<S,A>()

        return computeBeliefMDP(numSteps, setOf(fromBelief), beliefMDP)
    }

    override fun visualiseBeliefMDP(filename: String, numSteps: Int, withTransitionNode: Boolean, fromBelief: BeliefState<S>) {
        val beliefMDP = computeBeliefMDP(numSteps, fromBelief)
        beliefMDP.visualize(filename, withTransitionNode)
    }
}
