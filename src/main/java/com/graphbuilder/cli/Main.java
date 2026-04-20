package com.graphbuilder.cli;

import com.graphbuilder.GraphBuilder;
import com.graphbuilder.export.AdjacencyMatrixExporter;
import com.graphbuilder.export.DotExporter;
import com.graphbuilder.model.AsgGraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: graph-builder <file.java|directory> [-o output.dot] [-m matrix.csv]");
            System.exit(1);
        }

        List<Path> inputFiles = new ArrayList<>();
        Path dotOutput = null;
        Path matrixOutput = null;

        for (int i = 0; i < args.length; i++) {
            if ("-o".equals(args[i]) && i + 1 < args.length) {
                dotOutput = Path.of(args[++i]);
            } else if ("-m".equals(args[i]) && i + 1 < args.length) {
                matrixOutput = Path.of(args[++i]);
            } else {
                Path path = Path.of(args[i]);
                if (Files.isDirectory(path)) {
                    try (var stream = Files.walk(path)) {
                        stream.filter(p -> p.toString().endsWith(".java"))
                              .forEach(inputFiles::add);
                    } catch (IOException e) {
                        System.err.println("Error reading directory: " + e.getMessage());
                        System.exit(1);
                    }
                } else {
                    inputFiles.add(path);
                }
            }
        }

        if (inputFiles.isEmpty()) {
            System.err.println("No .java files found");
            System.exit(1);
        }

        try {
            var builder = new GraphBuilder();
            AsgGraph graph = builder.buildFromFiles(inputFiles);

            if (matrixOutput != null) {
                new AdjacencyMatrixExporter().exportToFile(graph, matrixOutput);
                Path legend = AdjacencyMatrixExporter.legendPathFor(matrixOutput);
                System.out.println("Matrix written to " + matrixOutput + " (legend at " + legend + ")");
            }

            var dotExporter = new DotExporter();
            if (dotOutput != null) {
                dotExporter.exportToFile(graph, dotOutput);
                System.out.println("Graph written to " + dotOutput);
            } else if (matrixOutput == null) {
                System.out.println(dotExporter.export(graph));
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
