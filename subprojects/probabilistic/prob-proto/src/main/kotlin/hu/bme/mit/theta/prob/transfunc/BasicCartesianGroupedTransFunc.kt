package hu.bme.mit.theta.prob.transfunc

import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.analysis.pred.PredState
import hu.bme.mit.theta.core.decl.ConstDecl
import hu.bme.mit.theta.core.decl.Decls
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.stmt.HavocStmt
import hu.bme.mit.theta.core.stmt.NonDetStmt
import hu.bme.mit.theta.core.stmt.SequenceStmt
import hu.bme.mit.theta.core.stmt.Stmt
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.Type
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.ExprUtils
import hu.bme.mit.theta.core.utils.PathUtils
import hu.bme.mit.theta.core.utils.PrimeCounter
import hu.bme.mit.theta.core.utils.indexings.VarIndexing
import hu.bme.mit.theta.core.utils.indexings.VarIndexingFactory
import hu.bme.mit.theta.prob.pcfa.ProbStmt
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.utils.WithPushPop
import java.util.*

class BasicCartesianGroupedTransFunc(
    val solver: Solver
): GroupedTransFunc<PredState, StmtAction, PredPrec> {

    private val actLits: ArrayList<ConstDecl<BoolType>> = arrayListOf()
    private val litPrefix = "__" + javaClass.simpleName + "_" + PredGroupedTransFunc.instanceCounter + "_"

    override fun getSuccStates(state: PredState, action: StmtAction, prec: PredPrec): List<List<PredState>> {
        val stmt =
            if(action.stmts.size == 1) action.stmts.first()
            else SequenceStmt.of(action.stmts)
        return when(stmt) {
            is NonDetStmt -> handleNonDet(stmt, state, prec)
            is HavocStmt<*> -> {
                if(isSimplificationApplicable(stmt, prec))
                    handleHavocSimplified(stmt, state, prec)
                else
                    handleHavocGeneral(stmt, state, prec)
            }
            is SequenceStmt ->
                if (stmt.stmts.any {it is HavocStmt<*>  || it is ProbStmt}) TODO()
                else getNonGroupedNextStates(state, stmt, prec).map { listOf(it) }
            else -> {
                getNonGroupedNextStates(state, stmt, prec).map { listOf(it) }
            }
        }
    }

    private fun generateActivationLiterals(n: Int) {
        while (actLits.size < n) {
            actLits.add(Decls.Const(litPrefix + actLits.size, BoolExprs.Bool()))
        }
    }

    private fun handleNonDet(stmt: NonDetStmt, state: PredState, prec: PredPrec): List<List<PredState>> {
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
            maxPrimes = vars.maxOfOrNull(primes::get) ?: 0
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

    private fun isSimplificationApplicable(stmt: HavocStmt<*>, prec: PredPrec): Boolean {
        val havocVar = stmt.varDecl
        return prec.preds.none {
            val vars = arrayListOf<VarDecl<*>>()
            ExprUtils.collectVars(it, vars)
            return@none vars.any(havocVar::equals)
        }
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

    private fun getNonGroupedNextStates(state: PredState, stmt: Stmt, prec: PredPrec): List<PredState> {
        val unfoldResult = stmt.unfold()
        val stmtExpr =
            if(unfoldResult.exprs.size == 1) unfoldResult.exprs.first()
            else BoolExprs.And(unfoldResult.exprs)
        val expr = BoolExprs.And(stmtExpr, state.toExpr())
        val exprIndexing = VarIndexingFactory.indexing(0)

        val newStatePreds: MutableList<Expr<BoolType>> = ArrayList()
        val precIndexing = unfoldResult.indexing

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

    override fun getSuccStatesWithStmt(
        state: PredState,
        action: StmtAction,
        prec: PredPrec
    ): List<List<Pair<Stmt, PredState>>> {
        TODO("Not yet implemented")
    }
}