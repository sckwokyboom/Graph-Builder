# jgrapht Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hand-rolled `AsgGraph` storage and the custom `GraphCycleDetector` with `org.jgrapht:jgrapht-core`, enforcing the DAG invariant at edge-insertion time and unlocking built-in graph algorithms for future features.

**Architecture:** `AsgGraph` becomes a subclass of `DirectedAcyclicGraph<ITokenVertex, AsgEdge>`. `AsgEdge` becomes a `DefaultEdge` subclass with an `EdgeCategory category` field. Cycle attempts at `addEdge` are translated to a domain `AsgCycleException`. Domain helpers (`vertexById`, `verticesInRange`, `outgoingOf`, plus compatibility shims `vertices()` / `edges()`) stay on `AsgGraph`. `DotExporter` gains a deterministic edge ordering to keep `output.dot` stable against jgrapht's unordered `edgeSet()`.

**Tech Stack:** Java 25 (Gradle toolchain), `org.jgrapht:jgrapht-core` (pin latest stable 1.5.x at implementation time), JUnit 5, Eclipse JDT (unchanged).

**Source spec:** `docs/superpowers/specs/2026-04-20-jgrapht-migration-design.md`.

---

## File Map

**Create:**
- `src/main/java/com/graphbuilder/model/AsgCycleException.java` — domain exception carrying `source`, `target`, `category` for any rejected edge.
- `src/test/java/com/graphbuilder/model/AsgGraphCycleRejectionTest.java` — verifies DAG enforcement at `addEdge`.
- `src/test/java/com/graphbuilder/model/EdgeCategoryCodeTest.java` — verifies `EdgeCategory.code()` uniqueness/positivity.
- `src/test/java/com/graphbuilder/model/TokenVertexCategoryCodeTest.java` — same for `TokenVertexCategory.code()`.
- `src/test/resources/reference-example.expected.dot` — golden DOT output for the reference example.
- `src/test/java/com/graphbuilder/export/DotExporterGoldenTest.java` — byte-exact comparison against the golden file.

**Modify:**
- `build.gradle.kts` — add jgrapht dependency.
- `src/main/java/com/graphbuilder/model/EdgeCategory.java` — add `int code()`.
- `src/main/java/com/graphbuilder/model/TokenVertexCategory.java` — add `int code()`.
- `src/main/java/com/graphbuilder/model/AsgEdge.java` — rewrite as `DefaultEdge` subclass.
- `src/main/java/com/graphbuilder/model/AsgGraph.java` — rewrite as `DirectedAcyclicGraph` subclass with domain helpers.
- `src/main/java/com/graphbuilder/context/BuildContext.java` — new `addEdge` call-site.
- `src/main/java/com/graphbuilder/GraphBuilder.java` — remove `validateDag` + post-hoc `GraphCycleDetector` check + nested `GraphCycleException`.
- `src/main/java/com/graphbuilder/export/DotExporter.java` — deterministic edge sort before emit.
- `src/test/java/com/graphbuilder/model/AsgGraphTest.java` — adjust to new `addEdge` signature; add `outgoingOf` test.
- `src/test/java/com/graphbuilder/export/DotExporterTest.java` — adjust to new `addEdge` signature.
- `src/test/java/com/graphbuilder/GraphBuilderIntegrationTest.java` — replace `graphIsDag` test body with an `AsgCycleException` rejection check.

**Delete:**
- `src/main/java/com/graphbuilder/model/GraphCycleDetector.java`
- `src/test/java/com/graphbuilder/model/GraphCycleDetectorTest.java`

---

## Task 1: Add jgrapht dependency

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add dependency line**

Modify `build.gradle.kts`. Inside the existing `dependencies { ... }` block, add this line below the Eclipse JDT block and above `testImplementation`:

```kotlin
    implementation("org.jgrapht:jgrapht-core:1.5.2")
```

If a newer 1.5.x version exists at implementation time, use that; the test suite must pass against the pinned version. Do not jump to 2.x without updating this plan — the API is different.

- [ ] **Step 2: Verify resolution**

Run: `./gradlew --quiet dependencies --configuration runtimeClasspath | grep jgrapht`
Expected: line containing `org.jgrapht:jgrapht-core:1.5.2` (or the chosen version) and `org.jheaps:jheaps:0.14`.

- [ ] **Step 3: Verify compile still works**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. No source changes yet, so nothing should break.

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts
git commit -m "Added jgrapht-core dependency"
```

---

## Task 2: Add stable integer codes to `EdgeCategory`

**Files:**
- Test: `src/test/java/com/graphbuilder/model/EdgeCategoryCodeTest.java`
- Modify: `src/main/java/com/graphbuilder/model/EdgeCategory.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/graphbuilder/model/EdgeCategoryCodeTest.java`:

```java
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'com.graphbuilder.model.EdgeCategoryCodeTest'`
Expected: compile error — `EdgeCategory` has no method `code()`.

- [ ] **Step 3: Add `code()` to `EdgeCategory`**

Replace the body of `src/main/java/com/graphbuilder/model/EdgeCategory.java` with:

```java
package com.graphbuilder.model;

