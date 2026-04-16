package com.graphbuilder;

import com.graphbuilder.builder.*;
import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.AsgGraph;
import com.graphbuilder.parser.JdtParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class GraphBuilder {
    private final JdtParser parser = new JdtParser();

    public AsgGraph buildFromSource(String sourceCode, String sourcePath) {
        CompilationUnit cu = parser.parse(sourceCode, sourcePath);
        AsgGraph graph = new AsgGraph();
        BuildContext context = new BuildContext(cu, graph, sourcePath);

        new VertexBuilder().build(context);
        new StructuralEdgeBuilder().build(context);
        new DeclarationEdgeBuilder().build(context);
        new FlowEdgeBuilder().build(context);
        new TypeEdgeBuilder().build(context);

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
}
