package hu.bme.mit.theta.prob;

import hu.bme.mit.theta.frontend.transformation.ArchitectureConfig;
import hu.bme.mit.theta.xcfa.model.XcfaEdge;
import hu.bme.mit.theta.xcfa.model.XcfaLabel;
import hu.bme.mit.theta.xcfa.model.XcfaLocation;
import hu.bme.mit.theta.xcfa.model.XcfaProcedure;
import hu.bme.mit.theta.xcfa.passes.procedurepass.ProcedurePass;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import static hu.bme.mit.theta.xcfa.model.XcfaLabel.Sequence;

public class SeparatorPass extends ProcedurePass {
    private final Predicate<XcfaLabel> shouldSeparate;

	/* Example usage:
	   		new SeparatorPass((l) -> l instanceof XcfaLabel.StmtXcfaLabel && l.getStmt() instanceof HavocStmt)
	 */

    public SeparatorPass(final Predicate<XcfaLabel> shouldSeparate) {
        this.shouldSeparate = shouldSeparate;
    }

    @Override
    public XcfaProcedure.Builder run(XcfaProcedure.Builder builder) {
        for (XcfaEdge edge : new ArrayList<>(builder.getEdges())) {
            List<XcfaLabel> newLabels = new ArrayList<>();
            boolean removed = false;
            XcfaLocation source = edge.getSource();
            List<XcfaLabel> unfoldedLabels = new LinkedList<>(edge.getLabels());
            for (int i = 0; i < unfoldedLabels.size(); i++) {
                XcfaLabel label = unfoldedLabels.get(i);
                if (label instanceof XcfaLabel.SequenceLabel) {
                    unfoldedLabels.addAll(i + 1, ((XcfaLabel.SequenceLabel) label).getLabels());
                } else if (label instanceof XcfaLabel.NondetLabel) {
                    throw new UnsupportedOperationException("Nondet labels are not yet supported!");
                } else {
                    if (shouldSeparate.test(label)) {
                        if (!removed) {
                            builder.removeEdge(edge);
                            removed = true;
                        }
                        if (newLabels.size() > 0) {
                            XcfaLocation tmp = XcfaLocation.create("tmp" + XcfaLocation.uniqeCounter());
                            builder.addLoc(tmp);
                            builder.addEdge(edge.withSource(source).withLabels(List.of(Sequence(newLabels))).withTarget(tmp));
                            source = tmp;
                            newLabels.clear();
                        }
                        XcfaLocation tmp = XcfaLocation.create("tmp" + XcfaLocation.uniqeCounter());
                        builder.addLoc(tmp);
                        builder.addEdge(edge.withSource(source).withLabels(List.of(label)).withTarget(tmp));
                        source = tmp;
                    } else {
                        newLabels.add(label);
                    }
                }
            }
            if(removed) {
                builder.addEdge(edge.withSource(source).withLabels(List.of(Sequence(newLabels))).withTarget(edge.getTarget()));
            }
        }
        return builder;
    }

    @Override
    public boolean isPostInlining() {
        return true;
    }
}