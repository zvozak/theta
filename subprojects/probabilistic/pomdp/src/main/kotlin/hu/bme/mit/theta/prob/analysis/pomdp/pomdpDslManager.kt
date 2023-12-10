package hu.bme.mit.theta.prob.analysis.pomdp

import hu.bme.mit.theta.pomdp.dsl.gen.PomdpDslLexer
import hu.bme.mit.theta.pomdp.dsl.gen.PomdpDslParser
import hu.bme.mit.theta.pomdp.dsl.gen.PomdpDslParser.TransitionContext
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File
import java.io.IOException
import java.io.InputStream

object PomdpDslManager {
    @Throws(IOException::class)
    fun createPOMDP(filename: String): SimplePomdp {
        val stream: InputStream = File(filename).inputStream()
        return createPomdp(stream)
    }

    @Throws(IOException::class)
    fun createPomdp(inputStream: InputStream?): SimplePomdp {
        val lexer = PomdpDslLexer(CharStreams.fromStream(inputStream))
        val tokenStream = CommonTokenStream(lexer)
        val parser = PomdpDslParser(tokenStream)
        val model: PomdpDslParser.PomdpContext = parser.pomdp()

        val states = extractStates(model)
        val actions = extractActions(model)
        val observations = extractObservations(model)
        val initBeliefState = extractInitBeliefState(states, model)
        var transitions = extractTransitions(model, actions, states)

        val mdp = SimpleMDP(
            model.discount.text.toDouble(),
            Values.valueOf(model.values.text.uppercase()),
            states as MutableSet<State>,
            actions as MutableSet<Action>,
            transitions,
            null
        )

        var pomdp = SimplePomdp(mdp)
        return pomdp
    }

    private fun extractTransitions(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
    ): HashMap<State, MutableMap<Action, Distribution<State>>> {
        var transitionRelation = hashMapOf<State, MutableMap<Action, MutableMap<State, Double>>>()
        /*
        for (source in states){
            transitionRelation.put(source, mutableMapOf())
            var tran = transitionRelation[source]
            for(action in actions){
                tran!!.put(action, mutableMapOf())
                for(destination in states){
                    tran!![action]!!.put(destination, 0.0)
                }
            }
        }*/

        var tranDefType = getTransitionDefinitionType(model.transitions[0])
        require(model.transitions.all { t -> getTransitionDefinitionType(t) == tranDefType }) {
            "Inconsistent transition definition."
        }

        when (tranDefType) {
            TransitionDefinitionType.FULL -> {
                for (tran in model.transitions) {
                    var action = actions.first { a -> a.name == tran.action.text }
                    var source = states.first { s -> s.name == tran.source.text }
                    var destination = states.first { s -> s.name == tran.destination.text }
                    var prob = tran.prob.text.toDouble()

                    require(prob <= 1 && prob >= 0)

                    if (transitionRelation.containsKey(source).not()) transitionRelation[source] = mutableMapOf()

                    if (transitionRelation[source]!!.containsKey(action).not()) {
                        transitionRelation[source]!!.put(action, mutableMapOf())
                    }

                    transitionRelation[source]!![action]!!.put(destination, prob)
                }
            }

            TransitionDefinitionType.ONELINERS -> {
                for (tran in model.transitions) {
                    var action = actions.first { a -> a.name == tran.action.text }
                    var source = states.first { a -> a.name == tran.source.text }

                    require(tran.probs.size == states.size)

                    if (transitionRelation.containsKey(source).not()) transitionRelation[source] = mutableMapOf()

                    if (transitionRelation[source]!!.containsKey(action).not()) {
                        transitionRelation[source]!!.put(action, mutableMapOf())
                    }

                    states.zip(tran.probs)
                        .forEach { (destination, prob) ->
                            transitionRelation[source]!![action]!!.put(destination, prob.text.toDouble())
                        }
                }
            }

            TransitionDefinitionType.MATRIX -> {
                for (tran in model.transitions) {
                    var action = actions.first { a -> a.name == tran.action.text }

                    require(tran.sources.size == states.size && tran.sources.all { s -> s.probs.size == states.size })

                    states.zip(
                        tran.sources
                            .map { s -> states.zip(s.probs.map { p -> p.text.toDouble() }) }
                    ).forEach { (source, probs) ->
                        for ((destination, prob) in probs) {
                            if (transitionRelation.containsKey(source).not()) transitionRelation[source] =
                                mutableMapOf()

                            if (transitionRelation[source]!!.containsKey(action).not()) {
                                transitionRelation[source]!!.put(action, mutableMapOf())
                            }
                            transitionRelation[source]!![action]!!.put(destination, prob)
                        }
                    }
                }
            }
        }

        var transitions =
            hashMapOf<State, MutableMap<Action, Distribution<State>>>()

        transitionRelation.forEach { (source, tran) ->
            if (transitions.containsKey(source).not()) {
                transitions.put(source, mutableMapOf())
            }

            for ((action, distribution) in tran) {
                transitions[source]!!.put(action, Distribution(distribution))
            }
        }
        return transitions
    }

    private fun extractInitBeliefState(
        states: Set<State>,
        model: PomdpDslParser.PomdpContext,
    ): Distribution<State>? {
        if (model.beliefStateProbs == null || model.beliefStateProbs.size == 0){
            return null
        }
        val pomdpInitState = buildMap<State, Double> {
            states.zip(model.beliefStateProbs.map { p ->
                p.text.toDouble()
            }).forEach { (state, prob) ->
                put(state, prob)
            }
        }
        return Distribution(pomdpInitState)
    }

    private fun extractObservations(model: PomdpDslParser.PomdpContext): Set<Observation> {
        val observations: Set<Observation> =
            if (model.numberOfObservations != null) {
                NamedElement.createNumberedElements<Observation>(model.numberOfObservations.text.toInt())
            } else {
                buildSet {
                    for (e in model.observations) {
                        add(Observation(e.text))
                    }
                }
            }
        return observations
    }

    private fun extractActions(model: PomdpDslParser.PomdpContext): Set<Action> {
        val actions: Set<Action> =
            if (model.numberOfActions != null) {
                NamedElement.createNumberedElements<Action>(model.numberOfActions.text.toInt())
            } else {
                buildSet {
                    for (e in model.actions) {
                        add(Action(e.text))
                    }
                }
            }
        return actions
    }

    private fun extractStates(model: PomdpDslParser.PomdpContext): Set<State> {
        val states: Set<State> =
            if (model.numberOfStates != null) {
                NamedElement.createNumberedElements<State>(model.numberOfStates.text.toInt())
            } else {
                buildSet {
                    for (e in model.states) {
                        add(State(e.text))
                    }
                }
            }
        return states
    }

    fun getTransitionDefinitionType(transition: TransitionContext): TransitionDefinitionType {
        require(transition.action != null)
        {
            "Invalid transition format: action must be specified."
        }

        if (transition.prob != null &&
            transition.destination != null &&
            transition.source != null
        ) {
            return TransitionDefinitionType.FULL
        }
        if (transition.probs != null &&
            transition.source != null) {
            return TransitionDefinitionType.ONELINERS
        }

        if (transition.sourceWithProbs != null){
            return TransitionDefinitionType.MATRIX
        }

        throw IllegalArgumentException("Invalid transition format")
    }

    enum class TransitionDefinitionType {
        FULL,
        ONELINERS,
        MATRIX
    }
}
