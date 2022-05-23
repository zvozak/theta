package hu.bme.mit.theta.prob.transfunc

import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.CfaPrec
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.core.stmt.Stmt

class CfaGroupedTransFunc<S: ExprState, P: Prec>(
    val subTransFunc: GroupedTransFunc<S, in CfaAction, P>
): GroupedTransFunc<CfaState<S>, CfaAction, CfaPrec<P>> {

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

    override fun getSuccStatesWithStmt(
        state: CfaState<S>,
        action: CfaAction,
        prec: CfaPrec<P>
    ): List<List<Pair<Stmt, CfaState<S>>>> {
        val source = action.source
        require(state.loc == source)
        val target = action.target
        val subPrec  = prec.getPrec(target)
        val subState = state.state // CfaState = Loc information + Substate information
        val subSuccStates = subTransFunc.getSuccStatesWithStmt(subState, action, subPrec)
        return subSuccStates.map { nextStates ->
            nextStates.map { (stmt, state) -> stmt to CfaState.of(target, state) }
        }
    }
}