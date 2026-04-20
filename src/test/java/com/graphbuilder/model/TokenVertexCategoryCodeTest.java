package com.graphbuilder.model;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TokenVertexCategoryCodeTest {

    @Test
    void allCodesArePositive() {
        for (TokenVertexCategory c : TokenVertexCategory.values()) {
            assertTrue(c.code() > 0, c.name() + " must have a positive code, got " + c.code());
        }
    }

    @Test
    void allCodesAreUnique() {
        Set<Integer> seen = new HashSet<>();
        for (TokenVertexCategory c : TokenVertexCategory.values()) {
            assertTrue(seen.add(c.code()),
                "Duplicate code " + c.code() + " on " + c.name());
        }
    }

    @Test
    void annotationDeclarationHasStableCodeOne() {
        // First-variant anchor for the Python tensor exporter contract.
        assertEquals(1, TokenVertexCategory.ANNOTATION_DECLARATION.code());
    }

    @Test
    void volatileHasStableCodeNinetyEight() {
        // Last-variant anchor: together with ANNOTATION_DECLARATION==1, detects
        // silent renumbering of any variant in between.
        assertEquals(98, TokenVertexCategory.VOLATILE.code());
    }
}
