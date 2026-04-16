package com.graphbuilder.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AsgGraph {
    private final List<ITokenVertex> vertices = new ArrayList<>();
    private final List<AsgEdge> edges = new ArrayList<>();

    public void addVertex(ITokenVertex vertex) {
        vertices.add(vertex);
    }

    public void addEdge(AsgEdge edge) {
        edges.add(edge);
    }

    public List<ITokenVertex> vertices() {
        return vertices;
    }

    public List<AsgEdge> edges() {
        return edges;
    }

    public ITokenVertex vertexById(int id) {
        return vertices.stream().filter(v -> v.id() == id).findFirst().orElse(null);
    }

    public List<ITokenVertex> verticesInRange(int startOffset, int endOffset) {
        return vertices.stream()
            .filter(v -> v.documentOffset() >= startOffset && v.documentOffset() < endOffset)
            .sorted(Comparator.comparingInt(ITokenVertex::id))
            .toList();
    }

    public ITokenVertex firstVertexInRange(int startOffset, int endOffset) {
        return vertices.stream()
            .filter(v -> v.documentOffset() >= startOffset && v.documentOffset() < endOffset)
            .min(Comparator.comparingInt(ITokenVertex::id))
            .orElse(null);
    }
}
