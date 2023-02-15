package hu.bme.mit.theta.prob.analysis.menuabstraction

import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.model.ImmutableValuation
import hu.bme.mit.theta.core.stmt.AssumeStmt
import hu.bme.mit.theta.core.stmt.Stmt
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.booltype.SmartBoolExprs
import hu.bme.mit.theta.core.type.booltype.SmartBoolExprs.And
import hu.bme.mit.theta.core.type.inttype.IntExprs
import hu.bme.mit.theta.core.type.inttype.IntType
import hu.bme.mit.theta.probabilistic.EnumeratedDistribution

class BasicStmtAction(private val _stmts: List<Stmt>) : StmtAction() {
    override fun getStmts(): List<Stmt> = _stmts
    override fun toString(): String {
        return _stmts.toString()
    }
}

fun Stmt.toAction() = BasicStmtAction(listOf(this))

fun createState(vararg v: Pair<VarDecl<IntType>, Int>): ExplState {
    val builder = ImmutableValuation.builder()
    for ((decl, value) in v) {
        builder.put(decl, IntExprs.Int(value))
    }
    return ExplState.of(builder.build())
}

fun createBoolState(vararg v: Pair<VarDecl<BoolType>, Boolean>): ExplState {
    val builder = ImmutableValuation.builder()
    for ((decl, value) in v) {
        builder.put(decl, BoolExprs.Bool(value))
    }
    return ExplState.of(builder.build())
}

fun constrainRange(vararg constraints: Pair<VarDecl<IntType>, Pair<Int, Int>>): AssumeStmt {
    val expr = And(
        constraints.map {
            val (v, r) = it; val (l, u) = r
            And(
                IntExprs.Geq(v.ref, IntExprs.Int(l)),
                IntExprs.Leq(v.ref, IntExprs.Int(u))
            )
        }
    )
    return AssumeStmt.of(expr)
}

fun Expr<BoolType>.then(vararg results: Pair<Double, Stmt>) = ProbabilisticCommand<StmtAction>(
    this, EnumeratedDistribution(results.associate { it.second.toAction() to it.first })
)