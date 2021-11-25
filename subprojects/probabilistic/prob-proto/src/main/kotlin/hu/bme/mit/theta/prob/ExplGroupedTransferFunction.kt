package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.expl.ExplPrec
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expr.ExprStates
import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.model.Valuation
import hu.bme.mit.theta.core.stmt.HavocStmt
import hu.bme.mit.theta.core.stmt.NonDetStmt
import hu.bme.mit.theta.core.stmt.Stmt
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.And
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Not
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.ExprUtils
import hu.bme.mit.theta.core.utils.PathUtils
import hu.bme.mit.theta.core.utils.PrimeCounter
import hu.bme.mit.theta.core.utils.VarIndexing
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.utils.WithPushPop

class ExplGroupedTransferFunction(
    val solver: Solver
): GroupedTransferFunction<ExplState, StmtAction, ExplPrec> {

    override fun getSuccStates(state: ExplState, action: StmtAction, prec: ExplPrec): List<List<ExplState>> {
        require(action.stmts.size == 1) // LBE not supported yet
        val stmt = action.stmts.first()
        return when(stmt) {
            is NonDetStmt -> handleNonDet(stmt, state, prec)
            is HavocStmt<*> ->
                // The simplified computation of havoc is always correct for explicit abstraction
                listOf(getNonGroupedNextStates(state, stmt, prec))
            else ->
                getNonGroupedNextStates(state, stmt, prec).map { listOf(it) }
        }
    }

    private fun handleNonDet(
        stmt: NonDetStmt, state: ExplState, prec: ExplPrec
    ): List<List<ExplState>> {
        val stmts = stmt.stmts

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
            solver.add(PathUtils.unfold(fullExpr, VarIndexing.all(0)))
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

    private fun getNonGroupedNextStates(
        state: ExplState, stmt: Stmt, prec: ExplPrec
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
}