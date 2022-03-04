package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.analysis.expl.ExplPrec
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.pred.ExprSplitters
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.analysis.pred.PredState
import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.cfa.analysis.prec.GlobalCfaPrec
import hu.bme.mit.theta.cfa.analysis.prec.LocalCfaPrec
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.stmt.NonDetStmt
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.ExprUtils.collectVars
import hu.bme.mit.theta.core.utils.WpState
import hu.bme.mit.theta.prob.AbstractionGame.*
import java.util.*

typealias StateNodeValues<S, LAbs, LConc> = Map<StateNode<S, LAbs, LConc>, Double>
typealias ChoiceNodeValues<S, LAbs, LConc> = Map<ChoiceNode<S, LAbs, LConc>, Double>
interface RefinableStateSelector {
    fun <S: State, LAbs, LConc> select(
        game: AbstractionGame<S, LAbs, LConc>,
        VAmin: StateNodeValues<S, LAbs, LConc>,VAmax: StateNodeValues<S, LAbs, LConc>,
        VCmin: ChoiceNodeValues<S, LAbs, LConc>, VCmax: ChoiceNodeValues<S, LAbs, LConc>
    ): StateNode<S, LAbs, LConc>?
}

fun <S: State, LAbs, LConc> isRefinable(
    s: StateNode<S, *, LConc>,
    VCmax: ChoiceNodeValues<S, LAbs, LConc>,
    VCmin: ChoiceNodeValues<S, LAbs, LConc>
): Boolean {
    val choices = s.outgoingEdges.map { it.end }
    if (choices.isEmpty()) return false
    val max = choices.mapNotNull(VCmax::get).max()!!
    val min = choices.mapNotNull(VCmin::get).min()!!
    val maxChoices = choices.filter { doubleEquals(max, VCmax[it]!!) }.toSet()
    val minChoices = choices.filter { doubleEquals(min, VCmin[it]!!) }.toSet()
    return maxChoices!=minChoices
}

object coarsestRefinableStateSelector: RefinableStateSelector {
    override fun <S : State, LAbs, LConc> select(
        game: AbstractionGame<S, LAbs, LConc>,
        VAmin: StateNodeValues<S, LAbs, LConc>, VAmax: StateNodeValues<S, LAbs, LConc>,
        VCmin: ChoiceNodeValues<S, LAbs, LConc>, VCmax: ChoiceNodeValues<S, LAbs, LConc>
    ): StateNode<S, LAbs, LConc>? {
        return game.stateNodes.filter{ isRefinable(it, VCmax, VCmin)}.maxBy { VAmax[it]!!-VAmin[it]!! } !!
    }
}

object randomizedCoarsestRefinableStateSelector: RefinableStateSelector {
    override fun <S : State, LAbs, LConc> select(
        game: AbstractionGame<S, LAbs, LConc>,
        VAmin: StateNodeValues<S, LAbs, LConc>, VAmax: StateNodeValues<S, LAbs, LConc>,
        VCmin: ChoiceNodeValues<S, LAbs, LConc>, VCmax: ChoiceNodeValues<S, LAbs, LConc>
    ): StateNode<S, LAbs, LConc>? {
        val refineables = game.stateNodes.filter { isRefinable(it, VCmax, VCmin) }
        val diffs = refineables.map { it to VAmax[it]!! - VAmin[it]!! }
        val max = diffs.maxBy { it.second }!!
        val maxRefineables = diffs.filter { it.second == max.second }
        val rand = Random().nextInt(maxRefineables.size)
        return maxRefineables[rand].first
    }
}