public enum EdgeCategory {
    ASSIGN("ASSIGN", 1),
    ARGUMENT("ARGUMENT", 2),
    ATTRIBUTE("ATTRIBUTE", 3),
    ANCESTOR("ANCESTOR", 4),
    CALL("CALL", 5),
    CREATION("CREATION", 6),
    CONTROL_FLOW_SCOPE("CONTROL_FLOW_SCOPE", 7),
    DECLARING("DECLARING", 8),
    FORMAL_PARAMETER("FORMAL_PARAM", 9),
    GENERIC("GENERIC", 10),
    IMPORTS("IMPORTS", 11),
    KEYWORD_CHAIN("KEYWORD_CHAIN", 12),
    NEXT_TOKEN("NEXT_TOKEN", 13),
    NEXT_DECLARATION("NEXT_DECL", 14),
    NEXT_ANCESTOR("NEXT_ANCESTOR", 15),
    OPERATION("OPERATION", 16),
    STATEMENT("STATEMENT", 17),
    TYPE_ONTOLOGY("TYPE_ONTOLOGY", 18),
    VARIABLE_ONTOLOGY("VARIABLE_ONTOLOGY", 19),
    SYNTAX_LINK("SYNTAX_LINK", 20);

    private final String dotLabel;
    private final int code;

    EdgeCategory(String dotLabel, int code) {
        this.dotLabel = dotLabel;
        this.code = code;
    }

    public String dotLabel() {
        return dotLabel;
    }

