package hu.bme.mit.theta.cfa.analysis;

import hu.bme.mit.theta.analysis.expr.ExprState;
import hu.bme.mit.theta.analysis.stmtoptimizer.StmtOptimizer;
import hu.bme.mit.theta.core.stmt.Stmt;

public class CfaStmtOptimizer<S extends ExprState> implements StmtOptimizer<CfaState<S>> {

    private final StmtOptimizer<S> stmtOptimizer;

    private CfaStmtOptimizer(final StmtOptimizer<S> stmtOptimizer) {
        this.stmtOptimizer = stmtOptimizer;
    }

    public static <S extends ExprState> CfaStmtOptimizer<S> create(final StmtOptimizer<S> stmtOptimizer){
        return new CfaStmtOptimizer<>(stmtOptimizer);
    }

    @Override
    public Stmt optimizeStmt(CfaState<S> state, Stmt stmt) {
        return stmtOptimizer.optimizeStmt(state.getState(), stmt);
    }
}
