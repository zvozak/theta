package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
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
import hu.bme.mit.theta.core.stmt.NonDetStmt
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.utils.WpState
import hu.bme.mit.theta.prob.AbstractionGame.*
import java.util.*
import kotlin.collections.HashSet

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
        stateToRefine: StateNode<S, LAbs>,
        origPrec: P
    ): P
}

fun <S: ExprState, LAbs: StmtAction, LConc> wprefineGameAbstraction(
    game: AbstractionGame<S, LAbs, LConc>,
    stateToRefine: StateNode<S,LAbs>,
    origPrecision: GlobalCfaPrec<PredPrec>,
    VAmin: StateNodeValues<S, LAbs>, VAmax: StateNodeValues<S, LAbs>,
    VCmin: ChoiceNodeValues<S, LConc>, VCmax: ChoiceNodeValues<S, LConc>
): GlobalCfaPrec<PredPrec> {

    require(!stateToRefine.outgoingEdges.isEmpty())

    val maxEdge = stateToRefine.outgoingEdges.maxBy { VCmax[it.end]!! }!!
    val minEdge = stateToRefine.outgoingEdges.minBy { VCmin[it.end]!! }!!

    // TODO: Allow LBE
    require(maxEdge.label.stmts.size == 1 && minEdge.label.stmts.size == 1)
    val maxStmt = maxEdge.label.stmts.first()

    val wpPreds = if(maxStmt is ProbStmt) {
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
    } else if(maxStmt is NonDetStmt) {
        TODO("Not supported yet")
    } else {
        val targetState = maxEdge.end.outgoingEdges.first().end.pmf.keys.first().state
        val wps = WpState.of(targetState.toExpr())
        ExprSplitters.atoms().apply(wps.wep(maxStmt).expr).toSet()
    }

    val newSubPrec = origPrecision.prec.join(PredPrec.of(wpPreds))
    return GlobalCfaPrec.create(newSubPrec)
}

class ItpGameAbstractionRefiner<S: ExprState, P: Prec, A: ExprAction, LConc>:
        GameAbstractionRefiner<S, P, A, LConc> {
    override fun refine(
        game: AbstractionGame<S, A, LConc>,
        stateToRefine: StateNode<S, A>,
        origPrec: P
    ): P {
        TODO("Not yet implemented")
    }
}

/**
 * Game Abstraction refiner
 */
class WpGARefiner<A: StmtAction, LConc>:
        GameAbstractionRefiner<PredState, PredPrec, A, LConc> {
    override fun refine(
        game: AbstractionGame<PredState, A, LConc>,
        stateToRefine: StateNode<PredState, A>,
        origPrec: PredPrec
    ): PredPrec {
        TODO("Not yet implemented")
    }
}

//class GlobalCfaPrecRefiner<S: ExprState, P: Prec, LAbs, LConc>(
//    val subRefiner: GameAbstractionRefiner<S, P, LAbs, LConc>
//): GameAbstractionRefiner<CfaState<S>, GlobalCfaPrec<P>, LAbs, LConc> {
//    override fun refine(
//        game: AbstractionGame<CfaState<S>, LAbs, LConc>,
//        stateToRefine: StateNode<CfaState<S>, LAbs>,
//        origPrec: GlobalCfaPrec<P>
//    ): GlobalCfaPrec<P> {
//        return GlobalCfaPrec.create(subRefiner.refine(
//            game, stateToRefine,
//        ))
//    }
//
//}

interface PrecRefiner<P: Prec> {

}

