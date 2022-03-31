package hu.bme.mit.theta.prob

import com.google.common.base.Stopwatch
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.expl.ExplInitFunc
import hu.bme.mit.theta.analysis.expl.ExplPrec
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expr.ExprState
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
import hu.bme.mit.theta.prob.game.MDPAbstractionAnalyzer
import hu.bme.mit.theta.prob.game.ThresholdType
import hu.bme.mit.theta.prob.game.analysis.AbstractionGameAnalysis
import hu.bme.mit.theta.prob.game.analysis.OptimType
import hu.bme.mit.theta.prob.refinement.*
import hu.bme.mit.theta.prob.transfunc.BasicCartesianGroupedTransFunc
import hu.bme.mit.theta.prob.transfunc.CfaGroupedTransFunc
import hu.bme.mit.theta.prob.transfunc.ExplStmtGroupedTransFunc
import hu.bme.mit.theta.prob.transfunc.PredGroupedTransFunc
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.io.OutputStream
import java.lang.UnsupportedOperationException
import java.util.concurrent.TimeUnit

enum class RefinableSelection(val selector: RefinableStateSelector) {
    COARSEST(coarsestRefinableStateSelector),
    NEAREST(nearestRefinableStateSelector),
    RAND_COARSEST(randomizedCoarsestRefinableStateSelector)
}
enum class PrecisionLocality { GLOBAL, LOCAL }

enum class PredicatePropagation(val propagator: PredicatePropagator) {
    NONE(nonPropagatingPropagator),
    TRACE(gameTracePropagator),
    CFA(cfaEdgePropagator)
}

@Serializable
data class PCFAConfig(
    val domain: CfaConfigBuilder.Domain,
    val refinableSelection: RefinableSelection,
    val precisionLocality: PrecisionLocality,
    val predicatePropagation: PredicatePropagation,
    val thresholdType: ThresholdType,
    val propertyThreshold: Double,
    val optimType: OptimType,
    val lbe: Boolean,
    val exact: Boolean,
    val tolerance: Double,
    val limit: Int,
    val useBVI: Boolean,

    @kotlinx.serialization.Transient val output: ()->OutputStream = { System.out }
) {
    override fun toString(): String {
        return Json.encodeToString(this)
    }
}

private typealias CfaPrecRefiner<S, P> = PrecRefiner<P, CfaState<S>, CfaAction, Unit>

@Serializable
data class PCFAAnalysisStats(
    val propertyInterval: Pair<Double, Double>?,
    val analysisTime: Long,
    val timeUnit: TimeUnit


) {
    override fun toString(): String {
        return Json.encodeToString(this)
    }
}

fun handlePCFA(cfa: CFA, cfg: PCFAConfig) {
    val dataCollector = JsonDataCollector(cfg.output)

    val m = module {
        single { dataCollector as DataCollector }
        single { AbstractionGameAnalysis() }
    }
    startKoin {
        modules(m)
    }
    val analyzer = MDPAbstractionAnalyzer()

    val stopwatch = Stopwatch.createStarted()

    val solver = Z3SolverFactory.getInstance().createSolver()
    val lts = CfaSbeLts.getInstance()

    dataCollector.logGlobalData(cfg)

    fun <S: ExprState,  SubP : Prec, P : CfaPrec<SubP>> check(
        transferFunction: CfaGroupedTransFunc<S, SubP>,
        initFunc: CfaInitFunc<S, SubP>,
        initP: P, precRefiner: PrecRefiner<P, CfaState<S>, CfaAction, Unit>
    ) = if (cfg.exact) {
        analyzer.computeProb(
            transferFunction, lts, initFunc,
            initP, cfa.errorLoc.get(), cfa.finalLoc.get(),
            cfg.optimType, cfg.tolerance, precRefiner, cfg.refinableSelection.selector,
            cfg.useBVI
        )
    } else {
        analyzer.checkThresholdProperty(
            transferFunction, lts, initFunc, initP,
            cfa.errorLoc.get(), cfa.finalLoc.get(),
            cfg.optimType, cfg.propertyThreshold, cfg.thresholdType,
            precRefiner, cfg.refinableSelection.selector,
            cfg.useBVI
        )
    }

    when (cfg.domain) {
        CfaConfigBuilder.Domain.EXPL -> {
            val subTransFunc = ExplStmtGroupedTransFunc(solver, cfg.limit)
            val transFunc = CfaGroupedTransFunc(subTransFunc)
            val subInitFunc = ExplInitFunc.create(solver, BoolExprs.True())
            val initFunc = CfaInitFunc.create(cfa.initLoc, subInitFunc)
            fun <P: CfaPrec<ExplPrec>> check(initP: P, precRefiner: CfaPrecRefiner<ExplState, P>) =
                check(transFunc, initFunc, initP, precRefiner)
            val res = when(cfg.precisionLocality) {
                PrecisionLocality.GLOBAL ->
                    check(GlobalCfaPrec.create(ExplPrec.empty()), GlobalCfaExplRefiner())
                PrecisionLocality.LOCAL ->
                    throw UnsupportedOperationException()
            }
        }

        CfaConfigBuilder.Domain.PRED_BOOL, CfaConfigBuilder.Domain.PRED_CART  -> {
            val subTransFunc =
                if(cfg.domain == CfaConfigBuilder.Domain.PRED_BOOL) PredGroupedTransFunc(solver)
                else BasicCartesianGroupedTransFunc(solver)
            val transFunc = CfaGroupedTransFunc(subTransFunc)
            val subInitFunc = PredInitFunc.create(
                PredAbstractors.booleanSplitAbstractor(solver),
                BoolExprs.True()
            )
            val initFunc = CfaInitFunc.create(cfa.initLoc, subInitFunc)
            fun <P: CfaPrec<PredPrec>> check(initP: P, precRefiner: CfaPrecRefiner<PredState, P>) =
                check(transFunc, initFunc, initP, precRefiner)
            val res = when(cfg.precisionLocality) {
                PrecisionLocality.GLOBAL ->
                    check(GlobalCfaPrec.create(PredPrec.of()), GlobalCfaPredRefiner())
                PrecisionLocality.LOCAL ->
                    check(LocalCfaPrec.create(PredPrec.of()), LocalCfaPredRefiner(cfg.predicatePropagation.propagator))
            }
        }

        CfaConfigBuilder.Domain.PRED_SPLIT -> throw UnsupportedOperationException()
    }

    stopwatch.stop()
    dataCollector.logGlobalData(
        PCFAAnalysisStats(
            null,
            stopwatch.elapsed(TimeUnit.MILLISECONDS),
            TimeUnit.MILLISECONDS
        )
    )

    dataCollector.flush()
}