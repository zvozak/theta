package hu.bme.mit.theta.prob.analysis.menuabstraction

import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.analysis.pred.PredState
import hu.bme.mit.theta.analysis.pred.PredState.bottom
import hu.bme.mit.theta.core.decl.Decl
import hu.bme.mit.theta.core.decl.Decls
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.booltype.SmartBoolExprs.And
import hu.bme.mit.theta.core.type.booltype.SmartBoolExprs.Not
import hu.bme.mit.theta.core.utils.PathUtils.unfold
import hu.bme.mit.theta.core.utils.StmtUtils
import hu.bme.mit.theta.core.utils.indexings.VarIndexing
import hu.bme.mit.theta.core.utils.indexings.VarIndexingFactory
import hu.bme.mit.theta.prob.analysis.ProbabilisticCommand
import hu.bme.mit.theta.prob.analysis.addNewIndexToNonZero
import hu.bme.mit.theta.probabilistic.FiniteDistribution
import hu.bme.mit.theta.probabilistic.FiniteDistribution.Companion.dirac
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.utils.WithPushPop

class PredProbabilisticCommandTransFunc(
    val solver: Solver
) : ProbabilisticCommandTransFunc<PredState, StmtAction, PredPrec> {

    private companion object {
        var instanceCounter = 0
    }
    private val litPrefix = "__" + javaClass.simpleName + "_" + (instanceCounter++) + "_"
    private val actLits = arrayListOf<Decl<BoolType>>()

    override fun getNextStates(state: PredState, command: ProbabilisticCommand<StmtAction>, prec: PredPrec): Collection<FiniteDistribution<PredState>> {

        fun getActLit(resultIdx: Int, predIdx: Int) = actLits[resultIdx * prec.preds.size + predIdx]

        val results = arrayListOf<FiniteDistribution<PredState>>()
        val canFail = WithPushPop(solver).use {
            solver.add(unfold(state.toExpr(), 0))
            solver.add(unfold(Not(command.guard), 0))
            solver.check().isSat
        }
        if(canFail) results.add(dirac(bottom()))

        var i = 0
        val auxIndex = hashMapOf<Expr<BoolType>, Int>()
        val targetIndexing = hashMapOf<Expr<BoolType>, VarIndexing>()
        generateActivationLiterals(prec.preds.size * command.result.support.size)

        val resultExprDistr = command.result.transform {
            val stmtUnfoldResult = StmtUtils.toExpr(it.stmts, VarIndexingFactory.indexing(0))

            val expr = unfold(And(stmtUnfoldResult.exprs), 0)
            val multiIndexedExpr = addNewIndexToNonZero(expr,i)
            targetIndexing[multiIndexedExpr] = stmtUnfoldResult.indexing

            // Next state predicates with activation literals
            val activationExprs = arrayListOf<Expr<BoolType>>()
            for ((predIdx, pred) in prec.preds.withIndex()) {
                val actLit = getActLit(i, predIdx)
                val indexedPred = addNewIndexToNonZero(unfold(pred, stmtUnfoldResult.indexing), i)
                activationExprs.add(BoolExprs.Iff(indexedPred, actLit.ref))
            }

            val resultExpr = And(multiIndexedExpr, And(activationExprs))
            auxIndex[resultExpr] = i
            i++
            return@transform resultExpr
        }

        WithPushPop(solver).use {
            solver.add(unfold(And(state.toExpr(), command.guard), VarIndexingFactory.indexing(0)))
            solver.add(And(resultExprDistr.support))

            while (solver.check().isSat) {
                val model = solver.model
                val feedbackList = arrayListOf<Expr<BoolType>>()
                val result = resultExprDistr.transform { expr ->
                    val aux = auxIndex[expr]!!
                    val preds = arrayListOf<Expr<BoolType>>()
                    for ((predIdx, pred) in prec.preds.withIndex()) {
                        val actLit = getActLit(aux, predIdx)
                        val truthValue = model.eval(actLit).get()
                        preds.add(
                            if (truthValue == True()) pred else prec.negate(pred)
                        )
                        feedbackList.add(
                            if (truthValue == True()) actLit.ref else Not(actLit.ref)
                        )
                    }
                    val newState = PredState.of(preds)
                    newState
                }

                val feedback = Not(And(feedbackList))
                solver.add(feedback)

                results.add(result)
            }
        }

        return results
    }

    private fun generateActivationLiterals(n: Int) {
        while(actLits.size < n) {
                actLits.add(Decls.Const(litPrefix + actLits.size, BoolExprs.Bool()))
        }
    }
}