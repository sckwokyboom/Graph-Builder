package com.graphbuilder;

import com.graphbuilder.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.graphbuilder.model.TokenVertexCategory.*;
import static com.graphbuilder.model.EdgeCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphBuilderIntegrationTest {

    private static AsgGraph graph;

    @BeforeAll
    static void buildGraph() throws IOException {
        String source = Files.readString(Path.of("src/test/resources/reference-example.java"));
        graph = new GraphBuilder().buildFromSource(source, "reference-example.java");
    }

    // --- Vertex tests ---

    @Test void hasClassVertices() {
        assertTrue(hasVertex(CLASS, "class"));
        assertTrue(hasVertex(CLASS_DECLARATION, "FileCollector"));
        assertTrue(hasVertex(CLASS_DECLARATION, "StreamFileCollector"));
    }

    @Test void hasFieldVertices() {
        assertTrue(hasVertex(PRIVATE, "private"));
        assertTrue(hasVertex(FINAL, "final"));
        assertTrue(hasVertex(TYPE_IDENTIFIER, "String"));
        assertTrue(hasVertex(FIELD_VAR_DECLARATION, "dirName"));
        assertTrue(hasVertex(TYPE_IDENTIFIER, "List"));
        assertTrue(hasVertex(TYPE_IDENTIFIER, "File"));
        assertTrue(hasVertex(FIELD_VAR_DECLARATION, "files"));
    }

    @Test void hasMethodVertices() {
        assertTrue(hasVertex(PUBLIC, "public"));
        assertTrue(hasVertex(TYPE_IDENTIFIER, "void"));
        assertTrue(hasVertex(METHOD_DECLARATION, "collect"));
        assertTrue(hasVertex(TYPE_IDENTIFIER, "Path"));
        assertTrue(hasVertex(PARAM_VAR_DECLARATION, "directory"));
        assertTrue(hasVertex(PARAM_VAR_DECLARATION, "depth"));
    }

    @Test void hasBodyVertices() {
        assertTrue(hasVertex(FIELD_VAR_ACCESS, "dirName"));
        assertTrue(hasVertex(PARAM_VAR_ACCESS, "directory"));
        assertTrue(hasVertex(METHOD_INVOCATION, "getFileName"));
        assertTrue(hasVertex(METHOD_INVOCATION, "toString"));
        assertTrue(hasVertex(LOCAL_VAR_DECLARATION, "filePathes"));
        assertTrue(hasVertex(FOR, "for"));
        assertTrue(hasVertex(LOCAL_VAR_DECLARATION, "filePathe"));
        assertTrue(hasVertex(FIELD_VAR_ACCESS, "files"));
        assertTrue(hasVertex(METHOD_INVOCATION, "add"));
        assertTrue(hasVertex(METHOD_INVOCATION, "toFile"));
        assertTrue(hasVertex(NEW, "new"));
        assertTrue(hasVertex(CONSTRUCTOR_INVOCATION, "ArrayList"));
    }

    @Test void hasStreamFileCollectorVertices() {
        assertTrue(hasVertex(TYPE_IDENTIFIER, "Stream"));
        assertTrue(hasVertex(LOCAL_VAR_DECLARATION, "stream"));
        assertTrue(hasVertex(METHOD_INVOCATION, "forEach"));
        assertTrue(hasVertex(LAMBDA_VAR_DECLARATION, "path"));
        assertTrue(hasVertex(LAMBDA_VAR_ACCESS, "path"));
        assertTrue(hasVertex(PARAM_VAR_ACCESS, "depth"));
        assertTrue(hasVertex(INTEGER_LITERAL, "1"));
    }

    // --- Edge tests ---

    @Test void declaringEdges() {
        assertTrue(hasEdge(CLASS, "class", CLASS_DECLARATION, "FileCollector", DECLARING));
        assertTrue(hasEdge(TYPE_IDENTIFIER, "String", FIELD_VAR_DECLARATION, "dirName", DECLARING));
        assertTrue(hasEdge(TYPE_IDENTIFIER, "void", METHOD_DECLARATION, "collect", DECLARING));
    }

    @Test void attributeEdges() {
        assertTrue(hasEdge(CLASS_DECLARATION, "FileCollector", PRIVATE, "private", ATTRIBUTE));
        assertTrue(hasEdge(CLASS_DECLARATION, "FileCollector", PUBLIC, "public", ATTRIBUTE));
        assertTrue(hasEdge(CLASS_DECLARATION, "StreamFileCollector", PUBLIC, "public", ATTRIBUTE));
    }

    @Test void callEdges() {
        assertTrue(hasEdge(PARAM_VAR_ACCESS, "directory", METHOD_INVOCATION, "getFileName", CALL));
        assertTrue(hasEdge(METHOD_INVOCATION, "getFileName", METHOD_INVOCATION, "toString", CALL));
        assertTrue(hasEdge(FIELD_VAR_ACCESS, "files", METHOD_INVOCATION, "add", CALL));
        assertTrue(hasEdge(LOCAL_VAR_ACCESS, "filePathe", METHOD_INVOCATION, "toFile", CALL));
        assertTrue(hasEdge(LOCAL_VAR_ACCESS, "stream", METHOD_INVOCATION, "forEach", CALL));
    }

    @Test void assignEdges() {
        assertTrue(hasEdge(FIELD_VAR_ACCESS, "dirName", PARAM_VAR_ACCESS, "directory", ASSIGN));
        assertTrue(hasEdge(FIELD_VAR_DECLARATION, "files", NEW, "new", ASSIGN));
    }

    @Test void genericEdges() {
        assertTrue(hasEdge(TYPE_IDENTIFIER, "List", TYPE_IDENTIFIER, "File", GENERIC));
        assertTrue(hasEdge(TYPE_IDENTIFIER, "List", TYPE_IDENTIFIER, "Path", GENERIC));
    }

    @Test void creationEdges() {
        assertTrue(hasEdge(NEW, "new", CONSTRUCTOR_INVOCATION, "ArrayList", CREATION));
    }

    @Test void statementEdges() {
        assertTrue(hasEdge(LAMBDA_VAR_DECLARATION, "path", METHOD_INVOCATION, "collect", STATEMENT));
    }

    @Test void controlFlowScopeEdges() {
        assertTrue(hasEdge(METHOD_DECLARATION, "collect", FOR, "for", CONTROL_FLOW_SCOPE));
    }

    @Test void operationEdges() {
        assertTrue(hasEdge(PARAM_VAR_ACCESS, "depth", INTEGER_LITERAL, "1", OPERATION));
    }

    @Test void formalParamEdges() {
        assertTrue(hasEdge(METHOD_DECLARATION, "collect", TYPE_IDENTIFIER, "Path", FORMAL_PARAMETER));
    }

    @Test void formalParamOnlyForFirstParameter() {
        // For collect(Path directory, int depth), METHOD_DECL should have FORMAL_PARAM
        // only to Path (first), NOT to int (second — reached via NEXT_DECL chain).
        boolean hasExtraEdge = graph.edges().stream().anyMatch(e ->
            e.source().category() == METHOD_DECLARATION && e.source().value().equals("collect") &&
            e.target().category() == TYPE_IDENTIFIER && e.target().value().equals("int") &&
            e.category() == FORMAL_PARAMETER);
        assertFalse(hasExtraEdge,
            "METHOD_DECL(collect) must not have FORMAL_PARAM edge to the second parameter's type");
    }

    @Test void graphIsDag() {
        List<ITokenVertex> cycle = GraphCycleDetector.findCycle(graph);
        assertTrue(cycle.isEmpty(),
            "Reference example must produce a DAG, but found cycle: " + cycle);
    }

    @Test void nextDeclEdges() {
        assertTrue(hasEdge(PARAM_VAR_DECLARATION, "directory", TYPE_IDENTIFIER, "int", NEXT_DECLARATION));
    }

    @Test void argumentEdges() {
        assertTrue(hasEdge(METHOD_INVOCATION, "forEach", LAMBDA_VAR_DECLARATION, "path", ARGUMENT));
        assertTrue(hasEdge(METHOD_INVOCATION, "collect", LAMBDA_VAR_ACCESS, "path", ARGUMENT));
        assertTrue(hasEdge(LAMBDA_VAR_ACCESS, "path", PARAM_VAR_ACCESS, "depth", ARGUMENT));
    }

    @Test void keywordChainEdges() {
        assertTrue(hasEdge(PRIVATE, "private", FINAL, "final", KEYWORD_CHAIN));
    }

    @Test void nextTokenEdges() {
        assertTrue(hasEdge(PUBLIC, "public", TYPE_IDENTIFIER, "void", NEXT_TOKEN));
    }

    @Test void dotExportWorks() {
        var exporter = new com.graphbuilder.export.DotExporter();
        String dot = exporter.export(graph);
        assertTrue(dot.startsWith("strict digraph G {"));
        assertTrue(dot.contains("CLASS_DECL"));
        assertTrue(dot.contains("DECLARING"));
    }

    // --- Helpers ---

    private boolean hasVertex(TokenVertexCategory category, String value) {
        return graph.vertices().stream()
            .anyMatch(v -> v.category() == category && v.value().equals(value));
    }

    private boolean hasEdge(TokenVertexCategory srcCat, String srcVal,
                            TokenVertexCategory tgtCat, String tgtVal, EdgeCategory edgeCat) {
        return graph.edges().stream().anyMatch(e ->
            e.source().category() == srcCat && e.source().value().equals(srcVal) &&
            e.target().category() == tgtCat && e.target().value().equals(tgtVal) &&
            e.category() == edgeCat);
    }
}
