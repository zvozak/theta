package hu.bme.mit.theta.prob

import com.google.common.collect.Lists
import hu.bme.mit.theta.analysis.Action
import hu.bme.mit.theta.analysis.LTS
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.common.Utils
import hu.bme.mit.theta.core.stmt.Stmt
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType
import java.util.*


sealed class PCFAAction: Action {
    class StandardAction(val action: CfaAction): PCFAAction()
    class ProbabilisticAction(val distr: EnumeratedDistribution<CfaAction>): PCFAAction()
    class NonDetAction(val choices: List<CfaAction>): PCFAAction()
}

data class PCFAState<S: ExprState>(val loc: PCFA.Loc, val state: S): ExprState {
    override fun isBottom(): Boolean = state.isBottom

    override fun toExpr(): Expr<BoolType> {
        // TODO Should be loc = l and toExpr(state)
        return state.toExpr()
    }

    override fun toString(): String {
        return Utils.lispStringBuilder(javaClass.simpleName).add(loc.name).body().add(state).toString()
    }
}

class PCFA {

    class Loc private constructor(val name: String) {
        val inEdges: MutableCollection<Edge> = arrayListOf()
        val outEdges: MutableCollection<Edge> = arrayListOf()

        override fun toString(): String {
            return name
        }
    }

    sealed class Edge(val source: Loc) {
        class StandardEdge(source: Loc, val target: Loc, val stmt: Stmt): Edge(source)
        class ProbabilisticEdge(source: Loc, val distr: EnumeratedDistribution<Stmt>): Edge(source)
    }

}

class PcfaLts<S: ExprState>(): LTS<PCFAState<S>, PCFAAction> {
    override fun getEnabledActionsFor(state: PCFAState<S>): MutableCollection<PCFAAction> {
        val edges = state.loc.outEdges
        if(edges.isEmpty()) return Collections.emptyList()
        require(edges.size == 1 || edges.all { it is PCFA.Edge.StandardEdge })
        if(edges.first() is PCFA.Edge.ProbabilisticEdge) {

        } else if (edges.size == 1) {
            val edge = edges.first() as PCFA.Edge.StandardEdge
            return Collections.singleton(PCFAAction.StandardAction(
                CfaAction(listOf(edge.stmt), edge.source, edge.target)
            ))
        }
    }
}