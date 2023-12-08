package hu.bme.mit.theta.prob.analysis.pomdp

import hu.bme.mit.theta.common.visualization.EdgeAttributes
import hu.bme.mit.theta.common.visualization.Graph
import hu.bme.mit.theta.common.visualization.NodeAttributes
import hu.bme.mit.theta.common.visualization.Shape
import hu.bme.mit.theta.common.visualization.writer.GraphvizWriter
import java.awt.Color

class SimpleMDP : IMDP<State, Action> {
    override lateinit var values: Values
    override var discount: Double = 0.0
    override val states = mutableSetOf<State>()
    override val actions: MutableSet<Action> = mutableSetOf<Action>()
    override val transitionRelation = hashMapOf<State, MutableMap<Action, Distribution<State>>>()
    override var initState: State? = null

    //region Modifiers
    fun addState(name: String): State {
        val newState = State(name)
        require(states.contains(newState).not()) { "State $name has already been defined." }

        states.add(newState)
        return newState
    }

    override fun addState(newState: State) {
        require(states.contains(newState).not()) { "State $newState.name has already been defined." }
        states.add(newState)
    }

    override fun addTransition(source: State, destinations: Distribution<State>, action: Action) {
        require(states.containsAll(transitionRelation.keys)) { "A transition must not lead to states outside of the MDP!" }
        if (transitionRelation.containsKey(source).not()) transitionRelation[source] = mutableMapOf()

        require(
            transitionRelation[source]!!.containsKey(action).not()
        ) { "Action $action.name has already been defined on state $source.name." }
        transitionRelation[source]!![action] = destinations
    }
    /*
    override fun setInitState(s: State) {
        require(s in states) { "Initial state could not be set: no state found with name $s.name." }
        initState = s
    }

    override fun setValues(values: Values) {
        this.values = values
    }*/

    fun setInitState(name: String) {
        initState = states.find { s -> s.name == name }
        require(initState != null) { "State $name cannot be set as initial state: it cannot be found." }
    }


    //endregion

    //region Accessors
    /*
    override fun getInitState(): State = initState ?: throw IllegalStateException("No initial state has been set yet.")
    override fun getDiscount() = discount
    override fun setDiscount(discount: Double) {
        this.discount = discount
    }
*/
    override fun getNextStateDistribution(s: State, a: Action): Distribution<State> {
        require(s in states) { "State $s.name cannot be found." }
        require(transitionRelation.containsKey(s)) { "No actions are available from state $s.name." }

        return transitionRelation[s]!!.get(a)
            ?: throw IllegalArgumentException("Action $a.name is not available from state $s.name.")
    }

    override fun getAvailableActions(s: State): Collection<Action> =
        transitionRelation[s]?.keys ?: throw java.lang.IllegalArgumentException("No state found with name $s.name.")
    //endregion

    //region Visualisation
    fun buildGraph(): Graph {
        val graph = Graph("mdp", "mdp");
        val stateAttr = NodeAttributes.builder().shape(Shape.CIRCLE)
        val transitionNodeAttr = NodeAttributes.builder().shape(Shape.RECTANGLE).fillColor(Color.black)
        val transitionEdgeAttr = EdgeAttributes.builder()

        for (state in states) {
            stateAttr.label(state.name)
            graph.addNode(state.name, stateAttr.build())
        }

        for (sourceState in states) {
            var id = 1
            for (distributions in transitionRelation[sourceState]!!) {
                val actionID =
                    if (distributions.key.name.isBlank()) {
                        sourceState.name + "tran" + id++
                    } else {
                        distributions.key.name
                    }

                graph.addNode(actionID, transitionNodeAttr.build())
                graph.addEdge(sourceState.name, actionID, transitionEdgeAttr.label("").build())

                for ((destinationState, probability) in distributions.value.pmf) {
                    transitionEdgeAttr.label(probability.toString())
                    graph.addEdge(actionID, destinationState.name, transitionEdgeAttr.build())
                }
            }
        }

        return graph
    }

    override fun visualize(filename: String) {
        val graph = this.buildGraph()
        GraphvizWriter.getInstance().writeFile(graph, filename)
    }

    //endregion

