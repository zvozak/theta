package hu.bme.mit.theta.prob.refinement

import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.pred.ExprSplitters
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.analysis.pred.PredState
import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.cfa.analysis.prec.LocalCfaPrec
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.model.ImmutableValuation
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.ExprSimplifier
import hu.bme.mit.theta.core.utils.ExprUtils
import hu.bme.mit.theta.core.utils.WpState
import hu.bme.mit.theta.prob.game.AbstractionGame

object cfaEdgePropagator: PredicatePropagator {
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

        val addedPreds = hashMapOf<CFA.Loc, List<Expr<BoolType>>>()
        var newPrec = origPrecision
        fun refinePrec(
            loc: CFA.Loc,
            newPreds: List<Expr<BoolType>>
        ) {
            val origLocalPrec = newPrec.getPrec(loc)
            val newLocalPrec = origLocalPrec.join(PredPrec.of(newPreds.map {
                ExprSimplifier.simplify(it, ImmutableValuation.empty())
            }))
            addedPreds[loc] = newPreds
            newPrec = newPrec.refine(loc, newLocalPrec)
        }
        val nonConstantPreds = newPredicates.filter {
            // TODO: stop at first var
            val l = arrayListOf<VarDecl<*>>();
            ExprUtils.collectVars(it, l)
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
                        ExprUtils.collectVars(it, l)
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