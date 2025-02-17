package hu.bme.mit.theta.prob.analysis.pomdp

import hu.bme.mit.theta.pomdp.dsl.gen.PomdpDslLexer
import hu.bme.mit.theta.pomdp.dsl.gen.PomdpDslParser
import hu.bme.mit.theta.pomdp.dsl.gen.PomdpDslParser.ObservationContext
import hu.bme.mit.theta.pomdp.dsl.gen.PomdpDslParser.TransitionContext
import hu.bme.mit.theta.pomdp.dsl.gen.PomdpDslParser.RewardContext
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File
import java.io.IOException
import java.io.InputStream

object PomdpDslManager {
    @Throws(IOException::class)
    fun createPOMDP(filename: String): SimplePOMDP{
        val stream: InputStream = File(filename).inputStream()
        return createPomdp(stream)
    }

    //region Reward
    @Throws(IOException::class)
    fun createPomdp(inputStream: InputStream?): SimplePOMDP{
        val lexer = PomdpDslLexer(CharStreams.fromStream(inputStream))
        val tokenStream = CommonTokenStream(lexer)
        val parser = PomdpDslParser(tokenStream)
        val model: PomdpDslParser.PomdpContext = parser.pomdp()

        val states = extractStates(model)
        val actions = extractActions(model)
        val observations = extractObservations(model)
        val initBeliefState = extractInitBeliefState(states, model)
        val transitions = extractTransitions(model, actions, states)
        val observationfunction: HashMap<Pair<Action, State>, Distribution<Observation>> =
            extractObservations(model, actions, states, observations)
        val rewardFunction = extractRewards(model, actions, states, observations)

        val mdp = MDP(
            //model.discount.text.replace('-', '_').toDouble(),
            //Values.valueOf(model.values.text.replace('-', '_').uppercase()),
            states as MutableSet<State>,
            actions as MutableSet<Action>,
            transitions,
            //rewardFunction,
            null
        )

        val initBelief = if (initBeliefState == null){
            null
        } else {
            BeliefState(initBeliefState)
        }

        return SimplePOMDP(mdp, observations, observationfunction, initBelief)
    }

    private fun extractRewards(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
        observations: Set<Observation>,
    ): HashMap<NTuple4<State, Action, State, Observation>, Double> {
        val rewards = hashMapOf<NTuple4<State, Action, State, Observation>, Double>()
        if (model.rewardfunction == null || model.rewardfunction.size == 0){
            return rewards
        }

        val rewardDefType = getRewardDefinitionType(model.rewardfunction[0])
        require(model.rewardfunction.all { t -> getRewardDefinitionType(t) == rewardDefType }) {
            "Inconsistent transition definition."
        }

        when (rewardDefType) {
            RewardDefinitionType.FULL -> {
                extractFullyDefinedRewards(model, actions, states, observations, rewards)
            }

            RewardDefinitionType.ONELINERS -> {
                extractRewardsDefinedWithDestination(model, actions, states, observations, rewards)
            }

            RewardDefinitionType.MATRIX -> {
                extractRewardsFromMatrix(model, actions, states, observations, rewards)
            }
        }

        return rewards
    }

    private fun extractRewardsFromMatrix(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
        observations: Set<Observation>,
        rewards: java.util.HashMap<NTuple4<State, Action, State, Observation>, Double>,
    ) {
        for (reward in model.rewardfunction) {
            val source = states.first { s -> s.name == reward.source.text.replace('-', '_') }
            val action = actions.first { a -> a.name == reward.action.text.replace('-', '_') }

            require(states.size*observations.size == reward.rews.size)

            for (i in 0..states.size-1){
                for (j in 0..observations.size-1){
                    rewards[NTuple4(source, action, states.elementAt(i), observations.elementAt(j))] = reward.rews[i*observations.size + j].text.toDouble()
                }
            }
        }
    }