    public int code() {
        return code;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'com.graphbuilder.model.EdgeCategoryCodeTest'`
Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/graphbuilder/model/EdgeCategory.java \
        src/test/java/com/graphbuilder/model/EdgeCategoryCodeTest.java
git commit -m "Added stable int codes to EdgeCategory"
```

---

## Task 3: Add stable integer codes to `TokenVertexCategory`

**Files:**
- Test: `src/test/java/com/graphbuilder/model/TokenVertexCategoryCodeTest.java`
- Modify: `src/main/java/com/graphbuilder/model/TokenVertexCategory.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/graphbuilder/model/TokenVertexCategoryCodeTest.java`:

```java
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'com.graphbuilder.model.TokenVertexCategoryCodeTest'`
Expected: compile error — `TokenVertexCategory` has no method `code()`.

- [ ] **Step 3: Add `code()` to `TokenVertexCategory`**

Open `src/main/java/com/graphbuilder/model/TokenVertexCategory.java`. The enum has ~95 constants, each currently declared as `NAME("dotLabel")`. Convert each constant to `NAME("dotLabel", N)` where `N` is a sequential integer starting at 1 in declaration order. Then update the constructor and add the accessor.

Replace the body below the `package` line with:

```java
package com.graphbuilder.model;

public enum TokenVertexCategory {
    // Control flow
    ANNOTATION_DECLARATION("ANNOTATION_DECL", 1),
    ARROW("ARROW", 2),
    BREAK("BREAK", 3),
    CASE("CASE", 4),
    CATCH("CATCH", 5),
    CLASS_DECLARATION("CLASS_DECL", 6),
    CONSTRUCTOR_DECLARATION("CONSTRUCTOR_DECL", 7),
    CONTINUE("CONTINUE", 8),
    DO("DO", 9),
    ELSE("ELSE", 10),
    ENUM_DECLARATION("ENUM_DECL", 11),
    ENUM_CONSTANT_DECLARATION("ENUM_CONST_DECL", 12),
    FINALLY("FINALLY", 13),
    GOTO("GOTO", 14),
    IF("IF", 15),
    INTERFACE_DECLARATION("INTERFACE_DECL", 16),
    FIELD_VAR_DECLARATION("FIELD_VAR_DECL", 17),
    FOR("FOR", 18),
    LAMBDA_VAR_DECLARATION("LAMBDA_VAR_DECL", 19),
    LOCAL_VAR_DECLARATION("LOCAL_VAR_DECL", 20),
    METHOD_DECLARATION("METHOD_DECL", 21),
    PARAM_VAR_DECLARATION("PARAM_VAR_DECL", 22),
    SWITCH("SWITCH", 23),
    SYNCHRONIZED("SYNCHRONIZED", 24),
    THROW("THROW", 25),
    TRY("TRY", 26),
    WHILE("WHILE", 27),

    // Data flow
    ARITHMETIC_ASSIGN("ARITHMETIC_ASSIGN", 28),
    ARRAY_CONSTRUCTOR_INVOCATION("ARRAY_CONSTRUCTOR_INV", 29),
    BINARY_ARITHMETICS("BINARY_ARITHMETICS", 30),
    BINARY_BIT("BINARY_BIT", 31),
    BINARY_LOGICS("BINARY_LOGICS", 32),
    BIT_ASSIGN("BIT_ASSIGN", 33),
    BIT_LOGIC("BIT_LOGIC", 34),
    BOOLEAN_LITERAL("BOOLEAN_LITERAL", 35),
    CHAR_LITERAL("CHAR_LITERAL", 36),
    CONSTRUCTOR_INVOCATION("CONSTRUCTOR_INV", 37),
    COMPARISON("COMPARISON", 38),
    DOUBLE_LITERAL("DOUBLE_LITERAL", 39),
    EQUAL("EQUAL", 40),
    EQUAL_EQUAL("EQUAL_EQUAL", 41),
    FLOAT_LITERAL("FLOAT_LITERAL", 42),
    GREATER("GREATER", 43),
    INSTANCE_OF("INSTANCE_OF", 44),
    INTEGER_LITERAL("INTEGER_LITERAL", 45),
    FIELD_VAR_ACCESS("FIELD_VAR_ACCESS", 46),
    METHOD_INVOCATION("METHOD_INV", 47),
    METHOD_REFERENCE("METHOD_REF", 48),
    LAMBDA_VAR_ACCESS("LAMBDA_VAR_ACCESS", 49),
    LESS("LESS", 50),
    LOCAL_VAR_ACCESS("LOCAL_VAR_ACCESS", 51),
    LONG_LITERAL("LONG_LITERAL", 52),
    NULL_LITERAL("NULL", 53),
    PARAM_VAR_ACCESS("PARAM_VAR_ACCESS", 54),
    RETURN("RETURN", 55),
    STRING_LITERAL("STRING_LITERAL", 56),
    UNARY_ARITHMETICS("UNARY_ARITHMETICS", 57),
    UNARY_LOGICS("UNARY_LOGICS", 58),

    // Syntax / AST
    AT("AT", 59),
    ABSTRACT("ABSTRACT", 60),
    ARRAY_TYPE_IDENTIFIER("ARRAY_TYPE_ID", 61),
    ASSERT("ASSERT", 62),
    CLASS("CLASS", 63),
    COLON("COLON", 64),
    COLON_COLON("COLON_COLON", 65),
    COMMA("COMMA", 66),
    CONST("CONST", 67),
    DEFAULT("DEFAULT", 68),
    DOT_TOKEN("DOT_TOKEN", 69),
    ELLIPSIS("ELLIPSIS", 70),
    ENUM("ENUM", 71),
    EXTENDS("EXTENDS", 72),
    FINAL("FINAL", 73),
    IMPLEMENTS("IMPLEMENTS", 74),
    IMPORT("IMPORT", 75),
    INTERFACE("INTERFACE", 76),
    TYPE_IDENTIFIER("TYPE_ID", 77),
    LBRACE("LBRACE", 78),
    LBRACK("LBRACK", 79),
    LPAREN("LPAREN", 80),
    NATIVE("NATIVE", 81),
    NEW("NEW", 82),
    PACKAGE("PACKAGE", 83),
    PRIVATE("PRIVATE", 84),
    PROTECTED("PROTECTED", 85),
    PUBLIC("PUBLIC", 86),
    RBRACE("RBRACE", 87),
    RBRACK("RBRACK", 88),
    RPAREN("RPAREN", 89),
    QUESTION("QUESTION", 90),
    SEMICOLON("SEMICOLON", 91),
    STATIC("STATIC", 92),
    SUPER("SUPER", 93),
    THIS("THIS", 94),
    THROWS("THROWS", 95),
    TILDE("TILDE", 96),
    TRANSIENT("TRANSIENT", 97),
    VOLATILE("VOLATILE", 98);

    private final String dotLabel;
    private final int code;

    TokenVertexCategory(String dotLabel, int code) {
        this.dotLabel = dotLabel;
        this.code = code;
    }

    public String dotLabel() {
        return dotLabel;
    }

    public int code() {
        return code;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'com.graphbuilder.model.TokenVertexCategoryCodeTest'`
Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/graphbuilder/model/TokenVertexCategory.java \
        src/test/java/com/graphbuilder/model/TokenVertexCategoryCodeTest.java
git commit -m "Added stable int codes to TokenVertexCategory"
```

---

## Task 4: Capture golden DOT output as a regression fixture

This captures the current output before changing the exporter, so Task 8's deterministic sort can be verified as byte-exact.

**Files:**
- Create: `src/test/resources/reference-example.expected.dot`
- Create: `src/test/java/com/graphbuilder/export/DotExporterGoldenTest.java`

- [ ] **Step 1: Generate the golden file from current code**

The CLI (`com.graphbuilder.cli.Main`) takes a positional input path and an optional `-o` flag for the output file. From the project root, produce the golden DOT with the **current** (pre-migration) production code:

```bash
./gradlew run --args='src/test/resources/reference-example.java -o src/test/resources/reference-example.expected.dot' --console=plain
```

Confirm the file was created:
```bash
wc -l src/test/resources/reference-example.expected.dot
```
Expected: ~121 lines (matching the size of the pre-existing `output.dot` in the repo root).

- [ ] **Step 2: Write the golden test**

Create `src/test/java/com/graphbuilder/export/DotExporterGoldenTest.java`:

```java
package com.graphbuilder.export;

import com.graphbuilder.GraphBuilder;
import com.graphbuilder.model.AsgGraph;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DotExporterGoldenTest {

    @Test
    void referenceExampleMatchesGolden() throws IOException {
        AsgGraph graph = new GraphBuilder().buildFromFile(
            Path.of("src/test/resources/reference-example.java"));
        String actual = new DotExporter().export(graph);
        String expected = Files.readString(
            Path.of("src/test/resources/reference-example.expected.dot"));
        assertEquals(expected, actual,
            "DOT output drifted from golden. If the change is intentional, update the golden file.");
    }
}
```

- [ ] **Step 3: Run test to verify it passes on current code**

Run: `./gradlew test --tests 'com.graphbuilder.export.DotExporterGoldenTest'`
Expected: PASS. This baseline proves the golden captures today's output; any drift after the migration will be caught immediately.

- [ ] **Step 4: Commit**

```bash
git add src/test/resources/reference-example.expected.dot \
        src/test/java/com/graphbuilder/export/DotExporterGoldenTest.java
git commit -m "Added golden DOT fixture for reference example"
```

---

## Task 5: Add `AsgCycleException`

**Files:**
- Create: `src/main/java/com/graphbuilder/model/AsgCycleException.java`

No test yet — the exception is exercised in Task 7 after `AsgGraph` starts using it.

- [ ] **Step 1: Create the class**

Create `src/main/java/com/graphbuilder/model/AsgCycleException.java`:

```java
package com.graphbuilder.model;

/**
 * Thrown when an edge addition would introduce a cycle into the ASG.
 * Signals a graph-construction bug — the caller should not catch this.
 */
public class AsgCycleException extends RuntimeException {

    private final ITokenVertex source;
    private final ITokenVertex target;
    private final EdgeCategory category;

    public AsgCycleException(ITokenVertex source, ITokenVertex target,
                             EdgeCategory category, Throwable cause) {
        super(buildMessage(source, target, category), cause);
        this.source = source;
        this.target = target;
        this.category = category;
    }

    public ITokenVertex source() { return source; }
    public ITokenVertex target() { return target; }
    public EdgeCategory category() { return category; }

    private static String buildMessage(ITokenVertex s, ITokenVertex t, EdgeCategory c) {
        return "Cycle detected when adding edge [" + c.name() + "]"
            + " from vertex #" + s.id()
            + " (" + s.category().name() + " '" + s.value() + "'"
            + " at " + s.sourcePath() + ":" + s.line() + ")"
            + " to vertex #" + t.id()
            + " (" + t.category().name() + " '" + t.value() + "'"
            + " at " + t.sourcePath() + ":" + t.line() + ")";
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/graphbuilder/model/AsgCycleException.java
git commit -m "Added AsgCycleException"
```

---

## Task 6: Rewrite `AsgEdge` as `DefaultEdge` subclass

`AsgEdge` transitions from a record to a class extending `org.jgrapht.graph.DefaultEdge`. The public accessor surface (`source()`, `target()`, `category()`) is preserved so consumers don't change.

**Files:**
- Modify: `src/main/java/com/graphbuilder/model/AsgEdge.java`

- [ ] **Step 1: Replace `AsgEdge`**

Replace the entire contents of `src/main/java/com/graphbuilder/model/AsgEdge.java` with:

```java
package com.graphbuilder.model;

import org.jgrapht.graph.DefaultEdge;

public class AsgEdge extends DefaultEdge {

    private final EdgeCategory category;

    public AsgEdge(EdgeCategory category) {
        this.category = category;
    }

    public EdgeCategory category() {
        return category;
    }

    public ITokenVertex source() {
        return (ITokenVertex) getSource();
    }

    public ITokenVertex target() {
        return (ITokenVertex) getTarget();
    }
}
```

Notes:
- `getSource()` / `getTarget()` are `protected` on `DefaultEdge`; the public `source()` / `target()` wrappers are the supported way to expose them.
- Identity-based `equals` / `hashCode` (inherited from `Object` via `DefaultEdge`) are correct for our "one edge per (source, target)" invariant — each `AsgEdge` instance is unique by reference.
- No `record` and no `source` / `target` fields: endpoints live in the graph's internal structure, populated by jgrapht when the edge is inserted.

- [ ] **Step 2: Attempt compile**

Run: `./gradlew compileJava`
Expected: **fails** — every call site that did `new AsgEdge(source, target, category)` now has a signature mismatch. This is expected and will be fixed across Tasks 7–10. Leave the failure and move on.

The only known production call-site is `src/main/java/com/graphbuilder/context/BuildContext.java:70` (see Task 8). Test call-sites live in `AsgGraphTest`, `DotExporterTest`, and `GraphCycleDetectorTest` — all handled in later tasks.

Do NOT commit yet — the tree does not compile.

---

## Task 7: Rewrite `AsgGraph` as `DirectedAcyclicGraph` subclass

`AsgGraph` becomes a subclass of `org.jgrapht.graph.DirectedAcyclicGraph<ITokenVertex, AsgEdge>`. It keeps domain-specific helpers (`vertexById` with O(1) lookup, `verticesInRange`, `firstVertexInRange`, `outgoingOf`) and adds compatibility shims `vertices()` / `edges()` so the existing exporter and tests continue to compile.

**Files:**
- Modify: `src/main/java/com/graphbuilder/model/AsgGraph.java`

- [ ] **Step 1: Replace `AsgGraph`**

Replace the entire contents of `src/main/java/com/graphbuilder/model/AsgGraph.java` with:

```java
package com.graphbuilder.model;

import org.jgrapht.graph.DirectedAcyclicGraph;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsgGraph extends DirectedAcyclicGraph<ITokenVertex, AsgEdge> {

    private final Map<Integer, ITokenVertex> byId = new HashMap<>();

    public AsgGraph() {
        super(AsgEdge.class);
    }

    @Override
    public boolean addVertex(ITokenVertex v) {
        boolean added = super.addVertex(v);
        if (added) {
            byId.put(v.id(), v);
        }
        return added;
    }

    /**
     * Adds a categorized edge. Returns the inserted edge, or null if an edge
     * between source and target already existed. Throws {@link AsgCycleException}
     * if the edge would introduce a cycle.
     */
    public AsgEdge addEdge(ITokenVertex source, ITokenVertex target, EdgeCategory category) {
        AsgEdge edge = new AsgEdge(category);
        try {
            boolean added = super.addEdge(source, target, edge);
            return added ? edge : null;
        } catch (IllegalArgumentException e) {
            // DirectedAcyclicGraph.CycleFoundException (subtype of IAE) on cycle.
            throw new AsgCycleException(source, target, category, e);
        }
    }

    public ITokenVertex vertexById(int id) {
        return byId.get(id);
    }

    public List<ITokenVertex> verticesInRange(int startOffset, int endOffset) {
        return vertexSet().stream()
            .filter(v -> v.documentOffset() >= startOffset && v.documentOffset() < endOffset)
            .sorted(Comparator.comparingInt(ITokenVertex::id))
            .toList();
    }

    public ITokenVertex firstVertexInRange(int startOffset, int endOffset) {
        return vertexSet().stream()
            .filter(v -> v.documentOffset() >= startOffset && v.documentOffset() < endOffset)
            .min(Comparator.comparingInt(ITokenVertex::id))
            .orElse(null);
    }

    public List<AsgEdge> outgoingOf(ITokenVertex v, EdgeCategory category) {
        return outgoingEdgesOf(v).stream()
            .filter(e -> e.category() == category)
            .toList();
    }

    /**
     * Compatibility shim: returns vertices sorted by id (preserves historical insertion order,
     * since ids are assigned monotonically by {@link com.graphbuilder.context.BuildContext}).
     */
    public List<ITokenVertex> vertices() {
        return vertexSet().stream()
            .sorted(Comparator.comparingInt(ITokenVertex::id))
            .toList();
    }

    /**
     * Compatibility shim: returns edges in a deterministic order
     * (by source id, target id, category code) — jgrapht's {@link #edgeSet()} has no order guarantee.
     */
    public List<AsgEdge> edges() {
        return edgeSet().stream()
            .sorted(
                Comparator
                    .<AsgEdge>comparingInt(e -> e.source().id())
                    .thenComparingInt(e -> e.target().id())
                    .thenComparingInt(e -> e.category().code()))
            .toList();
    }
}
```

- [ ] **Step 2: Attempt compile**

Run: `./gradlew compileJava`
Expected: still fails — `BuildContext.java:70` still calls the old 3-arg `AsgEdge` constructor. Fixed in Task 8.

---

## Task 8: Migrate `BuildContext.addEdge` call-site

**Files:**
- Modify: `src/main/java/com/graphbuilder/context/BuildContext.java`

- [ ] **Step 1: Replace the addEdge body**

In `src/main/java/com/graphbuilder/context/BuildContext.java`, replace the existing `addEdge` method (lines 68-72):

```java
    public void addEdge(ITokenVertex source, ITokenVertex target, EdgeCategory category) {
        if (source != null && target != null) {
            graph.addEdge(new AsgEdge(source, target, category));
        }
    }
```

with:

```java
    public void addEdge(ITokenVertex source, ITokenVertex target, EdgeCategory category) {
        if (source != null && target != null) {
            graph.addEdge(source, target, category);
        }
    }
```

- [ ] **Step 2: Verify compile succeeds for main sources**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. Test sources still have stale references — handled in Task 10.

- [ ] **Step 3: Don't commit yet**

Keep this change in the working tree; it joins with subsequent fixes in a single commit at the end of Task 10.

---

## Task 9: Remove `validateDag` and post-hoc cycle check from `GraphBuilder`

With `DirectedAcyclicGraph` the DAG invariant is enforced at `addEdge`, so the post-hoc check is dead and the toggle is meaningless.

**Files:**
- Modify: `src/main/java/com/graphbuilder/GraphBuilder.java`

- [ ] **Step 1: Replace `GraphBuilder`**

Replace the entire contents of `src/main/java/com/graphbuilder/GraphBuilder.java` with:

```java
package com.graphbuilder;

import com.graphbuilder.builder.*;
import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.AsgGraph;
import com.graphbuilder.parser.JdtParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class GraphBuilder {
    private final JdtParser parser = new JdtParser();

    public AsgGraph buildFromSource(String sourceCode, String sourcePath) {
        CompilationUnit cu = parser.parse(sourceCode, sourcePath);
        AsgGraph graph = new AsgGraph();
        BuildContext context = new BuildContext(cu, graph, sourcePath);

        new VertexBuilder().build(context);
        new StructuralEdgeBuilder().build(context);
        new DeclarationEdgeBuilder().build(context);
        new FlowEdgeBuilder().build(context);
        new TypeEdgeBuilder().build(context);

        return graph;
    }

    public AsgGraph buildFromFile(Path javaFile) throws IOException {
        String source = Files.readString(javaFile);
        return buildFromSource(source, javaFile.toString());
    }

    public AsgGraph buildFromFiles(List<Path> javaFiles) throws IOException {
        var sb = new StringBuilder();
        for (Path file : javaFiles) {
            sb.append(Files.readString(file)).append("\n");
        }
        return buildFromSource(sb.toString(), javaFiles.getFirst().toString());
    }
}
```

Note: this removes `validateDag`, `GraphCycleException`, and the imports of `GraphCycleDetector` / `ITokenVertex` / `Collectors`. No external caller uses them (verified via grep over `src/`). Any builder that produces a cycle now throws `AsgCycleException` mid-build — the intended contract.

- [ ] **Step 2: Verify compile**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

---

## Task 10: Fix test call-sites for new `AsgEdge` / `AsgGraph` API

Three test files still reference the old 3-arg `AsgEdge` record. Two of them (`AsgGraphTest`, `DotExporterTest`) need minor updates. The third (`GraphCycleDetectorTest`) is deleted entirely in Task 11.

**Files:**
- Modify: `src/test/java/com/graphbuilder/model/AsgGraphTest.java`
- Modify: `src/test/java/com/graphbuilder/export/DotExporterTest.java`

- [ ] **Step 1: Update `AsgGraphTest`**

Replace `src/test/java/com/graphbuilder/model/AsgGraphTest.java` with:

```java
package com.graphbuilder.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AsgGraphTest {

    @Test
    void addVertexAndEdge() {
        var graph = new AsgGraph();
        var v0 = new TokenVertex(0, TokenVertexCategory.CLASS, "class", 0, 1, 0, "Test.java");
        var v1 = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "Foo", 6, 1, 6, "Test.java");
        graph.addVertex(v0);
        graph.addVertex(v1);
        graph.addEdge(v0, v1, EdgeCategory.DECLARING);

        assertEquals(2, graph.vertices().size());
        assertEquals(1, graph.edges().size());
        assertEquals(v0, graph.vertexById(0));
        assertEquals(v1, graph.vertexById(1));
    }

    @Test
    void verticesInRange() {
        var graph = new AsgGraph();
        var v0 = new TokenVertex(0, TokenVertexCategory.CLASS, "class", 0, 1, 0, "Test.java");
        var v1 = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "Foo", 6, 1, 6, "Test.java");
        var v2 = new TokenVertex(2, TokenVertexCategory.PUBLIC, "public", 20, 2, 0, "Test.java");
        graph.addVertex(v0);
        graph.addVertex(v1);
        graph.addVertex(v2);

        var inRange = graph.verticesInRange(0, 15);
        assertEquals(2, inRange.size());
        assertEquals(v0, inRange.get(0));
        assertEquals(v1, inRange.get(1));
    }

    @Test
    void outgoingOfFiltersByCategory() {
        var graph = new AsgGraph();
        var v0 = new TokenVertex(0, TokenVertexCategory.CLASS, "class", 0, 1, 0, "Test.java");
        var v1 = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "Foo", 6, 1, 6, "Test.java");
        var v2 = new TokenVertex(2, TokenVertexCategory.PUBLIC, "public", 20, 2, 0, "Test.java");
        graph.addVertex(v0);
        graph.addVertex(v1);
        graph.addVertex(v2);
        graph.addEdge(v0, v1, EdgeCategory.DECLARING);
        graph.addEdge(v0, v2, EdgeCategory.ATTRIBUTE);

        List<AsgEdge> declaring = graph.outgoingOf(v0, EdgeCategory.DECLARING);
        assertEquals(1, declaring.size());
        assertEquals(v1, declaring.get(0).target());

        List<AsgEdge> attribute = graph.outgoingOf(v0, EdgeCategory.ATTRIBUTE);
        assertEquals(1, attribute.size());
        assertEquals(v2, attribute.get(0).target());
    }
}
```

- [ ] **Step 2: Update `DotExporterTest`**

Open `src/test/java/com/graphbuilder/export/DotExporterTest.java`. Find the single call-site on line 16:

```java
        graph.addEdge(new AsgEdge(v0, v1, EdgeCategory.DECLARING));
```

Replace with:

```java
        graph.addEdge(v0, v1, EdgeCategory.DECLARING);
```

Do not modify anything else in that file.

- [ ] **Step 3: Build and run the existing passing tests**

Run: `./gradlew compileTestJava`
Expected: fails if `GraphCycleDetectorTest.java` still exists with stale references. That's addressed in Task 11. For now, confirm the two files you just edited compile cleanly — the error output should only reference `GraphCycleDetectorTest` and `GraphCycleDetector`.

- [ ] **Step 4: Don't commit yet**

Held together with Task 11.

---

## Task 11: Delete `GraphCycleDetector` and add the replacement test

**Files:**
- Delete: `src/main/java/com/graphbuilder/model/GraphCycleDetector.java`
- Delete: `src/test/java/com/graphbuilder/model/GraphCycleDetectorTest.java`
- Create: `src/test/java/com/graphbuilder/model/AsgGraphCycleRejectionTest.java`
- Modify: `src/test/java/com/graphbuilder/GraphBuilderIntegrationTest.java`

- [ ] **Step 1: Delete the detector and its test**

```bash
rm src/main/java/com/graphbuilder/model/GraphCycleDetector.java \
   src/test/java/com/graphbuilder/model/GraphCycleDetectorTest.java
```

- [ ] **Step 2: Write the rejection test**

Create `src/test/java/com/graphbuilder/model/AsgGraphCycleRejectionTest.java`:

```java
package com.graphbuilder.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AsgGraphCycleRejectionTest {

    @Test
    void rejectsEdgeThatIntroducesCycle() {
        var graph = new AsgGraph();
        var a = new TokenVertex(0, TokenVertexCategory.CLASS_DECLARATION, "A", 0, 1, 0, "T.java");
        var b = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "B", 5, 1, 5, "T.java");
        var c = new TokenVertex(2, TokenVertexCategory.CLASS_DECLARATION, "C", 10, 1, 10, "T.java");
        graph.addVertex(a);
        graph.addVertex(b);
        graph.addVertex(c);
        graph.addEdge(a, b, EdgeCategory.NEXT_TOKEN);
        graph.addEdge(b, c, EdgeCategory.NEXT_TOKEN);

        AsgCycleException ex = assertThrows(
            AsgCycleException.class,
            () -> graph.addEdge(c, a, EdgeCategory.NEXT_TOKEN));

        assertEquals(c, ex.source());
        assertEquals(a, ex.target());
        assertEquals(EdgeCategory.NEXT_TOKEN, ex.category());
        assertTrue(ex.getMessage().contains("NEXT_TOKEN"), "message should identify the category");
        assertTrue(ex.getMessage().contains("#0"), "message should identify target id");
        assertTrue(ex.getMessage().contains("#2"), "message should identify source id");
    }

