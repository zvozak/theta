package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.expl.ExplInitFunc
import hu.bme.mit.theta.analysis.expl.ExplPrec
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.pred.PredAbstractors
import hu.bme.mit.theta.analysis.pred.PredInitFunc
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.analysis.pred.PredState
import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.CfaInitFunc
import hu.bme.mit.theta.cfa.analysis.CfaPrec
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.cfa.analysis.config.CfaConfigBuilder
import hu.bme.mit.theta.cfa.analysis.lts.CfaSbeLts
import hu.bme.mit.theta.cfa.analysis.prec.GlobalCfaPrec
import hu.bme.mit.theta.cfa.analysis.prec.LocalCfaPrec
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.prob.TransferFunctions.CfaGroupedTransferFunction
import hu.bme.mit.theta.prob.TransferFunctions.ExplGroupedTransferFunction
import hu.bme.mit.theta.prob.TransferFunctions.PredGroupedTransferFunction
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import java.lang.UnsupportedOperationException

enum class RefinableSelection(val selector: RefinableStateSelector) {
    COARSEST(coarsestRefinableStateSelector),
    NEAREST(nearestRefinableStateSelector)
}
enum class PrecisionLocality { GLOBAL, LOCAL }

enum class PredicatePropagation(val propagator: PredicatePropagator) {
    TRACE(shortestTracePropagator),
    NONE(nonPropagatingPropagator)
}

data class PCFAConfig(
    val domain: CfaConfigBuilder.Domain,
    val refinableSelection: RefinableSelection,
    val precisionLocality: PrecisionLocality,
    val predicatePropagation: PredicatePropagation,
    val propertyType: PropertyType,
    val propertyThreshold: Double,
    val optimType: OptimType,
    val lbe: Boolean
)

private typealias CfaPrecRefiner<S, P> = PrecRefiner<P, CfaState<S>, CfaAction, Unit>

fun handlePCFA(cfa: CFA, cfg: PCFAConfig) {
    val solver = Z3SolverFactory.getInstance().createSolver()
    val lts = CfaSbeLts.getInstance()
    when (cfg.domain) {
        CfaConfigBuilder.Domain.EXPL -> {
            val subTransFunc = ExplGroupedTransferFunction(solver)
            val transFunc = CfaGroupedTransferFunction(subTransFunc)
            val subInitFunc = ExplInitFunc.create(solver, BoolExprs.True())
            val initFunc = CfaInitFunc.create(cfa.initLoc, subInitFunc)
            fun <P: CfaPrec<ExplPrec>> check(initP: P, precRefiner: CfaPrecRefiner<ExplState, P>) =
                checkPCFA(
                    transFunc, lts, initFunc, initP,
                    cfa.errorLoc.get(), cfa.finalLoc.get(),
                    cfg.optimType, cfg.propertyThreshold, cfg.propertyType,
                    precRefiner, cfg.refinableSelection.selector
                )
            val res = when(cfg.precisionLocality) {
                PrecisionLocality.GLOBAL ->
                    check(GlobalCfaPrec.create(ExplPrec.empty()), GlobalCfaExplRefiner())
                PrecisionLocality.LOCAL ->
                    throw UnsupportedOperationException()
            }
        }

        CfaConfigBuilder.Domain.PRED_BOOL -> {
            val subTransFunc = PredGroupedTransferFunction(solver)
            val transFunc = CfaGroupedTransferFunction(subTransFunc)
            val subInitFunc = PredInitFunc.create(
                PredAbstractors.booleanSplitAbstractor(solver),
                BoolExprs.True()
            )
            val initFunc = CfaInitFunc.create(cfa.initLoc, subInitFunc)
            fun <P: CfaPrec<PredPrec>> check(initP: P, precRefiner: CfaPrecRefiner<PredState, P>) =
                checkPCFA(
                    transFunc, lts, initFunc, initP,
                    cfa.errorLoc.get(), cfa.finalLoc.get(),
                    cfg.optimType, cfg.propertyThreshold, cfg.propertyType,
                    precRefiner, cfg.refinableSelection.selector
                )
            val res = when(cfg.precisionLocality) {
                PrecisionLocality.GLOBAL ->
                    check(GlobalCfaPrec.create(PredPrec.of()), GlobalCfaPredRefiner())
                PrecisionLocality.LOCAL ->
                    check(LocalCfaPrec.create(PredPrec.of()), LocalCfaPredRefiner(cfg.predicatePropagation.propagator))
            }
        }

        CfaConfigBuilder.Domain.PRED_CART -> throw UnsupportedOperationException()
        CfaConfigBuilder.Domain.PRED_SPLIT -> throw UnsupportedOperationException()
    }
}