    private fun extractRewardsDefinedWithDestination(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
        observations: Set<Observation>,
        rewards: HashMap<NTuple4<State, Action, State, Observation>, Double>,
    ) {
        for (reward in model.rewardfunction) {
            val source = states.first { s -> s.name == reward.source.text.replace('-', '_') }
            val action = actions.first { a -> a.name == reward.action.text.replace('-', '_') }
            val destination = states.first { s -> s.name == reward.destination.text.replace('-', '_') }

            observations.zip(reward.rews.map { r -> r.text.toDouble() }).forEach { (obs, rew) ->
                rewards[NTuple4(source, action, destination, obs)] = rew
            }
        }
    }

    private fun extractFullyDefinedRewards(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
        observations: Set<Observation>,
        rewards: HashMap<NTuple4<State, Action, State, Observation>, Double>,
    ) {
        for (reward in model.rewardfunction) {
            val source = states.first { s -> s.name == reward.source.text.replace('-', '_') }
            val action = actions.first { a -> a.name == reward.action.text.replace('-', '_') }
            val destination = states.first { s -> s.name == reward.destination.text.replace('-', '_') }
            val obs = observations.first { o -> o.name == reward.obs.text.replace('-', '_') }
            val rew = reward.rew.text.toDouble()

            rewards[NTuple4(source, action, destination, obs)] = rew
        }
    }

    private fun getRewardDefinitionType(rewardfunction: RewardContext): RewardDefinitionType {
        require(rewardfunction.action != null && rewardfunction.source != null)
        {
            "Invalid reward format: action must be specified."
        }

        if (rewardfunction.obs != null &&
            rewardfunction.destination != null &&
            rewardfunction.rew != null
        ) {
            return RewardDefinitionType.FULL
        }
        if (rewardfunction.rews != null &&
            rewardfunction.destination != null
        ) {
            return RewardDefinitionType.ONELINERS
        }

        if (rewardfunction.rews != null) {
            return RewardDefinitionType.MATRIX
        }

        throw IllegalArgumentException("Invalid reward format")
    }

    enum class RewardDefinitionType {
        FULL,
        ONELINERS,
        MATRIX
    }

    //endregion

    private fun extractInitBeliefState(
        states: Set<State>,
        model: PomdpDslParser.PomdpContext,
    ): Distribution<State>? {
        if (model.beliefStateProbs == null || model.beliefStateProbs.size == 0) {
            return null
        }
        val pomdpInitState = buildMap {
            states.zip(model.beliefStateProbs.map { p ->
                p.text.toDouble()
            }).forEach { (state, prob) ->
                put(state, prob)
            }
        }
        return Distribution(HashMap(pomdpInitState))
    }

    private fun extractObservations(model: PomdpDslParser.PomdpContext): Set<Observation> {
        val observations: Set<Observation> =
            if (model.numberOfObservations != null) {
                NamedElement.createNumberedElements<Observation>(
                    model.numberOfObservations.text.replace('-', '_').toInt()
                )
            } else {
                buildSet {
                    for (e in model.observations) {
                        add(Observation(e.text.replace('-', '_')))
                    }
                }
            }
        return observations
    }

    private fun extractActions(model: PomdpDslParser.PomdpContext): Set<Action> {
        val actions: Set<Action> =
            if (model.numberOfActions != null) {
                NamedElement.createNumberedElements<Action>(model.numberOfActions.text.replace('-', '_').toInt())
            } else {
                buildSet {
                    for (e in model.actions) {
                        add(Action(e.text.replace('-', '_')))
                    }
                }
            }
        return actions
    }

    private fun extractStates(model: PomdpDslParser.PomdpContext): Set<State> {
        val states: Set<State> =
            if (model.numberOfStates != null) {
                NamedElement.createNumberedElements<State>(model.numberOfStates.text.replace('-', '_').toInt())
            } else {
                buildSet {
                    for (e in model.states) {
                        add(State(e.text.replace('-', '_')))
                    }
                }
            }
        return states
    }


