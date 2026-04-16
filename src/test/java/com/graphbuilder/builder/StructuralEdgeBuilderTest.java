package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.*;
import com.graphbuilder.parser.JdtParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;

import static com.graphbuilder.model.EdgeCategory.*;
import static com.graphbuilder.model.TokenVertexCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class StructuralEdgeBuilderTest {

    private BuildContext buildGraph(String source) {
        var parser = new JdtParser();
        CompilationUnit cu = parser.parse(source, "Test.java");
        var graph = new AsgGraph();
        var ctx = new BuildContext(cu, graph, "Test.java");
        new VertexBuilder().build(ctx);
        new StructuralEdgeBuilder().build(ctx);
        return ctx;
    }

    private boolean hasEdge(BuildContext ctx, TokenVertexCategory srcCat, String srcVal,
                            TokenVertexCategory tgtCat, String tgtVal, EdgeCategory edgeCat) {
        return ctx.graph().edges().stream().anyMatch(e ->
                e.source().category() == srcCat && e.source().value().equals(srcVal) &&
                e.target().category() == tgtCat && e.target().value().equals(tgtVal) &&
                e.category() == edgeCat);
    }

    @Test
    void keywordChain_consecutiveModifiers() {
        var ctx = buildGraph("class Foo { private final int x = 0; }");
        assertTrue(hasEdge(ctx, PRIVATE, "private", FINAL, "final", KEYWORD_CHAIN),
                "Expected KEYWORD_CHAIN from PRIVATE to FINAL");
    }

    @Test
    void nextToken_lastModifierToType() {
        var ctx = buildGraph("class Foo { private String name; }");
        assertTrue(hasEdge(ctx, PRIVATE, "private", TYPE_IDENTIFIER, "String", NEXT_TOKEN),
                "Expected NEXT_TOKEN from PRIVATE to TYPE_ID(String)");
    }

    @Test
    void nextToken_afterKeywordChain() {
        var ctx = buildGraph("class Foo { private final int x = 0; }");
        // PRIVATE -> FINAL should be KEYWORD_CHAIN, FINAL -> int should be NEXT_TOKEN
        assertTrue(hasEdge(ctx, FINAL, "final", TYPE_IDENTIFIER, "int", NEXT_TOKEN),
                "Expected NEXT_TOKEN from FINAL to TYPE_ID(int)");
        // PRIVATE should NOT have NEXT_TOKEN to int (it goes to FINAL via KEYWORD_CHAIN)
        assertFalse(hasEdge(ctx, PRIVATE, "private", TYPE_IDENTIFIER, "int", NEXT_TOKEN),
                "PRIVATE should not have NEXT_TOKEN to int when FINAL is between");
    }

    @Test
    void nextToken_publicToReturnType() {
        var ctx = buildGraph("class Foo { public void bar() {} }");
        assertTrue(hasEdge(ctx, PUBLIC, "public", TYPE_IDENTIFIER, "void", NEXT_TOKEN),
                "Expected NEXT_TOKEN from PUBLIC to TYPE_ID(void)");
    }

    @Test
    void nextToken_forEachVarToIterable() {
        var ctx = buildGraph("""
                class Foo {
                    String[] items;
                    void m() {
                        for (String s : items) {}
                    }
                }
                """);
        assertTrue(hasEdge(ctx, LOCAL_VAR_DECLARATION, "s", FIELD_VAR_ACCESS, "items", NEXT_TOKEN),
                "Expected NEXT_TOKEN from LOCAL_VAR_DECL(s) to FIELD_VAR_ACCESS(items)");
    }
}
