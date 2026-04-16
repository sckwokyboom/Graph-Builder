package com.graphbuilder.parser;

import org.junit.jupiter.api.Test;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import static org.junit.jupiter.api.Assertions.*;

class JdtParserTest {

    @Test
    void parsesSimpleClass() {
        var parser = new JdtParser();
        CompilationUnit cu = parser.parse("class Foo {}", "Foo.java");
        assertNotNull(cu);
        assertEquals(1, cu.types().size());
        TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
        assertEquals("Foo", type.getName().getIdentifier());
    }

    @Test
    void parsesWithBindings() {
        var parser = new JdtParser();
        String source = """
            import java.util.List;
            class Foo {
                List<String> items;
            }
            """;
        CompilationUnit cu = parser.parse(source, "Foo.java");
        assertNotNull(cu);
        assertEquals(0, cu.getProblems().length);
    }
}
