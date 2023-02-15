import hu.bme.mit.theta.core.decl.Decls
import hu.bme.mit.theta.core.type.anytype.Exprs.*
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.inttype.IntExprs
import hu.bme.mit.theta.core.type.inttype.IntExprs.Eq
import hu.bme.mit.theta.core.type.inttype.IntExprs.Int
import hu.bme.mit.theta.core.utils.PathUtils
import hu.bme.mit.theta.core.utils.indexings.VarIndexingFactory
import hu.bme.mit.theta.prob.analysis.addNewIndexToNonZero
import hu.bme.mit.theta.prob.analysis.extractMultiIndexValuation
import hu.bme.mit.theta.solver.utils.WithPushPop
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisUtilsTest {

    @Test
    fun multiIndexTest() {
        val solver = Z3SolverFactory.getInstance().createSolver()
        val A = Decls.Var("A", Int())
        val a00 = A.getConstDecl(listOf(0, 0))

        // Checking object equality
        assert(a00 == A.getConstDecl(listOf(0, 0)))

        val a01 = A.getConstDecl(listOf(0, 1))
        val a10 = A.getConstDecl(listOf(1, 0))
        val a11 = A.getConstDecl(listOf(1, 1))

        // Checking that both indices matter when checking equality
        assert(a00 != a01)
        assert(a00 != a10)

        val expr = BoolExprs.And(
            listOf(
                Eq(a00.ref, Int(0)),
                Eq(a01.ref, Int(1)),
                Eq(a10.ref, Int(2)),
                Eq(a11.ref, Int(3))
            )
        )
        WithPushPop(solver).use {
            solver.add(expr)
            // This expression must be sat, as different multiindex consts
            // must be treated separately
            assert(solver.check().isSat)
            val model = solver.model
            assertEquals(Int(0), model.eval(a00).get())
            assertEquals(Int(1), model.eval(a01).get())
            assertEquals(Int(2), model.eval(a10).get())
            assertEquals(Int(3), model.eval(a11).get())
        }
    }

    @Test
    fun addNewIndexToNonZeroTest() {
        val A = Decls.Var("A", Int())
        val B = Decls.Var("B", Int())
        val origVarExpr =
            BoolExprs.And(listOf(
                Eq(Prime(A.ref), IntExprs.Add(A.ref, Int(1))),
                Eq(Prime(B.ref), IntExprs.Add(A.ref, Int(2))),
                Eq(Prime(Prime(B.ref)), IntExprs.Add(Prime(B.ref), Int(2))),
                Eq(A.ref, Int(1)),
                Eq(B.ref, Int(2))
            ))
        val origConstExpr = PathUtils.unfold(origVarExpr, 0)
        val multiIndexedExpr = addNewIndexToNonZero(origConstExpr, 2)
        val expectedExpr =
            BoolExprs.And(listOf(
                Eq(A.getConstDecl(listOf(1, 2)).ref, IntExprs.Add(A.getConstDecl(0).ref, Int(1))),
                Eq(B.getConstDecl(listOf(1, 2)).ref, IntExprs.Add(A.getConstDecl(0).ref, Int(2))),
                Eq(B.getConstDecl(listOf(2, 2)).ref, IntExprs.Add(B.getConstDecl(listOf(1, 2)).ref, Int(2))),
                Eq(A.getConstDecl(0).ref, Int(1)),
                Eq(B.getConstDecl(0).ref, Int(2))
            ))
        assertEquals(expectedExpr, multiIndexedExpr)
    }

    @Test
    fun extractMultiIndexValuationTest() {
        val A = Decls.Var("A", Int())
        val B = Decls.Var("B", Int())
        val origVarExpr1 =
            BoolExprs.And(listOf(
                Eq(Prime(A.ref), IntExprs.Add(A.ref, Int(1))),
                Eq(Prime(B.ref), IntExprs.Add(A.ref, Int(2))),
                Eq(Prime(Prime(B.ref)), IntExprs.Add(Prime(B.ref), Int(2))),
                Eq(A.ref, Int(1)),
                Eq(B.ref, Int(2))
            ))
        val origConstExpr1 = PathUtils.unfold(origVarExpr1, 0)
        val multiIndexedExpr1 = addNewIndexToNonZero(origConstExpr1, 1)

        val origVarExpr2 =
            BoolExprs.And(listOf(
                Eq(Prime(A.ref), IntExprs.Add(A.ref, Int(5))),
                Eq(Prime(B.ref), IntExprs.Add(A.ref, Int(6))),
            ))
        val origConstExpr2 = PathUtils.unfold(origVarExpr2, 0)
        val multiIndexedExpr2 = addNewIndexToNonZero(origConstExpr2, 2)

        val indexing1 = VarIndexingFactory.basicVarIndexing(0).inc(A, 1).inc(B, 2)
        val indexing2 = VarIndexingFactory.basicVarIndexing(0).inc(A, 1).inc(B, 1)

        val solver = Z3SolverFactory.getInstance().createSolver()
        solver.add(multiIndexedExpr1)
        solver.add(multiIndexedExpr2)
        assert(solver.check().isSat)

        val valuation1 = extractMultiIndexValuation(solver.model, listOf(A, B), indexing1, 1, true)
        assertEquals(Int(2) , valuation1.eval(A).get())
        assertEquals(Int(5) , valuation1.eval(B).get())

        val valuation2 = extractMultiIndexValuation(solver.model, listOf(A, B), indexing2, 2, true)
        assertEquals(Int(6) , valuation2.eval(A).get())
        assertEquals(Int(7) , valuation2.eval(B).get())

        val valuation0 = extractMultiIndexValuation(solver.model, listOf(A, B), VarIndexingFactory.indexing(0), 1, true)
        assertEquals(Int(1) , valuation0.eval(A).get())
        assertEquals(Int(2) , valuation0.eval(B).get())

    }

}