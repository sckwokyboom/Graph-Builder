package com.graphbuilder.export;

import com.graphbuilder.model.AsgGraph;
import com.graphbuilder.model.EdgeCategory;
import com.graphbuilder.model.TokenVertex;
import com.graphbuilder.model.TokenVertexCategory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AdjacencyMatrixExporterTest {

    @Test
    void matrixEncodesEdgeCategoryCodes() {
        var graph = new AsgGraph();
        var a = new TokenVertex(0, TokenVertexCategory.CLASS, "class", 0, 1, 0, "T.java");
        var b = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "Foo", 6, 1, 6, "T.java");
        var c = new TokenVertex(2, TokenVertexCategory.PUBLIC, "public", 20, 2, 0, "T.java");
        graph.addVertex(a);
        graph.addVertex(b);
        graph.addVertex(c);
        graph.addEdge(a, b, EdgeCategory.DECLARING);
        graph.addEdge(a, c, EdgeCategory.ATTRIBUTE);

        int[][] matrix = new AdjacencyMatrixExporter().toMatrix(graph);

        assertEquals(3, matrix.length);
        assertEquals(EdgeCategory.DECLARING.code(), matrix[0][1]);
        assertEquals(EdgeCategory.ATTRIBUTE.code(), matrix[0][2]);
        assertEquals(0, matrix[1][0]);
        assertEquals(0, matrix[2][0]);
        assertEquals(0, matrix[0][0]);
    }

    @Test
    void csvIsDenseAndCommaSeparated() {
        String csv = new AdjacencyMatrixExporter()
            .toCsv(new int[][] {{0, 8, 3}, {0, 0, 0}, {0, 0, 0}});

        assertEquals("0,8,3\n0,0,0\n0,0,0\n", csv);
    }

    @Test
    void legendMapsEachIndexToItsVertex() {
        var graph = new AsgGraph();
        var a = new TokenVertex(0, TokenVertexCategory.CLASS, "class", 0, 1, 0, "T.java");
        var b = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "Foo", 6, 1, 6, "T.java");
        graph.addVertex(a);
        graph.addVertex(b);

        String legend = new AdjacencyMatrixExporter().toLegend(graph);

        assertTrue(legend.startsWith("index\tid\tcategory\tvalue\n"));
        assertTrue(legend.contains("0\t0\tCLASS\tclass"));
        assertTrue(legend.contains("1\t1\tCLASS_DECLARATION\tFoo"));
    }

    @Test
    void legendSanitizesTabsAndNewlinesInValues() {
        var graph = new AsgGraph();
        var v = new TokenVertex(0, TokenVertexCategory.STRING_LITERAL, "a\tb\nc", 0, 1, 0, "T.java");
        graph.addVertex(v);

        String legend = new AdjacencyMatrixExporter().toLegend(graph);

        assertTrue(legend.contains("0\t0\tSTRING_LITERAL\ta b c"));
    }

    @Test
    void exportToFileWritesMatrixAndLegend(@org.junit.jupiter.api.io.TempDir Path dir) throws IOException {
        var graph = new AsgGraph();
        var a = new TokenVertex(0, TokenVertexCategory.CLASS, "class", 0, 1, 0, "T.java");
        var b = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "Foo", 6, 1, 6, "T.java");
        graph.addVertex(a);
        graph.addVertex(b);
        graph.addEdge(a, b, EdgeCategory.DECLARING);

        Path csv = dir.resolve("graph.csv");
        new AdjacencyMatrixExporter().exportToFile(graph, csv);

        Path legend = AdjacencyMatrixExporter.legendPathFor(csv);
        assertEquals(dir.resolve("graph.legend.tsv"), legend);
        assertTrue(Files.exists(csv));
        assertTrue(Files.exists(legend));
        assertEquals("0," + EdgeCategory.DECLARING.code() + "\n0,0\n", Files.readString(csv));
    }

    @Test
    void legendPathForHandlesFilenameWithoutExtension() {
        assertEquals(Path.of("/tmp/graph.legend.tsv"),
            AdjacencyMatrixExporter.legendPathFor(Path.of("/tmp/graph")));
    }
}
