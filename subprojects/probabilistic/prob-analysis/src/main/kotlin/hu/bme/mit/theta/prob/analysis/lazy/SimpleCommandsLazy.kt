package hu.bme.mit.theta.prob.analysis.lazy

import hu.bme.mit.theta.analysis.expl.*
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
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.ExprSimplifier
import hu.bme.mit.theta.core.utils.WpState
import hu.bme.mit.theta.prob.analysis.BasicStmtAction
import hu.bme.mit.theta.prob.analysis.menuabstraction.ProbabilisticCommand
import hu.bme.mit.theta.prob.analysis.toAction
import hu.bme.mit.theta.probabilistic.FiniteDistribution
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.xta.analysis.expl.XtaExplUtils

class SimpleCommandsLazy {

    fun checkExpl(
        commands: Collection<ProbabilisticCommand<BasicStmtAction>>,
        initValuation: Valuation,
        invar: Expr<BoolType>,
        smtSolver: Solver
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
            ProbLazyAbstractionLambdas::checkContainment,
            ProbLazyAbstractionLambdas::isLeq,
            ProbLazyAbstractionLambdas::mayBeEnabled,
            { sc, a ->
                val res = concreteTransFunc.getSuccStates(sc, a, fullPrec)
                require(res.size == 1) {"Concrete trans func returned multiple successor states :-("}
                res.first()
            },
            ProbLazyAbstractionLambdas::block,
            ProbLazyAbstractionLambdas::postImage,
            ProbLazyAbstractionLambdas::preImage,
            ProbLazyAbstractionLambdas::topAfter
        )
        return checker.fullyExpandedWithSimEdges()
    }

    private object ProbLazyAbstractionLambdas {

        fun checkContainment(sc: ExplState, sa: ExplState): Boolean = ExplOrd.getInstance().isLeq(sc, sa)

        fun isLeq(sc: ExplState, sa: ExplState): Boolean = ExplOrd.getInstance().isLeq(sc, sa)

        fun mayBeEnabled(state: ExplState, command: ProbabilisticCommand<BasicStmtAction>): Boolean {
            val simplified = ExprSimplifier.simplify(command.guard, state)
            return simplified != False()
        }

        fun block(abstrState: ExplState, expr: Expr<BoolType>, concrState: ExplState): ExplState {
            require(ExplOrd.getInstance().isLeq(concrState, abstrState)) {
                "Block failed: Concrete state $concrState not contained in abstract state $abstrState!"
            }
            require(expr.eval(concrState) == BoolExprs.False()) {
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

        fun postImage(state: ExplState, action: BasicStmtAction): ExplState {
            val res = MutableValuation.copyOf(state.`val`)
            StmtApplier.apply(SequenceStmt(action.stmts), res, true)
            return ExplState.of(res)
        }

        fun preImage(state: ExplState, action: BasicStmtAction): Expr<BoolType> =
            WpState.of(state.toExpr()).wep(SequenceStmt(action.stmts)).expr

        fun topAfter(state: ExplState, action: BasicStmtAction): ExplState = ExplState.top()
    }


}