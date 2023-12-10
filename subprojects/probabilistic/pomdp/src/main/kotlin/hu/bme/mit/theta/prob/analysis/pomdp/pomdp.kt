package hu.bme.mit.theta.prob.analysis.pomdp

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

open class POMDPImpl<S, A, O>(val mdp: IMDP<S, A>, open val observationFunction: Map<S, Map<A, Distribution<O>>>) : IPOMDP<S, A, O> {

    override fun getUnderlyingMDP(): IMDP<S, A> = mdp

    override fun getObservations(s: S, a: A): Distribution<O> = observationFunction[s]?.get(a) ?: throw IllegalArgumentException("State or action is unkown.")
    /* TODO
        override fun computeBeliefMDP(numSteps: Int): MDP<BeliefState<S>, A> {
            val beliefMDP: MDP<BeliefState<S>, A>
        }*/
}

open class SimplePomdp(
    mdp: SimpleMDP,
    observationFunction: Map<State, Map<Action, Distribution<Observation>>>,
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
}