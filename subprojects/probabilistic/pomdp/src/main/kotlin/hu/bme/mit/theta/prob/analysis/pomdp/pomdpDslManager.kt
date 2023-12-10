package hu.bme.mit.theta.prob.analysis.pomdp

import hu.bme.mit.theta.pomdp.dsl.gen.PomdpDslLexer
import hu.bme.mit.theta.pomdp.dsl.gen.PomdpDslParser
import hu.bme.mit.theta.pomdp.dsl.gen.PomdpDslParser.ObservationContext
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
        val transitions = extractTransitions(model, actions, states)

        val observationfunction : Map<State, Map<Action, Distribution<Observation>>> = extractObservations(model, actions, states, observations)


        val mdp = SimpleMDP(
            model.discount.text.toDouble(),
            Values.valueOf(model.values.text.uppercase()),
            states as MutableSet<State>,
            actions as MutableSet<Action>,
            transitions,
            null
        )

        val pomdp = SimplePomdp(mdp, observationfunction, initBeliefState)
        return pomdp
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
                    for (e in model.observationfunction) {
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


    //region Extract Transitions
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
    private fun extractTransitions(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
    ): HashMap<State, MutableMap<Action, Distribution<State>>> {
        val transitionRelation = hashMapOf<State, MutableMap<Action, MutableMap<State, Double>>>()
        val tranDefType = getTransitionDefinitionType(model.transitions[0])
        require(model.transitions.all { t -> getTransitionDefinitionType(t) == tranDefType }) {
            "Inconsistent transition definition."
        }

        when (tranDefType) {
            TransitionDefinitionType.FULL -> {
                extractFullyDefinedTransitions(model, actions, states, transitionRelation)
            }

            TransitionDefinitionType.ONELINERS -> {
                extractTransitionsDefinedWithSource(model, actions, states, transitionRelation)
            }

            TransitionDefinitionType.MATRIX -> {
                extractTransitionFromMatrix(model, actions, states, transitionRelation)
            }
        }

        val transitions =
            transformTransitionsToDistributions(transitionRelation)
        return transitions
    }

    private fun transformTransitionsToDistributions(transitionRelation: HashMap<State, MutableMap<Action, MutableMap<State, Double>>>): HashMap<State, MutableMap<Action, Distribution<State>>> {
        val transitions =
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

    private fun extractTransitionFromMatrix(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
        transitionRelation: HashMap<State, MutableMap<Action, MutableMap<State, Double>>>,
    ) {
        for (tran in model.transitions) {
            val action = actions.first { a -> a.name == tran.action.text }

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

    private fun extractTransitionsDefinedWithSource(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
        transitionRelation: HashMap<State, MutableMap<Action, MutableMap<State, Double>>>,
    ) {
        for (tran in model.transitions) {
            val action = actions.first { a -> a.name == tran.action.text }
            val source = states.first { a -> a.name == tran.source.text }

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

    private fun extractFullyDefinedTransitions(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
        transitionRelation: HashMap<State, MutableMap<Action, MutableMap<State, Double>>>,
    ) {
        for (tran in model.transitions) {
            val action = actions.first { a -> a.name == tran.action.text }
            val source = states.first { s -> s.name == tran.source.text }
            val destination = states.first { s -> s.name == tran.destination.text }
            val prob = tran.prob.text.toDouble()

            require(prob <= 1 && prob >= 0)

            if (transitionRelation.containsKey(source).not()) transitionRelation[source] = mutableMapOf()

            if (transitionRelation[source]!!.containsKey(action).not()) {
                transitionRelation[source]!!.put(action, mutableMapOf())
            }

            transitionRelation[source]!![action]!!.put(destination, prob)
        }
    }

//endregion



    //region Extract Observations
    fun getObservationDefinitionType(observation: ObservationContext): ObservationDefinitionType {
        require(observation.action != null)
        {
            "Invalid observation format: action must be specified."
        }

        if (observation.probs != null &&
            observation.destination != null
        ) {
            return ObservationDefinitionType.FULL
        }

        if (observation.destinationWithProbs != null){
            return ObservationDefinitionType.MATRIX
        }

        throw IllegalArgumentException("Invalid observation format")
    }

    enum class ObservationDefinitionType {
        FULL,
        MATRIX
    }
    private fun extractObservations(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
        observations: Set<Observation>,
    ): HashMap<State, MutableMap<Action, Distribution<Observation>>> {

        val observationDefType = getObservationDefinitionType(model.observationfunction[0])
        require(model.observationfunction.all { t -> getObservationDefinitionType(t) == observationDefType }) {
            "Inconsistent observation definition."
        }

        val observationFunction =
            when (observationDefType) {
                ObservationDefinitionType.FULL -> {
                    extractFullyDefinedObservations(model, actions, states, observations)
                }
                ObservationDefinitionType.MATRIX -> {
                    extractObservationFromMatrix(model, actions, states, observations)
                }
            }

        val observations =
            TransformObservationsToDistributions(observationFunction)
        return observations
    }

    private fun TransformObservationsToDistributions(
        observationFunction: HashMap<State, MutableMap<Action, MutableMap<Observation, Double>>>):
            HashMap<State, MutableMap<Action, Distribution<Observation>>> {
        val observations =
            hashMapOf<State, MutableMap<Action, Distribution<Observation>>>()

        observationFunction.forEach { (source, observation) ->
            if (observations.containsKey(source).not()) {
                observations.put(source, mutableMapOf())
            }

            for ((action, distribution) in observation) {
                observations[source]!!.put(action, Distribution(distribution))
            }
        }
        return observations
    }

    private fun extractObservationFromMatrix(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
        observations: Set<Observation>,
    ): HashMap<State, MutableMap<Action, MutableMap<Observation, Double>>> {
        val observationFunction: HashMap<State, MutableMap<Action, MutableMap<Observation, Double>>> = hashMapOf()

        for (observation in model.observationfunction) {
            val action = actions.first { a -> a.name == observation.action.text }

            require(observation.destinations.size == states.size && observation.destinations.all { s -> s.probs.size == observations.size })

            states.zip(
                observation.destinations
                    .map { d -> observations.zip(d.probs.map { p -> p.text.toDouble() }) }
            ).forEach { (destination, probs) ->
                for ((observation, prob) in probs) {
                    if (observationFunction.containsKey<State>(destination).not()) observationFunction[destination] =
                        mutableMapOf()

                    if (observationFunction[destination]!!.containsKey(action).not()) {
                        observationFunction[destination]!!.put(action, mutableMapOf())
                    }
                    observationFunction[destination]!![action]!!.put(observation, prob)
                }
            }
        }
        return observationFunction
    }

    private fun extractFullyDefinedObservations(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
        observations: Set<Observation>,
    ) : HashMap<State, MutableMap<Action, MutableMap<Observation, Double>>> {
        val observationFunction: HashMap<State, MutableMap<Action, MutableMap<Observation, Double>>> = hashMapOf()

        for (observation in model.observationfunction) {
            val action = actions.first { a -> a.name == observation.action.text }
            val destination = states.first { s -> s.name == observation.destination.text }

            if (observationFunction.containsKey(destination).not()) observationFunction[destination] = mutableMapOf()

            if (observationFunction[destination]!!.containsKey(action).not()) {
                observationFunction[destination]!!.put(action, mutableMapOf())
            }

            observations.zip(model.observation.destinationWithProbs.probs.map { p -> p.text.toDouble() }).forEach{
                (observation, prob) ->
                    observationFunction[destination]!![action]!!.put(observation, prob)
            }
        }

        return observationFunction
    }

//endregion


}
