package hu.bme.mit.theta.prob.pcfa

import hu.bme.mit.theta.core.stmt.NonDetStmt
import hu.bme.mit.theta.core.stmt.Stmt
import hu.bme.mit.theta.prob.EnumeratedDistribution

class ProbStmt(
    val distr: EnumeratedDistribution<Stmt, Unit>
) : NonDetStmt(distr.pmf.keys.toList()) {
    override fun toString(): String {
        return "prob-" + super.toString() //TODO
    }

    override fun replaceStmts(mapping: MutableMap<Stmt, Stmt>): NonDetStmt {
        return ProbStmt(
            EnumeratedDistribution(
                distr.pmf.map { (key, v) -> (mapping[key] ?: key) to v }.toMap(),
                distr.metadata.map { (key, v) -> (mapping[key] ?: key) to v }.toMap()
            )
        )
    }
}