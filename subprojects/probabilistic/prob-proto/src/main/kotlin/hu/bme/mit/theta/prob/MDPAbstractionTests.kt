package hu.bme.mit.theta.prob

import hu.bme.mit.theta.analysis.expl.ExplInitFunc
import hu.bme.mit.theta.analysis.expl.ExplPrec
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expl.ExplStmtOptimizer
import hu.bme.mit.theta.analysis.expl.ExplStmtTransFunc
import hu.bme.mit.theta.analysis.pred.PredAbstractors
import hu.bme.mit.theta.analysis.pred.PredInitFunc
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.analysis.stmtoptimizer.StmtOptimizer
import hu.bme.mit.theta.cfa.analysis.CfaInitFunc
import hu.bme.mit.theta.cfa.analysis.lts.CfaSbeLts
import hu.bme.mit.theta.cfa.analysis.prec.GlobalCfaPrec
import hu.bme.mit.theta.cfa.analysis.prec.LocalCfaPrec
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.interchange.jani.model.Model
import hu.bme.mit.theta.interchange.jani.model.json.JaniModelMapper
import hu.bme.mit.theta.prob.formalism.SmdpInitFunc
import hu.bme.mit.theta.prob.formalism.SmdpLts
import hu.bme.mit.theta.prob.formalism.SmdpTransFunc
import hu.bme.mit.theta.prob.formalism.toSMDP
import hu.bme.mit.theta.prob.game.StochasticGame
import hu.bme.mit.theta.prob.game.StochasticGame.Companion.Player
import hu.bme.mit.theta.prob.game.analysis.OptimType
import hu.bme.mit.theta.prob.pcfa.*
import hu.bme.mit.theta.prob.transfunc.CfaGroupedTransFunc
import hu.bme.mit.theta.prob.transfunc.ExplStmtGroupedTransFunc
import hu.bme.mit.theta.prob.transfunc.PredGroupedTransFunc
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths


fun main() {
    val p = Paths.get("subprojects","probabilistic", "prob-proto", "models", "jamini.jani")
//    val p = Paths.get("E:\\egyetem\\dipterv\\qcomp\\benchmarks\\mdp\\beb\\beb.3-4.jani")
    val file = p.toFile()
    val model = JaniModelMapper().readValue(file, Model::class.java)

    val solver = Z3SolverFactory.getInstance().createSolver()

    val smdp = model.toSMDP()
    val domainInitFunc = ExplInitFunc.create(solver, smdp.getFullInitExpr())
    val initFunc = SmdpInitFunc(domainInitFunc, smdp)
    val lts = SmdpLts<ExplState>(smdp, ExplStmtOptimizer.getInstance())
    val domainTransFunc = ExplStmtGroupedTransFunc(solver, 0)
    val stmtOptimizer = ExplStmtOptimizer.getInstance()
    val transFunc = SmdpTransFunc(domainTransFunc, stmtOptimizer) {it}
}
fun mainOld() {

    lateinit var err: StochasticGame<String, String, Unit, Unit>.Node
    val testGame = StochasticGame<String, String, Unit, Unit>().apply {
        val init = ANode("Init")

        val A1 = CNode("A1")
        val A2 = ANode("A2")
        val A3 = CNode("A3")

        val B1 = CNode("B1")
        val B2 = ANode("B2")
        val B3 = CNode("B3")
        val B4 = ANode("B4")

        val C1 = ANode("C1")
        val C2 = CNode("C2")
        val C3 = ANode("C3")
        val C4 = CNode("C4")

        err = ANode("Err")
        val fin = ANode("Fin")

        edge(Unit, init, mapOf(A1 to 1.0))
        edge(Unit, init, mapOf(B1 to 1.0))

        edge(Unit, A1, mapOf(A2 to 1.0))
        edge(Unit, A2, mapOf(C2 to 0.2, A3 to 0.8))
        edge(Unit, A3, mapOf(A1 to 1.0))
        edge(Unit, A3, mapOf(C1 to 1.0))

        edge(Unit, B1, mapOf(B2 to 0.2, B3 to 0.8))
        edge(Unit, B2, mapOf(B3 to 1.0))
        edge(Unit, B2, mapOf(C4 to 1.0))
        edge(Unit, B3, mapOf(C4 to 0.9, fin to 0.1))
        edge(Unit, B3, mapOf(B4 to 1.0))
        edge(Unit, B4, mapOf(B1 to 1.0))
        edge(Unit, B4, mapOf(B2 to 1.0))

        edge(Unit, C1, mapOf(C2 to 1.0))
        edge(Unit, C1, mapOf(C4 to 1.0))
        edge(Unit, C2, mapOf(C3 to 1.0))
        edge(Unit, C4, mapOf(C3 to 1.0))
        edge(Unit, C3, mapOf(C1 to 1.0))
        edge(Unit, C3, mapOf(err to 0.5, fin to 0.5))
    }

    val nodes = testGame.allNodes
    val ergEdges = nodes.map {
        it.outEdges.flatMap { it.end.keys.map { nodes.indexOf(it) } }
    }
    val sccs = testGame.computeSCCs(nodes, ergEdges)

    testGame.addSelfLoops()
    val V1 = testGame.TVI({OptimType.MIN}, nodes.associateWith { if(it == err) 1.0 else 0.0 }, 1e-6)
    val V2 = testGame.TVI({OptimType.MAX}, nodes.associateWith { if(it == err) 1.0 else 0.0 }, 1e-6)
    val V3 = testGame.TVI({if (it == Player.A) OptimType.MIN else OptimType.MAX},
        nodes.associateWith { if(it == err) 1.0 else 0.0 }, 1e-6)
    val V4 = testGame.TVI({if (it == Player.A) OptimType.MAX else OptimType.MIN},
        nodes.associateWith { if(it == err) 1.0 else 0.0 }, 1e-6)
//    OTFMDPAbstractionTests().justSomeTest()
}

