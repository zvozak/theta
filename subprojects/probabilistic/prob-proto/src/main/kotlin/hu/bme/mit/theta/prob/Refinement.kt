package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.analysis.expl.ExplPrec
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expr.ExprAction
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.pred.ExprSplitters
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.analysis.pred.PredState
import hu.bme.mit.theta.analysis.waitlist.Waitlist
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.cfa.analysis.prec.GlobalCfaPrec
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.stmt.NonDetStmt
import hu.bme.mit.theta.core.stmt.Stmt
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.ExprUtils
import hu.bme.mit.theta.core.utils.ExprUtils.collectVars
import hu.bme.mit.theta.core.utils.WpState
import hu.bme.mit.theta.prob.AbstractionGame.*
import hu.bme.mit.theta.solver.ItpSolver
import java.util.*
import kotlin.collections.HashSet
import kotlin.math.max

typealias StateNodeValues<S, LAbs> = Map<StateNode<S, LAbs>, Double>
typealias ChoiceNodeValues<S, LConc> = Map<ChoiceNode<S, LConc>, Double>
interface RefinableStateSelector {
    fun <S: State, LAbs, LConc> select(
        game: AbstractionGame<S, LAbs, LConc>,
        VAmin: StateNodeValues<S, LAbs>,VAmax: StateNodeValues<S, LAbs>,
        VCmin: ChoiceNodeValues<S, LConc>, VCmax: ChoiceNodeValues<S, LConc>
    ): StateNode<S, LAbs>?
}

fun <S: State, LConc> isRefinable(
    s: StateNode<S, *>,
    VCmax: ChoiceNodeValues<S, LConc>,
    VCmin: ChoiceNodeValues<S, LConc>
): Boolean {
    val choices = s.outgoingEdges.map { it.end }
    if (choices.isEmpty()) return false
    val max = choices.mapNotNull(VCmax::get).max()!!
    val min = choices.mapNotNull(VCmin::get).min()!!
    val maxChoices = choices.filter { doubleEquals(max, VCmax[it]!!) }.toSet()
    val minChoices = choices.filter { doubleEquals(min, VCmin[it]!!) }.toSet()
    return maxChoices.minus(minChoices).isNotEmpty()
}

object coarsestRefinableStateSelector: RefinableStateSelector {
    override fun <S : State, LAbs, LConc> select(
        game: AbstractionGame<S, LAbs, LConc>,
        VAmin: StateNodeValues<S, LAbs>, VAmax: StateNodeValues<S, LAbs>,
        VCmin: ChoiceNodeValues<S, LConc>, VCmax: ChoiceNodeValues<S, LConc>
    ): StateNode<S, LAbs>? {
        return game.stateNodes.filter{ isRefinable(it, VCmax, VCmin)}.maxBy { VAmax[it]!!-VAmin[it]!! } !!
    }
}

object nearestRefinableStateSelector: RefinableStateSelector {
    override fun <S : State, LAbs, LConc> select(
        game: AbstractionGame<S, LAbs, LConc>,
        VAmin: StateNodeValues<S, LAbs>, VAmax: StateNodeValues<S, LAbs>,
        VCmin: ChoiceNodeValues<S, LConc>, VCmax: ChoiceNodeValues<S, LConc>
    ): StateNode<S, LAbs>? {
        val q = ArrayDeque<StateNode<S, LAbs>>()
        val visited = hashSetOf<StateNode<S, LAbs>>()
        q.addAll(game.initNodes)
        visited.addAll(game.initNodes)
        while (!q.isEmpty()) {
            val s = q.poll()
            val nexts = s.outgoingEdges
                .map { it.end }
                .flatMap { it.outgoingEdges }
                // TODO: get rid of this cast
                .flatMap { it.end.pmf.keys.map { it as StateNode<S, LAbs> } }
                .toSet()
                .filterNot(visited::contains)
            val refinable = nexts.firstOrNull { isRefinable(it, VCmax, VCmin)}
            if(refinable != null) return refinable
            q.addAll(nexts)
        }
        return null // no refinable state
    }
}

interface GameAbstractionRefiner<S: State, P: Prec, LAbs, LConc> {
    fun refine(
        game: AbstractionGame<S, LAbs, LConc>,
        stateToRefine: StateNode<S,LAbs>,
        origPrecision: GlobalCfaPrec<PredPrec>,
        VAmin: StateNodeValues<S, LAbs>, VAmax: StateNodeValues<S, LAbs>,
        VCmin: ChoiceNodeValues<S, LConc>, VCmax: ChoiceNodeValues<S, LConc>
    ): P
}


