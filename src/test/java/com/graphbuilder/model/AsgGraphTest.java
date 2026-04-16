package com.graphbuilder.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AsgGraphTest {

    @Test
    void addVertexAndEdge() {
        var graph = new AsgGraph();
        var v0 = new TokenVertex(0, TokenVertexCategory.CLASS, "class", 0, 1, 0, "Test.java");
        var v1 = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "Foo", 6, 1, 6, "Test.java");
        graph.addVertex(v0);
        graph.addVertex(v1);
        graph.addEdge(new AsgEdge(v0, v1, EdgeCategory.DECLARING));

        assertEquals(2, graph.vertices().size());
        assertEquals(1, graph.edges().size());
        assertEquals(v0, graph.vertexById(0));
        assertEquals(v1, graph.vertexById(1));
    }

    @Test
    void verticesInRange() {
        var graph = new AsgGraph();
        var v0 = new TokenVertex(0, TokenVertexCategory.CLASS, "class", 0, 1, 0, "Test.java");
        var v1 = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "Foo", 6, 1, 6, "Test.java");
        var v2 = new TokenVertex(2, TokenVertexCategory.PUBLIC, "public", 20, 2, 0, "Test.java");
        graph.addVertex(v0);
        graph.addVertex(v1);
        graph.addVertex(v2);

        var inRange = graph.verticesInRange(0, 15);
        assertEquals(2, inRange.size());
        assertEquals(v0, inRange.get(0));
        assertEquals(v1, inRange.get(1));
    }
}
