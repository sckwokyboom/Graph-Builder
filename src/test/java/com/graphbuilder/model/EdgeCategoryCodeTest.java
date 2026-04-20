package com.graphbuilder.model;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class EdgeCategoryCodeTest {

    @Test
    void allCodesArePositive() {
        for (EdgeCategory c : EdgeCategory.values()) {
            assertTrue(c.code() > 0, c.name() + " must have a positive code, got " + c.code());
        }
    }

    @Test
    void allCodesAreUnique() {
        Set<Integer> seen = new HashSet<>();
        for (EdgeCategory c : EdgeCategory.values()) {
            assertTrue(seen.add(c.code()),
                "Duplicate code " + c.code() + " on " + c.name());
        }
    }

    @Test
    void assignHasStableCodeOne() {
        // Pinned to detect accidental reordering: ASSIGN is the first category
        // and its code is the contract the Python-side tensor exporter will rely on.
        assertEquals(1, EdgeCategory.ASSIGN.code());
    }

    @Test
    void syntaxLinkHasStableCodeTwenty() {
        // Pinned as the last-variant anchor: together with ASSIGN==1, this detects
        // silent renumbering of any variant in between (e.g. if someone adds a
        // new variant and shuffles the existing codes).
        assertEquals(20, EdgeCategory.SYNTAX_LINK.code());
    }
}
