package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.AsgGraph;
import com.graphbuilder.model.ITokenVertex;
import com.graphbuilder.model.TokenVertexCategory;
import com.graphbuilder.parser.JdtParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.graphbuilder.model.TokenVertexCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class VertexBuilderTest {

    private BuildContext buildVertices(String source) {
        var parser = new JdtParser();
        CompilationUnit cu = parser.parse(source, "Test.java");
        var graph = new AsgGraph();
        var context = new BuildContext(cu, graph, "Test.java");
        new VertexBuilder().build(context);
        return context;
    }

    private List<ITokenVertex> vertices(BuildContext context) {
        return context.graph().vertices();
    }

    private void assertVertex(ITokenVertex v, TokenVertexCategory expectedCategory, String expectedValue) {
        assertEquals(expectedCategory, v.category(),
                "Expected category " + expectedCategory + " but got " + v.category() + " for vertex '" + v.value() + "'");
        assertEquals(expectedValue, v.value(),
                "Expected value '" + expectedValue + "' but got '" + v.value() + "' for vertex id " + v.id());
    }

    @Test
    void simpleClass() {
        var ctx = buildVertices("class Foo {}");
        var verts = vertices(ctx);

        assertTrue(verts.size() >= 2, "Expected at least 2 vertices, got " + verts.size());
        assertVertex(verts.get(0), CLASS, "class");
        assertVertex(verts.get(1), CLASS_DECLARATION, "Foo");
    }

    @Test
    void fieldDeclaration() {
        var ctx = buildVertices("class Foo { private String name; }");
        var verts = vertices(ctx);

        // CLASS, CLASS_DECL, PRIVATE, TYPE_ID(String), FIELD_VAR_DECL(name)
        assertTrue(verts.size() >= 5, "Expected at least 5 vertices, got " + verts.size());

        assertVertex(verts.get(0), CLASS, "class");
        assertVertex(verts.get(1), CLASS_DECLARATION, "Foo");
        assertVertex(verts.get(2), PRIVATE, "private");
        assertVertex(verts.get(3), TYPE_IDENTIFIER, "String");
        assertVertex(verts.get(4), FIELD_VAR_DECLARATION, "name");
    }

    @Test
    void fieldWithModifierChain() {
        var ctx = buildVertices("class Foo { private final int x = 0; }");
        var verts = vertices(ctx);

        // CLASS, CLASS_DECL, PRIVATE, FINAL, TYPE_ID(int), FIELD_VAR_DECL(x), INTEGER_LITERAL(0)
        assertTrue(verts.size() >= 7, "Expected at least 7 vertices, got " + verts.size());

        assertVertex(verts.get(0), CLASS, "class");
        assertVertex(verts.get(1), CLASS_DECLARATION, "Foo");
        assertVertex(verts.get(2), PRIVATE, "private");
        assertVertex(verts.get(3), FINAL, "final");
        assertVertex(verts.get(4), TYPE_IDENTIFIER, "int");
        assertVertex(verts.get(5), FIELD_VAR_DECLARATION, "x");
        assertVertex(verts.get(6), INTEGER_LITERAL, "0");
    }

    @Test
    void methodDeclaration() {
        var ctx = buildVertices("class Foo { public void bar() {} }");
        var verts = vertices(ctx);

        // CLASS, CLASS_DECL, PUBLIC, TYPE_ID(void), METHOD_DECL(bar)
        assertTrue(verts.size() >= 5, "Expected at least 5 vertices, got " + verts.size());

        assertVertex(verts.get(0), CLASS, "class");
        assertVertex(verts.get(1), CLASS_DECLARATION, "Foo");
        assertVertex(verts.get(2), PUBLIC, "public");
        assertVertex(verts.get(3), TYPE_IDENTIFIER, "void");
        assertVertex(verts.get(4), METHOD_DECLARATION, "bar");
    }

    @Test
    void methodWithParams() {
        var ctx = buildVertices("class Foo { void m(String a, int b) {} }");
        var verts = vertices(ctx);

        // CLASS, CLASS_DECL, TYPE_ID(void), METHOD_DECL(m), TYPE_ID(String), PARAM_VAR_DECL(a), TYPE_ID(int), PARAM_VAR_DECL(b)
        assertTrue(verts.size() >= 8, "Expected at least 8 vertices, got " + verts.size());

        assertVertex(verts.get(0), CLASS, "class");
        assertVertex(verts.get(1), CLASS_DECLARATION, "Foo");
        assertVertex(verts.get(2), TYPE_IDENTIFIER, "void");
        assertVertex(verts.get(3), METHOD_DECLARATION, "m");
        assertVertex(verts.get(4), TYPE_IDENTIFIER, "String");
        assertVertex(verts.get(5), PARAM_VAR_DECLARATION, "a");
        assertVertex(verts.get(6), TYPE_IDENTIFIER, "int");
        assertVertex(verts.get(7), PARAM_VAR_DECLARATION, "b");
    }

    @Test
    void methodInvocationAndVarAccess() {
        var ctx = buildVertices("""
                class Foo {
                    String name;
                    void m() {
                        name.toString();
                    }
                }
                """);
        var verts = vertices(ctx);

        // Find the relevant vertices after the method declaration
        // CLASS, CLASS_DECL, TYPE_ID(String), FIELD_VAR_DECL(name), TYPE_ID(void), METHOD_DECL(m),
        // FIELD_VAR_ACCESS(name), METHOD_INV(toString)
        assertTrue(verts.size() >= 8, "Expected at least 8 vertices, got " + verts.size());

        // Verify field access and method invocation are present
        boolean hasFieldAccess = verts.stream()
                .anyMatch(v -> v.category() == FIELD_VAR_ACCESS && v.value().equals("name"));
        boolean hasMethodInvocation = verts.stream()
                .anyMatch(v -> v.category() == METHOD_INVOCATION && v.value().equals("toString"));

        assertTrue(hasFieldAccess, "Expected FIELD_VAR_ACCESS for 'name'");
        assertTrue(hasMethodInvocation, "Expected METHOD_INVOCATION for 'toString'");

        // Verify ordering: field access before method invocation
        int fieldAccessIdx = -1;
        int methodInvIdx = -1;
        for (int i = 0; i < verts.size(); i++) {
            if (verts.get(i).category() == FIELD_VAR_ACCESS && verts.get(i).value().equals("name")) {
                fieldAccessIdx = i;
            }
            if (verts.get(i).category() == METHOD_INVOCATION && verts.get(i).value().equals("toString")) {
                methodInvIdx = i;
            }
        }
        assertTrue(fieldAccessIdx < methodInvIdx, "FIELD_VAR_ACCESS should come before METHOD_INVOCATION");
    }

    @Test
    void newAndConstructorInvocation() {
        var ctx = buildVertices("""
                class Foo {
                    Object o = new Object();
                }
                """);
        var verts = vertices(ctx);

        boolean hasNew = verts.stream()
                .anyMatch(v -> v.category() == NEW && v.value().equals("new"));
        boolean hasConstructorInv = verts.stream()
                .anyMatch(v -> v.category() == CONSTRUCTOR_INVOCATION && v.value().equals("Object"));

        assertTrue(hasNew, "Expected NEW vertex");
        assertTrue(hasConstructorInv, "Expected CONSTRUCTOR_INVOCATION for 'Object'");

        // NEW should come before CONSTRUCTOR_INVOCATION
        int newIdx = -1;
        int ctorIdx = -1;
        for (int i = 0; i < verts.size(); i++) {
            if (verts.get(i).category() == NEW && newIdx == -1) newIdx = i;
            if (verts.get(i).category() == CONSTRUCTOR_INVOCATION && verts.get(i).value().equals("Object") && ctorIdx == -1) {
                ctorIdx = i;
            }
        }
        assertTrue(newIdx < ctorIdx, "NEW should come before CONSTRUCTOR_INVOCATION");
    }

    @Test
    void forEachLoop() {
        var ctx = buildVertices("""
                class Foo {
                    void m() {
                        int[] arr = new int[0];
                        for (int x : arr) {}
                    }
                }
                """);
        var verts = vertices(ctx);

        boolean hasFor = verts.stream()
                .anyMatch(v -> v.category() == FOR && v.value().equals("for"));
        assertTrue(hasFor, "Expected FOR vertex");

        // The loop variable 'x' in enhanced-for should be LOCAL_VAR_DECLARATION
        boolean hasLocalVarDecl = verts.stream()
                .anyMatch(v -> v.category() == LOCAL_VAR_DECLARATION && v.value().equals("x"));
        assertTrue(hasLocalVarDecl, "Expected LOCAL_VAR_DECLARATION for enhanced-for variable 'x'");
    }

    @Test
    void lambdaExpression() {
        var ctx = buildVertices("""
                import java.util.function.Consumer;
                class Foo {
                    Consumer<String> c = (String s) -> {};
                }
                """);
        var verts = vertices(ctx);

        // Lambda parameter 's' should be PARAM_VAR_DECLARATION (since it's a SingleVariableDeclaration
        // in a LambdaExpression, and LambdaExpression is neither EnhancedForStatement nor CatchClause)
        // But the task says LAMBDA_VAR_DECL -- this is for VariableDeclarationFragment in LambdaExpression
        // For (s) -> {} without type, s is a VariableDeclarationFragment in LambdaExpression

        // Let's test with inferred type lambda
        var ctx2 = buildVertices("""
                import java.util.function.Consumer;
                class Foo {
                    Consumer<String> c = s -> {};
                }
                """);
        var verts2 = vertices(ctx2);

        boolean hasLambdaVarDecl = verts2.stream()
                .anyMatch(v -> v.category() == LAMBDA_VAR_DECLARATION && v.value().equals("s"));
        assertTrue(hasLambdaVarDecl, "Expected LAMBDA_VAR_DECLARATION for lambda parameter 's'");
    }

    @Test
    void genericType() {
        var ctx = buildVertices("""
                import java.util.List;
                class Foo {
                    List<String> items;
                }
                """);
        var verts = vertices(ctx);

        long typeIdCount = verts.stream()
                .filter(v -> v.category() == TYPE_IDENTIFIER)
                .count();
        assertTrue(typeIdCount >= 2, "Expected at least 2 TYPE_IDENTIFIER vertices (List, String), got " + typeIdCount);

        boolean hasList = verts.stream()
                .anyMatch(v -> v.category() == TYPE_IDENTIFIER && v.value().equals("List"));
        boolean hasString = verts.stream()
                .anyMatch(v -> v.category() == TYPE_IDENTIFIER && v.value().equals("String"));

        assertTrue(hasList, "Expected TYPE_IDENTIFIER for 'List'");
        assertTrue(hasString, "Expected TYPE_IDENTIFIER for 'String'");
    }
}
