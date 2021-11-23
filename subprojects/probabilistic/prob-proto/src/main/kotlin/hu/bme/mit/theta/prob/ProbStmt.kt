package hu.bme.mit.theta.prob

import hu.bme.mit.theta.core.stmt.NonDetStmt
import hu.bme.mit.theta.core.stmt.Stmt

class ProbStmt(
    val distr: EnumeratedDistribution<Stmt, Void>
) : NonDetStmt(distr.pmf.keys.toList()) {
    override fun toString(): String {
        return "prob-" + super.toString() //TODO
    }
}