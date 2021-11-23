package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.pred.PredAbstractors
import hu.bme.mit.theta.analysis.pred.PredInitFunc
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.cfa.analysis.CfaInitFunc
import hu.bme.mit.theta.cfa.analysis.lts.CfaSbeLts
import hu.bme.mit.theta.cfa.analysis.prec.GlobalCfaPrec
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.prob.*
import hu.bme.mit.theta.solver.z3.Z3SolverFactory

fun main() {
    OTFMDPAbstractionTests().justSomeTest()
}

class OTFMDPAbstractionTests {
    fun justSomeTest() {
        val v1 = int("v1")
        val v2 = int("v2")
        val v3 = int("v3")

//        val model = CFA {
//            val p = seq(
//                v1 assign 1,
//                uniform(v2, 3), // {0,1,2}
//                v3 assign v1 + v2
//            )
//            finalAssert(p, v3 eq 3)
//        }
//        val testPredList = listOf(v1 eq 1, v2 eq 2, v3 eq 3)

        // Loop with probabilistic exit
        val model = CFA {
            val s = init(loc("Start"))
            val a = loc("A")
            edge(s, a, v1 assign 1)
            val p = seq(a,
                v1 assign v1 + 1,
                uniform(v2, 2),
            )
            val stop = loc("Stop")
            branch(p, a, stop, v2 eq 0)
            finalAssert(stop, v1 lt 5)
        }
        val testPredList = listOf(
            v1 eq 1,
            v1 eq 2,
            v1 eq 3,
            v1 eq 4,
            v1 lt 5,
            v2 eq 0,
            v2 eq 1,
        )

        val solver = Z3SolverFactory.getInstance().createSolver()

        val subInitFunc = PredInitFunc.create(
            PredAbstractors.booleanSplitAbstractor(solver),
            BoolExprs.True()
        )
        val initFunc = CfaInitFunc.create(model.initLoc, subInitFunc)
        val subTransFunc = PredGroupedTransferFunction(solver)
        val transFunc = CfaGroupedTransferFunction(subTransFunc)
        val lts = CfaSbeLts.getInstance()
        val initPrec = GlobalCfaPrec.create(PredPrec.of())

//        val testPrec = GlobalCfaPrec.create(PredPrec.of(testPredList))
        val testPrec = GlobalCfaPrec.create(PredPrec.of())

//        val testInit = initFunc.getInitStates(testPrec)
//
//        val testNext = testInit.map {
//            val action = lts.getEnabledActionsFor(it).first()
//            transFunc.getSuccStates(it, action, testPrec)
//        }
//
//        val s = testNext[0][0][0]
//        val act = lts.getEnabledActionsFor(s).first()
//        val nexts = transFunc.getSuccStates(s, act, testPrec)

        val game = computeGameAbstraction(
            initFunc, lts, transFunc, testPrec
        ).first

        val checkResult = checkPCFA(
            transFunc, lts, initFunc,
            testPrec,
            model.errorLoc.get(), model.finalLoc.get(), OptimType.MAX,
            0.1, PropertyType.LESS_THAN,
            nearestRefinableStateSelector
        )
    }
}