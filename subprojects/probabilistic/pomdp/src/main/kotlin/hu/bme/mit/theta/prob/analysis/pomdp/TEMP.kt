package hu.bme.mit.theta.prob.analysis.pomdp


object ParseError {
    val transition = "Transition could not be read: "
    fun createInvalidFileFormatMessage(reason: String): String {
        return "Invalid file format: $reason"
    }

    fun createFieldIsMissingError(fieldName: String): String {
        val problem = "Field $fieldName must be specified."
        return createInvalidFileFormatMessage(problem)
    }

    fun createFieldIsInvalid(fieldName: String, expectedNumberOfValues: Int): String {
        val problem = "Field $fieldName is incorrect. The expected number of values set is $expectedNumberOfValues."
        return createInvalidFileFormatMessage(problem)
    }

    fun createFieldIsMissingOrNotInOrderError(fieldName: String): String {
        val problem =
            "Field $fieldName is either unspecified, missing, or is not in the line it is expected to be in."
        return createInvalidFileFormatMessage(problem)
    }
}

/* SIMPLEPOMDP alatt volt
    companion object {
        fun readFromFile(filename: String): SimplePomdp {
            require(filename.isNotBlank()) { "Cannot read POMDP: input file must be specified." }
            val file = File(filename)
            require(file.extension == "txt") { "Cannot read POMDP: Invalid file extension. File must be of txt format." }
            val mdp: SimpleMDP = SimpleMDP()
            val sc = PeekableScanner(filename)

            mdp.readDiscount(sc)
            mdp.readValues(sc)
            mdp.readStates(sc)
            mdp.readActions(sc)
            mdp.readAndAddTransition(sc)

            val observations = readObservations(sc)

            val observationMap: Map<State, Distribution<Observation>> = emptyMap()


            val observationFunction: (State) -> Distribution<Observation> =
                { state -> observationMap.get(state) ?: throw IllegalArgumentException("") }

            return SimplePomdp(mdp, observationFunction)
        }

        private fun readObservations(sc: PeekableScanner): Set<Observation> {
            return readNamedElements<Observation>(sc)
        }
    }

     */

/* namedelement alá

    inline fun <reified T : NamedElement> readNamedElements(
        inputReader: PeekableScanner,
    ): Set<T> {
        val fieldName = T::class.simpleName
        require(fieldName != null)
        require(inputReader.hasNext()) {
            ParseError.createFieldIsMissingError(fieldName)
        }

        val input: List<String> = inputReader.next().split(' ')
        require(input.size >= 2 && input[0] == "$fieldName:" && input.all { it.isNotBlank() }) {
            ParseError.createFieldIsMissingOrNotInOrderError(fieldName)
        }

        val firstCharacter = input[1][0]
        if (input.size == 2) {
            require(firstCharacter != '0' && input[1].all { it.isDigit() }) {
                ParseError.createInvalidFileFormatMessage(
                    "The number of $fieldName must be a positive integer. " +
                            "The names of the $fieldName must begin with a letter. " +
                            "If the names of the $fieldName are specified, the number of $fieldName must not be specified."
                )
            }
            return NamedElementFactory.createNumberedElements<T>(numberOfStates = input[1].toInt()) // TODO check outcome..
        } else {
            return buildSet {
                for (name in input) {
                    add(NamedElementFactory.createElement<T>(name))
                }
            }
        }
    }
 */