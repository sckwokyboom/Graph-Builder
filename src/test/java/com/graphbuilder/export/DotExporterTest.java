package com.graphbuilder.export;

import com.graphbuilder.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DotExporterTest {

    @Test
    void exportSimpleGraph() {
        var graph = new AsgGraph();
        var v0 = new TokenVertex(0, TokenVertexCategory.CLASS, "class", 0, 1, 0, "Test.java");
        var v1 = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "Foo", 6, 1, 6, "Test.java");
        graph.addVertex(v0);
        graph.addVertex(v1);
        graph.addEdge(new AsgEdge(v0, v1, EdgeCategory.DECLARING));

        var exporter = new DotExporter();
        String dot = exporter.export(graph);

        assertTrue(dot.startsWith("strict digraph G {"));
        assertTrue(dot.contains("\"CLASS (0)\\nclass\""));
        assertTrue(dot.contains("\"CLASS_DECL (1)\\nFoo\""));
        assertTrue(dot.contains("-> \"CLASS_DECL (1)\\nFoo\""));
        assertTrue(dot.contains("label=\"DECLARING\""));
        assertTrue(dot.endsWith("}\n"));
    }
}