    @Test
    void rejectsSelfLoop() {
        var graph = new AsgGraph();
        var a = new TokenVertex(0, TokenVertexCategory.CLASS_DECLARATION, "A", 0, 1, 0, "T.java");
        graph.addVertex(a);

        assertThrows(AsgCycleException.class,
            () -> graph.addEdge(a, a, EdgeCategory.NEXT_TOKEN));
    }

    @Test
    void acyclicGraphIsAccepted() {
        var graph = new AsgGraph();
        var a = new TokenVertex(0, TokenVertexCategory.CLASS_DECLARATION, "A", 0, 1, 0, "T.java");
        var b = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "B", 5, 1, 5, "T.java");
        var c = new TokenVertex(2, TokenVertexCategory.CLASS_DECLARATION, "C", 10, 1, 10, "T.java");
        var d = new TokenVertex(3, TokenVertexCategory.CLASS_DECLARATION, "D", 15, 1, 15, "T.java");
        graph.addVertex(a);
        graph.addVertex(b);
        graph.addVertex(c);
        graph.addVertex(d);
        graph.addEdge(a, b, EdgeCategory.NEXT_TOKEN);
        graph.addEdge(a, c, EdgeCategory.NEXT_TOKEN);
        graph.addEdge(b, d, EdgeCategory.NEXT_TOKEN);
        graph.addEdge(c, d, EdgeCategory.NEXT_TOKEN);

