package hu.bme.mit.theta.prob.formalism

import hu.bme.mit.theta.analysis.Action
import hu.bme.mit.theta.analysis.InitFunc
import hu.bme.mit.theta.analysis.LTS
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.stmtoptimizer.StmtOptimizer
import hu.bme.mit.theta.analysis.stmtoptimizer.StmtSimplifier
import hu.bme.mit.theta.core.decl.Decl
import hu.bme.mit.theta.core.decl.Decls
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.model.ImmutableValuation
import hu.bme.mit.theta.core.model.Valuation
import hu.bme.mit.theta.core.stmt.AssumeStmt
import hu.bme.mit.theta.core.stmt.LoopStmt
import hu.bme.mit.theta.core.stmt.SequenceStmt
import hu.bme.mit.theta.core.stmt.Stmt
import hu.bme.mit.theta.core.stmt.Stmts
import hu.bme.mit.theta.core.stmt.Stmts.*
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.Type
import hu.bme.mit.theta.core.type.abstracttype.AbstractExprs
import hu.bme.mit.theta.core.type.anytype.Exprs
import hu.bme.mit.theta.core.type.anytype.RefExpr
import hu.bme.mit.theta.core.type.booltype.AndExpr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.rattype.RatExprs
import hu.bme.mit.theta.core.type.rattype.RatLitExpr
import hu.bme.mit.theta.core.type.rattype.RatType
import hu.bme.mit.theta.core.utils.ExprSimplifier
import hu.bme.mit.theta.core.utils.TypeUtils
import hu.bme.mit.theta.prob.EnumeratedDistribution
import hu.bme.mit.theta.prob.formalism.SMDP.Action.InnerAction
import hu.bme.mit.theta.prob.formalism.SMDP.Action.StandardAction
import hu.bme.mit.theta.prob.pcfa.ProbStmt
import hu.bme.mit.theta.prob.transfunc.GroupedTransFunc
import java.beans.Expression

/**
 * A symbolic MDP class based on the MDP subset of JANI models.
 */
class SMDP(
    val globalVars: Collection<VarDecl<*>>,
    val automata: List<AutomatonInstance>,
    val syncVecs: List<List<StandardAction?>>,
    val initExprs: Collection<Expr<BoolType>>
) {
    class Location(
        val name: String,
        val outEdges: MutableList<Edge>,
        val parent: AutomatonInstance? = null
    )
    sealed class Action(val name: String) { // This is not a Theta::Action, but an action label for transitions
        object InnerAction : Action("<inner>")
        class StandardAction(name: String) : Action(name)
    }
    class Assignment<T: Type>(val ref: VarDecl<T>, val expr: Expr<T>) {
        fun toStmt() = Assign(ref, expr)
    }
    class Edge(
        val sourceLoc: Location,
        val guard: Expr<BoolType>,
        val action: Action?,
        val destinations: List<Destination>
    ) {
        init {sourceLoc.outEdges.add(this)}
    }
    class Destination(
        val probability: Expr<RatType>,
        val assignments: List<Assignment<*>>,
        val loc: Location
    )
    class ComposedDestination(
        val probability: Expr<RatType>, // TODO change to real type
        val assignments: List<Assignment<*>>,
        val locs: List<Location>
    )

    class Automaton(
        val locations: Collection<Location>,
        val initLocs: Collection<Location>,
        val actions: Collection<Action>,
        val localVars: Collection<VarDecl<*>>,
        val edges: Collection<Edge>,
        val initExprs: Collection<Expr<BoolType>>
    ) { var numInstances = 0 }

    class AutomatonInstance(val template: Automaton){
        val id = template.numInstances++

        private val locLUT = template.locations.associateWith { Location(it.name, arrayListOf(), this) }
        private val varLUT = template.localVars.associateWith { Decls.Var(it.name+"_$id", it.type) }

        val localVars = varLUT.values.toList()
        val initExprs = template.initExprs.map(::replaceVars)
        private fun <T: Type> replaceVars(e: Expr<T>): Expr<T> =
            if (e is RefExpr) {
                val d = e.decl as Decl<T>
                if (d is VarDecl<T> && varLUT.containsKey(d)) Exprs.Ref(varLUT[d] as VarDecl<T>)
                else e
            } else {
                e.withOps(e.ops.map { replaceVars(it) })
            }

        val locs = locLUT.values.toList()
        val edges = template.edges.map { edge ->
            Edge(locLUT[edge.sourceLoc]!!, replaceVars(edge.guard), edge.action, edge.destinations.map { dest ->
                Destination(replaceVars(dest.probability), dest.assignments.map {
                    val newExpr = replaceVars(it.expr)
                    val newRef = if(varLUT.containsKey(it.ref)) varLUT[it.ref] else it.ref
                    Assignment(newRef as VarDecl<Type>, newExpr as Expr<Type>)
                }, locLUT[dest.loc]!!)
            })
        }

        val initLocs = template.initLocs.map { locLUT[it]!! }
    }

    fun getFullInitExpr(): AndExpr = BoolExprs.And(
        (initExprs + automata.flatMap { it.initExprs }).ifEmpty { listOf(BoolExprs.True()) }
    )
}

