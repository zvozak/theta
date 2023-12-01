package hu.bme.mit.theta.prob.analysis.pomdp

import java.util.*

/*
@constructor For more information about Tony's POMDP file syntax, see [POMDP syntax](https://cs.brown.edu/research/ai/pomdp/examples/pomdp-file-spec.html).
 */
class TonyPomdpParser  {
    fun readFromFile(fileName: String){

    }
}


class PeekableScanner(source: String?) {
    private val scan: Scanner
    private var next: String?

    init {
        scan = Scanner(source)
        next = if (scan.hasNext()) scan.next() else null
    }

    operator fun hasNext(): Boolean {
        return next != null
    }

    operator fun next(): String {
        val current = next
        next = scan.next()
        return current ?: throw IllegalAccessError ("End of file has already been reached.")
    }

    fun peek(): String? {
        return next
    }
}