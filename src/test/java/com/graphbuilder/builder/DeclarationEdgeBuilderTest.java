package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.*;
import com.graphbuilder.parser.JdtParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;

import static com.graphbuilder.model.EdgeCategory.*;
import static com.graphbuilder.model.TokenVertexCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class DeclarationEdgeBuilderTest {

    private BuildContext buildGraph(String source) {
        var parser = new JdtParser();
        CompilationUnit cu = parser.parse(source, "Test.java");
        var graph = new AsgGraph();
        var ctx = new BuildContext(cu, graph, "Test.java");
        new VertexBuilder().build(ctx);
        new StructuralEdgeBuilder().build(ctx);
        new DeclarationEdgeBuilder().build(ctx);
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
    void declaring_classToClassDecl() {
        var ctx = buildGraph("class Foo {}");
        assertTrue(hasEdge(ctx, CLASS, "class", CLASS_DECLARATION, "Foo", DECLARING),
                "Expected DECLARING from CLASS to CLASS_DECL");
    }

    @Test
    void declaring_typeIdToFieldDecl() {
        var ctx = buildGraph("class Foo { private String name; }");
        assertTrue(hasEdge(ctx, TYPE_IDENTIFIER, "String", FIELD_VAR_DECLARATION, "name", DECLARING),
                "Expected DECLARING from TYPE_ID(String) to FIELD_VAR_DECL(name)");
    }

    @Test
    void declaring_typeIdToMethodDecl() {
        var ctx = buildGraph("class Foo { public void bar() {} }");
        assertTrue(hasEdge(ctx, TYPE_IDENTIFIER, "void", METHOD_DECLARATION, "bar", DECLARING),
                "Expected DECLARING from TYPE_ID(void) to METHOD_DECL(bar)");
    }

    @Test
    void declaring_typeIdToParamDecl() {
        var ctx = buildGraph("class Foo { void m(String a) {} }");
        assertTrue(hasEdge(ctx, TYPE_IDENTIFIER, "String", PARAM_VAR_DECLARATION, "a", DECLARING),
                "Expected DECLARING from TYPE_ID(String) to PARAM_VAR_DECL(a)");
    }

    @Test
    void attribute_classDeclToMembers() {
        var ctx = buildGraph("""
                class Foo {
                    private String name;
                    public void bar() {}
                }
                """);
        assertTrue(hasEdge(ctx, CLASS_DECLARATION, "Foo", PRIVATE, "private", ATTRIBUTE),
                "Expected ATTRIBUTE from CLASS_DECL(Foo) to PRIVATE");
        assertTrue(hasEdge(ctx, CLASS_DECLARATION, "Foo", PUBLIC, "public", ATTRIBUTE),
                "Expected ATTRIBUTE from CLASS_DECL(Foo) to PUBLIC");
    }

    @Test
    void formalParameter_methodToParamType() {
        var ctx = buildGraph("class Foo { void m(String a, int b) {} }");
        assertTrue(hasEdge(ctx, METHOD_DECLARATION, "m", TYPE_IDENTIFIER, "String", FORMAL_PARAMETER),
                "Expected FORMAL_PARAM from METHOD_DECL(m) to TYPE_ID(String)");
        assertTrue(hasEdge(ctx, METHOD_DECLARATION, "m", TYPE_IDENTIFIER, "int", FORMAL_PARAMETER),
                "Expected FORMAL_PARAM from METHOD_DECL(m) to TYPE_ID(int)");
    }

    @Test
    void nextDeclaration_chainedParams() {
        var ctx = buildGraph("class Foo { void m(String a, int b) {} }");
        assertTrue(hasEdge(ctx, PARAM_VAR_DECLARATION, "a", TYPE_IDENTIFIER, "int", NEXT_DECLARATION),
                "Expected NEXT_DECL from PARAM_VAR_DECL(a) to TYPE_ID(int)");
    }

    @Test
    void attribute_fieldWithoutModifier() {
        // When a field has no modifier, the first vertex is the TYPE_ID
        var ctx = buildGraph("""
                class Foo {
                    String name;
                }
                """);
        assertTrue(hasEdge(ctx, CLASS_DECLARATION, "Foo", TYPE_IDENTIFIER, "String", ATTRIBUTE),
                "Expected ATTRIBUTE from CLASS_DECL(Foo) to TYPE_ID(String)");
    }
}
