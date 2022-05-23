package hu.bme.mit.theta.prob.refinement

import hu.bme.mit.theta.analysis.expl.ExplPrec
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.cfa.analysis.prec.GlobalCfaPrec
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.stmt.NonDetStmt
import hu.bme.mit.theta.core.utils.ExprUtils
import hu.bme.mit.theta.core.utils.WpState
import hu.bme.mit.theta.prob.game.AbstractionGame
import hu.bme.mit.theta.prob.pcfa.ProbStmt
import hu.bme.mit.theta.prob.game.doubleEquals

class GlobalCfaExplRefiner<LConc>: PrecRefiner<
        GlobalCfaPrec<ExplPrec>, CfaState<ExplState>, CfaAction, LConc> {
    override fun refine(
        game: AbstractionGame<CfaState<ExplState>, CfaAction, LConc>,
        stateToRefine: AbstractionGame.StateNode<CfaState<ExplState>, CfaAction, LConc>,
        origPrecision: GlobalCfaPrec<ExplPrec>,
        VAmin: StateNodeValues<CfaState<ExplState>, CfaAction, LConc>, VAmax: StateNodeValues<CfaState<ExplState>, CfaAction, LConc>,
        VCmin: ChoiceNodeValues<CfaState<ExplState>, CfaAction, LConc>, VCmax: ChoiceNodeValues<CfaState<ExplState>, CfaAction, LConc>
    ): GlobalCfaPrec<ExplPrec> {

        val choices = stateToRefine.outgoingEdges
        val max = choices.mapNotNull{VCmax[it.end]}.maxOrNull()!!
        val min = choices.mapNotNull{VCmin[it.end]}.minOrNull()!!
        val maxChoices = choices.filter { doubleEquals(max, VCmax[it.end]!!) }.toSet()
        val minChoices = choices.filter { doubleEquals(min, VCmin[it.end]!!) }.toSet()

        val diff1 = maxChoices.minus(minChoices)
        val diff2 = minChoices.minus(maxChoices)

        val wpEdge = diff1.firstOrNull() ?: diff2.first()
        require(wpEdge.label.stmts.size == 1)
        val wpStmt = wpEdge.label.stmts.first()

        val wpVars = if (wpStmt is ProbStmt) {
            // We assume that a probabilistic location cannot be non-deterministic at the same time
            // This works for the PCFA formalism, but won't be true in general
            require(wpEdge.end.outgoingEdges.size == 1)
            val wps = wpEdge.end.outgoingEdges.first().end.metadata.flatMap { (resultingState, stmtList) ->
                stmtList.flatMap { stmt ->
                    val wpstate = WpState.of(resultingState.state.toExpr())
                    val wepexpr = wpstate.wep(stmt).expr
                    arrayListOf<VarDecl<*>>().also { ExprUtils.collectVars(wepexpr, it) }
                }
            }
            wps.toSet()
        } else if (wpStmt is NonDetStmt) {
            TODO("Not supported yet")
        } else {
            val targetState = wpEdge.end.outgoingEdges.first().end.pmf.keys.first().state
            val wpstate = WpState.of(targetState.toExpr())
            val wepexpr = wpstate.wep(wpStmt).expr
            arrayListOf<VarDecl<*>>().also { ExprUtils.collectVars(wepexpr, it) }
        }

        val newSubPrec = origPrecision.prec.join(ExplPrec.of(wpVars))
        return GlobalCfaPrec.create(newSubPrec)
    }
}