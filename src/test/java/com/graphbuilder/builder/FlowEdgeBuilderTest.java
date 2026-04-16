package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.*;
import com.graphbuilder.parser.JdtParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;

import static com.graphbuilder.model.EdgeCategory.*;
import static com.graphbuilder.model.TokenVertexCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class FlowEdgeBuilderTest {

    private BuildContext buildGraph(String source) {
        var parser = new JdtParser();
        CompilationUnit cu = parser.parse(source, "Test.java");
        var graph = new AsgGraph();
        var ctx = new BuildContext(cu, graph, "Test.java");
        new VertexBuilder().build(ctx);
        new StructuralEdgeBuilder().build(ctx);
        new DeclarationEdgeBuilder().build(ctx);
        new FlowEdgeBuilder().build(ctx);
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
    void statement_methodBodyStatements() {
        var ctx = buildGraph("""
                class Foo {
                    String name;
                    void m(String val) {
                        name = val;
                    }
                }
                """);
        assertTrue(hasEdge(ctx, METHOD_DECLARATION, "m", FIELD_VAR_ACCESS, "name", STATEMENT),
                "Expected STATEMENT from METHOD_DECL(m) to FIELD_VAR_ACCESS(name)");
    }

    @Test
    void controlFlowScope_forLoop() {
        var ctx = buildGraph("""
                class Foo {
                    String[] items;
                    void m() {
                        for (String s : items) {}
                    }
                }
                """);
        assertTrue(hasEdge(ctx, METHOD_DECLARATION, "m", FOR, "for", CONTROL_FLOW_SCOPE),
                "Expected CONTROL_FLOW_SCOPE from METHOD_DECL to FOR");
    }

    @Test
    void assign_assignmentExpression() {
        var ctx = buildGraph("""
                class Foo {
                    String name;
                    void m(String val) {
                        name = val;
                    }
                }
                """);
        assertTrue(hasEdge(ctx, FIELD_VAR_ACCESS, "name", PARAM_VAR_ACCESS, "val", ASSIGN),
                "Expected ASSIGN from FIELD_VAR_ACCESS(name) to PARAM_VAR_ACCESS(val)");
    }

    @Test
    void assign_variableInitializer() {
        var ctx = buildGraph("""
                class Foo {
                    Object o = new Object();
                }
                """);
        assertTrue(hasEdge(ctx, FIELD_VAR_DECLARATION, "o", NEW, "new", ASSIGN),
                "Expected ASSIGN from FIELD_VAR_DECL(o) to NEW");
    }

    @Test
    void call_simpleMethodInvocation() {
        var ctx = buildGraph("""
                class Foo {
                    String name;
                    void m() {
                        name.toString();
                    }
                }
                """);
        assertTrue(hasEdge(ctx, FIELD_VAR_ACCESS, "name", METHOD_INVOCATION, "toString", CALL),
                "Expected CALL from FIELD_VAR_ACCESS(name) to METHOD_INV(toString)");
    }

    @Test
    void call_chainedMethodInvocation() {
        var ctx = buildGraph("""
                class Foo {
                    String name;
                    void m() {
                        name.toString().trim();
                    }
                }
                """);
        assertTrue(hasEdge(ctx, FIELD_VAR_ACCESS, "name", METHOD_INVOCATION, "toString", CALL),
                "Expected CALL from FIELD_VAR_ACCESS(name) to METHOD_INV(toString)");
        assertTrue(hasEdge(ctx, METHOD_INVOCATION, "toString", METHOD_INVOCATION, "trim", CALL),
                "Expected CALL from METHOD_INV(toString) to METHOD_INV(trim)");
    }

    @Test
    void argument_chainedArguments() {
        var ctx = buildGraph("""
                class Foo {
                    String name;
                    void m(String a, String b) {
                        name.substring(a, b);
                    }
                }
                """);
        // method -> first arg
        assertTrue(hasEdge(ctx, METHOD_INVOCATION, "substring", PARAM_VAR_ACCESS, "a", ARGUMENT),
                "Expected ARGUMENT from METHOD_INV(substring) to first arg");
        // first arg -> second arg (chained)
        assertTrue(hasEdge(ctx, PARAM_VAR_ACCESS, "a", PARAM_VAR_ACCESS, "b", ARGUMENT),
                "Expected chained ARGUMENT from first arg to second arg");
    }

    @Test
    void creation_newToConstructor() {
        var ctx = buildGraph("""
                class Foo {
                    Object o = new Object();
                }
                """);
        assertTrue(hasEdge(ctx, NEW, "new", CONSTRUCTOR_INVOCATION, "Object", CREATION),
                "Expected CREATION from NEW to CONSTRUCTOR_INV(Object)");
    }

    @Test
    void operation_infixExpression() {
        var ctx = buildGraph("""
                class Foo {
                    int x = 1 + 2;
                }
                """);
        assertTrue(hasEdge(ctx, INTEGER_LITERAL, "1", INTEGER_LITERAL, "2", OPERATION),
                "Expected OPERATION from INTEGER_LITERAL(1) to INTEGER_LITERAL(2)");
    }

    @Test
    void statement_forBodyStatements() {
        var ctx = buildGraph("""
                class Foo {
                    String[] items;
                    String name;
                    void m() {
                        for (String s : items) {
                            name = s;
                        }
                    }
                }
                """);
        assertTrue(hasEdge(ctx, FOR, "for", FIELD_VAR_ACCESS, "name", STATEMENT),
                "Expected STATEMENT from FOR to first vertex of body statement");
    }
}