    /*
    //region File Readers

    val wildcard = "*"

    fun skipEmptyLinesAndComments(inputReader: PeekableScanner) { // TODO put this in the right places
        var currentline: String? = null
        var nextline: String? = inputReader.peek()
        while (nextline != null && (nextline.isBlank() || nextline.startsWith("//"))) {
            if (nextline.startsWith("/*")) {
                while (inputReader.hasNext()) {
                    currentline = inputReader.next()
                    if (currentline.startsWith("*/")) {
                        break
                    }
                }
            }
            inputReader.next()
            nextline = inputReader.peek()
        }
    }

    fun readDiscount(inputReader: PeekableScanner) {
        val fieldName = "discount"
        require(inputReader.hasNext()) {
            ParseError.createFieldIsMissingError(fieldName)
        }

        skipEmptyLinesAndComments(inputReader)

        val input: List<String> = inputReader.next()?.split(' ')
            ?: throw IllegalArgumentException(ParseError.createFieldIsMissingError(fieldName))
        require(input.size == 2 && input[0] == "$fieldName:") { }

        input[1].trim()
        discount = input[1].toDouble()
    }

    fun readValues(inputReader: PeekableScanner) {
        val fieldName = "values"
        require(inputReader.hasNext()) {
            ParseError.createFieldIsMissingError(fieldName)
        }

        val input: List<String> = inputReader.next().split(' ')
        require(input[0] == "$fieldName:") { ParseError.createFieldIsMissingOrNotInOrderError(fieldName) }
        require(input.size == 2) { ParseError.createFieldIsInvalid(fieldName, 1) }

        val possibleValues = Values.values().map { it.v }
        require(input[1] in possibleValues) {
            ParseError.createInvalidFileFormatMessage(
                "$input[1 is not a compatible value for parameter \"values\". " +
                        "Possible values are: ${possibleValues.joinToString(" ") { it }}"
            )
        }

        values = Values.valueOf(input[1])
    }

    fun readAndAddDistributions(inputReader: PeekableScanner, action: Action) {
        for (indexOfState in 0..states.size - 1) {
            addTransition(states.elementAt(indexOfState), readDistribution(inputReader), action)
        }
    }

    fun readDistribution(inputReader: PeekableScanner): Distribution<State> {
        require(inputReader.hasNext()) {
            ParseError.transition + "end of file is reached."
        }

        val input: List<String> = inputReader.next().split(' ')

        require(input.size == states.size) {
            ParseError.transition + "the specified distribution of destination states is incomplete." +
                    "The number of states is ${states.size}, but only a ${input.size} long distribution was specified." +
                    "The specified distribution is the following:\n" +
                    "${input}."
        }

        return Distribution(
            buildMap<State, Double> {
                for (i in 0..(input.size - 1)) {
                    put(states.elementAt(i), input[i].toDouble())
                }
            }
        )
    }

    fun readTransitions(
        firstLine: List<String>,
        inputReader: PeekableScanner,
    ) {
        val transitions: HashMap<State, MutableMap<Action, HashMap<State, Double>>> = hashMapOf()
        if (firstLine[0] != "T") {
            return
        }

        require(firstLine.size == 4) {
            ParseError.transition + "all transitions must be specified in the same format."
        }
        var line = firstLine
        var hasNext: Boolean =
            false // needed bc line has to be read at the end of the loop, but after reading, hasNext's value might change..

        do {
            if (line.isEmpty()) {
                continue
            }
            if (line[0] != "T") {
                return
            }

            require(line.size == 4) {
                ParseError.transition + "all transitions must be specified in the same format."
            }
            val actionName = line[1]
            val sourceName = line[2]
            val destinationAndProb = line[3].split(' ')
            val destinationName = destinationAndProb[0]
            val probability = destinationAndProb[1].toDouble()

            val action = actions.first { it.name == actionName }
            val sources =
                if (sourceName == wildcard) {
                    states
                } else {
                    setOf(states.first { it.name == sourceName })
                }


            val destinations: Set<State> =
                if (destinationName == wildcard) {
                    states
                } else {
                    setOf(states.first { it.name == destinationName })
                }

            for (destination in destinations) {
                for (source in sources) {
                    if (transitions.containsKey(source).not()) {
                        transitions.put(source, mutableMapOf())
                        val transition = transitions.getValue(source)
                        if (transition.containsKey(action).not()) {
                            transition.put(action, hashMapOf())
                        }
                        val prob = transition.getValue(action)
                        prob.put(destination, probability)
                    }
                }
            }

            hasNext = inputReader.hasNext()
            if (hasNext) {
                line = inputReader.next().split(':')
            }
        } while (hasNext)

        for (transition in transitions) {
            val source = transition.key
            val actions = transition.value
            transitionRelation.put(source, mutableMapOf())
            for (prob in actions) {
                val action = prob.key
                transitionRelation[source]!!.put(action, Distribution(prob.value))
            }
        }
    }


    fun readAndAddTransition(inputReader: PeekableScanner) {
        require(inputReader.hasNext()) {
            ParseError.transition + "end of file is reached."
        }

        var input: List<String> = inputReader.next().split(':').map { it.trim() }
        require(input.size >= 2 && input[0] == "T" && input.all { it.isNotBlank() }) {
            ParseError.transition + "line is empty or not well-formed. Line must start with 'T: '."
        }

        val action = actions.first { it.name == input[1] }
        var hasNext = false
        when (input.size) {
            2 -> // T : action
                // %f %f ...
                when (inputReader.peek()) {
                    "UNIFORM" -> {
                        inputReader.next()
                        for (source in states) {
                            addTransition(source, Distribution.createUniformDistribution(states), action)
                        }
                    }

                    "IDENTITY" -> {
                        inputReader.next()
                        for (source in states) {
                            addTransition(source, Distribution.createIdentityDistribution(source, states), action)
                        }
                    }

                    else ->
                        do {
                            skipEmptyLinesAndComments(inputReader)
                            readAndAddDistributions(inputReader, action)
                            while (inputReader.hasNext()) {
                                val line = inputReader.next()
                                if (line.isBlank()) {
                                    continue
                                }
                                input = line.split(':').map { it.trim() }

                            }
                        } while (hasNext)
                }

            3 -> // T: <action> : <start-state>
                // %f %f ... %f
            {
                val source = states.first { it.name == input[2] }
                val distro = when (inputReader.peek()) {
                    "UNIFORM" ->
                        Distribution.createUniformDistribution(states)

                    "IDENTITY" ->
                        Distribution.createIdentityDistribution(source, states)

                    else ->
                        readDistribution(inputReader)
                }
                addTransition(source, distro, action)
            }

            4 -> // T: <action> : <start-state> : <end-state> %f
                readTransitions(input, inputReader)

            else -> throw IllegalArgumentException(ParseError.transition + "invalid format.")
        }
    }

    fun readStates(sc: PeekableScanner) {
        readNamedElements<State>(sc)
    }

    fun readActions(sc: PeekableScanner) {
        readNamedElements<Action>(sc)
    }

    //endregion

     */
}