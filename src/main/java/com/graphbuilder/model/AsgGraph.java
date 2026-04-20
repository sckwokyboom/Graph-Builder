package com.graphbuilder.model;

import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.graph.GraphCycleProhibitedException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsgGraph extends DirectedAcyclicGraph<ITokenVertex, AsgEdge> {

    private final Map<Integer, ITokenVertex> byId = new HashMap<>();
    private int edgeSequence = 0;

    public AsgGraph() {
        super(AsgEdge.class);
    }

    @Override
    public boolean addVertex(ITokenVertex v) {
        boolean added = super.addVertex(v);
        if (added) {
            byId.put(v.id(), v);
        }
        return added;
    }

    /**
     * Adds a categorized edge. Returns the inserted edge, or null if an edge
     * between source and target already existed. Throws {@link AsgCycleException}
     * if the edge would introduce a cycle (including self-loops).
     */
    public AsgEdge addEdge(ITokenVertex source, ITokenVertex target, EdgeCategory category) {
        // Check for self-loop upfront. A self-loop is semantically a cycle;
        // we throw AsgCycleException with domain context rather than relying on
        // jgrapht's error message wording (which could change).
        if (source.equals(target)) {
            throw new AsgCycleException(source, target, category,
                new IllegalArgumentException("self-loop"));
        }
        AsgEdge edge = new AsgEdge(category);
        try {
            boolean added = super.addEdge(source, target, edge);
            if (added) {
                edge.setInsertionOrder(edgeSequence++);
            }
            return added ? edge : null;
        } catch (GraphCycleProhibitedException e) {
            // DirectedAcyclicGraph.addEdge throws GraphCycleProhibitedException when the edge would create a cycle.
            throw new AsgCycleException(source, target, category, e);
        }
        // Other IllegalArgumentExceptions (e.g., "no such vertex in graph") propagate as-is.
    }

    public ITokenVertex vertexById(int id) {
        return byId.get(id);
    }

    public List<ITokenVertex> verticesInRange(int startOffset, int endOffset) {
        return vertexSet().stream()
            .filter(v -> v.documentOffset() >= startOffset && v.documentOffset() < endOffset)
            .sorted(Comparator.comparingInt(ITokenVertex::id))
            .toList();
    }

    public ITokenVertex firstVertexInRange(int startOffset, int endOffset) {
        return vertexSet().stream()
            .filter(v -> v.documentOffset() >= startOffset && v.documentOffset() < endOffset)
            .min(Comparator.comparingInt(ITokenVertex::id))
            .orElse(null);
    }

    public List<AsgEdge> outgoingOf(ITokenVertex v, EdgeCategory category) {
        return outgoingEdgesOf(v).stream()
            .filter(e -> e.category() == category)
            .toList();
    }

    /**
     * Compatibility shim: returns vertices sorted by id (preserves historical insertion order,
     * since ids are assigned monotonically by {@link com.graphbuilder.context.BuildContext}).
     */
    public List<ITokenVertex> vertices() {
        return vertexSet().stream()
            .sorted(Comparator.comparingInt(ITokenVertex::id))
            .toList();
    }

    /**
     * Compatibility shim: returns edges in insertion order — matches the original ArrayList-backed
     * AsgGraph behaviour so that the DOT golden output is stable.
     * jgrapht's {@link #edgeSet()} has no order guarantee, so each edge records its sequence number
     * at insertion time.
     */
    public List<AsgEdge> edges() {
        return edgeSet().stream()
            .sorted(Comparator.comparingInt(AsgEdge::insertionOrder))
            .toList();
    }
}
