package com.graphbuilder;

import com.graphbuilder.builder.*;
import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.AsgGraph;
import com.graphbuilder.model.GraphCycleDetector;
import com.graphbuilder.model.ITokenVertex;
import com.graphbuilder.parser.JdtParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class GraphBuilder {
    private final JdtParser parser = new JdtParser();
    private boolean validateDag = true;

    /**
     * Enable or disable DAG validation after graph construction.
     * When enabled (default), throws {@link GraphCycleException} if a cycle is detected.
     */
    public GraphBuilder validateDag(boolean validate) {
        this.validateDag = validate;
        return this;
    }

    public AsgGraph buildFromSource(String sourceCode, String sourcePath) {
        CompilationUnit cu = parser.parse(sourceCode, sourcePath);
        AsgGraph graph = new AsgGraph();
        BuildContext context = new BuildContext(cu, graph, sourcePath);

        new VertexBuilder().build(context);
        new StructuralEdgeBuilder().build(context);
        new DeclarationEdgeBuilder().build(context);
        new FlowEdgeBuilder().build(context);
        new TypeEdgeBuilder().build(context);

        if (validateDag) {
            List<ITokenVertex> cycle = GraphCycleDetector.findCycle(graph);
            if (!cycle.isEmpty()) {
                String path = cycle.stream()
                        .map(v -> v.category().dotLabel() + "(" + v.id() + "," + v.value() + ")")
                        .collect(Collectors.joining(" -> "));
                throw new GraphCycleException("ASG must be a DAG, but cycle was detected: " + path);
            }
        }
        return graph;
    }

    public AsgGraph buildFromFile(Path javaFile) throws IOException {
        String source = Files.readString(javaFile);
        return buildFromSource(source, javaFile.toString());
    }

    public AsgGraph buildFromFiles(List<Path> javaFiles) throws IOException {
        var sb = new StringBuilder();
        for (Path file : javaFiles) {
            sb.append(Files.readString(file)).append("\n");
        }
        return buildFromSource(sb.toString(), javaFiles.getFirst().toString());
    }

    /** Thrown when the built ASG contains a cycle. */
    public static class GraphCycleException extends RuntimeException {
        public GraphCycleException(String message) {
            super(message);
        }
    }
}