    //region Extract Transitions
    private fun getTransitionDefinitionType(transition: TransitionContext): TransitionDefinitionType {
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
            transition.source != null
        ) {
            return TransitionDefinitionType.ONELINERS
        }

        if (transition.probs != null) {
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
    ): HashMap<State, HashMap<Action, Distribution<State>>> {
        val transitionRelation = hashMapOf<State, HashMap<Action, HashMap<State, Double>>>()
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

        return transformTransitionsToDistributions(transitionRelation)
    }

    private fun transformTransitionsToDistributions(transitionRelation: HashMap<State, HashMap<Action, HashMap<State, Double>>>): HashMap<State, HashMap<Action, Distribution<State>>> {
        val transitions =
            hashMapOf<State, HashMap<Action, Distribution<State>>>()

        transitionRelation.forEach { (source, tran) ->
            if (transitions.containsKey(source).not()) {
                transitions[source] = hashMapOf()
            }

            for ((action, distribution) in tran) {
                transitions[source]!![action] = Distribution(distribution)
            }
        }
        return transitions
    }

    private fun extractTransitionFromMatrix(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
        transitionRelation: HashMap<State, HashMap<Action, HashMap<State, Double>>>,
    ) {
        for (tran in model.transitions) {
            val action = actions.first { a -> a.name == tran.action.text.replace('-', '_') }

            require(tran.probs.size == states.size * states.size)

            for (i in 0..states.size-1){
                val source = states.elementAt(i)
                if (transitionRelation.containsKey(source).not()) {
                    transitionRelation[source] = hashMapOf()
                }

                if (transitionRelation[source]!!.containsKey(action).not()) {
                    transitionRelation[source]!![action] = hashMapOf()
                }

                for (j in 0..states.size-1){
                    val destination = states.elementAt(j)
                    transitionRelation[source]!![action]!![destination] = tran.probs[i*states.size + j].text.toDouble()
                }
            }
        }
    }

    private fun extractTransitionsDefinedWithSource(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
        transitionRelation: HashMap<State, HashMap<Action, HashMap<State, Double>>>,
    ) {
        for (tran in model.transitions) {
            val action = actions.first { a -> a.name == tran.action.text.replace('-', '_') }
            val source = states.first { a -> a.name == tran.source.text.replace('-', '_') }

            require(tran.probs.size == states.size)

            if (transitionRelation.containsKey(source).not()) transitionRelation[source] = hashMapOf()

            if (transitionRelation[source]!!.containsKey(action).not()) {
                transitionRelation[source]!![action] = hashMapOf()
            }

            states.zip(tran.probs)
                .forEach { (destination, prob) ->
                    transitionRelation[source]!![action]!![destination] = prob.text.replace('-', '_').toDouble()
                }
        }
    }

    private fun extractFullyDefinedTransitions(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
        transitionRelation: HashMap<State, HashMap<Action, HashMap<State, Double>>>,
    ) {
        for (tran in model.transitions) {
            val action = actions.first { a -> a.name == tran.action.text.replace('-', '_') }
            val source = states.first { s -> s.name == tran.source.text.replace('-', '_') }
            val destination = states.first { s -> s.name == tran.destination.text.replace('-', '_') }
            val prob = tran.prob.text.replace('-', '_').toDouble()

            require(prob in 0.0..1.0)

            if (transitionRelation.containsKey(source).not()) transitionRelation[source] = hashMapOf()

            if (transitionRelation[source]!!.containsKey(action).not()) {
                transitionRelation[source]!![action] = hashMapOf()
            }

            transitionRelation[source]!![action]!![destination] = prob
        }
    }

//endregion


