# jgrapht Migration Design Spec

## Overview

Replace the hand-rolled `AsgGraph` storage (backed by `List<ITokenVertex>` + `List<AsgEdge>`) and the custom `GraphCycleDetector` with `org.jgrapht:jgrapht-core`. Gain DAG invariant enforcement at construction time, plus built-in algorithms (topological sort, BFS/DFS iterators, shortest paths, SCC) that future features (retriever chain-finding, matrix/tensor export) will build on.

## Decisions

- **Graph type:** `DirectedAcyclicGraph<ITokenVertex, AsgEdge>` — cycle attempts throw at `addEdge` time. One edge per `(source, target)` pair; categories are single-valued.
- **API boundary:** `AsgGraph extends DirectedAcyclicGraph` (hybrid). Domain-specific shortcuts stay as methods on `AsgGraph`; all jgrapht algorithms available directly via inheritance.
- **Edge representation:** `AsgEdge extends DefaultEdge` with a single `EdgeCategory category` field. Override `getSource()` / `getTarget()` to expose them as public `source()` / `target()` returning `ITokenVertex`, preserving existing call-site signatures.
- **Cycle semantics:** a cycle is a construction bug. Translate jgrapht's `IllegalArgumentException` into a domain `AsgCycleException` with `source`, `target`, `category`, and a message that identifies the offending edge. Do not silence.
- **DOT exporter:** keep the existing custom `DotExporter`. No migration to jgrapht's `DOTExporter`. `output.dot` must remain byte-for-byte identical on the reference example.

## 1. Dependency

Add to `build.gradle.kts` under `dependencies`:

```kotlin
implementation("org.jgrapht:jgrapht-core:<latest>")
```

Version pinned to the latest stable release at the time of implementation (Java 21+ compatible). Exact version is selected in the implementation plan, not baked into this spec.

## 2. Model Changes

### `EdgeCategory`

Add a stable integer code to every variant:

```java
public enum EdgeCategory {
    ASSIGN(1), ARGUMENT(2), ATTRIBUTE(3), ANCESTOR(4), CALL(5),
    CREATION(6), CONTROL_FLOW_SCOPE(7), DECLARING(8), FORMAL_PARAMETER(9),
    GENERIC(10), IMPORTS(11), KEYWORD_CHAIN(12), NEXT_TOKEN(13),
    NEXT_DECLARATION(14), NEXT_ANCESTOR(15), OPERATION(16), STATEMENT(17),
    TYPE_ONTOLOGY(18), VARIABLE_ONTOLOGY(19), SYNTAX_LINK(20);

    private final int code;
    EdgeCategory(int code) { this.code = code; }
    public int code() { return code; }
}
```

Rationale: future matrix/tensor exporters will encode edge types as integers. Explicit codes make the mapping stable across enum reorderings — `ordinal()` would silently shift class labels and poison downstream models without any compile error.

### `TokenVertexCategory`

Apply the same treatment — explicit `int code()` per variant. Codes assigned in declaration order; any additions append.

### `AsgEdge`

Was:
```java
public record AsgEdge(ITokenVertex source, ITokenVertex target, EdgeCategory category) {}
```

Becomes:
```java
public class AsgEdge extends DefaultEdge {
    private final EdgeCategory category;
    public AsgEdge(EdgeCategory category) { this.category = category; }
    public EdgeCategory category() { return category; }
    public ITokenVertex source() { return (ITokenVertex) getSource(); }
    public ITokenVertex target() { return (ITokenVertex) getTarget(); }
}
```

Notes:
- `equals` / `hashCode` inherited from `DefaultEdge` (identity-based) — correct for the "one edge per pair" invariant.
- `source()` / `target()` are public methods delegating to the protected `getSource()` / `getTarget()` on `DefaultEdge`. Existing call sites (`edge.source()`, `edge.target()`, `edge.category()`) compile unchanged.

### `AsgGraph`

```java
public class AsgGraph extends DirectedAcyclicGraph<ITokenVertex, AsgEdge> {

    private final Map<Integer, ITokenVertex> byId = new HashMap<>();

    public AsgGraph() { super(AsgEdge.class); }

    @Override
    public boolean addVertex(ITokenVertex v) {
        boolean added = super.addVertex(v);
        if (added) byId.put(v.id(), v);
        return added;
    }

    public AsgEdge addEdge(ITokenVertex source, ITokenVertex target, EdgeCategory category) {
        AsgEdge edge = new AsgEdge(category);
        try {
            boolean added = addEdge(source, target, edge);
            if (!added) return null; // edge already existed
            return edge;
        } catch (IllegalArgumentException e) {
            // DirectedAcyclicGraph throws GraphCycleProhibitedException (subclass of IAE)
            throw new AsgCycleException(source, target, category, e);
        }
    }

    public ITokenVertex vertexById(int id) { return byId.get(id); }

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

    // Compatibility shims for existing DotExporter / tests that call .vertices() / .edges().
    public List<ITokenVertex> vertices() {
        return vertexSet().stream()
            .sorted(Comparator.comparingInt(ITokenVertex::id))
            .toList();
    }

    public List<AsgEdge> edges() { return List.copyOf(edgeSet()); }
}
```

