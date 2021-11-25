package hu.bme.mit.theta.prob;

import hu.bme.mit.theta.xcfa.model.XcfaEdge;
import hu.bme.mit.theta.xcfa.model.XcfaLocation;
import hu.bme.mit.theta.xcfa.model.XcfaProcedure;
import hu.bme.mit.theta.xcfa.passes.procedurepass.ProcedurePass;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public class RemoveUnreachable extends ProcedurePass {
	@Override
	public XcfaProcedure.Builder run(XcfaProcedure.Builder builder) {
		Set<XcfaEdge> reachableEdges = new LinkedHashSet<>();
		filterReachableEdges(builder.getInitLoc(), reachableEdges);
		for (XcfaEdge edge : new ArrayList<>(builder.getEdges())) {
			if(!reachableEdges.contains(edge)) {
				builder.removeEdge(edge);
			}
		}
		return builder;
	}

	private void filterReachableEdges(XcfaLocation loc, Set<XcfaEdge> reachableEdges) {
		Set<XcfaEdge> outgoingEdges = new LinkedHashSet<>(loc.getOutgoingEdges());
		while(!outgoingEdges.isEmpty()) {
			Optional<XcfaEdge> any = outgoingEdges.stream().findAny();
			XcfaEdge outgoingEdge = any.get();
			outgoingEdges.remove(outgoingEdge);
			if (!reachableEdges.contains(outgoingEdge)) {
				reachableEdges.add(outgoingEdge);
				outgoingEdges.addAll(outgoingEdge.getTarget().getOutgoingEdges());
			}
		}
	}
}
