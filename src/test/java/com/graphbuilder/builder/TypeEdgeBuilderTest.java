package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.*;
import com.graphbuilder.parser.JdtParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;

import static com.graphbuilder.model.EdgeCategory.*;
import static com.graphbuilder.model.TokenVertexCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class TypeEdgeBuilderTest {

    private BuildContext buildGraph(String source) {
        var parser = new JdtParser();
        CompilationUnit cu = parser.parse(source, "Test.java");
        var graph = new AsgGraph();
        var ctx = new BuildContext(cu, graph, "Test.java");
        new VertexBuilder().build(ctx);
        new StructuralEdgeBuilder().build(ctx);
        new DeclarationEdgeBuilder().build(ctx);
        new FlowEdgeBuilder().build(ctx);
        new TypeEdgeBuilder().build(ctx);
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
    void generic_parameterizedType() {
        var ctx = buildGraph("""
                import java.util.List;
                class Foo {
                    List<String> items;
                }
                """);
        assertTrue(hasEdge(ctx, TYPE_IDENTIFIER, "List", TYPE_IDENTIFIER, "String", GENERIC),
                "Expected GENERIC from TYPE_ID(List) to TYPE_ID(String)");
    }

    @Test
    void generic_nestedParameterizedType() {
        var ctx = buildGraph("""
                import java.util.Map;
                import java.util.List;
                class Foo {
                    Map<String, List<Integer>> items;
                }
                """);
        assertTrue(hasEdge(ctx, TYPE_IDENTIFIER, "Map", TYPE_IDENTIFIER, "String", GENERIC),
                "Expected GENERIC from TYPE_ID(Map) to TYPE_ID(String)");
        assertTrue(hasEdge(ctx, TYPE_IDENTIFIER, "Map", TYPE_IDENTIFIER, "List", GENERIC),
                "Expected GENERIC from TYPE_ID(Map) to TYPE_ID(List)");
        assertTrue(hasEdge(ctx, TYPE_IDENTIFIER, "List", TYPE_IDENTIFIER, "Integer", GENERIC),
                "Expected GENERIC from TYPE_ID(List) to TYPE_ID(Integer)");
    }

    @Test
    void ancestor_superclass() {
        var ctx = buildGraph("""
                class Base {}
                class Child extends Base {}
                """);
        assertTrue(hasEdge(ctx, CLASS_DECLARATION, "Child", TYPE_IDENTIFIER, "Base", ANCESTOR),
                "Expected ANCESTOR from CLASS_DECL(Child) to TYPE_ID(Base)");
    }

    @Test
    void ancestor_implementsInterface() {
        var ctx = buildGraph("""
                interface Iface {}
                class Impl implements Iface {}
                """);
        assertTrue(hasEdge(ctx, CLASS_DECLARATION, "Impl", TYPE_IDENTIFIER, "Iface", ANCESTOR),
                "Expected ANCESTOR from CLASS_DECL(Impl) to TYPE_ID(Iface)");
    }

    @Test
    void ancestor_multipleInterfaces_nextAncestor() {
        var ctx = buildGraph("""
                interface A {}
                interface B {}
                class Impl implements A, B {}
                """);
        assertTrue(hasEdge(ctx, CLASS_DECLARATION, "Impl", TYPE_IDENTIFIER, "A", ANCESTOR),
                "Expected ANCESTOR from CLASS_DECL(Impl) to TYPE_ID(A)");
        assertTrue(hasEdge(ctx, CLASS_DECLARATION, "Impl", TYPE_IDENTIFIER, "B", ANCESTOR),
                "Expected ANCESTOR from CLASS_DECL(Impl) to TYPE_ID(B)");
        assertTrue(hasEdge(ctx, TYPE_IDENTIFIER, "A", TYPE_IDENTIFIER, "B", NEXT_ANCESTOR),
                "Expected NEXT_ANCESTOR from TYPE_ID(A) to TYPE_ID(B)");
    }
}