    //region Extract Observations
    private fun getObservationDefinitionType(observation: ObservationContext): ObservationDefinitionType {
        require(observation.action != null)
        {
            "Invalid observation format: action must be specified."
        }

        if (observation.probs != null &&
            observation.destination != null
        ) {
            return ObservationDefinitionType.FULL
        }

        if (observation.probs != null) {
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
    ): HashMap<Pair<Action, State>, Distribution<Observation>> {

        val observationDefType = getObservationDefinitionType(model.observationfunction[0])
        require(model.observationfunction.all { t -> getObservationDefinitionType(t) == observationDefType }) {
            "Inconsistent observation definition."
        }

        val observationFunction =
            when (observationDefType) {
                ObservationDefinitionType.FULL -> {
                    extractFullyDefinedObservations(model, actions, states, observations)
                }

                ObservationDefinitionType.FULL -> {
                    extractOneLinerObservations(model, actions, states, observations)
                }

                ObservationDefinitionType.MATRIX -> {
                    extractObservationFromMatrix(model, actions, states, observations)
                }
            }

        return transformObservationsToDistributions(observationFunction)
    }

    private fun transformObservationsToDistributions(
        observationFunction: HashMap<Pair<Action, State>, HashMap<Observation, Double>>,
    ): HashMap<Pair<Action, State>, Distribution<Observation>> {
        val observations =
            hashMapOf<Pair<Action, State>, Distribution<Observation>>()

        observationFunction.forEach { (key, distribution) ->
            observations[key] = Distribution(distribution)
        }

        return observations
    }

    private fun extractObservationFromMatrix(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
        observations: Set<Observation>,
    ): HashMap<Pair<Action, State>, HashMap<Observation, Double>> {
        val observationFunction: HashMap<Pair<Action, State>, HashMap<Observation, Double>> = hashMapOf()

        for (observation in model.observationfunction) {
            val action = actions.first { a -> a.name == observation.action.text.replace('-', '_') }

            require(observation.probs.size == states.size * observations.size)

            for (i in 0..states.size-1){
                val destination = states.elementAt(i)
                val key = Pair(action, destination)

                if (observationFunction.containsKey(key).not()) {
                    observationFunction[key] =
                        hashMapOf()
                }

                for (j in 0..states.size-1){
                    val obs = observations.elementAt(j)
                    val prob = observation.probs[i*states.size + j].text.toDouble()
                    observationFunction[key]!![obs] = prob
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
    ): HashMap<Pair<Action, State>, HashMap<Observation, Double>> {
        val observationFunction: HashMap<Pair<Action, State>, HashMap<Observation, Double>> = hashMapOf()

        for (observation in model.observationfunction) {
            val action = actions.first { a -> a.name == observation.action.text.replace('-', '_') }
            val destination = states.first { s -> s.name == observation.destination.text.replace('-', '_') }
            val obs = observations.first{ o -> o.name == observation.obs.text.replace('-', '_')}
            var key = Pair(action, destination)
            var prob = observation.prob.text.toDouble()

            if (observationFunction.containsKey(key).not()) {
                observationFunction[key] = hashMapOf()
            }

            observationFunction[key]!![obs] = prob
        }

        return observationFunction
    }

    private fun extractOneLinerObservations(
        model: PomdpDslParser.PomdpContext,
        actions: Set<Action>,
        states: Set<State>,
        observations: Set<Observation>,
    ): HashMap<Pair<Action, State>, HashMap<Observation, Double>> {
        val observationFunction: HashMap<Pair<Action, State>, HashMap<Observation, Double>> = hashMapOf()

        for (observation in model.observationfunction) {
            val action = actions.first { a -> a.name == observation.action.text.replace('-', '_') }
            val destination = states.first { s -> s.name == observation.destination.text.replace('-', '_') }
            val key = Pair(action, destination)

            if (observationFunction.containsKey(key).not()) {
                observationFunction[key] =
                    hashMapOf()
            }

            observations.zip(model.observation.probs.map { p -> p.text.toDouble() }).forEach { (o, prob) ->
                observationFunction[key]!![o] = prob
            }
        }

        return observationFunction
    }

//endregion


}