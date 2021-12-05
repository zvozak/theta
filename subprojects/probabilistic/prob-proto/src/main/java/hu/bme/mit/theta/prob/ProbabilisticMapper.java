package hu.bme.mit.theta.prob;

import hu.bme.mit.theta.core.decl.VarDecl;
import hu.bme.mit.theta.core.model.ImmutableValuation;
import hu.bme.mit.theta.core.stmt.Stmt;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.LitExpr;
import hu.bme.mit.theta.core.type.anytype.RefExpr;
import hu.bme.mit.theta.core.type.fptype.FpExprs;
import hu.bme.mit.theta.core.type.fptype.FpLitExpr;
import hu.bme.mit.theta.core.type.fptype.FpRoundingMode;
import hu.bme.mit.theta.core.type.inttype.IntLitExpr;
import hu.bme.mit.theta.core.type.inttype.IntType;
import hu.bme.mit.theta.core.type.rattype.RatExprs;
import hu.bme.mit.theta.core.utils.FpUtils;
import hu.bme.mit.theta.frontend.FrontendMetadata;
import hu.bme.mit.theta.frontend.transformation.model.types.complex.CComplexType;
import hu.bme.mit.theta.xcfa.model.XcfaEdge;
import hu.bme.mit.theta.xcfa.model.XcfaLabel;
import hu.bme.mit.theta.xcfa.model.XcfaProcedure;
import hu.bme.mit.theta.xcfa.passes.procedurepass.ProcedurePass;
import kotlin.Pair;
import kotlin.Unit;
import kotlin.ranges.IntRange;
import kotlin.ranges.LongRange;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkState;
import static hu.bme.mit.theta.core.stmt.Stmts.Assign;
import static hu.bme.mit.theta.core.stmt.Stmts.Skip;
import static hu.bme.mit.theta.core.type.abstracttype.AbstractExprs.Add;
import static hu.bme.mit.theta.core.type.abstracttype.AbstractExprs.Div;
import static hu.bme.mit.theta.core.type.inttype.IntExprs.Int;
import static hu.bme.mit.theta.core.utils.TypeUtils.cast;
import static hu.bme.mit.theta.xcfa.model.XcfaLabel.Stmt;

public class ProbabilisticMapper extends ProcedurePass {
	private static final Map<String, BiFunction<XcfaProcedure.Builder, XcfaLabel.ProcedureCallXcfaLabel, XcfaLabel>> handlers = new LinkedHashMap<>();
	private static void addHandler(String[] names, BiFunction<XcfaProcedure.Builder, XcfaLabel.ProcedureCallXcfaLabel, XcfaLabel> handler) {
		for (String name : names) {
			handlers.put(name, handler);
		}
	}
	static {
		addHandler(new String[]{"coin"}, ProbabilisticMapper::handleCoin);
		addHandler(new String[]{"uniform"}, ProbabilisticMapper::handleUniform);
		addHandler(new String[]{"prob_bool_bias"}, ProbabilisticMapper::handleProbBoolBias);
		addHandler(new String[]{"prob_bool"}, ProbabilisticMapper::handleProbBool);
	}

	private static XcfaLabel handleCoin(XcfaProcedure.Builder builder, XcfaLabel.ProcedureCallXcfaLabel label) {
		final List<Expr<?>> params = label.getParams();
		checkState(params.get(0) instanceof RefExpr, "Return param must be a reference!");
		final VarDecl<?> varGeneric = (VarDecl<?>) ((RefExpr<?>) params.get(0)).getDecl();
		final VarDecl<IntType> var = cast(varGeneric, Int());
		final Expr<?> a = params.get(1);
		final Expr<?> b = params.get(2);
		final int evalA = ((IntLitExpr) cast(a.eval(ImmutableValuation.empty()), Int())).getValue().intValue();
		final int evalB = ((IntLitExpr) cast(b.eval(ImmutableValuation.empty()), Int())).getValue().intValue();
		final int sum = evalA+evalB;
		return Stmt(new ProbStmt(new EnumeratedDistribution<>(List.of(
				new Pair<>(Assign(var, Int(0)), (double) evalA/sum),
				new Pair<>(Assign(var, Int(1)), (double) evalB/sum)
		))));
	}

	private static XcfaLabel handleProbBoolBias(XcfaProcedure.Builder builder, XcfaLabel.ProcedureCallXcfaLabel label) {
		final List<Expr<?>> params = label.getParams();
		checkState(params.get(0) instanceof RefExpr, "Return param must be a reference!");
		final VarDecl<?> varGeneric = (VarDecl<?>) ((RefExpr<?>) params.get(0)).getDecl();
		final VarDecl<IntType> var = cast(varGeneric, Int());
		final Expr<?> bias = params.get(1);
		// TODO: parse this as double
		final double evalBias =
				FpUtils.fpLitExprToBigFloat(
						FpRoundingMode.getDefaultRoundingMode(),
						(FpLitExpr) bias.eval(ImmutableValuation.empty())
				).doubleValue();
		return Stmt(new ProbStmt(new EnumeratedDistribution<>(List.of(
				new Pair<>(Assign(var, Int(0)), 1.0-evalBias),
				new Pair<>(Assign(var, Int(1)), evalBias)
		))));
	}

	private static XcfaLabel handleProbBool(XcfaProcedure.Builder builder, XcfaLabel.ProcedureCallXcfaLabel label) {
		final List<Expr<?>> params = label.getParams();
		checkState(params.get(0) instanceof RefExpr, "Return param must be a reference!");
		final VarDecl<?> varGeneric = (VarDecl<?>) ((RefExpr<?>) params.get(0)).getDecl();
		final VarDecl<IntType> var = cast(varGeneric, Int());
		return Stmt(new ProbStmt(new EnumeratedDistribution<>(List.of(
				new Pair<>(Assign(var, Int(0)), 0.5),
				new Pair<>(Assign(var, Int(1)), 0.5)
		))));
	}


	private static XcfaLabel handleUniform(XcfaProcedure.Builder builder, XcfaLabel.ProcedureCallXcfaLabel label) {
		final List<Expr<?>> params = label.getParams();
		checkState(params.get(0) instanceof RefExpr, "Return param must be a reference!");
		final VarDecl<?> varGeneric = (VarDecl<?>) ((RefExpr<?>) params.get(0)).getDecl();
		final VarDecl<IntType> var = cast(varGeneric, Int());
		final Expr<?> n = params.get(1);
		final IntLitExpr eval = (IntLitExpr) cast(n.eval(ImmutableValuation.empty()), Int());
		return Stmt(PCFADSLKt.uniform(var, eval.getValue().intValue()));
	}

	@Override
	public XcfaProcedure.Builder run(XcfaProcedure.Builder builder) {
		for (XcfaEdge edge : new ArrayList<>(builder.getEdges())) {
			List<XcfaLabel> newStmts = new ArrayList<>();
			boolean found = false;
			for (XcfaLabel stmt : edge.getLabels()) {
				if(stmt instanceof XcfaLabel.ProcedureCallXcfaLabel) {
					if(handlers.containsKey(((XcfaLabel.ProcedureCallXcfaLabel) stmt).getProcedure())) {
						newStmts.add(handlers.get(((XcfaLabel.ProcedureCallXcfaLabel) stmt).getProcedure()).apply(builder, (XcfaLabel.ProcedureCallXcfaLabel) stmt));
						found = true;
					}
				}
				else newStmts.add(stmt);
			}
			if(found) {
				builder.removeEdge(edge);
				builder.addEdge(XcfaEdge.of(edge.getSource(), edge.getTarget(), newStmts));
			}
		}
		return builder;
	}
}
