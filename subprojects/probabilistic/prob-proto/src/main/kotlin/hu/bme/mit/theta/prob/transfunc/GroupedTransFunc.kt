package hu.bme.mit.theta.prob.transfunc

import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.core.stmt.Stmt

// TODO: wouldn't this be clearer if distributions were also handled by this class?
interface GroupedTransFunc<S: State, A: StmtAction, P: Prec> {
    fun getSuccStates(state: S, action: A, prec: P): List<List<S>> =
        getSuccStatesWithStmt(state, action, prec).map { it.map { it.second } }

    fun getSuccStatesWithStmt(state: S, action: A, prec: P): List<List<Pair<Stmt, S>>>
}

