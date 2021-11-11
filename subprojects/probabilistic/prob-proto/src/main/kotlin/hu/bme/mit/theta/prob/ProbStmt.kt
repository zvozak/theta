package hu.bme.mit.theta.prob

import hu.bme.mit.theta.core.stmt.NonDetStmt
import hu.bme.mit.theta.core.stmt.Stmt

class ProbStmt(
    val distr: EnumeratedDistribution<Stmt>
) : NonDetStmt(distr.pmf.keys.toList()) {
}