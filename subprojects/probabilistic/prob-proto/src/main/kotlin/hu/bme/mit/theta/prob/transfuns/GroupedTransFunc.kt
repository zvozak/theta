package hu.bme.mit.theta.prob.transfuns

import hu.bme.mit.theta.analysis.Action
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State

// TODO: wouldn't this be clearer if distributions were also handled by this class?
interface GroupedTransferFunction<S: State, A: Action, P: Prec> {
    fun getSuccStates(state: S, action: A, prec: P): List<List<S>>
}