object nearestRefinableStateSelector: RefinableStateSelector {
    override fun <S : State, LAbs, LConc> select(
        game: AbstractionGame<S, LAbs, LConc>,
        VAmin: StateNodeValues<S, LAbs, LConc>, VAmax: StateNodeValues<S, LAbs, LConc>,
        VCmin: ChoiceNodeValues<S, LAbs, LConc>, VCmax: ChoiceNodeValues<S, LAbs, LConc>
    ): StateNode<S, LAbs, LConc>? {
        val q = ArrayDeque<StateNode<S, LAbs, LConc>>()
        val visited = hashSetOf<StateNode<S, LAbs, LConc>>()
        q.addAll(game.initNodes)
        visited.addAll(game.initNodes)
        while (!q.isEmpty()) {
            val s = q.poll()
            val nexts = s.outgoingEdges
                .map { it.end }
                .flatMap { it.outgoingEdges }
                // TODO: get rid of this cast
                .flatMap { it.end.pmf.keys.map { it as StateNode<S, LAbs, LConc> } }
                .toSet()
                .filterNot(visited::contains)
            val refinable = nexts.firstOrNull { isRefinable(it, VCmax, VCmin)}
            if(refinable != null) {
                return refinable
            }
            q.addAll(nexts)
        }
        return null // no refinable state
    }
}

interface PrecRefiner<P: Prec, S: State, LAbs, LConc> {
    fun refine(
        game: AbstractionGame<S, LAbs, LConc>,
        stateToRefine: StateNode<S, LAbs, LConc>,
        origPrecision: P,
        VAmin: StateNodeValues<S, LAbs, LConc>, VAmax: StateNodeValues<S, LAbs, LConc>,
        VCmin: ChoiceNodeValues<S, LAbs, LConc>, VCmax: ChoiceNodeValues<S, LAbs, LConc>
    ): P
}

class GlobalCfaExplRefiner<LConc>: PrecRefiner<
        GlobalCfaPrec<ExplPrec>, CfaState<ExplState>, CfaAction, LConc> {
    override fun refine(
        game: AbstractionGame<CfaState<ExplState>, CfaAction, LConc>,
        stateToRefine: StateNode<CfaState<ExplState>, CfaAction, LConc>,
        origPrecision: GlobalCfaPrec<ExplPrec>,
        VAmin: StateNodeValues<CfaState<ExplState>, CfaAction, LConc>, VAmax: StateNodeValues<CfaState<ExplState>, CfaAction, LConc>,
        VCmin: ChoiceNodeValues<CfaState<ExplState>, CfaAction, LConc>, VCmax: ChoiceNodeValues<CfaState<ExplState>, CfaAction, LConc>
    ): GlobalCfaPrec<ExplPrec> {

        val choices = stateToRefine.outgoingEdges
        val max = choices.mapNotNull{VCmax[it.end]}.max()!!
        val min = choices.mapNotNull{VCmin[it.end]}.min()!!
        val maxChoices = choices.filter { doubleEquals(max, VCmax[it.end]!!) }.toSet()
        val minChoices = choices.filter { doubleEquals(min, VCmin[it.end]!!) }.toSet()

        val diff1 = maxChoices.minus(minChoices)
        val diff2 = minChoices.minus(maxChoices)

        val wpEdge = diff1.firstOrNull() ?: diff2.first()
        require(wpEdge.label.stmts.size == 0)
        val wpStmt = wpEdge.label.stmts.first()

        val wpVars = if (wpStmt is ProbStmt) {
            // We assume that a probabilistic location cannot be non-deterministic at the same time
            // This works for the PCFA formalism, but won't be true in general
            require(wpEdge.end.outgoingEdges.size == 1)
            val wps = wpEdge.end.outgoingEdges.first().end.metadata.flatMap { (resultingState, stmtList) ->
                stmtList.flatMap { stmt ->
                    val wpstate = WpState.of(resultingState.state.toExpr())
                    val wepexpr = wpstate.wep(stmt).expr
                    arrayListOf<VarDecl<*>>().also { collectVars(wepexpr, it) }
                }
            }
            wps.toSet()
        } else if (wpStmt is NonDetStmt) {
            TODO("Not supported yet")
        } else {
            val targetState = wpEdge.end.outgoingEdges.first().end.pmf.keys.first().state
            val wpstate = WpState.of(targetState.toExpr())
            val wepexpr = wpstate.wep(wpStmt).expr
            arrayListOf<VarDecl<*>>().also { collectVars(wepexpr, it) }
        }

        val newSubPrec = origPrecision.prec.join(ExplPrec.of(wpVars))
        return GlobalCfaPrec.create(newSubPrec)
    }
}

