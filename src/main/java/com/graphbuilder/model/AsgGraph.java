package com.graphbuilder.model;

import org.jgrapht.graph.DirectedAcyclicGraph;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsgGraph extends DirectedAcyclicGraph<ITokenVertex, AsgEdge> {

    private final Map<Integer, ITokenVertex> byId = new HashMap<>();

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
     * if the edge would introduce a cycle.
     */
    public AsgEdge addEdge(ITokenVertex source, ITokenVertex target, EdgeCategory category) {
        AsgEdge edge = new AsgEdge(category);
        try {
            boolean added = super.addEdge(source, target, edge);
            return added ? edge : null;
        } catch (IllegalArgumentException e) {
            // DirectedAcyclicGraph.CycleFoundException (subtype of IAE) on cycle.
            throw new AsgCycleException(source, target, category, e);
        }
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
     * Compatibility shim: returns edges in a deterministic order
     * (by source id, target id, category code) — jgrapht's {@link #edgeSet()} has no order guarantee.
     */
    public List<AsgEdge> edges() {
        return edgeSet().stream()
            .sorted(
                Comparator
                    .<AsgEdge>comparingInt(e -> e.source().id())
                    .thenComparingInt(e -> e.target().id())
                    .thenComparingInt(e -> e.category().code()))
            .toList();
    }
}
