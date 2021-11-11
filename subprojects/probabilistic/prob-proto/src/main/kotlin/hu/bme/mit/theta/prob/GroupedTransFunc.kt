package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.Action
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.analysis.expr.ExprAction
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.analysis.pred.PredState
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.CfaPrec
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.core.decl.ConstDecl
import hu.bme.mit.theta.core.decl.Decls
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.stmt.HavocStmt
import hu.bme.mit.theta.core.stmt.NonDetStmt
import hu.bme.mit.theta.core.stmt.Stmt
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.Type
import hu.bme.mit.theta.core.type.anytype.PrimeExpr
import hu.bme.mit.theta.core.type.anytype.RefExpr
import hu.bme.mit.theta.core.type.booltype.AndExpr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolExprs.And
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.*
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.utils.WithPushPop

interface GroupedTransferFunction<S: State, A: Action, P: Prec> {
    fun getSuccStates(state: S, action: A, prec: P): List<List<S>>
}

// TODO: wouldn't this be clearer if distributions were also handled by this class?
class CfaGroupedTransferFunction<S: ExprState, P: Prec>(
    val subTransFunc: GroupedTransferFunction<S, in CfaAction, P>
): GroupedTransferFunction<CfaState<S>, CfaAction, CfaPrec<P>> {

    override fun getSuccStates(state: CfaState<S>, action: CfaAction, prec: CfaPrec<P>): List<List<CfaState<S>>> {
        val source = action.source
        require(state.loc == source)
        val target = action.target
        val subPrec  = prec.getPrec(target)
        val subState = state.state // CfaState = Loc information + Substate information
        val subSuccStates = subTransFunc.getSuccStates(subState, action, subPrec)
        return subSuccStates.map { nextStates ->
            nextStates.map { CfaState.of(target, it) }
        }
    }

}

