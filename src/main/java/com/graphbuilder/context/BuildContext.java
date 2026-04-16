package com.graphbuilder.context;

import com.graphbuilder.model.*;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import java.util.*;

public class BuildContext {
    private final CompilationUnit compilationUnit;
    private final AsgGraph graph;
    private final String sourcePath;
    private final Map<ASTNode, List<ITokenVertex>> nodeVertexMap = new IdentityHashMap<>();
    private int nextId = 0;

    public BuildContext(CompilationUnit compilationUnit, AsgGraph graph, String sourcePath) {
        this.compilationUnit = compilationUnit;
        this.graph = graph;
        this.sourcePath = sourcePath;
    }

    public CompilationUnit compilationUnit() { return compilationUnit; }
    public AsgGraph graph() { return graph; }

    public ITokenVertex addVertex(TokenVertexCategory category, String value, ASTNode node) {
        int offset = node.getStartPosition();
        int line = compilationUnit.getLineNumber(offset);
        int column = compilationUnit.getColumnNumber(offset);
        var vertex = new TokenVertex(nextId++, category, value, offset, line, column, sourcePath);
        graph.addVertex(vertex);
        nodeVertexMap.computeIfAbsent(node, k -> new ArrayList<>()).add(vertex);
        return vertex;
    }

    public ITokenVertex addVertexAtOffset(TokenVertexCategory category, String value, int offset) {
        int line = compilationUnit.getLineNumber(offset);
        int column = compilationUnit.getColumnNumber(offset);
        var vertex = new TokenVertex(nextId++, category, value, offset, line, column, sourcePath);
        graph.addVertex(vertex);
        return vertex;
    }

    public void registerVertex(ITokenVertex vertex, ASTNode node) {
        nodeVertexMap.computeIfAbsent(node, k -> new ArrayList<>()).add(vertex);
    }

    public ITokenVertex findVertex(ASTNode node, TokenVertexCategory category) {
        return nodeVertexMap.getOrDefault(node, List.of()).stream()
            .filter(v -> v.category() == category)
            .findFirst().orElse(null);
    }

    public List<ITokenVertex> verticesFor(ASTNode node) {
        return nodeVertexMap.getOrDefault(node, List.of());
    }

    public ITokenVertex firstVertexInRange(ASTNode node) {
        int start = node.getStartPosition();
        int end = start + node.getLength();
        return graph.firstVertexInRange(start, end);
    }

    public List<ITokenVertex> verticesInRange(ASTNode node) {
        int start = node.getStartPosition();
        int end = start + node.getLength();
        return graph.verticesInRange(start, end);
    }

    public void addEdge(ITokenVertex source, ITokenVertex target, EdgeCategory category) {
        if (source != null && target != null) {
            graph.addEdge(new AsgEdge(source, target, category));
        }
    }
}
