package hu.bme.mit.theta.prob

import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.CFA.Loc
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.stmt.AssignStmt
import hu.bme.mit.theta.core.stmt.Stmt
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.decl.Decls.Var
import hu.bme.mit.theta.core.stmt.AssumeStmt
import hu.bme.mit.theta.core.type.Type
import hu.bme.mit.theta.core.type.anytype.Exprs.Ref
import hu.bme.mit.theta.core.type.booltype.*
import hu.bme.mit.theta.core.type.booltype.BoolExprs.*
import hu.bme.mit.theta.core.type.inttype.*
import hu.bme.mit.theta.core.type.inttype.IntExprs.*

fun CFA(func: CFA.Builder.() -> Unit): CFA {
    val builder = CFA.builder()
    builder.func()
    return builder.build()
}

fun CFA.Builder.edge(src: Loc, trgt: Loc, stmt: Stmt) = this.createEdge(src, trgt, stmt)
fun CFA.Builder.loc(name: String) = this.createLoc(name)
fun CFA.Builder.init(loc: Loc): Loc { this.initLoc = loc; return loc }
fun CFA.Builder.error(loc: Loc): Loc { this.errorLoc = loc; return loc }
fun CFA.Builder.final(loc: Loc): Loc { this.finalLoc = loc; return loc }
fun int(name: String): VarDecl<IntType> = Var(name, Int())
fun bool(name: String): VarDecl<BoolType> = Var(name, Bool())
fun assume(expr: Expr<BoolType>): AssumeStmt = AssumeStmt.of(expr)
fun assume(varDecl: VarDecl<BoolType>): AssumeStmt = AssumeStmt.of(Ref(varDecl))
fun not(expr: Expr<BoolType>): NotExpr = BoolExprs.Not(expr)

infix fun VarDecl<IntType>.lt(i: Int) = Lt(Ref(this), Int(i))
infix fun VarDecl<IntType>.lt(other: VarDecl<IntType>) = Lt(Ref(this), Ref(other))
infix fun VarDecl<IntType>.lt(expr: Expr<IntType>) = Lt(Ref(this), expr)

infix fun VarDecl<IntType>.leq(i: Int) = Leq(Ref(this), Int(i))
infix fun VarDecl<IntType>.leq(other: VarDecl<IntType>) = Leq(Ref(this), Ref(other))
infix fun VarDecl<IntType>.leq(expr: Expr<IntType>) = Leq(Ref(this), expr)

infix fun VarDecl<IntType>.gt(i: Int) = Gt(Ref(this), Int(i))
infix fun VarDecl<IntType>.gt(other: VarDecl<IntType>) = Gt(Ref(this), Ref(other))
infix fun VarDecl<IntType>.gt(expr: Expr<IntType>) = Gt(Ref(this), expr)

infix fun VarDecl<IntType>.geq(i: Int) = Geq(Ref(this), Int(i))
infix fun VarDecl<IntType>.geq(other: VarDecl<IntType>) = Geq(Ref(this), Ref(other))
infix fun VarDecl<IntType>.geq(expr: Expr<IntType>) = Geq(Ref(this), expr)

infix fun VarDecl<IntType>.eq(expr: Expr<IntType>) = Eq(Ref(this), expr)
infix fun VarDecl<IntType>.eq(i: Int) = IntExprs.Eq(Ref(this), Int(i))
infix fun VarDecl<IntType>.eq(varDecl: VarDecl<IntType>) = Eq(Ref(this), Ref(varDecl))

operator fun VarDecl<IntType>.plus(other: VarDecl<IntType>): IntAddExpr = Add(
    listOf(Ref(this), Ref(other))
)
operator fun VarDecl<IntType>.plus(other: Int): IntAddExpr = Add(
    listOf(Ref(this), Int(other))
)


operator fun VarDecl<IntType>.minus(other: VarDecl<IntType>): IntAddExpr = Add(
    listOf(Ref(this), Neg(Ref(other)))
)
operator fun VarDecl<IntType>.unaryMinus(): IntNegExpr = Neg(Ref(this))


infix fun <T: Type> VarDecl<T>.assign(expr: Expr<T>): AssignStmt<T> = AssignStmt.of(this, expr)
infix fun <T: Type> VarDecl<T>.assign(varDecl: VarDecl<T>): AssignStmt<T> = AssignStmt.of(this, Ref(varDecl))
infix fun VarDecl<IntType>.assign(i: Int): AssignStmt<IntType> = AssignStmt.of(this, Int(i))

class SourceWithStatement(val src: Loc, stmt: Stmt)
operator fun SourceWithStatement.minus(trgt: Loc) {
}

infix fun Expr<BoolType>.and(other: Expr<BoolType>): AndExpr = BoolExprs.And(this, other)
infix fun Expr<BoolType>.or(other: Expr<BoolType>): OrExpr = BoolExprs.Or(this, other)

fun prob(distr: EnumeratedDistribution<Stmt, Void>): ProbStmt = ProbStmt(distr)
fun coin(v: VarDecl<BoolType>, e1: Double, e2: Double): ProbStmt =
    ProbStmt(EnumeratedDistribution(
        listOf( (v assign True()) to (e1/(e1+e2)), (v assign False()) to (e2/(e1+e2)) )
    ))
fun uniform(v: VarDecl<IntType>, n: Int): ProbStmt =
    ProbStmt(EnumeratedDistribution(
        (0 until n).map { (v assign it) to (1.0/n) }
    ))

fun CFA.Builder.seq(start: Loc, vararg stmts: Stmt): Loc {
    var i = 0
    var currLoc = start
    var nextLoc = currLoc
    for (stmt in stmts) {
        nextLoc = loc("L${i++}")
        edge(currLoc, nextLoc, stmt)
        currLoc = nextLoc
    }
    return nextLoc
}
fun CFA.Builder.seq(vararg stmts: Stmt): Loc = seq(init(loc("Start")), *stmts)

fun CFA.Builder.branch(start: Loc, endT: Loc, endF: Loc, cond: Expr<BoolType>) {
    edge(start, endT, assume(cond))
    edge(start, endF, assume(not(cond)))
}

fun CFA.Builder.finalAssert(at: Loc, cond: Expr<BoolType>) =
    branch(at, final(loc("Final")), error(loc("Error")), cond)

val test = CFA {
    val v1 = int("v1")
    val v2 = int("v2")
    val v3 = int("v3")

    val p = seq(
        v1 assign 1,
        uniform(v2, 2),
        v3 assign v1+v2
    )
    finalAssert(p, v3 eq 3)

//    val start = init(loc("A"))
//    val b = loc("B")
//    val c = loc("C")
//    val d = loc("D")
//    val fin = final(loc("Final"))
//    val err = error(loc("Error"))
//
//    edge(start, b, v1 assign 1)
//
//    edge(b, c, uniform(v2, 2))
//
//    edge(c, d, v3 assign v1+v2)
//    edge(d, fin, assume(v3 eq 3))
//    edge(d, err, assume(not(v3 eq 3)))
}