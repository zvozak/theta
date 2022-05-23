import hu.bme.mit.theta.analysis.expl.ExplInitFunc
import hu.bme.mit.theta.analysis.expl.ExplPrec
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expl.ExplStmtOptimizer
import hu.bme.mit.theta.interchange.jani.model.Model
import hu.bme.mit.theta.interchange.jani.model.json.JaniModelMapper
import hu.bme.mit.theta.prob.formalism.SmdpInitFunc
import hu.bme.mit.theta.prob.formalism.SmdpLts
import hu.bme.mit.theta.prob.formalism.SmdpTransFunc
import hu.bme.mit.theta.prob.formalism.toSMDP
import hu.bme.mit.theta.prob.transfunc.ExplStmtGroupedTransFunc
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import org.junit.Test
import java.nio.file.Paths

class JaniTests {

    @Test
    fun testTranslation() {
        val p = Paths.get("models", "jamini.jani")
//    val p = Paths.get("E:\\egyetem\\dipterv\\qcomp\\benchmarks\\mdp\\beb\\beb.3-4.jani")
        val file = p.toFile()
        val solver = Z3SolverFactory.getInstance().createSolver()
        val model = JaniModelMapper().readValue(file, Model::class.java)


        val smdp = model.toSMDP()
        val domainInitFunc = ExplInitFunc.create(solver, smdp.getFullInitExpr())
        val initFunc = SmdpInitFunc(domainInitFunc, smdp)
        val lts = SmdpLts<ExplState>(smdp, ExplStmtOptimizer.getInstance())
        val domainTransFunc = ExplStmtGroupedTransFunc(solver, 0)
        val stmtOptimizer = ExplStmtOptimizer.getInstance()
        val transFunc = SmdpTransFunc(domainTransFunc, stmtOptimizer) {it}

        val init = initFunc.getInitStates(ExplPrec.of(smdp.globalVars))
        val acts = lts.getEnabledActionsFor(init.first())
        transFunc.getSuccStates(init.first(), acts.first(), ExplPrec.of(smdp.globalVars))
    }
}