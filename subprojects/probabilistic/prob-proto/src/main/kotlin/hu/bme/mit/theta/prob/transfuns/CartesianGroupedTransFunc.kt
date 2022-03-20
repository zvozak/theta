package hu.bme.mit.theta.prob.transfuns

import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.analysis.pred.PredState
import hu.bme.mit.theta.core.decl.ConstDecl
import hu.bme.mit.theta.core.decl.Decls
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.stmt.NonDetStmt
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.ExprUtils
import hu.bme.mit.theta.core.utils.PathUtils
import hu.bme.mit.theta.core.utils.PrimeCounter
import hu.bme.mit.theta.core.utils.indexings.VarIndexing
import hu.bme.mit.theta.core.utils.indexings.VarIndexingFactory
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.utils.WithPushPop
import java.util.*

class CartesianGroupedTransFunc(
    val solver: Solver,
    val computeNonDetExactly: Boolean = true
): GroupedTransferFunction<PredState, StmtAction, PredPrec> {

    private val actLits: ArrayList<ConstDecl<BoolType>> = arrayListOf()
    private val litPrefix = "__" + javaClass.simpleName + "_" + PredGroupedTransFunc.instanceCounter + "_"

    override fun getSuccStates(state: PredState, action: StmtAction, prec: PredPrec): List<List<PredState>> {
        TODO("Not yet implemented")
    }

    private fun handleNonDetCartesian(stmt: NonDetStmt, state: PredState, prec: PredPrec): List<List<PredState>> {
        val subStmts = stmt.stmts
        var maxPrimes = 0
        val nextIndexings = arrayListOf<VarIndexing>()
        val subExprs = subStmts.mapIndexed { idx, subStmt ->
            val unfoldResult = subStmt.unfold()
            val preExpr = BoolExprs.And(unfoldResult.exprs)
            val expr = offsetNonZeroPrimes(preExpr, maxPrimes)
            val primes = PrimeCounter.countPrimes(expr)
            nextIndexings.add(primes)
            val vars = arrayListOf<VarDecl<*>>()
            ExprUtils.collectVars(expr, vars)
            maxPrimes = vars.map(primes::get).max() ?: 0
            return@mapIndexed expr
        }.toMutableList()
        subExprs.add(state.toExpr())
        val exprIndexing = VarIndexingFactory.indexing(0)

        WithPushPop(solver).use { wp ->
            solver.add(PathUtils.unfold(BoolExprs.And(subExprs), exprIndexing))
            solver.check()
            if (solver.status.isUnsat) {
                return listOf()
            }

            for (pred in prec.preds) {
                WithPushPop(solver).use { wp1 ->
                    TODO()
                    //val combinations = solver.add(
                    //    PathUtils.unfold()
                    //)
                }
            }
        }
        TODO()
    }

    private fun generateActivationLiterals(n: Int) {
        while (actLits.size < n) {
            actLits.add(Decls.Const(litPrefix + actLits.size, BoolExprs.Bool()))
        }
    }

    private fun handleNonDetExact(stmt: NonDetStmt, state: PredState, prec: PredPrec): List<List<PredState>> {
        val subStmts = stmt.stmts

        var maxPrimes = 0
        val nextIndexings = arrayListOf<VarIndexing>()
        val subExprs = subStmts.mapIndexed { idx, subStmt ->
            val unfoldResult = subStmt.unfold()
            val preExpr = BoolExprs.And(unfoldResult.exprs)
            val expr = offsetNonZeroPrimes(preExpr, maxPrimes)
            val primes = PrimeCounter.countPrimes(expr)
            nextIndexings.add(primes)
            val vars = arrayListOf<VarDecl<*>>()
            ExprUtils.collectVars(expr, vars)
            maxPrimes = vars.map(primes::get).max() ?: 0
            return@mapIndexed expr
        }.toMutableList()
        subExprs.add(state.toExpr())

        val fullExpr = BoolExprs.And(subExprs)

        val preds = prec.preds.toList()
        val numActLits = preds.size * subStmts.size
        generateActivationLiterals(numActLits)
        val res = arrayListOf<List<PredState>>()

        WithPushPop(solver).use { wp ->
            solver.add(PathUtils.unfold(fullExpr, VarIndexingFactory.indexing(0)))
            var litIdx = 0
            for (indexing in nextIndexings) {
                for (pred in preds) {
                    val activationExpr = BoolExprs.Iff(
                        actLits[litIdx++].ref, PathUtils.unfold(pred, indexing)
                    )
                    solver.add(activationExpr)
                }
            }
            while(solver.check().isSat) {
                val model = solver.model

                val feedback = ArrayList<Expr<BoolType>>(numActLits+1)
                feedback.add(BoolExprs.True())

                litIdx = 0
                val newStateList = arrayListOf<PredState>()
                for(a in subStmts.indices) {
                    val newPreds = hashSetOf<Expr<BoolType>>()
                    for(pred in preds) {
                        val lit = actLits[litIdx]
                        val eval = model.eval(lit)
                        if(eval.isPresent) {
                            if(eval.get() == BoolExprs.True()) {
                                newPreds.add(pred)
                                feedback.add(lit.ref)
                            } else {
                                newPreds.add(prec.negate(pred))
                                feedback.add(BoolExprs.Not(lit.ref))
                            }
                        }
                        litIdx++
                    }
                    newStateList.add(PredState.of(newPreds))
                }
                res.add(newStateList)
                solver.add(BoolExprs.Not(BoolExprs.And(feedback)))
            }
        }
        return res
    }

    private fun getNonGroupedNextStates(state: PredState, action: StmtAction, prec: PredPrec): List<PredState> {
        require(action.stmts.size == 1) {"Action-based LBE not supported - use SEQ stmt"}

        val unfoldResult = action.stmts.first().unfold()
        val stmtExpr =
            if(unfoldResult.exprs.size == 1) unfoldResult.exprs.first()
            else BoolExprs.And(unfoldResult.exprs)
        val expr = BoolExprs.And(stmtExpr, state.toExpr())
        val exprIndexing = VarIndexingFactory.indexing(0)

        val newStatePreds: MutableList<Expr<BoolType>> = ArrayList()
        val precIndexing = action.nextIndexing()

        WithPushPop(solver).use { wp ->
            solver.add(PathUtils.unfold(expr, exprIndexing))
            solver.check()
            if (solver.status.isUnsat) {
                return listOf()
            }
            for (pred in prec.preds) {
                var ponEntailed = false
                var negEntailed = false
                WithPushPop(solver).use { wp1 ->
                    solver.add(
                        PathUtils.unfold(
                            prec.negate(pred),
                            precIndexing
                        )
                    )
                    ponEntailed = solver.check().isUnsat
                }
                WithPushPop(solver).use { wp2 ->
                    solver.add(PathUtils.unfold(pred, precIndexing))
                    negEntailed = solver.check().isUnsat
                }
                assert(!(ponEntailed && negEntailed)) { "Ponated and negated predicates are both entailed." }
                if (ponEntailed) {
                    newStatePreds.add(pred)
                }
                if (negEntailed) {
                    newStatePreds.add(prec.negate(pred))
                }
            }
        }

        return listOf(PredState.of(newStatePreds))
    }
}