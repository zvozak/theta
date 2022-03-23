package hu.bme.mit.theta.prob.refinement

import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.pred.ExprSplitters
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.prec.GlobalCfaPrec
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.model.ImmutableValuation
import hu.bme.mit.theta.core.stmt.NonDetStmt
import hu.bme.mit.theta.core.stmt.SequenceStmt
import hu.bme.mit.theta.core.utils.ExprSimplifier
import hu.bme.mit.theta.core.utils.ExprUtils
import hu.bme.mit.theta.core.utils.WpState
import hu.bme.mit.theta.prob.AbstractionGame
import hu.bme.mit.theta.prob.ProbStmt
import hu.bme.mit.theta.prob.doubleEquals

class GlobalCfaPredRefiner<S: ExprState, LConc>: PrecRefiner<
        GlobalCfaPrec<PredPrec>, S, CfaAction, LConc> {

    override fun refine(
        game: AbstractionGame<S, CfaAction, LConc>,
        stateToRefine: AbstractionGame.StateNode<S, CfaAction, LConc>,
        origPrecision: GlobalCfaPrec<PredPrec>,
        VAmin: StateNodeValues<S, CfaAction, LConc>, VAmax: StateNodeValues<S, CfaAction, LConc>,
        VCmin: ChoiceNodeValues<S, CfaAction, LConc>, VCmax: ChoiceNodeValues<S, CfaAction, LConc>
    ): GlobalCfaPrec<PredPrec> {

        require(stateToRefine.outgoingEdges.isNotEmpty())

        val choices = stateToRefine.outgoingEdges
        val max = choices.mapNotNull{VCmax[it.end]}.max()!!
        val min = choices.mapNotNull{VCmin[it.end]}.min()!!
        val maxChoices = choices.filter { doubleEquals(max, VCmax[it.end]!!) }.toSet()
        val minChoices = choices.filter { doubleEquals(min, VCmin[it.end]!!) }.toSet()

        val diff1 = maxChoices.minus(minChoices)
        val diff2 = minChoices.minus(maxChoices)

        val wpEdge = diff1.firstOrNull() ?: diff2.first()

        val wpStmt =
            if(wpEdge.label.stmts.size == 1) wpEdge.label.stmts.first()
            else SequenceStmt.of(wpEdge.label.stmts)

        val wpPreds = if (wpStmt is ProbStmt) {
            // We assume that a probabilistic location cannot be non-deterministic at the same time
            // This works for the PCFA formalism, but won't be true in general
            require(wpEdge.end.outgoingEdges.size == 1)
            val wps = wpEdge.end.outgoingEdges.first().end.metadata.flatMap { (resultingState, stmtList) ->
                stmtList.map { stmt ->
                    val wpstate = WpState.of(resultingState.state.toExpr())
                    return@map wpstate.wep(stmt).expr
                }
            }
            wps.flatMap(ExprSplitters.atoms()::apply).toSet()
        } else if (wpStmt is NonDetStmt) {
            TODO("Not supported yet")
        } else {
            val targetState = wpEdge.end.outgoingEdges.first().end.pmf.keys.first().state
            val wps = WpState.of(targetState.toExpr())
            ExprSplitters.atoms().apply(wps.wep(wpStmt).expr).toSet()
        }

        val predsUsed = wpPreds.filter {
            //TODO: stop at first val
            val l = arrayListOf<VarDecl<*>>()
            ExprUtils.collectVars(it, l)
            l.size>0
        }
        val newSubPrec = origPrecision.prec.join(PredPrec.of(predsUsed.map {
            ExprSimplifier.simplify(it, ImmutableValuation.empty())
        }))
        return GlobalCfaPrec.create(newSubPrec)
    }
}