        assertEquals(4, graph.edges().size());
    }
}
```

- [ ] **Step 3: Update `GraphBuilderIntegrationTest.graphIsDag`**

Open `src/test/java/com/graphbuilder/GraphBuilderIntegrationTest.java`. The test at roughly lines 141-145 is:

```java
    @Test void graphIsDag() {
        List<ITokenVertex> cycle = GraphCycleDetector.findCycle(graph);
        assertTrue(cycle.isEmpty(),
            "Reference example must produce a DAG, but found cycle: " + cycle);
    }
```

Replace with a test that documents the new contract — graph construction itself is the DAG proof, because `AsgGraph` rejects cycles at insertion:

```java
    @Test void graphIsDag() {
        // Structural invariant: AsgGraph is a DirectedAcyclicGraph and rejects cycles at addEdge.
        // If @BeforeAll's buildGraph() returned normally, construction did not throw AsgCycleException
        // and the graph is guaranteed acyclic. Sanity-check that we actually have edges.
        assertFalse(graph.edges().isEmpty(), "Reference example should produce edges");
    }
```

Then, in the same file, remove the now-unused import `import com.graphbuilder.model.GraphCycleDetector;` if present, and remove the `import java.util.List;` import only if no other test in the file uses `List<...>`. (Grep the file to confirm before removing.)

- [ ] **Step 4: Run all tests**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`. All tests pass. In particular:
- `EdgeCategoryCodeTest`, `TokenVertexCategoryCodeTest` pass.
- `AsgGraphCycleRejectionTest` passes.
- `AsgGraphTest` (3 tests) passes.
- `DotExporterTest`, `DotExporterGoldenTest` pass byte-exact against the golden fixture.
- `GraphBuilderIntegrationTest` (all ~20 tests) pass.
- `JdtParserTest` and all `*EdgeBuilderTest` / `VertexBuilderTest` pass unchanged.

