package com.graphbuilder.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AsgCycleExceptionTest {

    private static TokenVertex makeVertex(int id, TokenVertexCategory cat, String value, String path, int line) {
        return new TokenVertex(id, cat, value, 0, line, 0, path);
    }

    @Test
    void accessorsReturnConstructorArguments() {
        ITokenVertex source = makeVertex(1, TokenVertexCategory.CLASS, "MyClass", "Foo.java", 3);
        ITokenVertex target = makeVertex(2, TokenVertexCategory.CLASS_DECLARATION, "MyDecl", "Bar.java", 7);
        Throwable cause = new IllegalArgumentException("jgrapht says no");

        AsgCycleException ex = new AsgCycleException(source, target, EdgeCategory.ASSIGN, cause);

        assertSame(source, ex.source());
        assertSame(target, ex.target());
        assertSame(EdgeCategory.ASSIGN, ex.category());
        assertSame(cause, ex.getCause());
    }

    @Test
    void messageContainsBothVertexIds() {
        ITokenVertex source = makeVertex(42, TokenVertexCategory.CLASS, "SrcVal", "src/A.java", 10);
        ITokenVertex target = makeVertex(99, TokenVertexCategory.CLASS_DECLARATION, "TgtVal", "src/B.java", 20);
        AsgCycleException ex = new AsgCycleException(source, target, EdgeCategory.ASSIGN,
                new IllegalArgumentException("jgrapht says no"));

        String msg = ex.getMessage();
        assertTrue(msg.contains("42"), "message must contain source vertex id 42, was: " + msg);
        assertTrue(msg.contains("99"), "message must contain target vertex id 99, was: " + msg);
    }

    @Test
    void messageContainsBothCategoryNames() {
        ITokenVertex source = makeVertex(1, TokenVertexCategory.CLASS, "SrcVal", "src/A.java", 1);
        ITokenVertex target = makeVertex(2, TokenVertexCategory.CLASS_DECLARATION, "TgtVal", "src/B.java", 2);
        AsgCycleException ex = new AsgCycleException(source, target, EdgeCategory.ASSIGN,
                new IllegalArgumentException("jgrapht says no"));

        String msg = ex.getMessage();
        assertTrue(msg.contains(TokenVertexCategory.CLASS.name()),
                "message must contain source vertex category, was: " + msg);
        assertTrue(msg.contains(TokenVertexCategory.CLASS_DECLARATION.name()),
                "message must contain target vertex category, was: " + msg);
    }

    @Test
    void messageContainsBothValues() {
        ITokenVertex source = makeVertex(1, TokenVertexCategory.CLASS, "SourceValue", "src/A.java", 1);
        ITokenVertex target = makeVertex(2, TokenVertexCategory.CLASS_DECLARATION, "TargetValue", "src/B.java", 2);
        AsgCycleException ex = new AsgCycleException(source, target, EdgeCategory.ASSIGN,
                new IllegalArgumentException("jgrapht says no"));

        String msg = ex.getMessage();
        assertTrue(msg.contains("SourceValue"), "message must contain source value, was: " + msg);
        assertTrue(msg.contains("TargetValue"), "message must contain target value, was: " + msg);
    }

    @Test
    void messageContainsBothSourcePaths() {
        ITokenVertex source = makeVertex(1, TokenVertexCategory.CLASS, "v", "src/Alpha.java", 1);
        ITokenVertex target = makeVertex(2, TokenVertexCategory.CLASS_DECLARATION, "v", "src/Beta.java", 2);
        AsgCycleException ex = new AsgCycleException(source, target, EdgeCategory.ASSIGN,
                new IllegalArgumentException("jgrapht says no"));

        String msg = ex.getMessage();
        assertTrue(msg.contains("src/Alpha.java"), "message must contain source path, was: " + msg);
        assertTrue(msg.contains("src/Beta.java"), "message must contain target path, was: " + msg);
    }

    @Test
    void messageContainsEdgeCategoryName() {
        ITokenVertex source = makeVertex(1, TokenVertexCategory.CLASS, "v", "A.java", 1);
        ITokenVertex target = makeVertex(2, TokenVertexCategory.CLASS_DECLARATION, "v", "B.java", 2);
        AsgCycleException ex = new AsgCycleException(source, target, EdgeCategory.ASSIGN,
                new IllegalArgumentException("jgrapht says no"));

        String msg = ex.getMessage();
        assertTrue(msg.contains("ASSIGN"), "message must contain edge category ASSIGN, was: " + msg);
    }
}
