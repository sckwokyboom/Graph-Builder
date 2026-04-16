package com.graphbuilder.export;

import com.graphbuilder.model.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DotExporter {

    public String export(AsgGraph graph) {
        var sb = new StringBuilder();
        sb.append("strict digraph G {\n");
        sb.append("  ordering=in;\n");

        for (ITokenVertex v : graph.vertices()) {
            String nodeId = nodeId(v);
            sb.append("  ").append(quote(nodeId))
              .append(" [ label=").append(quote(quote(nodeId)))
              .append(" penwidth=\"3\" shape=\"rect\" style=\"rounded\" fontname=\"Helvetica-Bold\" ];\n");
        }

        for (AsgEdge e : graph.edges()) {
            String srcId = nodeId(e.source());
            String tgtId = nodeId(e.target());
            sb.append("  ").append(quote(srcId))
              .append(" -> ").append(quote(tgtId))
              .append(" [ color=\"blue\" fontcolor=\"blue\" fontname=\"Helvetica-Bold\" penwidth=\"2\" label=")
              .append(quote(e.category().dotLabel()))
              .append(" ];\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    public void exportToFile(AsgGraph graph, Path outputFile) throws IOException {
        Files.writeString(outputFile, export(graph));
    }

    private String nodeId(ITokenVertex v) {
        return v.category().dotLabel() + " (" + v.id() + ")\n" + v.value();
    }

    private String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
