package com.graphbuilder.export;

import com.graphbuilder.model.AsgEdge;
import com.graphbuilder.model.AsgGraph;
import com.graphbuilder.model.ITokenVertex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports the ASG as a dense adjacency matrix. {@code matrix[i][j]} holds the
 * {@code EdgeCategory.code()} of the edge from vertex {@code i} to vertex {@code j},
 * or {@code 0} when there is no such edge. Rows and columns are ordered by vertex id
 * (same order as {@link AsgGraph#vertices()}).
 *
 * <p>Companion legend maps each matrix index to the originating vertex so consumers
 * can interpret what each row represents.
 */
public class AdjacencyMatrixExporter {

    public int[][] toMatrix(AsgGraph graph) {
        List<ITokenVertex> vertices = graph.vertices();
        int n = vertices.size();
        Map<Integer, Integer> idToIndex = new HashMap<>(n);
        for (int i = 0; i < n; i++) {
            idToIndex.put(vertices.get(i).id(), i);
        }
        int[][] matrix = new int[n][n];
        for (AsgEdge edge : graph.edges()) {
            int row = idToIndex.get(edge.source().id());
            int col = idToIndex.get(edge.target().id());
            matrix[row][col] = edge.category().code();
        }
        return matrix;
    }

    public String toCsv(int[][] matrix) {
        var sb = new StringBuilder();
        for (int[] row : matrix) {
            for (int j = 0; j < row.length; j++) {
                if (j > 0) sb.append(',');
                sb.append(row[j]);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public String toLegend(AsgGraph graph) {
        var sb = new StringBuilder("index\tid\tcategory\tvalue\n");
        List<ITokenVertex> vertices = graph.vertices();
        for (int i = 0; i < vertices.size(); i++) {
            ITokenVertex v = vertices.get(i);
            sb.append(i).append('\t')
              .append(v.id()).append('\t')
              .append(v.category().name()).append('\t')
              .append(sanitize(v.value())).append('\n');
        }
        return sb.toString();
    }

    public void exportToFile(AsgGraph graph, Path csvPath) throws IOException {
        Files.writeString(csvPath, toCsv(toMatrix(graph)));
        Files.writeString(legendPathFor(csvPath), toLegend(graph));
    }

    public static Path legendPathFor(Path csvPath) {
        String name = csvPath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        String legendName = base + ".legend.tsv";
        Path parent = csvPath.getParent();
        return parent == null ? Path.of(legendName) : parent.resolve(legendName);
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