If `DotExporterGoldenTest` fails, jgrapht's `edgeSet()` returned edges in a different order and the deterministic sort in `AsgGraph.edges()` (Task 7) is either missing or has the wrong key. Verify Task 7 step 1 was applied as written. Do not regenerate the golden to silence the failure — that defeats the point of the test.

- [ ] **Step 5: Commit the full migration**

```bash
git add -A src/
git status
```

Confirm `git status` shows:
- Deleted: `src/main/java/com/graphbuilder/model/GraphCycleDetector.java`
- Deleted: `src/test/java/com/graphbuilder/model/GraphCycleDetectorTest.java`
- Modified: `src/main/java/com/graphbuilder/model/AsgEdge.java`, `AsgGraph.java`, `GraphBuilder.java`, `BuildContext.java`
- New: `src/main/java/com/graphbuilder/model/AsgCycleException.java`
- New: `src/test/java/com/graphbuilder/model/AsgGraphCycleRejectionTest.java`
- Modified: `src/test/java/com/graphbuilder/model/AsgGraphTest.java`, `src/test/java/com/graphbuilder/export/DotExporterTest.java`, `src/test/java/com/graphbuilder/GraphBuilderIntegrationTest.java`

```bash
git commit -m "Migrated AsgGraph to jgrapht DirectedAcyclicGraph

Replaces hand-rolled storage and cycle detector with jgrapht's
DirectedAcyclicGraph. Cycles now fail loudly at addEdge time via
AsgCycleException instead of being caught post-hoc. DotExporter sorts
edges deterministically to stay byte-stable against edgeSet()."
```

