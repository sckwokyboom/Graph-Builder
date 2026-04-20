package com.graphbuilder.export;

import com.graphbuilder.GraphBuilder;
import com.graphbuilder.model.AsgGraph;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DotExporterGoldenTest {

    @Test
    void referenceExampleMatchesGolden() throws IOException {
        AsgGraph graph = new GraphBuilder().buildFromFile(
            Path.of("src/test/resources/reference-example.java"));
        String actual = new DotExporter().export(graph);
        String expected = Files.readString(
            Path.of("src/test/resources/reference-example.expected.dot"));
        assertEquals(expected, actual,
            "DOT output drifted from golden. If the change is intentional, update the golden file.");
    }
}
