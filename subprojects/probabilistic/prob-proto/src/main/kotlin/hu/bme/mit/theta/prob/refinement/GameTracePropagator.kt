package hu.bme.mit.theta.prob.refinement

import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.pred.ExprSplitters
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.analysis.pred.PredState
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.cfa.analysis.prec.LocalCfaPrec
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.model.ImmutableValuation
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.ExprSimplifier
import hu.bme.mit.theta.core.utils.ExprUtils
import hu.bme.mit.theta.core.utils.WpState
import hu.bme.mit.theta.prob.AbstractionGame

object gameTracePropagator: PredicatePropagator {
    override fun <LAbs : StmtAction, LConc> propagate(
        game: AbstractionGame<CfaState<PredState>, LAbs, LConc>,
        refinedState: AbstractionGame.StateNode<CfaState<PredState>, LAbs, LConc>,
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
            stateNode: AbstractionGame.StateNode<CfaState<PredState>, LAbs, LConc>,
            newPreds: Collection<Expr<BoolType>>
        ) {
            val loc = stateNode.state.loc
            val origLocalPrec = newPrec.getPrec(loc)
            val newLocalPrec = origLocalPrec.join(PredPrec.of(newPreds.map {
                ExprSimplifier.simplify(it, ImmutableValuation.empty())
            }))
            newPrec = newPrec.refine(loc, newLocalPrec)
        }
        val addedPreds = newPredicates.filter {
            // TODO: stop at first var
            val l = arrayListOf<VarDecl<*>>();
            ExprUtils.collectVars(it, l)
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
                BoolExprs.Or(stmts!!.map { wps.wep(it).expr })
            }
            currPreds = preconditions
                .flatMap(ExprSplitters.atoms()::apply)
                .toSet()
                .filter {
                    // TODO: stop at first var
                    val l = arrayListOf<VarDecl<*>>();
                    ExprUtils.collectVars(it, l)
                    l.size>0
                }
                .toList()
            refinePrec(currStateNode, currPreds)

            currStateNode = game.getFirstPredecessorEdge(predecessorEdge.start)?.start!!
        }

        return newPrec
    }

}