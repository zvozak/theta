package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.analysis.pred.PredState
import hu.bme.mit.theta.core.decl.ConstDecl
import hu.bme.mit.theta.core.decl.Decls
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.stmt.HavocStmt
import hu.bme.mit.theta.core.stmt.NonDetStmt
import hu.bme.mit.theta.core.stmt.Stmt
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.Type
import hu.bme.mit.theta.core.type.anytype.Exprs
import hu.bme.mit.theta.core.type.anytype.PrimeExpr
import hu.bme.mit.theta.core.type.anytype.RefExpr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolExprs.And
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Not
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.*
import hu.bme.mit.theta.core.utils.indexings.VarIndexing
import hu.bme.mit.theta.core.utils.indexings.VarIndexingFactory
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.utils.WithPushPop

fun Stmt.unfold(): StmtUnfoldResult =
    StmtUtils.toExpr(this, VarIndexingFactory.indexing(0))

class PredGroupedTransferFunction(
    val solver: Solver
): GroupedTransferFunction<PredState, StmtAction, PredPrec> {

    private val actLits: ArrayList<ConstDecl<BoolType>> = arrayListOf()
    private val litPrefix = "__" + javaClass.simpleName + "_" + instanceCounter + "_"

    companion object { var instanceCounter = 0 }
    init { instanceCounter++ }

    override fun getSuccStates(state: PredState, action: StmtAction, prec: PredPrec): List<List<PredState>> {
        require(action.stmts.size == 1) // LBE not supported yet
        val stmt = action.stmts.first()
        return when(stmt) {
            is NonDetStmt -> handleNonDet(stmt, state, prec)
            is HavocStmt<*> -> {
                if(isSimplificationApplicable(stmt, prec))
                    handleHavocSimplified(stmt, state, prec)
                else
                    handleHavocGeneral(stmt, state, prec)
            }
            else -> {
                getNonGroupedNextStates(state, stmt, prec).map { listOf(it) }
            }
        }
    }

    private fun getNonGroupedNextStates(state: PredState, stmt: Stmt, prec: PredPrec): List<PredState> {
        val unfoldResult = stmt.unfold()
        val stmtExpr =
            if(unfoldResult.exprs.size == 1) unfoldResult.exprs.first()
            else And(unfoldResult.exprs)
        val fullExpr = And(stmtExpr, state.toExpr())

        val preds = prec.preds.toList()
        val numActLits = preds.size
        generateActivationLiterals(numActLits)
        val res = arrayListOf<PredState>()
        WithPushPop(solver).use {
            solver.add(PathUtils.unfold(fullExpr, VarIndexingFactory.indexing(0)))
            for ((i, pred) in preds.withIndex()) {
                solver.add(BoolExprs.Iff(actLits[i].ref, PathUtils.unfold(pred, unfoldResult.indexing)))
            }
            while (solver.check().isSat) {
                val model = solver.model

                val feedback = ArrayList<Expr<BoolType>>(numActLits+1)
                feedback.add(BoolExprs.True())

                val newPreds = hashSetOf<Expr<BoolType>>()
                for((i, pred) in preds.withIndex()) {
                    val lit = actLits[i]
                    val eval = model.eval(lit)
                    if(eval.isPresent) {
                        if(eval.get() == BoolExprs.True()) {
                            newPreds.add(pred)
                            feedback.add(lit.ref)
                        } else {
                            newPreds.add(prec.negate(pred))
                            feedback.add(Not(lit.ref))
                        }
                    }
                }
                res.add(PredState.of(newPreds))
                solver.add(Not(And(feedback)))
            }
        }

        return res
    }

    private fun isSimplificationApplicable(stmt: HavocStmt<*>, prec: PredPrec): Boolean {
        val havocVar = stmt.varDecl
        return prec.preds.none {
            val vars = arrayListOf<VarDecl<*>>()
            ExprUtils.collectVars(it, vars)
            return@none vars.any(havocVar::equals)
        }
    }

    private fun handleNonDet(stmt: NonDetStmt, state: PredState, prec: PredPrec): ArrayList<List<PredState>> {
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

        val preds = prec.preds.toList()
        val numActLits = preds.size * stmts.size
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
                for(a in stmts.indices) {
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
                                feedback.add(Not(lit.ref))
                            }
                        }
                        litIdx++
                    }
                    newStateList.add(PredState.of(newPreds))
                }
                res.add(newStateList)
                solver.add(Not(And(feedback)))
            }
        }
        return res
    }

    private fun <T: Type> handleHavocSimplified(
        stmt: HavocStmt<T>, state: PredState, prec: PredPrec
    ): List<List<PredState>> {
        return listOf(getNonGroupedNextStates(state, stmt, prec))
    }

    private fun <T: Type> handleHavocGeneral(
        stmt: HavocStmt<T>, state: PredState, prec: PredPrec
    ): List<List<PredState>> {
        TODO("Not supported yet")
    }

    private fun generateActivationLiterals(n: Int) {
        while (actLits.size < n) {
            actLits.add(Decls.Const(litPrefix + actLits.size, BoolExprs.Bool()))
        }
    }
}

fun <T: Type> offsetNonZeroPrimes(expr: Expr<T>, offset: Int): Expr<T> {
    if(offset == 0) return expr
    return if(expr is PrimeExpr) {
        // TODO: make this use less object creations
        Exprs.Prime(expr, offset)
    } else if(expr is RefExpr) {
        expr
    } else {
        expr.map { op -> offsetNonZeroPrimes(op, offset) }
    }
}