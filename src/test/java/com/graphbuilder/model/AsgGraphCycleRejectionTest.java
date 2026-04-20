package com.graphbuilder.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AsgGraphCycleRejectionTest {

    @Test
    void rejectsEdgeThatIntroducesCycle() {
        var graph = new AsgGraph();
        var a = new TokenVertex(0, TokenVertexCategory.CLASS_DECLARATION, "A", 0, 1, 0, "T.java");
        var b = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "B", 5, 1, 5, "T.java");
        var c = new TokenVertex(2, TokenVertexCategory.CLASS_DECLARATION, "C", 10, 1, 10, "T.java");
        graph.addVertex(a);
        graph.addVertex(b);
        graph.addVertex(c);
        graph.addEdge(a, b, EdgeCategory.NEXT_TOKEN);
        graph.addEdge(b, c, EdgeCategory.NEXT_TOKEN);

        AsgCycleException ex = assertThrows(
            AsgCycleException.class,
            () -> graph.addEdge(c, a, EdgeCategory.NEXT_TOKEN));

        assertEquals(c, ex.source());
        assertEquals(a, ex.target());
        assertEquals(EdgeCategory.NEXT_TOKEN, ex.category());
        assertTrue(ex.getMessage().contains("NEXT_TOKEN"), "message should identify the category");
        assertTrue(ex.getMessage().contains("#0"), "message should identify target id");
        assertTrue(ex.getMessage().contains("#2"), "message should identify source id");
    }

    @Test
    void rejectsSelfLoop() {
        var graph = new AsgGraph();
        var a = new TokenVertex(0, TokenVertexCategory.CLASS_DECLARATION, "A", 0, 1, 0, "T.java");
        graph.addVertex(a);

        assertThrows(AsgCycleException.class,
            () -> graph.addEdge(a, a, EdgeCategory.NEXT_TOKEN));
    }

    @Test
    void acyclicGraphIsAccepted() {
        var graph = new AsgGraph();
        var a = new TokenVertex(0, TokenVertexCategory.CLASS_DECLARATION, "A", 0, 1, 0, "T.java");
        var b = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "B", 5, 1, 5, "T.java");
        var c = new TokenVertex(2, TokenVertexCategory.CLASS_DECLARATION, "C", 10, 1, 10, "T.java");
        var d = new TokenVertex(3, TokenVertexCategory.CLASS_DECLARATION, "D", 15, 1, 15, "T.java");
        graph.addVertex(a);
        graph.addVertex(b);
        graph.addVertex(c);
        graph.addVertex(d);
        graph.addEdge(a, b, EdgeCategory.NEXT_TOKEN);
        graph.addEdge(a, c, EdgeCategory.NEXT_TOKEN);
        graph.addEdge(b, d, EdgeCategory.NEXT_TOKEN);
        graph.addEdge(c, d, EdgeCategory.NEXT_TOKEN);

        assertEquals(4, graph.edges().size());
    }

    @Test
    void addingEdgeForMissingVertexDoesNotPretendToBeACycle() {
        var graph = new AsgGraph();
        var a = new TokenVertex(0, TokenVertexCategory.CLASS_DECLARATION, "A", 0, 1, 0, "T.java");
        var b = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "B", 5, 1, 5, "T.java");
        graph.addVertex(a);
        // deliberately NOT adding b

        // jgrapht throws IllegalArgumentException (not a cycle) when an endpoint is missing.
        // We must propagate that as-is, not wrap it as AsgCycleException.
        assertThrows(IllegalArgumentException.class,
            () -> graph.addEdge(a, b, EdgeCategory.NEXT_TOKEN));
        // And it must NOT be an AsgCycleException — that would be a misleading diagnosis.
        try {
            graph.addEdge(a, b, EdgeCategory.NEXT_TOKEN);
        } catch (AsgCycleException misleading) {
            fail("Missing-vertex error must not be reported as a cycle: " + misleading.getMessage());
        } catch (IllegalArgumentException expected) {
            // good
        }
    }
}
