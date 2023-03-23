package hu.bme.mit.theta.prob.analysis.menuabstraction

import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.analysis.pred.PredState
import hu.bme.mit.theta.analysis.pred.PredState.bottom
import hu.bme.mit.theta.core.decl.Decls
import hu.bme.mit.theta.core.stmt.Stmts
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Not
import hu.bme.mit.theta.core.type.inttype.IntExprs.*
import hu.bme.mit.theta.prob.analysis.toAction
import hu.bme.mit.theta.probabilistic.FiniteDistribution
import hu.bme.mit.theta.probabilistic.FiniteDistribution.Companion.dirac
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test


class PredProbabilisticTransFuncTest {
    lateinit var solver: Solver
    lateinit var transFunc: PredProbabilisticCommandTransFunc

    val A = Decls.Var("A", Int())
    val B = Decls.Var("B", Int())
    val C = Decls.Var("C", Int())

    @Before
    fun initEach() {
        solver = Z3SolverFactory.getInstance().createSolver()
        transFunc = PredProbabilisticCommandTransFunc(solver)
    }

    @Test
    fun simpleDeterministicCommandTest() {
        val command = ProbabilisticCommand<StmtAction>(
            BoolExprs.True(), dirac(
                Stmts.Assign(A, Add(A.ref, Int(1))).toAction()
            )
        )

        val p = Lt(A.ref, Int(1))
        check(
            createPredState(p),
            command, PredPrec.of(p),
            setOf(
                dirac(createPredState(p)),
                dirac(createPredState(Not(p)))
            )
        )

        val p1 = Eq(A.ref, Int(0))
        check(
            createPredState(p, p1),
            command, PredPrec.of(listOf(p, p1)),
            setOf(dirac(createPredState(Not(p), Not(p1))))
        )
    }

    @Test
    fun simpleProbCommandTest() {
        val command = ProbabilisticCommand<StmtAction>(
            BoolExprs.True(), FiniteDistribution(
                mapOf(
                    Stmts.Assign(A, Add(A.ref, Int(1))).toAction() to 0.2,
                    Stmts.Assign(A, Add(A.ref, Int(2))).toAction() to 0.8,
                )
            )
        )

        val p = Lt(A.ref, Int(1))
        check(
            createPredState(p),
            command, PredPrec.of(p),
            setOf(
                dirac(createPredState(p)),
                FiniteDistribution(
                    createPredState(p) to 0.2,
                    createPredState(Not(p)) to 0.8
                ),
                dirac(createPredState(Not(p)))
            )
        )

        val q0 = Eq(A.ref, Int(0))
        val q1 = Eq(A.ref, Int(1))
        check(
            createPredState(p, q0),
            command, PredPrec.of(listOf(p, q1)),
            setOf(
                FiniteDistribution(
                    createPredState(Not(p), q1) to 0.2,
                    createPredState(Not(p), Not(q1)) to 0.8
                )
            )
        )
    }

    @Test
    fun guardTest() {
        val command = ProbabilisticCommand<StmtAction>(
            Leq(A.ref, Int(2)), dirac(
                Stmts.Assign(A, Int(2)).toAction()
            )
        )

        val p = Lt(A.ref, Int(1))
        check(
            createPredState(p),
            command, PredPrec.of(p),
            setOf(dirac(createPredState(Not(p))))
        )

        val q = Gt(A.ref, Int(3))
        check(
            createPredState(q),
            command, PredPrec.of(q),
            setOf(dirac(bottom()))
        )

        val r = Leq(A.ref, Int(3))
        check(
            createPredState(r),
            command, PredPrec.of(listOf(p, q, r)),
            setOf(
                dirac(bottom()),
                dirac(createPredState(Not(p), Not(q), r))
            )
        )
    }

    @Test
    fun complexCommandTest() {
        //[2 <= C+B <= 3]
        //0.2: A += C
        //0.8: A := 1

        val CplusB = Add(C.ref, B.ref)
        val guard = BoolExprs.And(
            listOf(
                Geq(CplusB, Int(2)),
                Leq(CplusB, Int(3)),
            )
        )
        val command = ProbabilisticCommand<StmtAction>(
            guard, FiniteDistribution(
                Stmts.Assign(A, Add(A.ref, C.ref)).toAction() to 0.2,
                Stmts.Assign(A, Int(1)).toAction() to 0.8
            )
        )

        val p = Leq(C.ref, Int(2))
        val q = Gt(C.ref, B.ref)
        val r = Lt(A.ref, Int(1))

        check(
            createPredState(p, q, r),
            command, PredPrec.of(listOf(p, q, r)),
            setOf(
                dirac(bottom()), // B might be negative, violating the guard
                // guard is true => (B, C)={(2, 0), (2, 1)}
                dirac(createPredState(p, q, Not(r))),
                FiniteDistribution(
                    createPredState(p, q, r) to 0.2, // if C = 0
                    createPredState(p, q, Not(r)) to 0.8
                )
            )
        )
    }

    private fun check(
        initState: PredState,
        command: ProbabilisticCommand<StmtAction>,
        prec: PredPrec,
        expected: Set<FiniteDistribution<PredState>>
    ) {
        val result = transFunc.getNextStates(initState, command, prec)
        assertEquals(expected, result.toSet())
    }
}