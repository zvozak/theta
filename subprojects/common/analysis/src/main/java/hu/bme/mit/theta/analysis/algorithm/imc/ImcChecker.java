package hu.bme.mit.theta.analysis.algorithm.imc;

import hu.bme.mit.theta.analysis.Prec;
import hu.bme.mit.theta.analysis.Trace;
import hu.bme.mit.theta.analysis.algorithm.ARG;
import hu.bme.mit.theta.analysis.algorithm.SafetyChecker;
import hu.bme.mit.theta.analysis.algorithm.SafetyResult;
import hu.bme.mit.theta.analysis.expr.ExprState;
import hu.bme.mit.theta.analysis.expr.StmtAction;
import hu.bme.mit.theta.common.Utils;
import hu.bme.mit.theta.common.logging.Logger;
import hu.bme.mit.theta.core.model.ImmutableValuation;
import hu.bme.mit.theta.core.model.Valuation;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.booltype.BoolType;
import hu.bme.mit.theta.core.utils.PathUtils;
import hu.bme.mit.theta.core.utils.indexings.VarIndexing;
import hu.bme.mit.theta.solver.*;
import hu.bme.mit.theta.solver.utils.WithPushPop;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkState;
import static hu.bme.mit.theta.core.type.booltype.SmartBoolExprs.*;

public class ImcChecker<S extends ExprState, A extends StmtAction, P extends Prec> implements SafetyChecker<S, A, P> {

    private final Expr<BoolType> initRel;
    private final VarIndexing initIndexing;
    private final A transRel;
    private final Expr<BoolType> safetyProperty;

    private final Function<Valuation, S> valToState;
    private final boolean interpolateForward;

    private final ItpSolver solver;
    private final int upperBound;
    private final Logger logger;

    private ImcChecker(final Expr<BoolType> initRel,
                       final VarIndexing initIndexing,
                       final A transRel,
                       final Expr<BoolType> safetyProperty,
                       final Function<Valuation, S> valToState,
                       final boolean interpolateForward,
                       final ItpSolver solver,
                       final Logger logger,
                       final int upperBound) {
        this.initRel = initRel;
        this.initIndexing = initIndexing;
        this.transRel = transRel;
        this.safetyProperty = safetyProperty;
        this.valToState = valToState;
        this.interpolateForward = interpolateForward;
        this.solver = solver;
        this.upperBound = upperBound;
        this.logger = logger;
    }

    public static <S extends ExprState, A extends StmtAction, P extends Prec> ImcChecker<S, A, P> create(final Expr<BoolType> initRel,
                                                                                                         final VarIndexing initIndexing,
                                                                                                         final A transRel,
                                                                                                         final Expr<BoolType> safetyProperty,
                                                                                                         final Function<Valuation, S> valToState,
                                                                                                         final boolean interpolateForward,
                                                                                                         final ItpSolver solver,
                                                                                                         final Logger logger,
                                                                                                         final int upperBound) {
        return new ImcChecker<S, A, P>(initRel, initIndexing, transRel, safetyProperty, valToState, interpolateForward, solver, logger, upperBound);
    }

    public static <S extends ExprState, A extends StmtAction, P extends Prec> ImcChecker<S, A, P> create(final Expr<BoolType> initRel,
                                                                                                         final VarIndexing initIndexing,
                                                                                                         final A transRel,
                                                                                                         final Expr<BoolType> safetyProperty,
                                                                                                         final Function<Valuation, S> valToState,
                                                                                                         final boolean interpolateForward,
                                                                                                         final ItpSolver solver,
                                                                                                         final Logger logger) {
        return new ImcChecker<S, A, P>(initRel, initIndexing, transRel, safetyProperty, valToState, interpolateForward, solver, logger, -1);
    }

    @Override
    public SafetyResult<S, A> check(P prec) {
        logger.write(Logger.Level.INFO, "Configuration: %s%n", this);

        final List<Expr<BoolType>> formulas = new ArrayList<>(List.of(initRel));
        final List<VarIndexing> indexings = new ArrayList<>(List.of(initIndexing));

        final ItpMarker A = solver.createMarker();
        final ItpMarker B = solver.createMarker();
        final ItpPattern pattern = solver.createBinPattern(A, B);

        int currentBound = 0;
        SafetyResult<S, A> bmcresult = null;
        outerloop:
        while ((upperBound < 0 || currentBound < upperBound)) {
            currentBound++;
            logger.write(Logger.Level.MAINSTEP, "Iteration %d%n", currentBound);
            logger.write(Logger.Level.MAINSTEP, "| Expanding trace...%n");

            checkState(formulas.size() == currentBound, "Trace is not well formatted");
            checkState(indexings.size() == currentBound, "Trace is not well formatted");

            final VarIndexing newIndexing = indexings.get(currentBound - 1).add(transRel.nextIndexing());
            final Expr<BoolType> newFormula = PathUtils.unfold(transRel.toExpr(), indexings.get(currentBound - 1));

            formulas.add(newFormula);
            indexings.add(newIndexing);

            final Expr<BoolType> foldedProperty = Not(PathUtils.unfold(safetyProperty, newIndexing));

            solver.push();
            if(interpolateForward){
                solver.add(A, And(formulas.subList(0, 2)));
                solver.add(B, And(And(formulas.subList(2, formulas.size())), foldedProperty));
            } else {
                solver.add(B, And(formulas.subList(0, 2)));
                solver.add(A, And(And(formulas.subList(2, formulas.size())), foldedProperty));
            }


            Expr<BoolType> image = formulas.get(0);

            var status = solver.check();

            if(status.isSat()){
                bmcresult = SafetyResult.unsafe(Trace.of(List.of(valToState.apply(ImmutableValuation.empty())), List.of()), ARG.create((state1, state2) -> false)); // TODO: this is only a placeholder, we don't give back an ARG
                break outerloop;
            }

            while (status.isUnsat()) {

                final Interpolant interpolant = solver.getInterpolant(pattern);
                final Expr<BoolType> itpFormula = PathUtils.unfold(PathUtils.foldin(interpolateForward ? interpolant.eval(A) : Not(interpolant.eval(A)), indexings.get(1)), indexings.get(0));
                solver.pop();

                try (var wpp = new WithPushPop(solver)) {
                    solver.add(A, And(itpFormula, Not(image)));
                    if (solver.check() == SolverStatus.UNSAT) {
                        // Fixpoint found, itp implies image
                        bmcresult = SafetyResult.safe(ARG.create((state1, state2) -> false)); // TODO: this is only a placeholder, we don't give back an ARG
                        break outerloop;
                    }
                }

                image = Or(image, itpFormula);

                solver.push();
                if(interpolateForward){
                    solver.add(A, And(itpFormula, formulas.get(1)));
                    solver.add(B, And(And(formulas.subList(2, formulas.size())), foldedProperty));
                } else {
                    solver.add(B, And(itpFormula, formulas.get(1)));
                    solver.add(A, And(And(formulas.subList(2, formulas.size())), foldedProperty));
                }
                status = solver.check();
            }
            solver.pop();
        }
//        if (bmcresult == null) {
//            bmcresult = SafetyResult.safe(ARG.create((state1, state2) -> false)); // TODO: this is only a placeholder, we don't give back an ARG
//        }
        logger.write(Logger.Level.RESULT, "%s%n", bmcresult);
        return bmcresult;
    }

    @Override
    public String toString() {
        return Utils.lispStringBuilder(getClass().getSimpleName()).add(upperBound).add(initRel).add(transRel).toString();
    }

}
