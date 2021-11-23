package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.CfaPrec
import hu.bme.mit.theta.cfa.analysis.CfaState

class CfaGroupedTransferFunction<S: ExprState, P: Prec>(
    val subTransFunc: GroupedTransferFunction<S, in CfaAction, P>
): GroupedTransferFunction<CfaState<S>, CfaAction, CfaPrec<P>> {

    override fun getSuccStates(state: CfaState<S>, action: CfaAction, prec: CfaPrec<P>): List<List<CfaState<S>>> {
        val source = action.source
        require(state.loc == source)
        val target = action.target
        val subPrec  = prec.getPrec(target)
        val subState = state.state // CfaState = Loc information + Substate information
        val subSuccStates = subTransFunc.getSuccStates(subState, action, subPrec)
        return subSuccStates.map { nextStates ->
            nextStates.map { CfaState.of(target, it) }
        }
    }
}