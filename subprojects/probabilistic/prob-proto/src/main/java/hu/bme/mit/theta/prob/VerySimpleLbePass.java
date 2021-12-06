package hu.bme.mit.theta.prob;
import hu.bme.mit.theta.xcfa.model.XcfaEdge;
import hu.bme.mit.theta.xcfa.model.XcfaLabel;
import hu.bme.mit.theta.xcfa.model.XcfaLocation;
import hu.bme.mit.theta.xcfa.model.XcfaProcedure;
import hu.bme.mit.theta.xcfa.passes.procedurepass.EliminateSelfLoops;
import hu.bme.mit.theta.xcfa.passes.procedurepass.ProcedurePass;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static hu.bme.mit.theta.xcfa.model.XcfaLabel.Sequence;

public class VerySimpleLbePass extends ProcedurePass {
    @Override
    public XcfaProcedure.Builder run(XcfaProcedure.Builder builder) {
        builder = EliminateSelfLoops.instance.run(builder);
        Set<XcfaLocation> pathlocs = builder.getLocs().stream().filter(xcfaLocation -> xcfaLocation.getIncomingEdges().size() == 1 && xcfaLocation.getOutgoingEdges().size() == 1).collect(Collectors.toSet());
        for (XcfaLocation pathloc : pathlocs) {
            XcfaEdge inEdge = pathloc.getIncomingEdges().get(0);
            XcfaEdge outEdge = pathloc.getOutgoingEdges().get(0);

            builder.removeEdge(inEdge);
            builder.removeEdge(outEdge);
            builder.removeLoc(pathloc);

            List<XcfaLabel> stmts = new ArrayList<>(inEdge.getLabels());
            stmts.addAll(outEdge.getLabels());

            builder.addEdge(XcfaEdge.of(inEdge.getSource(), outEdge.getTarget(), List.of(Sequence(stmts))));
        }
        final List<XcfaEdge> edgesToHandle = builder.getEdges().stream().filter(xcfaEdge -> xcfaEdge.getLabels().stream().anyMatch(label -> !(label instanceof XcfaLabel.StmtXcfaLabel))).collect(Collectors.toList());
        for (XcfaEdge edge : edgesToHandle) {
            builder.removeEdge(edge);
            List<XcfaLabel> newLabelList = new ArrayList<>();
            XcfaLocation source = edge.getSource();
            for (XcfaLabel label : edge.getLabels()) {
                if(!(label instanceof XcfaLabel.StmtXcfaLabel)) {
                    if(newLabelList.size() > 0) {
                        XcfaLocation tmpLoc = XcfaLocation.create("_tmp" + XcfaLocation.uniqeCounter());
                        builder.addLoc(tmpLoc);
                        builder.addEdge(XcfaEdge.of(source, tmpLoc, List.of(Sequence(newLabelList))));
                        source = tmpLoc;
                    }
                    newLabelList.clear();
                    XcfaLocation tmpLoc = XcfaLocation.create("_tmp" + XcfaLocation.uniqeCounter());
                    builder.addLoc(tmpLoc);
                    builder.addEdge(XcfaEdge.of(source, tmpLoc, List.of(label)));
                    source = tmpLoc;
                }
                else {
                    newLabelList.add(label);
                }
            }
            builder.addEdge(XcfaEdge.of(source, edge.getTarget(), List.of(Sequence(newLabelList))));
        }
        return builder;
    }

    @Override
    public boolean isPostInlining() {
        return true;
    }
}