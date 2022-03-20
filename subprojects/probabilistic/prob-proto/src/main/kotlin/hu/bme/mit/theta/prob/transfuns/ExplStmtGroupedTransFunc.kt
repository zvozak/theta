package hu.bme.mit.theta.prob.transfuns

import hu.bme.mit.theta.analysis.expl.ExplPrec
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expl.StmtApplier
import hu.bme.mit.theta.analysis.expr.ExprStates
import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.model.MutableValuation
import hu.bme.mit.theta.core.model.Valuation
import hu.bme.mit.theta.core.stmt.HavocStmt
import hu.bme.mit.theta.core.stmt.NonDetStmt
import hu.bme.mit.theta.core.stmt.SequenceStmt
import hu.bme.mit.theta.core.stmt.Stmt
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.And
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Not
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.*
import hu.bme.mit.theta.core.utils.indexings.VarIndexing
import hu.bme.mit.theta.core.utils.indexings.VarIndexingFactory
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.utils.WithPushPop

class ExplStmtGroupedTransFunc(
    val solver: Solver,
    val enumerationLimit: Int = 0
): GroupedTransferFunction<ExplState, StmtAction, ExplPrec> {

    override fun getSuccStates(state: ExplState, action: StmtAction, prec: ExplPrec): List<List<ExplState>> {
        require(action.stmts.size == 1)
            { "Action-based LBE not supported - use Sequence statements to group multiple simple statements" }

        val stmt = action.stmts.first()
        return when(stmt) {
            is NonDetStmt -> handleNonDet(stmt, state, prec, enumerationLimit)
            is HavocStmt<*> ->
                // The simplified computation of havoc is always correct for explicit abstraction
                listOf(getNonGroupedNextStates(state, stmt, prec, enumerationLimit))
            is SequenceStmt ->
                if (stmt.stmts.any {it is HavocStmt<*>}) TODO()
                else getNonGroupedNextStates(state, stmt, prec).map { listOf(it) }
            else ->
                getNonGroupedNextStates(state, stmt, prec, enumerationLimit).map { listOf(it) }
        }
    }

    private fun handleNonDet(
        stmt: NonDetStmt, state: ExplState, prec: ExplPrec, limit: Int = 0
    ): List<List<ExplState>> {
        val stmts = stmt.stmts
        val valuation = MutableValuation.copyOf(state)
        val applyResults =
            stmts.map {StmtApplier.apply(it, valuation, false)}


        var maxPrimes = 0
        val nextIndexings = arrayListOf<VarIndexing>()
        val subExprs = stmts.mapIndexed { idx, subStmt ->
            val unfoldResult = subStmt.unfold()
            val preExpr = And(unfoldResult.exprs)
            val expr = offsetNonZeroPrimes(preExpr, maxPrimes)
            val primes = PrimeCounter.countPrimes(expr)
            nextIndexings.add(primes)
            val vars = arrayListOf<VarDecl<*>>()
            ExprUtils.collectVars(expr, vars)
            maxPrimes = vars.map(primes::get).max() ?: 0
            return@mapIndexed expr
        }.toMutableList()
        subExprs.add(state.toExpr())

        val fullExpr = And(subExprs)

        val result = arrayListOf<List<ExplState>>()

        WithPushPop(solver).use {
            solver.add(PathUtils.unfold(fullExpr, VarIndexingFactory.indexing(0)))
            while (solver.check().isSat) {
                val model = solver.model
                val feedback = arrayListOf<Expr<BoolType>>()

                val valuations = arrayListOf<Valuation>()
                val states = arrayListOf<ExplState>()
                for(indexing in nextIndexings) {
                    val valuation = PathUtils.extractValuation(model, indexing)
                    valuations.add(valuation)
                    val s = prec.createState(valuation)
                    states.add(s)
                    feedback.add(PathUtils.unfold(s.toExpr(), indexing))
                }

                result.add(states)
                solver.add(Not(And(feedback)))
            }
        }

        return result
    }

    private fun getNonGroupedNextStatesPrev(
        state: ExplState, stmt: Stmt, prec: ExplPrec, limit: Int = 0
    ): List<ExplState> {
        val unfoldResult = stmt.unfold()
        val stmtExpr =
            if(unfoldResult.exprs.size == 1) unfoldResult.exprs.first()
            else And(unfoldResult.exprs)
        val fullExpr = And(stmtExpr, state.toExpr())
        val succStates = ExprStates.createStatesForExpr(solver,
            And(state.toExpr(), fullExpr), 0,
            { valuation: Valuation? ->
                prec.createState(
                    valuation
                )
            }, unfoldResult.indexing
        )
        return if (succStates.isEmpty()) listOf() else succStates.toList()
    }

    private fun getNonGroupedNextStates(
        state: ExplState, stmt: Stmt, prec: ExplPrec, limit: Int = 0
    ): List<ExplState> {
        val valuation = MutableValuation.copyOf(state)

        val applyResult = StmtApplier.apply(stmt, valuation, false)
        if (applyResult == StmtApplier.ApplyResult.BOTTOM) {
            return listOf(ExplState.bottom())
        } else if (applyResult == StmtApplier.ApplyResult.FAILURE) {
            val toExprResult = StmtUtils.toExpr(stmt, VarIndexingFactory.indexing(0))
            val expr: Expr<BoolType> = And(valuation.toExpr(), And(toExprResult.exprs))
            val nextIdx = toExprResult.indexing
            // We query (max + 1) states from the solver to see if there
            // would be more than max
            val maxToQuery = if (limit == 0) 0 else limit + 1
            val succStates = ExprStates.createStatesForExpr(solver, expr, 0,
                { valuation: Valuation? -> prec.createState(valuation) }, nextIdx, maxToQuery
            )
            if (succStates.isEmpty()) {
                return listOf(ExplState.bottom())
            } else if (limit == 0 || succStates.size <= limit) {
                return succStates.toList()
            } else {
                val reapplyResult = StmtApplier.apply(stmt, valuation, true)
                assert(reapplyResult == StmtApplier.ApplyResult.SUCCESS)
            }
        }

        val abstracted = prec.createState(valuation)
        return listOf(abstracted)
    }
}