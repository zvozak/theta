package hu.bme.mit.theta.prob.analysis.lazy

import hu.bme.mit.theta.analysis.expl.*
import hu.bme.mit.theta.analysis.pred.PredOrd
import hu.bme.mit.theta.analysis.pred.PredState
import hu.bme.mit.theta.common.container.Containers
import hu.bme.mit.theta.core.decl.Decl
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.model.ImmutableValuation
import hu.bme.mit.theta.core.model.MutableValuation
import hu.bme.mit.theta.core.model.Valuation
import hu.bme.mit.theta.core.stmt.Stmts
import hu.bme.mit.theta.core.stmt.Stmts.SequenceStmt
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolExprs.False
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.booltype.SmartBoolExprs
import hu.bme.mit.theta.core.utils.ExprSimplifier
import hu.bme.mit.theta.core.utils.PathUtils
import hu.bme.mit.theta.core.utils.WpState
import hu.bme.mit.theta.prob.analysis.BasicStmtAction
import hu.bme.mit.theta.prob.analysis.menuabstraction.ProbabilisticCommand
import hu.bme.mit.theta.prob.analysis.toAction
import hu.bme.mit.theta.probabilistic.FiniteDistribution
import hu.bme.mit.theta.probabilistic.Goal
import hu.bme.mit.theta.solver.ItpSolver
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.utils.WithPushPop
import hu.bme.mit.theta.xta.analysis.expl.XtaExplUtils

class SimpleCommandsLazy(
    val smtSolver: Solver,
    val itpSolver: ItpSolver
) {

    fun checkExpl(
        commands: Collection<ProbabilisticCommand<BasicStmtAction>>,
        initValuation: Valuation,
        invar: Expr<BoolType>,
        goal: Goal
    ): Double {
        val errorCommands = listOf(ProbabilisticCommand(
            BoolExprs.Not(invar), FiniteDistribution.dirac(Stmts.Skip().toAction())
        ))
        val concreteTransFunc = ExplStmtTransFunc.create(smtSolver, 0)
        val vars = initValuation.decls.filterIsInstance<VarDecl<*>>()
        val fullPrec = ExplPrec.of(vars)
        val checker = ProbLazyAbstraction(
            {commands}, {errorCommands},
            ExplState.of(initValuation), ExplState.top(),
            ExplDomain::checkContainment,
            ExplDomain::isLeq,
            ExplDomain::mayBeEnabled,
            ExplDomain::isEnabled,
            { sc, a ->
                val res = concreteTransFunc.getSuccStates(sc, a, fullPrec)
                require(res.size == 1) {"Concrete trans func returned multiple successor states :-("}
                res.first()
            },
            ExplDomain::block,
            ExplDomain::postImage,
            ExplDomain::preImage,
            ExplDomain::topAfter,
            goal
        )
        return checker.fullyExpandedWithSimEdges()
    }

    private object ExplDomain {

        fun checkContainment(sc: ExplState, sa: ExplState): Boolean = ExplOrd.getInstance().isLeq(sc, sa)

        fun isLeq(sa1: ExplState, sa2: ExplState): Boolean = ExplOrd.getInstance().isLeq(sa1, sa2)

        fun mayBeEnabled(state: ExplState, command: ProbabilisticCommand<BasicStmtAction>): Boolean {
            val simplified = ExprSimplifier.simplify(command.guard, state)
            return simplified != False()
        }

        fun block(abstrState: ExplState, expr: Expr<BoolType>, concrState: ExplState): ExplState {
            require(ExplOrd.getInstance().isLeq(concrState, abstrState)) {
                "Block failed: Concrete state $concrState not contained in abstract state $abstrState!"
            }
            require(expr.eval(concrState) == False()) {
                "Block failed: Concrete state $concrState does not contradict $expr"
            }

            // Using backward strategy
            val valI = XtaExplUtils.interpolate(concrState, expr)

            val newVars: MutableCollection<Decl<*>> = Containers.createSet()
            newVars.addAll(valI.getDecls())
            newVars.addAll(abstrState.getDecls())
            val builder = ImmutableValuation.builder()
            for (decl in newVars) {
                builder.put(decl, concrState.eval(decl).get())
            }
            val `val`: Valuation = builder.build()
            val newAbstractExpl = ExplState.of(`val`)

            return newAbstractExpl
        }

        fun postImage(state: ExplState, action: BasicStmtAction, guard: Expr<BoolType>): ExplState {
            val res = MutableValuation.copyOf(state.`val`)
            val stmts = listOf(Stmts.Assume(guard))+action.stmts
            StmtApplier.apply(SequenceStmt(stmts), res, true)
            return ExplState.of(res)
        }

        fun preImage(state: ExplState, action: BasicStmtAction): Expr<BoolType> =
            WpState.of(state.toExpr()).wep(SequenceStmt(action.stmts)).expr

        fun topAfter(state: ExplState, action: BasicStmtAction): ExplState = ExplState.top()

        fun isEnabled(s: ExplState, probabilisticCommand: ProbabilisticCommand<*>): Boolean {
            return probabilisticCommand.guard.eval(s) == True()
        }

    }

    private val PredDomain = object {

        fun checkContainment(sc: ExplState, sa: PredState): Boolean {
            val res = sa.toExpr().eval(sc)
            return if(res == True()) true
            else if(res == False()) false
            else throw IllegalArgumentException("concrete state must be a full valuation")
        }

        fun isLeq(sa1: PredState, sa2: PredState): Boolean = PredOrd.create(smtSolver).isLeq(sa1, sa2)

        fun mayBeEnabled(state: PredState, command: ProbabilisticCommand<BasicStmtAction>): Boolean {
            WithPushPop(smtSolver).use {
                smtSolver.add(PathUtils.unfold(state.toExpr(), 0))
                smtSolver.add(PathUtils.unfold(command.guard, 0))
                return smtSolver.check().isSat
            }
        }

        fun block(abstrState: PredState, expr: Expr<BoolType>, concrState: ExplState): PredState {
            require(checkContainment(concrState, abstrState)) {
                "Block failed: Concrete state $concrState not contained in abstract state $abstrState!"
            }
            require(expr.eval(concrState) == False()) {
                "Block failed: Concrete state $concrState does not contradict $expr"
            }

            lateinit var itp: Expr<BoolType>
            WithPushPop(itpSolver).use {
                val A = itpSolver.createMarker()
                val B = itpSolver.createMarker()
                itpSolver.add(A, PathUtils.unfold(concrState.toExpr(), 0))
                itpSolver.add(B, PathUtils.unfold(expr, 0))
                val pattern = itpSolver.createBinPattern(A, B)
                itp = itpSolver.getInterpolant(pattern).eval(A)
            }

            val newAbstract =
                PredState.of(SmartBoolExprs.And(abstrState.toExpr(), PathUtils.foldin(itp, 0)))

            return newAbstract
        }

        fun postImage(state: PredState, action: BasicStmtAction, guard: Expr<BoolType>): PredState {
            TODO("do this somehow")
            val exprs = listOf(
                state.toExpr(),
                guard,
                action.toExpr()
            )
            val prevExpr = PathUtils.unfold(state.toExpr(), 0)
        }

        fun preImage(state: PredState, action: BasicStmtAction): Expr<BoolType> =
            WpState.of(state.toExpr()).wep(SequenceStmt(action.stmts)).expr

        fun topAfter(state: ExplState, action: BasicStmtAction): PredState = PredState.of()

        fun isEnabled(s: ExplState, probabilisticCommand: ProbabilisticCommand<*>): Boolean {
            return probabilisticCommand.guard.eval(s) == True()
        }

    }
}