class OTFMDPAbstractionTests {
    fun justSomeTest() {
        val v1 = int("v1")
        val v2 = int("v2")
        val v3 = int("v3")

//        val model = CFA {
//            val start = init(loc("Start"))
//            val p = seq( start,
//                v1 assign 0,
//                v2 assign 0
//            )
//            val a = loc("A")
//            val b = loc("B")
//            branch(p, a, b, v2 leq v1)
//        }

        val model = CFA {
            val p = seq(
                v1 assign 1,
                uniform(v2, 3), // {0,1,2}
                v3 assign v1 + v2
            )
            finalAssert(p, v3 eq 3)
        }
        val testPredList = listOf(v1 eq 1, v2 eq 2, v3 eq 3)

        // Loop with probabilistic exit
//        val model = CFA {
//            val s = init(loc("Start"))
//            val a = loc("A")
//            edge(s, a, v1 assign 1)
//            val p = seq(a,
//                v1 assign v1 + 1,
//                uniform(v2, 2),
//            )
//            val stop = loc("Stop")
//            branch(p, a, stop, v2 eq 0)
//            finalAssert(stop, v1 lt 5)
//        }
//        val testPredList = listOf(
//            v1 eq 1,
//            v1 eq 2,
//            v1 eq 3,
//            v1 eq 4,
//            v1 lt 5,
//            v2 eq 0,
//            v2 eq 1,
//        )

        val solver = Z3SolverFactory.getInstance().createSolver()

        val subInitFunc = PredInitFunc.create(
            PredAbstractors.booleanSplitAbstractor(solver),
            BoolExprs.True()
        )
        val initFunc = CfaInitFunc.create(model.initLoc, subInitFunc)


        val subTransFunc = PredGroupedTransFunc(solver)
        val transFunc = CfaGroupedTransFunc(subTransFunc)
        val lts = CfaSbeLts.getInstance()
        val initPrec = GlobalCfaPrec.create(PredPrec.of())

//        val testPrec = GlobalCfaPrec.create(PredPrec.of(testPredList))
//        val testPrec = GlobalCfaPrec.create(PredPrec.of())
        val testPrec = LocalCfaPrec.create(PredPrec.of())

        val explSubInitFunc = ExplInitFunc.create(solver, BoolExprs.True())
        val explInitFunc = CfaInitFunc.create(model.initLoc, explSubInitFunc)
        val explSubTransFunc = ExplStmtGroupedTransFunc(solver)
        val explTransFunc = CfaGroupedTransFunc(explSubTransFunc)
        val explInitPrec = GlobalCfaPrec.create(ExplPrec.empty())


//        val checkResult = checkThresholdProperty(
//            explTransFunc, lts, explInitFunc,
//            explInitPrec,
//            model.errorLoc.get(), model.finalLoc.get(), OptimType.MAX,
//            0.1, ThresholdType.LESS_THAN,
//            GlobalCfaExplRefiner(), nearestRefinableStateSelector,
//            true
//        )

    }
}