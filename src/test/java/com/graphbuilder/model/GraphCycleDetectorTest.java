package com.graphbuilder.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphCycleDetectorTest {

    private ITokenVertex vertex(int id) {
        return new TokenVertex(id, TokenVertexCategory.CLASS, "v" + id, id, 1, 0, "Test.java");
    }

    @Test
    void emptyGraphIsDag() {
        assertTrue(GraphCycleDetector.isDag(new AsgGraph()));
    }

    @Test
    void singleVertexIsDag() {
        var graph = new AsgGraph();
        graph.addVertex(vertex(0));
        assertTrue(GraphCycleDetector.isDag(graph));
    }

    @Test
    void linearChainIsDag() {
        var graph = new AsgGraph();
        var a = vertex(0); var b = vertex(1); var c = vertex(2);
        graph.addVertex(a); graph.addVertex(b); graph.addVertex(c);
        graph.addEdge(new AsgEdge(a, b, EdgeCategory.NEXT_TOKEN));
        graph.addEdge(new AsgEdge(b, c, EdgeCategory.NEXT_TOKEN));
        assertTrue(GraphCycleDetector.isDag(graph));
    }

    @Test
    void diamondIsDag() {
        var graph = new AsgGraph();
        var a = vertex(0); var b = vertex(1); var c = vertex(2); var d = vertex(3);
        graph.addVertex(a); graph.addVertex(b); graph.addVertex(c); graph.addVertex(d);
        graph.addEdge(new AsgEdge(a, b, EdgeCategory.NEXT_TOKEN));
        graph.addEdge(new AsgEdge(a, c, EdgeCategory.NEXT_TOKEN));
        graph.addEdge(new AsgEdge(b, d, EdgeCategory.NEXT_TOKEN));
        graph.addEdge(new AsgEdge(c, d, EdgeCategory.NEXT_TOKEN));
        assertTrue(GraphCycleDetector.isDag(graph));
    }

    @Test
    void selfLoopIsCycle() {
        var graph = new AsgGraph();
        var a = vertex(0);
        graph.addVertex(a);
        graph.addEdge(new AsgEdge(a, a, EdgeCategory.NEXT_TOKEN));
        assertFalse(GraphCycleDetector.isDag(graph));

        List<ITokenVertex> cycle = GraphCycleDetector.findCycle(graph);
        assertFalse(cycle.isEmpty());
        assertEquals(0, cycle.get(0).id());
    }

    @Test
    void twoVertexCycleDetected() {
        var graph = new AsgGraph();
        var a = vertex(0); var b = vertex(1);
        graph.addVertex(a); graph.addVertex(b);
        graph.addEdge(new AsgEdge(a, b, EdgeCategory.NEXT_TOKEN));
        graph.addEdge(new AsgEdge(b, a, EdgeCategory.NEXT_TOKEN));
        assertFalse(GraphCycleDetector.isDag(graph));
    }

    @Test
    void threeVertexCycleDetected() {
        var graph = new AsgGraph();
        var a = vertex(0); var b = vertex(1); var c = vertex(2);
        graph.addVertex(a); graph.addVertex(b); graph.addVertex(c);
        graph.addEdge(new AsgEdge(a, b, EdgeCategory.NEXT_TOKEN));
        graph.addEdge(new AsgEdge(b, c, EdgeCategory.NEXT_TOKEN));
        graph.addEdge(new AsgEdge(c, a, EdgeCategory.NEXT_TOKEN));

        List<ITokenVertex> cycle = GraphCycleDetector.findCycle(graph);
        assertEquals(4, cycle.size()); // a -> b -> c -> a
        assertEquals(cycle.get(0).id(), cycle.get(cycle.size() - 1).id());
    }

    @Test
    void cycleInLargerGraphDetected() {
        // a -> b -> c, d -> e -> d (cycle in second component)
        var graph = new AsgGraph();
        var a = vertex(0); var b = vertex(1); var c = vertex(2); var d = vertex(3); var e = vertex(4);
        for (var v : List.of(a, b, c, d, e)) graph.addVertex(v);
        graph.addEdge(new AsgEdge(a, b, EdgeCategory.NEXT_TOKEN));
        graph.addEdge(new AsgEdge(b, c, EdgeCategory.NEXT_TOKEN));
        graph.addEdge(new AsgEdge(d, e, EdgeCategory.NEXT_TOKEN));
        graph.addEdge(new AsgEdge(e, d, EdgeCategory.NEXT_TOKEN));

        assertFalse(GraphCycleDetector.isDag(graph));
    }
}