class GlobalCfaPredRefiner<S: ExprState, LConc>: PrecRefiner<
        GlobalCfaPrec<PredPrec>, S, CfaAction, LConc> {

    override fun refine(
        game: AbstractionGame<S, CfaAction, LConc>,
        stateToRefine: StateNode<S, CfaAction, LConc>,
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

        // TODO: Allow action-based LBE?
        require(wpEdge.label.stmts.size == 1)
        val wpStmt = wpEdge.label.stmts.first()

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
            collectVars(it, l)
            l.size>0
        }
        val newSubPrec = origPrecision.prec.join(PredPrec.of(predsUsed))
        return GlobalCfaPrec.create(newSubPrec)
    }
}

class LocalCfaPredRefiner<S: ExprState, LConc>(
    val predicatePropagator: PredicatePropagator
): PrecRefiner<LocalCfaPrec<PredPrec>, S, CfaAction, LConc> {
    override fun refine(
        game: AbstractionGame<S, CfaAction, LConc>,
        stateToRefine: StateNode<S, CfaAction, LConc>,
        origPrecision: LocalCfaPrec<PredPrec>,
        VAmin: StateNodeValues<S, CfaAction, LConc>, VAmax: StateNodeValues<S, CfaAction, LConc>,
        VCmin: ChoiceNodeValues<S, CfaAction, LConc>, VCmax: ChoiceNodeValues<S, CfaAction, LConc>,
    ): LocalCfaPrec<PredPrec> {
        val choices = stateToRefine.outgoingEdges
        val max = choices.mapNotNull{VCmax[it.end]}.max()!!
        val min = choices.mapNotNull{VCmin[it.end]}.min()!!
        val maxChoices = choices.filter { doubleEquals(max, VCmax[it.end]!!) }.toSet()
        val minChoices = choices.filter { doubleEquals(min, VCmin[it.end]!!) }.toSet()

        val diff1 = maxChoices.minus(minChoices)
        val diff2 = minChoices.minus(maxChoices)

        val wpEdge = diff1.firstOrNull() ?: diff2.first()
        require(wpEdge.label.stmts.size == 1)
        val wpStmt = wpEdge.label.stmts.first()

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

        val newPrec = predicatePropagator.propagate(
            game as AbstractionGame<CfaState<PredState>, CfaAction, LConc>,
            stateToRefine as StateNode<CfaState<PredState>, CfaAction, LConc>,
            origPrecision,
            wpPreds.toList()
        )

        return newPrec
    }
}

interface PredicatePropagator {
    fun <LAbs: StmtAction, LConc> propagate(
        game: AbstractionGame<CfaState<PredState>, LAbs, LConc>,
        refinedState: StateNode<CfaState<PredState>, LAbs, LConc>,
        origPrecision: LocalCfaPrec<PredPrec>,
        newPredicates: List<Expr<BoolType>>
    ): LocalCfaPrec<PredPrec>
}

object nonPropagatingPropagator: PredicatePropagator {
    override fun <LAbs : StmtAction, LConc> propagate(
        game: AbstractionGame<CfaState<PredState>, LAbs, LConc>,
        refinedState: StateNode<CfaState<PredState>, LAbs, LConc>,
        origPrecision: LocalCfaPrec<PredPrec>,
        newPredicates: List<Expr<BoolType>>
    ): LocalCfaPrec<PredPrec> {
        val loc = refinedState.state.loc
        val origLocalPrec = origPrecision.getPrec(loc)
        val newLocalPrec = origLocalPrec.join(PredPrec.of(newPredicates))
        return origPrecision.refine(loc, newLocalPrec)
    }
}