fun Stmt.unfold(): StmtUnfoldResult =
        StmtUtils.toExpr(this, VarIndexing.all(0))

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
        val fullExpr = And(stmtExpr,state.toExpr())

        val preds = prec.preds.toList()
        val numActLits = preds.size
        generateActivationLiterals(numActLits)
        val res = arrayListOf<PredState>()
        WithPushPop(solver).use {
            solver.add(PathUtils.unfold(fullExpr, VarIndexing.all(0)))
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
                            feedback.add(BoolExprs.Not(lit.ref))
                        }
                    }
                }
                res.add(PredState.of(newPreds))
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
            val indexing = unfoldResult.indexing.transform()
            for (i in 0 until maxPrimes) {
                indexing.incAll()
            }
            nextIndexings.add(indexing.build())
            val primes = PrimeCounter.countPrimes(expr)
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
            solver.add(PathUtils.unfold(fullExpr, VarIndexing.all(0)))
            for (indexing in nextIndexings) {
                for ((i, pred) in preds.withIndex()) {
                    solver.add(BoolExprs.Iff(actLits[i].ref, PathUtils.unfold(pred, indexing)))
                }
            }
            while(solver.check().isSat) {
                val model = solver.model

                val feedback = ArrayList<Expr<BoolType>>(numActLits+1)
                feedback.add(BoolExprs.True())

                var litIdx = 0
                val newStateList = arrayListOf<PredState>()
                for(a in stmts.indices) {
                    val newPreds = hashSetOf<Expr<BoolType>>()
                    for((i, pred) in preds.withIndex()) {
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
        // TODO: make this less ugly
        var res = expr
        for(i in 0 until offset) {
            res = PrimeExpr.of(res)
        }
        res
    } else if(expr is RefExpr) {
        expr
    } else {
        expr.map { op -> offsetNonZeroPrimes(op, offset) }
    }
}


//interface MultiActionTransFunc<S: State, A: Action, P: Prec> {
//    /**
//     * For N actions, returns a list of N-long lists, such that each inner
//     * list is a list of next states, one for each action.
//     */
//    fun getSuccStates(state: S, actions: List<A>, prec: P): List<List<S>>
//}

//
//class CfaMultiActionTransFunc<S : ExprState, P : Prec>(
//    val transFunc: MultiActionTransFunc<S, in CfaAction, P>
//) : MultiActionTransFunc<CfaState<S>, CfaAction, CfaPrec<P>> {
//
//    override fun getSuccStates(state: CfaState<S>, actions: List<CfaAction>, prec: CfaPrec<P>): List<List<CfaState<S>>> {
//        require(actions.isNotEmpty())
//
//        val sources = actions.map(CfaAction::getSource)
//        require(sources.all(state.loc::equals)) { "Location mismatch" }
//
//        val targets = actions.map(CfaAction::getTarget)
//        val target = targets.first()
//        require(targets.all(target::equals))
//
//        val subPrec: P = prec.getPrec(target)
//        val subState = state.state
//
//        val subSuccStates = transFunc.getSuccStates(subState, actions, subPrec)
//        return subSuccStates.map { nextStates ->
//            nextStates.map { CfaState.of(target, it) }
//        }
//    }
//}
//

//class PredMultiActionTransFunction(
//    val solver: Solver
//) : MultiActionTransFunc<PredState, ExprAction, PredPrec> {
//
//    companion object {
//        var instanceCounter = 0
//    }
//
//    init {
//        instanceCounter++
//    }
//
//    private val actLits: ArrayList<ConstDecl<BoolType>> = arrayListOf()
//    private val litPrefix = "__" + javaClass.simpleName + "_" + instanceCounter + "_"
//
//    override fun getSuccStates(state: PredState, actions: List<ExprAction>, prec: PredPrec): List<List<PredState>> {
//
//        var maxPrimes = 0
//        val nextIndexings = arrayListOf<VarIndexing>()
//        val subExprs = actions.mapIndexed { idx, action ->
//            val expr = offsetNonZeroPrimes(action.toExpr(), maxPrimes)
//            val indexing = action.nextIndexing().transform()
//            for (i in 0 until maxPrimes) {
//                indexing.incAll()
//            }
//            nextIndexings.add(indexing.build())
//            val primes = PrimeCounter.countPrimes(expr)
//            val vars = arrayListOf<VarDecl<*>>()
//            ExprUtils.collectVars(expr, vars)
//            maxPrimes = vars.map(primes::get).max() ?: 0
//            return@mapIndexed expr
//        }.toMutableList()
//        subExprs.add(state.toExpr())
//
//        val fullExpr = AndExpr.create(subExprs)
//
//        val preds = prec.preds.toList()
//        val numActLits = preds.size * actions.size
//        generateActivationLiterals(numActLits)
//        val res = arrayListOf<List<PredState>>()
//
//        WithPushPop(solver).use { wp ->
//            solver.add(PathUtils.unfold(fullExpr, VarIndexing.all(0)))
//            for (indexing in nextIndexings) {
//                for ((i, pred) in preds.withIndex()) {
//                    solver.add(BoolExprs.Iff(actLits[i].ref, PathUtils.unfold(pred, indexing)))
//                }
//            }
//            while(solver.check().isSat) {
//                val model = solver.model
//
//                val feedback = ArrayList<Expr<BoolType>>(numActLits+1)
//                feedback.add(BoolExprs.True())
//
//                var litIdx = 0
//                val newStateList = arrayListOf<PredState>()
//                for(a in actions.indices) {
//                    val newPreds = hashSetOf<Expr<BoolType>>()
//                    for((i, pred) in preds.withIndex()) {
//                        val lit = actLits[litIdx]
//                        val eval = model.eval(lit)
//                        if(eval.isPresent) {
//                            if(eval.get() == BoolExprs.True()) {
//                                newPreds.add(pred)
//                                feedback.add(lit.ref)
//                            } else {
//                                newPreds.add(prec.negate(pred))
//                                feedback.add(BoolExprs.Not(lit.ref))
//                            }
//                        }
//                        litIdx++
//                    }
//                    newStateList.add(PredState.of(newPreds))
//                }
//                res.add(newStateList)
//            }
//        }
//
//        return res
//    }
//
//    private fun generateActivationLiterals(n: Int) {
//        while (actLits.size < n) {
//            actLits.add(Decls.Const(litPrefix + actLits.size, BoolExprs.Bool()))
//        }
//    }
//
//}