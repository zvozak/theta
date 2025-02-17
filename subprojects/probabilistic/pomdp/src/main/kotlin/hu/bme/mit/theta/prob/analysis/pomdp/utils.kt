package hu.bme.mit.theta.prob.analysis.pomdp

data class NTuple4<T1, T2, T3, T4>(val t1: T1, val t2: T2, val t3: T3, val t4: T4)

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

open class Action(name: String) : NamedElement(name){
    override fun equals(other: Any?): Boolean {
        return other is Action && other.name.equals(this.name)
    }
}
open class State(name: String) : NamedElement(name){
    override fun equals(other: Any?): Boolean {
        return other is State && other.name.equals(this.name)
    }
}
open class Observation(name: String) : NamedElement(name){
    override fun equals(other: Any?): Boolean {
        return other is Observation && other.name.equals(this.name)
    }
}