---

## Task 12: Final verification

- [ ] **Step 1: Full build with tests**

Run: `./gradlew clean build`
Expected: `BUILD SUCCESSFUL`. No compiler warnings beyond the baseline.

- [ ] **Step 2: Confirm dead code is gone**

Run these greps and expect no hits:

```bash
grep -r "GraphCycleDetector" src/ || echo "OK: no references"
grep -r "validateDag" src/ || echo "OK: no references"
grep -r "GraphBuilder.GraphCycleException" src/ || echo "OK: no references"
grep -r "new AsgEdge(" src/ || echo "OK: no references"
```

Each should print `OK: no references`. If any grep returns a match, investigate before closing out.

- [ ] **Step 3: Verify the golden DOT still matches live output**

Run the CLI once more end-to-end (same invocation shape as Task 4 Step 1) and diff the newly-produced file against the committed golden:

```bash
./gradlew run --args='src/test/resources/reference-example.java -o /tmp/migration-check.dot' --console=plain
diff /tmp/migration-check.dot src/test/resources/reference-example.expected.dot
```

Expected: no diff output.

- [ ] **Step 4: Done**

No final commit needed — verification only. If anything printed above was unexpected, pause and investigate before declaring the migration complete.

---

## Out of Scope (tracked in spec §7)

The following are explicitly **not** part of this plan. Do not slip them in:
- `findChain(from, to, Set<EdgeCategory>)` retriever helper.
- Torch-compatible COO edge-list (`edge_index` / `edge_attr`) tensor exporter.
- JSON / GraphML exporters.
- Parallel edges of different categories between the same vertex pair.
- Switching `DotExporter` to jgrapht's `DOTExporter`.