object gameTracePropagator: PredicatePropagator {
    override fun <LAbs : StmtAction, LConc> propagate(
        game: AbstractionGame<CfaState<PredState>, LAbs, LConc>,
        refinedState: StateNode<CfaState<PredState>, LAbs, LConc>,
        origPrecision: LocalCfaPrec<PredPrec>,
        newPredicates: List<Expr<BoolType>>
    ): LocalCfaPrec<PredPrec> {
        // Use the first predecessor maps in the game to find a shortest play,
        // and add the new predicates to each location
        // TODO: this is a shortest play only if the game was created using BFS,
        //       which is true for the BVI-based analysis (full state-space exploration),
        //       but won't be for BRTDP (simulation-based partial state-space exploration)

        var newPrec = origPrecision
        fun refinePrec(
            stateNode: StateNode<CfaState<PredState>, LAbs, LConc>,
            newPreds: Collection<Expr<BoolType>>
        ) {
            val loc = stateNode.state.loc
            val origLocalPrec = newPrec.getPrec(loc)
            val newLocalPrec = origLocalPrec.join(PredPrec.of(newPreds))
            newPrec = newPrec.refine(loc, newLocalPrec)
        }
        val addedPreds = newPredicates.filter {
            // TODO: stop at first var
            val l = arrayListOf<VarDecl<*>>();
            collectVars(it, l)
            l.size>0
        }
        refinePrec(refinedState, addedPreds)

        var currStateNode = refinedState
        var currPreds = newPredicates
        while (currStateNode !in game.initNodes && currPreds.isNotEmpty()) {
            val predecessorEdge = game.getFirstPredecessorEdge(currStateNode)!! // this will be a choice node

            val preconditions = currPreds.map {
                val stmts = predecessorEdge.end.metadata[currStateNode]
                val wps = WpState.of(it)
                BoolExprs.Or(stmts!!.map {wps.wep(it).expr})
            }
            currPreds = preconditions
                .flatMap(ExprSplitters.atoms()::apply)
                .toSet()
                .filter {
                    // TODO: stop at first var
                    val l = arrayListOf<VarDecl<*>>();
                    collectVars(it, l)
                    l.size>0
                }
                .toList()
            refinePrec(currStateNode, currPreds)

            currStateNode = game.getFirstPredecessorEdge(predecessorEdge.start)?.start!!
        }

        return newPrec
    }

}

object cfaEdgePropagator: PredicatePropagator {
    override fun <LAbs : StmtAction, LConc> propagate(
        game: AbstractionGame<CfaState<PredState>, LAbs, LConc>,
        refinedState: StateNode<CfaState<PredState>, LAbs, LConc>,
        origPrecision: LocalCfaPrec<PredPrec>,
        newPredicates: List<Expr<BoolType>>
    ): LocalCfaPrec<PredPrec> {
        // Use the first predecessor maps in the game to find a shortest play,
        // and add the new predicates to each location
        // TODO: this is a shortest play only if the game was created using BFS,
        //       which is true for the BVI-based analysis (full state-space exploration),
        //       but won't be for BRTDP (simulation-based partial state-space exploration)

        val addedPreds = hashMapOf<CFA.Loc, List<Expr<BoolType>>>()
        var newPrec = origPrecision
        fun refinePrec(
            loc: CFA.Loc,
            newPreds: List<Expr<BoolType>>
        ) {
            val origLocalPrec = newPrec.getPrec(loc)
            val newLocalPrec = origLocalPrec.join(PredPrec.of(newPreds))
            addedPreds[loc] = newPreds
            newPrec = newPrec.refine(loc, newLocalPrec)
        }
        val nonConstantPreds = newPredicates.filter {
            // TODO: stop at first var
            val l = arrayListOf<VarDecl<*>>();
            collectVars(it, l)
            l.size>0
        }

        val l = refinedState.state.loc
        refinePrec(l, nonConstantPreds)

        var edges = l.inEdges

        // TODO: this won't work if the init loc is part of a loop
        while (edges.isNotEmpty()) {
            var nonEmpty = false

            for (edge in edges) {
                val preconditions = addedPreds[edge.target]!!.map {
                    val stmt = edge.stmt
                    val wps = WpState.of(it)
                    wps.wep(stmt).expr
                }
                val currPreds = preconditions
                    .flatMap(ExprSplitters.atoms()::apply)
                    .toSet()
                    .filter {
                        // TODO: stop at first var
                        val l = arrayListOf<VarDecl<*>>();
                        collectVars(it, l)
                        l.size>0
                    }
                    .toList()
                if(currPreds.isNotEmpty()) nonEmpty = true
                refinePrec(edge.source, currPreds)
            }

            val sources = edges.map { it.source }.toSet()
            edges = sources.flatMap { it.inEdges }.filter { it.source !in addedPreds.keys }
            if(!nonEmpty) break
        }

        return newPrec
    }

}