Notes:
- `vertices()` preserves id-order that callers implicitly depend on (current `ArrayList` was insertion-ordered and ids are monotonic, so id-sort is equivalent).
- `edges()` order: jgrapht's `edgeSet()` returns a `Set` without strong ordering guarantees. The existing `DotExporter` must be checked to confirm it does not depend on insertion order of edges for its output; if it does, the exporter must sort deterministically (e.g. by `(source.id, target.id, category.code)`). This is the only potential source of `output.dot` drift.

### `AsgCycleException`

New class: `RuntimeException` with fields `ITokenVertex source`, `ITokenVertex target`, `EdgeCategory category`, and a message of the form:

```
Cycle detected when adding edge [{category}] from vertex #{source.id()}({source.category()} '{source.value()}' at {source.sourcePath()}:{source.line()}) to vertex #{target.id()}({target.category()} '{target.value()}' at {target.sourcePath()}:{target.line()})
```

This message gives enough to locate the buggy builder.

## 3. Removals

- `GraphCycleDetector.java` — deleted. Its role is now played by `DirectedAcyclicGraph` + `AsgCycleException`.
- `GraphCycleDetectorTest.java` — deleted. Replaced by `AsgGraphCycleRejectionTest`.

## 4. Builders

All five builders (`VertexBuilder`, `DeclarationEdgeBuilder`, `TypeEdgeBuilder`, `StructuralEdgeBuilder`, `FlowEdgeBuilder`) currently do:

```java
graph.addEdge(new AsgEdge(source, target, category));
```

This becomes:

```java
graph.addEdge(source, target, category);
```

No other logic changes. Builders continue to throw `AsgCycleException` up to the caller if construction is invalid — they do not catch it.

## 5. Tests

### Deleted
- `GraphCycleDetectorTest` — replaced (see below).

### New
- **`AsgGraphCycleRejectionTest`** — constructs `a → b → c`, attempts `c → a`, asserts `AsgCycleException` is thrown and that `source`, `target`, `category`, and message are populated correctly.
- **`EdgeCategoryCodeTest`** — asserts all `code()` values are unique and positive.
- **`TokenVertexCategoryCodeTest`** — same for vertex codes.

### Updated (minor)
- `AsgGraphTest` — `vertices()` / `edges()` / `vertexById` / `verticesInRange` / `firstVertexInRange` continue to pass, subject to the edge-ordering note in §2 and §8. Add a test case for `outgoingOf(v, category)`.
- Builder tests — logic unchanged; if any assert the ordering of `graph.edges()`, they must be adjusted in lockstep with the deterministic ordering introduced in §8.
- `DotExporterTest`, `GraphBuilderIntegrationTest` — must produce identical output. `DotExporter` gets an explicit sort by `(source.id, target.id, category.code)` applied to `graph.edges()` as part of this migration (see §8) to guarantee determinism regardless of `edgeSet()` iteration order.

## 6. Work Order

1. Add `jgrapht-core` dependency; verify build succeeds.
2. Add `int code()` to `EdgeCategory` and `TokenVertexCategory`; add `*CodeTest`.
3. Rewrite `AsgEdge` as `DefaultEdge` subclass. Compile — existing tests should still compile and pass because `.source()` / `.target()` / `.category()` are preserved.
4. Rewrite `AsgGraph` as `DirectedAcyclicGraph` subclass with domain shortcuts; add `AsgCycleException`.
5. Update builders' `addEdge` call sites. Run builder tests.
6. Delete `GraphCycleDetector` + test; add `AsgGraphCycleRejectionTest`.
7. Run `DotExporterTest` and `GraphBuilderIntegrationTest`. If `output.dot` drifts, add deterministic edge ordering in `DotExporter`.
8. Final full test run.

## 7. Out of Scope

- `findChain(from, to, Set<EdgeCategory>)` — retriever chain search. Separate spec when the retriever is built.
- Matrix / COO edge-list tensor exporter (torch-compatible). Separate spec when consumed by a Python pipeline.
- JSON / GraphML exporters.
- Parallel edges of different categories between the same vertex pair. Explicitly rejected: one edge per pair.
- Switching `DotExporter` to jgrapht's `DOTExporter`.

## 8. Risks

- **Edge iteration order in `DotExporter`.** `edgeSet()` from jgrapht is not order-guaranteed. If the current `DotExporter` relies on insertion order, `output.dot` will change. Mitigation: add explicit sort by `(source.id, target.id, category.code)` in the exporter as part of the migration, then diff `output.dot` against the committed reference.
- **jgrapht's cycle-check cost at `addEdge`.** `DirectedAcyclicGraph` uses an online topological-order maintenance algorithm — amortized cost is modest but not free. For ASGs of expected size (thousands of vertices, tens of thousands of edges) this is fine. If profiling shows a hotspot later, swap to plain `DefaultDirectedGraph` + a post-construction `CycleDetector` pass.