object ExplRefiner {
    fun <LAbs : StmtAction, LConc> wprefineGameAbstraction(
        game: AbstractionGame<CfaState<ExplState>, LAbs, LConc>,
        stateToRefine: StateNode<CfaState<ExplState>, LAbs>,
        origPrecision: GlobalCfaPrec<ExplPrec>,
        VAmin: StateNodeValues<CfaState<ExplState>, LAbs>, VAmax: StateNodeValues<CfaState<ExplState>, LAbs>,
        VCmin: ChoiceNodeValues<CfaState<ExplState>, LConc>, VCmax: ChoiceNodeValues<CfaState<ExplState>, LConc>
    ): GlobalCfaPrec<ExplPrec> {

        require(stateToRefine.outgoingEdges.isNotEmpty())

        val maxEdge = stateToRefine.outgoingEdges.maxBy { VCmax[it.end]!! }!!
        val minEdge = stateToRefine.outgoingEdges.minBy { VCmin[it.end]!! }!!

        // TODO: Allow LBE
        require(maxEdge.label.stmts.size == 1 && minEdge.label.stmts.size == 1)
        val maxStmt = maxEdge.label.stmts.first()

        val wpVars = if (maxStmt is ProbStmt) {
            // We assume that a probabilistic location cannot be non-deterministic at the same time
            // This works for the PCFA formalism, but won't be true in general
            require(maxEdge.end.outgoingEdges.size == 1)
            val wps = maxEdge.end.outgoingEdges.first().end.metadata.flatMap { (resultingState, stmtList) ->
                stmtList.flatMap { stmt ->
                    val wpstate = WpState.of(resultingState.state.toExpr())
                    val wepexpr = wpstate.wep(stmt).expr
                    arrayListOf<VarDecl<*>>().also { collectVars(wepexpr, it) }
                }
            }
            wps.toSet()
        } else if (maxStmt is NonDetStmt) {
            TODO("Not supported yet")
        } else {
            val targetState = maxEdge.end.outgoingEdges.first().end.pmf.keys.first().state
            val wpstate = WpState.of(targetState.toExpr())
            val wepexpr = wpstate.wep(maxStmt).expr
            arrayListOf<VarDecl<*>>().also { collectVars(wepexpr, it) }
        }

        val newSubPrec = origPrecision.prec.join(ExplPrec.of(wpVars))
        return GlobalCfaPrec.create(newSubPrec)
    }
}

object PredRefiner {
    fun <S : ExprState, LAbs : StmtAction, LConc> wprefineGameAbstraction(
        game: AbstractionGame<S, LAbs, LConc>,
        stateToRefine: StateNode<S, LAbs>,
        origPrecision: GlobalCfaPrec<PredPrec>,
        VAmin: StateNodeValues<S, LAbs>, VAmax: StateNodeValues<S, LAbs>,
        VCmin: ChoiceNodeValues<S, LConc>, VCmax: ChoiceNodeValues<S, LConc>
    ): GlobalCfaPrec<PredPrec> {

        require(stateToRefine.outgoingEdges.isNotEmpty())

        val maxEdge = stateToRefine.outgoingEdges.maxBy { VCmax[it.end]!! }!!
        val minEdge = stateToRefine.outgoingEdges.minBy { VCmin[it.end]!! }!!

        // TODO: Allow LBE
        require(maxEdge.label.stmts.size == 1 && minEdge.label.stmts.size == 1)
        val maxStmt = maxEdge.label.stmts.first()

        val wpPreds = if (maxStmt is ProbStmt) {
            // We assume that a probabilistic location cannot be non-deterministic at the same time
            // This works for the PCFA formalism, but won't be true in general
            require(maxEdge.end.outgoingEdges.size == 1)
            val wps = maxEdge.end.outgoingEdges.first().end.metadata.flatMap { (resultingState, stmtList) ->
                stmtList.map { stmt ->
                    val wpstate = WpState.of(resultingState.state.toExpr())
                    return@map wpstate.wep(stmt).expr
                }
            }
            wps.flatMap(ExprSplitters.atoms()::apply).toSet()
        } else if (maxStmt is NonDetStmt) {
            TODO("Not supported yet")
        } else {
            val targetState = maxEdge.end.outgoingEdges.first().end.pmf.keys.first().state
            val wps = WpState.of(targetState.toExpr())
            ExprSplitters.atoms().apply(wps.wep(maxStmt).expr).toSet()
        }

        val newSubPrec = origPrecision.prec.join(PredPrec.of(wpPreds))
        return GlobalCfaPrec.create(newSubPrec)
    }
}

class ItpGameAbstractionRefiner<S: ExprState, P: Prec, LAbs: Stmt, LConc>(
    val solver: ItpSolver
):
        GameAbstractionRefiner<S, P, LAbs, LConc> {
    override fun refine(
        game: AbstractionGame<S, LAbs, LConc>,
        stateToRefine: StateNode<S,LAbs>,
        origPrecision: GlobalCfaPrec<PredPrec>,
        VAmin: StateNodeValues<S, LAbs>, VAmax: StateNodeValues<S, LAbs>,
        VCmin: ChoiceNodeValues<S, LConc>, VCmax: ChoiceNodeValues<S, LConc>
    ): P {
        require(stateToRefine.outgoingEdges.isNotEmpty())

        val maxEdge = stateToRefine.outgoingEdges.maxBy { VCmax[it.end]!! }!!
        val minEdge = stateToRefine.outgoingEdges.minBy { VCmin[it.end]!! }!!

        if(maxEdge.label is ProbStmt) {

        }

        TODO("Not yet implemented")
    }
}

interface PrecRefiner<P: Prec> {

}