class SMDPState<D: ExprState>(
    val domainState: D,
    val locs: List<SMDP.Location>
): ExprState {
    override fun isBottom(): Boolean = domainState.isBottom

    override fun toExpr(): Expr<BoolType> {
        // TODO: maybe add the locs?
        return domainState.toExpr()
    }
}

interface ProbabilisticAction<TInner: Action> : Action {
    fun getActionProbs(): List<Pair<TInner, Expr<RatType>>>
}

class SMDPAction(
    val guard: AssumeStmt,
    val destinations: List<SMDP.ComposedDestination>
): Action {}


class SmdpLts<D: ExprState>(
    val smdp: SMDP,
    val stmtOptimizer: StmtOptimizer<D>?
): LTS<SMDPState<D>, SMDPAction> {

    private fun edgesToSMDPAction(es: List<SMDP.Edge>, s: SMDPState<D>): SMDPAction {
        val fullGuard = BoolExprs.And(es.map { it.guard })
        val optimized =
            stmtOptimizer?.optimizeStmt(s.domainState, Assume(fullGuard)) as AssumeStmt? ?:
            Assume(fullGuard)
        val resolutions = es.fold(listOf<List<SMDP.Destination>>(listOf())) { acc, curr ->
            acc.flatMap { prefix ->
                curr.destinations.map { new -> prefix + new  }
            }
        }
        val composedDestinations = resolutions.map {
            SMDP.ComposedDestination(
                AbstractExprs.Mul(it.map(SMDP.Destination::probability)) as Expr<RatType>,
                it.flatMap { it.assignments },
                it.map { it.loc }
            )
        }

        return SMDPAction(optimized, composedDestinations)
    }

    override fun getEnabledActionsFor(state: SMDPState<D>): MutableCollection<SMDPAction> {
        // TODO: precompute and store
        val res = arrayListOf<SMDPAction>()
        syncs@ for (syncVec in smdp.syncVecs) {
            var resolutions: List<List<SMDP.Edge>> = arrayListOf(listOf())
            parts@ for ((idx, action) in syncVec.withIndex()) {
                if(action == null) continue@parts

                val available = state.locs[idx].outEdges.filter { it.action == action }
                if(available.isEmpty()) continue@syncs
                resolutions = resolutions.flatMap { prev -> available.map { new -> prev + new } }
            }
            res.addAll(resolutions.map { edgesToSMDPAction(it, state) })
        }

        val nonSyncEdges = state.locs.flatMapIndexed { idx, loc ->
            loc.outEdges.filter { it.action is InnerAction }.map {
                edgesToSMDPAction(listOf(it), state)
            }
        }
        res.addAll(nonSyncEdges)

        return res
    }
}

class SmdpInitFunc<D: ExprState, P: Prec>(
    val subInitFunc: InitFunc<D, P>,
    val smdp: SMDP
): InitFunc<SMDPState<D>, P> {
    private fun computeLocConfigs(): List<List<SMDP.Location>> {
        val locLists = smdp.automata.map(SMDP.AutomatonInstance::initLocs)
        var res = listOf<List<SMDP.Location>>(listOf())
        for (locList in locLists) {
            val next = res.flatMap { prev -> locList.map { new -> prev+new } }
            res = next
        }
        return res
    }

    private val initLocConfigs = computeLocConfigs()

    override fun getInitStates(prec: P): Collection<SMDPState<D>> {
        return subInitFunc.getInitStates(prec).flatMap {
            initLocConfigs.map { initLocConfig -> SMDPState(it, initLocConfig) }
        }
    }
}

class SimpleStmtAction(val stmt: Stmt): StmtAction() {
    override fun getStmts(): List<Stmt> {
        return listOf(stmt)
    }
}

class SmdpTransFunc<D: ExprState, P: Prec>(
    val domainTransFunc: GroupedTransFunc<D, StmtAction, P>,
    val stmtSimplifier: StmtOptimizer<D>,
    val stateToValuation: (D) -> Valuation = {ImmutableValuation.empty()}
) {
    fun getSuccStates(
        state: SMDPState<D>,
        action: SMDPAction,
        prec: P
    ): List<List<Pair<Stmt, SMDPState<D>>>> {
        fun evaluateProbability(p: Expr<RatType>): Double {
            val simplified = ExprSimplifier.simplify(p, stateToValuation(state.domainState))
            if(simplified is RatLitExpr) return (simplified.num.toDouble() / simplified.denom.toDouble())
            else throw RuntimeException("Not enough information to evaluate probability in the abstract state")
        }

        val domainAction = SimpleStmtAction(
            ProbStmt(
                EnumeratedDistribution(
                action.destinations.associate {
                    val assigns = it.assignments.map(SMDP.Assignment<*>::toStmt)
                    val stmt = SequenceStmt.of(listOf(action.guard) + assigns)
                    val prob = evaluateProbability(it.probability)
                    stmt to prob
                })
            )
        )
        val domainResult = domainTransFunc.getSuccStatesWithStmt(state.domainState, domainAction, prec)
        if(domainResult.any { it.any { it.second.isBottom } }) return listOf()

        return domainResult.map {
            //TODO: indexing is easy to break, use something else, or enforce ordering in the getSuccStatesWithStmt contract
            it.mapIndexed { idx, (stmt, nextState) ->
                stmt to SMDPState(nextState, action.destinations[idx].locs)
            }
        }
    }
}