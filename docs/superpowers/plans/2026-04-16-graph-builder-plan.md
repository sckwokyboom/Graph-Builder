# Graph-Builder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java 23+ tool that constructs Abstract Semantic Graphs (ASG) from Java source code, outputting DOT format, using Eclipse JDT for parsing.

**Architecture:** Multi-pass pipeline. Phase 0 parses via Eclipse JDT. Phase 1 creates vertices. Phases 2–5 add edges by category. Each phase is an isolated ASTVisitor operating on a shared BuildContext. A shared `findFirstVertexInRange(ASTNode)` method lets edge builders locate vertices by source position without complex mappings.

**Tech Stack:** Java 23+, Gradle (Kotlin DSL), Eclipse JDT Core 3.40.0+, JUnit 5

---

## File Structure

```
graph-builder/
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/java/com/graphbuilder/
│   ├── model/
│   │   ├── TokenVertexCategory.java    # enum, ~60 values, each with dotLabel()
│   │   ├── EdgeCategory.java           # enum, ~20 values, each with dotLabel()
│   │   ├── ITokenVertex.java           # interface: id, category, value, offset, line, col, sourcePath
│   │   ├── TokenVertex.java            # record implementing ITokenVertex
│   │   ├── AsgEdge.java                # record(source, target, category)
│   │   └── AsgGraph.java               # ordered lists of vertices + edges, lookup helpers
│   ├── parser/
│   │   └── JdtParser.java              # wraps ASTParser, returns CompilationUnit
│   ├── context/
│   │   └── BuildContext.java           # shared state: CompilationUnit + AsgGraph + node-to-vertex map
│   ├── builder/
│   │   ├── VertexBuilder.java          # phase 1: ASTVisitor → vertices
│   │   ├── StructuralEdgeBuilder.java  # phase 2: NEXT_TOKEN, KEYWORD_CHAIN
│   │   ├── DeclarationEdgeBuilder.java # phase 3: DECLARING, ATTRIBUTE, FORMAL_PARAM, NEXT_DECL
│   │   ├── FlowEdgeBuilder.java        # phase 4: STATEMENT, CONTROL_FLOW_SCOPE, ASSIGN, CALL, ARGUMENT, OPERATION, CREATION
│   │   └── TypeEdgeBuilder.java        # phase 5: GENERIC, TYPE_ONTOLOGY, VARIABLE_ONTOLOGY, ANCESTOR, IMPORTS, NEXT_ANCESTOR
│   ├── export/
│   │   └── DotExporter.java            # AsgGraph → DOT string
│   ├── GraphBuilder.java               # facade: parser + all phases
│   └── cli/
│       └── Main.java                   # CLI entry point
└── src/test/java/com/graphbuilder/
    ├── GraphBuilderIntegrationTest.java # reference example end-to-end
    ├── builder/
    │   ├── VertexBuilderTest.java
    │   ├── StructuralEdgeBuilderTest.java
    │   ├── DeclarationEdgeBuilderTest.java
    │   ├── FlowEdgeBuilderTest.java
    │   └── TypeEdgeBuilderTest.java
    └── export/
        └── DotExporterTest.java
```

---

### Task 1: Project Scaffolding

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`

- [ ] **Step 1: Create settings.gradle.kts**

```kotlin
rootProject.name = "graph-builder"
```

- [ ] **Step 2: Create build.gradle.kts**

```kotlin
plugins {
    java
    application
}

group = "com.graphbuilder"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(23)
    }
}

application {
    mainClass = "com.graphbuilder.cli.Main"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.eclipse.jdt:org.eclipse.jdt.core:3.40.0")
    implementation("org.eclipse.platform:org.eclipse.core.runtime:3.31.100")
    implementation("org.eclipse.platform:org.eclipse.core.resources:3.21.0")
    implementation("org.eclipse.platform:org.eclipse.core.contenttype:3.9.400")
    implementation("org.eclipse.platform:org.eclipse.equinox.common:3.19.100")
    implementation("org.eclipse.platform:org.eclipse.equinox.preferences:3.11.100")
    implementation("org.eclipse.platform:org.eclipse.core.jobs:3.15.300")
    implementation("org.eclipse.platform:org.eclipse.text:3.14.100")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.graphbuilder.cli.Main"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
```

- [ ] **Step 3: Create directory structure**

Run:
```bash
mkdir -p src/main/java/com/graphbuilder/{model,parser,context,builder,export,cli}
mkdir -p src/test/java/com/graphbuilder/{builder,export}
```

- [ ] **Step 4: Verify Gradle resolves dependencies**

Run: `./gradlew dependencies --configuration compileClasspath`
Expected: dependencies resolve successfully. If JDT Core version 3.40.0 is not found, try the latest available version on Maven Central.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts
git commit -m "Added Gradle project scaffolding"
```

---

### Task 2: Core Model

**Files:**
- Create: `src/main/java/com/graphbuilder/model/TokenVertexCategory.java`
- Create: `src/main/java/com/graphbuilder/model/EdgeCategory.java`
- Create: `src/main/java/com/graphbuilder/model/ITokenVertex.java`
- Create: `src/main/java/com/graphbuilder/model/TokenVertex.java`
- Create: `src/main/java/com/graphbuilder/model/AsgEdge.java`
- Create: `src/main/java/com/graphbuilder/model/AsgGraph.java`
- Test: `src/test/java/com/graphbuilder/model/AsgGraphTest.java`

- [ ] **Step 1: Write AsgGraph test**

```java
package com.graphbuilder.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AsgGraphTest {

    @Test
    void addVertexAndEdge() {
        var graph = new AsgGraph();
        var v0 = new TokenVertex(0, TokenVertexCategory.CLASS, "class", 0, 1, 0, "Test.java");
        var v1 = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "Foo", 6, 1, 6, "Test.java");
        graph.addVertex(v0);
        graph.addVertex(v1);
        graph.addEdge(new AsgEdge(v0, v1, EdgeCategory.DECLARING));

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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.graphbuilder.model.AsgGraphTest"`
Expected: compilation error — classes don't exist yet.

- [ ] **Step 3: Create TokenVertexCategory enum**

```java
package com.graphbuilder.model;

public enum TokenVertexCategory {
    // Control flow
    ANNOTATION_DECLARATION("ANNOTATION_DECL"),
    ARROW("ARROW"),
    BREAK("BREAK"),
    CASE("CASE"),
    CATCH("CATCH"),
    CLASS_DECLARATION("CLASS_DECL"),
    CONSTRUCTOR_DECLARATION("CONSTRUCTOR_DECL"),
    CONTINUE("CONTINUE"),
    DO("DO"),
    ELSE("ELSE"),
    ENUM_DECLARATION("ENUM_DECL"),
    ENUM_CONSTANT_DECLARATION("ENUM_CONST_DECL"),
    FINALLY("FINALLY"),
    GOTO("GOTO"),
    IF("IF"),
    INTERFACE_DECLARATION("INTERFACE_DECL"),
    FIELD_VAR_DECLARATION("FIELD_VAR_DECL"),
    FOR("FOR"),
    LAMBDA_VAR_DECLARATION("LAMBDA_VAR_DECL"),
    LOCAL_VAR_DECLARATION("LOCAL_VAR_DECL"),
    METHOD_DECLARATION("METHOD_DECL"),
    PARAM_VAR_DECLARATION("PARAM_VAR_DECL"),
    SWITCH("SWITCH"),
    SYNCHRONIZED("SYNCHRONIZED"),
    THROW("THROW"),
    TRY("TRY"),
    WHILE("WHILE"),

    // Data flow
    ARITHMETIC_ASSIGN("ARITHMETIC_ASSIGN"),
    ARRAY_CONSTRUCTOR_INVOCATION("ARRAY_CONSTRUCTOR_INV"),
    BINARY_ARITHMETICS("BINARY_ARITHMETICS"),
    BINARY_BIT("BINARY_BIT"),
    BINARY_LOGICS("BINARY_LOGICS"),
    BIT_ASSIGN("BIT_ASSIGN"),
    BIT_LOGIC("BIT_LOGIC"),
    BOOLEAN_LITERAL("BOOLEAN_LITERAL"),
    CHAR_LITERAL("CHAR_LITERAL"),
    CONSTRUCTOR_INVOCATION("CONSTRUCTOR_INV"),
    COMPARISON("COMPARISON"),
    DOUBLE_LITERAL("DOUBLE_LITERAL"),
    EQUAL("EQUAL"),
    EQUAL_EQUAL("EQUAL_EQUAL"),
    FLOAT_LITERAL("FLOAT_LITERAL"),
    GREATER("GREATER"),
    INSTANCE_OF("INSTANCE_OF"),
    INTEGER_LITERAL("INTEGER_LITERAL"),
    FIELD_VAR_ACCESS("FIELD_VAR_ACCESS"),
    METHOD_INVOCATION("METHOD_INV"),
    METHOD_REFERENCE("METHOD_REF"),
    LAMBDA_VAR_ACCESS("LAMBDA_VAR_ACCESS"),
    LESS("LESS"),
    LOCAL_VAR_ACCESS("LOCAL_VAR_ACCESS"),
    LONG_LITERAL("LONG_LITERAL"),
    NULL_LITERAL("NULL"),
    PARAM_VAR_ACCESS("PARAM_VAR_ACCESS"),
    RETURN("RETURN"),
    STRING_LITERAL("STRING_LITERAL"),
    UNARY_ARITHMETICS("UNARY_ARITHMETICS"),
    UNARY_LOGICS("UNARY_LOGICS"),

    // Syntax / AST
    AT("AT"),
    ABSTRACT("ABSTRACT"),
    ARRAY_TYPE_IDENTIFIER("ARRAY_TYPE_ID"),
    ASSERT("ASSERT"),
    CLASS("CLASS"),
    COLON("COLON"),
    COLON_COLON("COLON_COLON"),
    COMMA("COMMA"),
    CONST("CONST"),
    DEFAULT("DEFAULT"),
    DOT_TOKEN("DOT_TOKEN"),
    ELLIPSIS("ELLIPSIS"),
    ENUM("ENUM"),
    EXTENDS("EXTENDS"),
    FINAL("FINAL"),
    IMPLEMENTS("IMPLEMENTS"),
    IMPORT("IMPORT"),
    INTERFACE("INTERFACE"),
    TYPE_IDENTIFIER("TYPE_ID"),
    LBRACE("LBRACE"),
    LBRACK("LBRACK"),
    LPAREN("LPAREN"),
    NATIVE("NATIVE"),
    NEW("NEW"),
    PACKAGE("PACKAGE"),
    PRIVATE("PRIVATE"),
    PROTECTED("PROTECTED"),
    PUBLIC("PUBLIC"),
    RBRACE("RBRACE"),
    RBRACK("RBRACK"),
    RPAREN("RPAREN"),
    QUESTION("QUESTION"),
    SEMICOLON("SEMICOLON"),
    STATIC("STATIC"),
    SUPER("SUPER"),
    THIS("THIS"),
    THROWS("THROWS"),
    TILDE("TILDE"),
    TRANSIENT("TRANSIENT"),
    VOLATILE("VOLATILE");

    private final String dotLabel;

    TokenVertexCategory(String dotLabel) {
        this.dotLabel = dotLabel;
    }

    public String dotLabel() {
        return dotLabel;
    }
}
```

- [ ] **Step 4: Create EdgeCategory enum**

```java
package com.graphbuilder.model;

public enum EdgeCategory {
    ASSIGN("ASSIGN"),
    ARGUMENT("ARGUMENT"),
    ATTRIBUTE("ATTRIBUTE"),
    ANCESTOR("ANCESTOR"),
    CALL("CALL"),
    CREATION("CREATION"),
    CONTROL_FLOW_SCOPE("CONTROL_FLOW_SCOPE"),
    DECLARING("DECLARING"),
    FORMAL_PARAMETER("FORMAL_PARAM"),
    GENERIC("GENERIC"),
    IMPORTS("IMPORTS"),
    KEYWORD_CHAIN("KEYWORD_CHAIN"),
    NEXT_TOKEN("NEXT_TOKEN"),
    NEXT_DECLARATION("NEXT_DECL"),
    NEXT_ANCESTOR("NEXT_ANCESTOR"),
    OPERATION("OPERATION"),
    STATEMENT("STATEMENT"),
    TYPE_ONTOLOGY("TYPE_ONTOLOGY"),
    VARIABLE_ONTOLOGY("VARIABLE_ONTOLOGY"),
    SYNTAX_LINK("SYNTAX_LINK");

    private final String dotLabel;

    EdgeCategory(String dotLabel) {
        this.dotLabel = dotLabel;
    }

    public String dotLabel() {
        return dotLabel;
    }
}
```

- [ ] **Step 5: Create ITokenVertex, TokenVertex, AsgEdge**

```java
package com.graphbuilder.model;

public interface ITokenVertex {
    int id();
    TokenVertexCategory category();
    String value();
    int documentOffset();
    int line();
    int column();
    String sourcePath();
}
```

```java
package com.graphbuilder.model;

public record TokenVertex(
    int id,
    TokenVertexCategory category,
    String value,
    int documentOffset,
    int line,
    int column,
    String sourcePath
) implements ITokenVertex {}
```

```java
package com.graphbuilder.model;

public record AsgEdge(
    ITokenVertex source,
    ITokenVertex target,
    EdgeCategory category
) {}
```

- [ ] **Step 6: Create AsgGraph**

```java
package com.graphbuilder.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AsgGraph {
    private final List<ITokenVertex> vertices = new ArrayList<>();
    private final List<AsgEdge> edges = new ArrayList<>();

    public void addVertex(ITokenVertex vertex) {
        vertices.add(vertex);
    }

    public void addEdge(AsgEdge edge) {
        edges.add(edge);
    }

    public List<ITokenVertex> vertices() {
        return vertices;
    }

    public List<AsgEdge> edges() {
        return edges;
    }

    public ITokenVertex vertexById(int id) {
        return vertices.stream().filter(v -> v.id() == id).findFirst().orElse(null);
    }

    public List<ITokenVertex> verticesInRange(int startOffset, int endOffset) {
        return vertices.stream()
            .filter(v -> v.documentOffset() >= startOffset && v.documentOffset() < endOffset)
            .sorted(Comparator.comparingInt(ITokenVertex::id))
            .toList();
    }

    public ITokenVertex firstVertexInRange(int startOffset, int endOffset) {
        return vertices.stream()
            .filter(v -> v.documentOffset() >= startOffset && v.documentOffset() < endOffset)
            .min(Comparator.comparingInt(ITokenVertex::id))
            .orElse(null);
    }
}
```

- [ ] **Step 7: Run tests**

Run: `./gradlew test --tests "com.graphbuilder.model.AsgGraphTest"`
Expected: all tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/
git commit -m "Added core model: enums, ITokenVertex, TokenVertex, AsgEdge, AsgGraph"
```

---

### Task 3: JdtParser + BuildContext

**Files:**
- Create: `src/main/java/com/graphbuilder/parser/JdtParser.java`
- Create: `src/main/java/com/graphbuilder/context/BuildContext.java`
- Test: `src/test/java/com/graphbuilder/parser/JdtParserTest.java`

- [ ] **Step 1: Write JdtParser test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.graphbuilder.parser.JdtParserTest"`
Expected: compilation error — JdtParser doesn't exist.

- [ ] **Step 3: Implement JdtParser**

```java
package com.graphbuilder.parser;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.util.Map;

public class JdtParser {

    public CompilationUnit parse(String sourceCode, String unitName) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(sourceCode.toCharArray());
        parser.setUnitName(unitName);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setEnvironment(null, null, null, true);

        Map<String, String> options = Map.of(
            "org.eclipse.jdt.core.compiler.source", "23",
            "org.eclipse.jdt.core.compiler.compliance", "23",
            "org.eclipse.jdt.core.compiler.codegen.targetPlatform", "23"
        );
        parser.setCompilerOptions(options);

        return (CompilationUnit) parser.createAST(null);
    }
}
```

- [ ] **Step 4: Implement BuildContext**

```java
package com.graphbuilder.context;

import com.graphbuilder.model.*;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.util.*;

public class BuildContext {
    private final CompilationUnit compilationUnit;
    private final AsgGraph graph;
    private final String sourcePath;
    private final Map<ASTNode, List<ITokenVertex>> nodeVertexMap = new IdentityHashMap<>();
    private int nextId = 0;

    public BuildContext(CompilationUnit compilationUnit, AsgGraph graph, String sourcePath) {
        this.compilationUnit = compilationUnit;
        this.graph = graph;
        this.sourcePath = sourcePath;
    }

    public CompilationUnit compilationUnit() {
        return compilationUnit;
    }

    public AsgGraph graph() {
        return graph;
    }

    public ITokenVertex addVertex(TokenVertexCategory category, String value, ASTNode node) {
        int offset = node.getStartPosition();
        int line = compilationUnit.getLineNumber(offset);
        int column = compilationUnit.getColumnNumber(offset);
        var vertex = new TokenVertex(nextId++, category, value, offset, line, column, sourcePath);
        graph.addVertex(vertex);
        nodeVertexMap.computeIfAbsent(node, k -> new ArrayList<>()).add(vertex);
        return vertex;
    }

    public ITokenVertex addVertexAtOffset(TokenVertexCategory category, String value, int offset) {
        int line = compilationUnit.getLineNumber(offset);
        int column = compilationUnit.getColumnNumber(offset);
        var vertex = new TokenVertex(nextId++, category, value, offset, line, column, sourcePath);
        graph.addVertex(vertex);
        return vertex;
    }

    public void registerVertex(ITokenVertex vertex, ASTNode node) {
        nodeVertexMap.computeIfAbsent(node, k -> new ArrayList<>()).add(vertex);
    }

    public ITokenVertex findVertex(ASTNode node, TokenVertexCategory category) {
        return nodeVertexMap.getOrDefault(node, List.of()).stream()
            .filter(v -> v.category() == category)
            .findFirst().orElse(null);
    }

    public List<ITokenVertex> verticesFor(ASTNode node) {
        return nodeVertexMap.getOrDefault(node, List.of());
    }

    public ITokenVertex firstVertexInRange(ASTNode node) {
        int start = node.getStartPosition();
        int end = start + node.getLength();
        return graph.firstVertexInRange(start, end);
    }

    public List<ITokenVertex> verticesInRange(ASTNode node) {
        int start = node.getStartPosition();
        int end = start + node.getLength();
        return graph.verticesInRange(start, end);
    }

    public void addEdge(ITokenVertex source, ITokenVertex target, EdgeCategory category) {
        if (source != null && target != null) {
            graph.addEdge(new AsgEdge(source, target, category));
        }
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew test --tests "com.graphbuilder.parser.JdtParserTest"`
Expected: all tests pass. If dependency resolution fails, adjust Eclipse JDT version in build.gradle.kts to latest available.

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "Added JdtParser and BuildContext"
```

---

### Task 4: DotExporter

**Files:**
- Create: `src/main/java/com/graphbuilder/export/DotExporter.java`
- Test: `src/test/java/com/graphbuilder/export/DotExporterTest.java`

- [ ] **Step 1: Write DotExporter test**

```java
package com.graphbuilder.export;

import com.graphbuilder.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DotExporterTest {

    @Test
    void exportSimpleGraph() {
        var graph = new AsgGraph();
        var v0 = new TokenVertex(0, TokenVertexCategory.CLASS, "class", 0, 1, 0, "Test.java");
        var v1 = new TokenVertex(1, TokenVertexCategory.CLASS_DECLARATION, "Foo", 6, 1, 6, "Test.java");
        graph.addVertex(v0);
        graph.addVertex(v1);
        graph.addEdge(new AsgEdge(v0, v1, EdgeCategory.DECLARING));

        var exporter = new DotExporter();
        String dot = exporter.export(graph);

        assertTrue(dot.startsWith("strict digraph G {"));
        assertTrue(dot.contains("\"CLASS (0)\nclass\""));
        assertTrue(dot.contains("\"CLASS_DECL (1)\nFoo\""));
        assertTrue(dot.contains("-> \"CLASS_DECL (1)\nFoo\""));
        assertTrue(dot.contains("label=\"DECLARING\""));
        assertTrue(dot.endsWith("}\n"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.graphbuilder.export.DotExporterTest"`
Expected: compilation error.

- [ ] **Step 3: Implement DotExporter**

```java
package com.graphbuilder.export;

import com.graphbuilder.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class DotExporter {

    public String export(AsgGraph graph) {
        var sb = new StringBuilder();
        sb.append("strict digraph G {\n");
        sb.append("  ordering=in;\n");

        for (ITokenVertex v : graph.vertices()) {
            String nodeId = nodeId(v);
            sb.append("  ").append(quote(nodeId))
              .append(" [ label=").append(quote(quote(nodeId)))
              .append(" penwidth=\"3\" shape=\"rect\" style=\"rounded\" fontname=\"Helvetica-Bold\" ];\n");
        }

        for (AsgEdge e : graph.edges()) {
            String srcId = nodeId(e.source());
            String tgtId = nodeId(e.target());
            sb.append("  ").append(quote(srcId))
              .append(" -> ").append(quote(tgtId))
              .append(" [ color=\"blue\" fontcolor=\"blue\" fontname=\"Helvetica-Bold\" penwidth=\"2\" label=")
              .append(quote(e.category().dotLabel()))
              .append(" ];\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    public void exportToFile(AsgGraph graph, Path outputFile) throws IOException {
        Files.writeString(outputFile, export(graph));
    }

    private String nodeId(ITokenVertex v) {
        return v.category().dotLabel() + " (" + v.id() + ")\n" + v.value();
    }

    private String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "com.graphbuilder.export.DotExporterTest"`
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "Added DotExporter"
```

---

### Task 5: VertexBuilder

**Files:**
- Create: `src/main/java/com/graphbuilder/builder/VertexBuilder.java`
- Test: `src/test/java/com/graphbuilder/builder/VertexBuilderTest.java`

- [ ] **Step 1: Write tests for class/field/method declarations**

```java
package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.*;
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

    @Test
    void simpleClass() {
        var ctx = buildVertices("class Foo {}");
        var vertices = ctx.graph().vertices();
        assertEquals(CLASS, vertices.get(0).category());
        assertEquals("class", vertices.get(0).value());
        assertEquals(CLASS_DECLARATION, vertices.get(1).category());
        assertEquals("Foo", vertices.get(1).value());
    }

    @Test
    void fieldDeclaration() {
        var ctx = buildVertices("class Foo { private String name; }");
        var vertices = ctx.graph().vertices();
        // CLASS(0), CLASS_DECL(1), PRIVATE(2), TYPE_ID(3) String, FIELD_VAR_DECL(4) name
        assertEquals(PRIVATE, vertices.get(2).category());
        assertEquals(TYPE_IDENTIFIER, vertices.get(3).category());
        assertEquals("String", vertices.get(3).value());
        assertEquals(FIELD_VAR_DECLARATION, vertices.get(4).category());
        assertEquals("name", vertices.get(4).value());
    }

    @Test
    void fieldWithModifierChain() {
        var ctx = buildVertices("class Foo { private final int x = 0; }");
        var vertices = ctx.graph().vertices();
        // CLASS(0), CLASS_DECL(1), PRIVATE(2), FINAL(3), TYPE_ID(4) int, FIELD_VAR_DECL(5) x, INTEGER_LITERAL(6) 0
        assertEquals(PRIVATE, vertices.get(2).category());
        assertEquals(FINAL, vertices.get(3).category());
        assertEquals(TYPE_IDENTIFIER, vertices.get(4).category());
        assertEquals("int", vertices.get(4).value());
        assertEquals(FIELD_VAR_DECLARATION, vertices.get(5).category());
        assertEquals(INTEGER_LITERAL, vertices.get(6).category());
    }

    @Test
    void methodDeclaration() {
        var ctx = buildVertices("class Foo { public void bar() {} }");
        var vertices = ctx.graph().vertices();
        // CLASS(0), CLASS_DECL(1), PUBLIC(2), TYPE_ID(3) void, METHOD_DECL(4) bar
        assertEquals(PUBLIC, vertices.get(2).category());
        assertEquals(TYPE_IDENTIFIER, vertices.get(3).category());
        assertEquals("void", vertices.get(3).value());
        assertEquals(METHOD_DECLARATION, vertices.get(4).category());
        assertEquals("bar", vertices.get(4).value());
    }

    @Test
    void methodWithParams() {
        var ctx = buildVertices("class Foo { void m(String a, int b) {} }");
        var vertices = ctx.graph().vertices();
        // CLASS(0), CLASS_DECL(1), TYPE_ID(2) void, METHOD_DECL(3) m,
        // TYPE_ID(4) String, PARAM_VAR_DECL(5) a, TYPE_ID(6) int, PARAM_VAR_DECL(7) b
        assertEquals(METHOD_DECLARATION, vertices.get(3).category());
        assertEquals(TYPE_IDENTIFIER, vertices.get(4).category());
        assertEquals("String", vertices.get(4).value());
        assertEquals(PARAM_VAR_DECLARATION, vertices.get(5).category());
        assertEquals("a", vertices.get(5).value());
        assertEquals(TYPE_IDENTIFIER, vertices.get(6).category());
        assertEquals("int", vertices.get(6).value());
        assertEquals(PARAM_VAR_DECLARATION, vertices.get(7).category());
    }

    @Test
    void methodInvocationAndVarAccess() {
        var ctx = buildVertices("""
            class Foo {
                String name;
                void m() { name.toString(); }
            }
            """);
        var vertices = ctx.graph().vertices();
        // In method body: FIELD_VAR_ACCESS(name), METHOD_INV(toString)
        boolean hasFieldAccess = vertices.stream()
            .anyMatch(v -> v.category() == FIELD_VAR_ACCESS && v.value().equals("name"));
        boolean hasMethodInv = vertices.stream()
            .anyMatch(v -> v.category() == METHOD_INVOCATION && v.value().equals("toString"));
        assertTrue(hasFieldAccess);
        assertTrue(hasMethodInv);
    }

    @Test
    void newAndConstructorInvocation() {
        var ctx = buildVertices("""
            import java.util.ArrayList;
            class Foo {
                Object x = new ArrayList<>();
            }
            """);
        var vertices = ctx.graph().vertices();
        boolean hasNew = vertices.stream().anyMatch(v -> v.category() == NEW);
        boolean hasCtor = vertices.stream()
            .anyMatch(v -> v.category() == CONSTRUCTOR_INVOCATION && v.value().equals("ArrayList"));
        assertTrue(hasNew);
        assertTrue(hasCtor);
    }

    @Test
    void forEachLoop() {
        var ctx = buildVertices("""
            import java.util.List;
            import java.util.ArrayList;
            class Foo {
                void m() {
                    List<String> items = new ArrayList<>();
                    for (String item : items) {}
                }
            }
            """);
        var vertices = ctx.graph().vertices();
        boolean hasFor = vertices.stream().anyMatch(v -> v.category() == FOR);
        assertTrue(hasFor);
    }

    @Test
    void lambdaExpression() {
        var ctx = buildVertices("""
            import java.util.List;
            import java.util.ArrayList;
            class Foo {
                void m() {
                    List<String> items = new ArrayList<>();
                    items.forEach(x -> {});
                }
            }
            """);
        var vertices = ctx.graph().vertices();
        boolean hasLambdaDecl = vertices.stream()
            .anyMatch(v -> v.category() == LAMBDA_VAR_DECLARATION && v.value().equals("x"));
        assertTrue(hasLambdaDecl);
    }

    @Test
    void genericType() {
        var ctx = buildVertices("""
            import java.util.List;
            class Foo {
                List<String> items;
            }
            """);
        var vertices = ctx.graph().vertices();
        boolean hasListType = vertices.stream()
            .anyMatch(v -> v.category() == TYPE_IDENTIFIER && v.value().equals("List"));
        boolean hasStringType = vertices.stream()
            .anyMatch(v -> v.category() == TYPE_IDENTIFIER && v.value().equals("String"));
        assertTrue(hasListType);
        assertTrue(hasStringType);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.graphbuilder.builder.VertexBuilderTest"`
Expected: compilation error.

- [ ] **Step 3: Implement VertexBuilder**

```java
package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.TokenVertexCategory;
import org.eclipse.jdt.core.dom.*;

import static com.graphbuilder.model.TokenVertexCategory.*;

public class VertexBuilder {

    private BuildContext context;

    public void build(BuildContext context) {
        this.context = context;
        context.compilationUnit().accept(new Visitor());
    }

    private class Visitor extends ASTVisitor {

        // --- Type declarations ---

        @Override
        public boolean visit(TypeDeclaration node) {
            TokenVertexCategory keyword = node.isInterface() ? INTERFACE : CLASS;
            context.addVertex(keyword, node.isInterface() ? "interface" : "class", node);
            return true;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            context.addVertex(ENUM, "enum", node);
            return true;
        }

        @Override
        public boolean visit(AnnotationTypeDeclaration node) {
            context.addVertex(AT, "@", node);
            return true;
        }

        @Override
        public boolean visit(EnumConstantDeclaration node) {
            context.addVertex(ENUM_CONSTANT_DECLARATION, node.getName().getIdentifier(), node);
            return false;
        }

        // --- Modifiers ---

        @Override
        public boolean visit(Modifier node) {
            TokenVertexCategory cat = switch (node.getKeyword().toString()) {
                case "public" -> PUBLIC;
                case "private" -> PRIVATE;
                case "protected" -> PROTECTED;
                case "static" -> STATIC;
                case "final" -> FINAL;
                case "abstract" -> ABSTRACT;
                case "native" -> NATIVE;
                case "synchronized" -> SYNCHRONIZED;
                case "transient" -> TRANSIENT;
                case "volatile" -> VOLATILE;
                case "default" -> DEFAULT;
                default -> null;
            };
            if (cat != null) {
                context.addVertex(cat, node.getKeyword().toString(), node);
            }
            return false;
        }

        // --- Types ---

        @Override
        public boolean visit(PrimitiveType node) {
            context.addVertex(TYPE_IDENTIFIER, node.getPrimitiveTypeCode().toString(), node);
            return false;
        }

        @Override
        public boolean visit(ArrayType node) {
            context.addVertex(ARRAY_TYPE_IDENTIFIER, node.getElementType().toString(), node);
            return true;
        }

        // SimpleType and ParameterizedType: we visit their SimpleName children
        // via visit(SimpleName) below. No vertex creation here.

        // --- Names (the core dispatch) ---

        @Override
        public boolean visit(SimpleName node) {
            ASTNode parent = node.getParent();

            // Class/Interface/Enum/Annotation declaration name
            if (parent instanceof TypeDeclaration td && td.getName() == node) {
                TokenVertexCategory cat = td.isInterface() ? INTERFACE_DECLARATION : CLASS_DECLARATION;
                context.addVertex(cat, node.getIdentifier(), node);
                return false;
            }
            if (parent instanceof EnumDeclaration ed && ed.getName() == node) {
                context.addVertex(ENUM_DECLARATION, node.getIdentifier(), node);
                return false;
            }
            if (parent instanceof AnnotationTypeDeclaration atd && atd.getName() == node) {
                context.addVertex(ANNOTATION_DECLARATION, node.getIdentifier(), node);
                return false;
            }

            // Method declaration name
            if (parent instanceof MethodDeclaration md && md.getName() == node) {
                if (md.isConstructor()) {
                    context.addVertex(CONSTRUCTOR_DECLARATION, node.getIdentifier(), node);
                } else {
                    context.addVertex(METHOD_DECLARATION, node.getIdentifier(), node);
                }
                return false;
            }

            // Method invocation name
            if (parent instanceof MethodInvocation mi && mi.getName() == node) {
                context.addVertex(METHOD_INVOCATION, node.getIdentifier(), node);
                return false;
            }

            // Constructor type name (new Foo())
            if (isConstructorTypeName(node)) {
                context.addVertex(CONSTRUCTOR_INVOCATION, node.getIdentifier(), node);
                return false;
            }

            // Type name (in type contexts that aren't constructor invocations)
            if (parent instanceof SimpleType) {
                context.addVertex(TYPE_IDENTIFIER, node.getIdentifier(), node);
                return false;
            }

            // Variable declaration name
            if (parent instanceof VariableDeclarationFragment vdf && vdf.getName() == node) {
                ASTNode grandparent = vdf.getParent();
                if (grandparent instanceof FieldDeclaration) {
                    context.addVertex(FIELD_VAR_DECLARATION, node.getIdentifier(), node);
                } else if (grandparent instanceof LambdaExpression) {
                    context.addVertex(LAMBDA_VAR_DECLARATION, node.getIdentifier(), node);
                } else {
                    context.addVertex(LOCAL_VAR_DECLARATION, node.getIdentifier(), node);
                }
                return false;
            }

            // Parameter declaration name
            if (parent instanceof SingleVariableDeclaration svd && svd.getName() == node) {
                ASTNode grandparent = svd.getParent();
                if (grandparent instanceof EnhancedForStatement || grandparent instanceof CatchClause) {
                    context.addVertex(LOCAL_VAR_DECLARATION, node.getIdentifier(), node);
                } else {
                    context.addVertex(PARAM_VAR_DECLARATION, node.getIdentifier(), node);
                }
                return false;
            }

            // Method reference name
            if (parent instanceof ExpressionMethodReference ||
                parent instanceof TypeMethodReference ||
                parent instanceof SuperMethodReference) {
                context.addVertex(METHOD_REFERENCE, node.getIdentifier(), node);
                return false;
            }

            // Variable reference (access) — use binding to distinguish
            IBinding binding = node.resolveBinding();
            if (binding instanceof IVariableBinding vb) {
                if (vb.isField()) {
                    context.addVertex(FIELD_VAR_ACCESS, node.getIdentifier(), node);
                } else if (isLambdaParameter(vb)) {
                    context.addVertex(LAMBDA_VAR_ACCESS, node.getIdentifier(), node);
                } else if (vb.isParameter()) {
                    context.addVertex(PARAM_VAR_ACCESS, node.getIdentifier(), node);
                } else {
                    context.addVertex(LOCAL_VAR_ACCESS, node.getIdentifier(), node);
                }
                return false;
            }

            // Fallback: if no binding, try to determine from AST context
            if (binding == null && isVariableReference(node)) {
                context.addVertex(LOCAL_VAR_ACCESS, node.getIdentifier(), node);
                return false;
            }

            return false;
        }

        @Override
        public boolean visit(QualifiedName node) {
            // Visit qualifier and name separately
            return true;
        }

        // --- Control flow ---

        @Override
        public boolean visit(EnhancedForStatement node) {
            context.addVertex(FOR, "for", node);
            return true;
        }

        @Override
        public boolean visit(ForStatement node) {
            context.addVertex(FOR, "for", node);
            return true;
        }

        @Override
        public boolean visit(IfStatement node) {
            context.addVertex(IF, "if", node);
            return true;
        }

        @Override
        public boolean visit(WhileStatement node) {
            context.addVertex(WHILE, "while", node);
            return true;
        }

        @Override
        public boolean visit(DoStatement node) {
            context.addVertex(DO, "do", node);
            return true;
        }

        @Override
        public boolean visit(SwitchStatement node) {
            context.addVertex(SWITCH, "switch", node);
            return true;
        }

        @Override
        public boolean visit(SwitchExpression node) {
            context.addVertex(SWITCH, "switch", node);
            return true;
        }

        @Override
        public boolean visit(SwitchCase node) {
            context.addVertex(node.isDefault() ? DEFAULT : CASE, node.isDefault() ? "default" : "case", node);
            return true;
        }

        @Override
        public boolean visit(TryStatement node) {
            context.addVertex(TRY, "try", node);
            return true;
        }

        @Override
        public boolean visit(CatchClause node) {
            context.addVertex(CATCH, "catch", node);
            return true;
        }

        @Override
        public boolean visit(SynchronizedStatement node) {
            context.addVertex(SYNCHRONIZED, "synchronized", node);
            return true;
        }

        @Override
        public boolean visit(ThrowStatement node) {
            context.addVertex(THROW, "throw", node);
            return true;
        }

        @Override
        public boolean visit(BreakStatement node) {
            context.addVertex(BREAK, "break", node);
            return true;
        }

        @Override
        public boolean visit(ContinueStatement node) {
            context.addVertex(CONTINUE, "continue", node);
            return true;
        }

        @Override
        public boolean visit(ReturnStatement node) {
            context.addVertex(RETURN, "return", node);
            return true;
        }

        @Override
        public boolean visit(AssertStatement node) {
            context.addVertex(ASSERT, "assert", node);
            return true;
        }

        // --- Expressions ---

        @Override
        public boolean visit(ClassInstanceCreation node) {
            context.addVertex(NEW, "new", node);
            return true;
        }

        @Override
        public boolean visit(ArrayCreation node) {
            context.addVertex(NEW, "new", node);
            return true;
        }

        @Override
        public boolean visit(LambdaExpression node) {
            // Lambda parameters handled via visit(SimpleName) / visit(VariableDeclarationFragment)
            return true;
        }

        @Override
        public boolean visit(InstanceofExpression node) {
            context.addVertex(INSTANCE_OF, "instanceof", node);
            return true;
        }

        @Override
        public boolean visit(SuperFieldAccess node) {
            context.addVertex(SUPER, "super", node);
            return true;
        }

        @Override
        public boolean visit(SuperMethodInvocation node) {
            context.addVertex(SUPER, "super", node);
            return true;
        }

        @Override
        public boolean visit(ThisExpression node) {
            context.addVertex(THIS, "this", node);
            return false;
        }

        // --- Literals ---

        @Override
        public boolean visit(NumberLiteral node) {
            String token = node.getToken();
            TokenVertexCategory cat;
            if (token.endsWith("L") || token.endsWith("l")) {
                cat = LONG_LITERAL;
            } else if (token.endsWith("F") || token.endsWith("f")) {
                cat = FLOAT_LITERAL;
            } else if (token.endsWith("D") || token.endsWith("d") || token.contains(".") || token.contains("e") || token.contains("E")) {
                cat = DOUBLE_LITERAL;
            } else {
                cat = INTEGER_LITERAL;
            }
            context.addVertex(cat, token, node);
            return false;
        }

        @Override
        public boolean visit(StringLiteral node) {
            context.addVertex(STRING_LITERAL, node.getLiteralValue(), node);
            return false;
        }

        @Override
        public boolean visit(CharacterLiteral node) {
            context.addVertex(CHAR_LITERAL, String.valueOf(node.charValue()), node);
            return false;
        }

        @Override
        public boolean visit(BooleanLiteral node) {
            context.addVertex(BOOLEAN_LITERAL, String.valueOf(node.booleanValue()), node);
            return false;
        }

        @Override
        public boolean visit(NullLiteral node) {
            context.addVertex(NULL_LITERAL, "null", node);
            return false;
        }

        // --- Helpers ---

        private boolean isConstructorTypeName(SimpleName node) {
            ASTNode current = node.getParent();
            while (current instanceof Type) {
                current = current.getParent();
            }
            return current instanceof ClassInstanceCreation;
        }

        private boolean isLambdaParameter(IVariableBinding vb) {
            IMethodBinding method = vb.getDeclaringMethod();
            return method != null && (method.getDeclaringMember() != null ||
                   method.getName().startsWith("lambda$"));
        }

        private boolean isVariableReference(SimpleName node) {
            ASTNode parent = node.getParent();
            return parent instanceof Assignment ||
                   parent instanceof InfixExpression ||
                   parent instanceof MethodInvocation mi && mi.getExpression() == node ||
                   parent instanceof PrefixExpression ||
                   parent instanceof PostfixExpression;
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "com.graphbuilder.builder.VertexBuilderTest"`
Expected: all tests pass. Some tests may need adjustment depending on exact binding resolution behavior — fix as needed.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "Added VertexBuilder with all vertex category handlers"
```

---

### Task 6: StructuralEdgeBuilder

**Files:**
- Create: `src/main/java/com/graphbuilder/builder/StructuralEdgeBuilder.java`
- Test: `src/test/java/com/graphbuilder/builder/StructuralEdgeBuilderTest.java`

- [ ] **Step 1: Write tests**

```java
package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.*;
import com.graphbuilder.parser.JdtParser;
import org.junit.jupiter.api.Test;

import static com.graphbuilder.model.EdgeCategory.*;
import static com.graphbuilder.model.TokenVertexCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class StructuralEdgeBuilderTest {

    private BuildContext buildGraph(String source) {
        var parser = new JdtParser();
        var cu = parser.parse(source, "Test.java");
        var graph = new AsgGraph();
        var ctx = new BuildContext(cu, graph, "Test.java");
        new VertexBuilder().build(ctx);
        new StructuralEdgeBuilder().build(ctx);
        return ctx;
    }

    @Test
    void nextTokenModifierToType() {
        // private String name; → PRIVATE --NEXT_TOKEN--> TYPE_ID(String)
        var ctx = buildGraph("class Foo { private String name; }");
        var edges = ctx.graph().edges();
        boolean found = edges.stream().anyMatch(e ->
            e.source().category() == PRIVATE &&
            e.target().category() == TYPE_IDENTIFIER &&
            e.target().value().equals("String") &&
            e.category() == NEXT_TOKEN);
        assertTrue(found, "Expected NEXT_TOKEN from PRIVATE to TYPE_ID(String)");
    }

    @Test
    void keywordChainModifiers() {
        // private final int x; → PRIVATE --KEYWORD_CHAIN--> FINAL
        var ctx = buildGraph("class Foo { private final int x = 0; }");
        var edges = ctx.graph().edges();
        boolean found = edges.stream().anyMatch(e ->
            e.source().category() == PRIVATE &&
            e.target().category() == FINAL &&
            e.category() == KEYWORD_CHAIN);
        assertTrue(found, "Expected KEYWORD_CHAIN from PRIVATE to FINAL");
    }

    @Test
    void nextTokenFinalToType() {
        // private final int x; → FINAL --NEXT_TOKEN--> TYPE_ID(int)
        var ctx = buildGraph("class Foo { private final int x = 0; }");
        var edges = ctx.graph().edges();
        boolean found = edges.stream().anyMatch(e ->
            e.source().category() == FINAL &&
            e.target().category() == TYPE_IDENTIFIER &&
            e.category() == NEXT_TOKEN);
        assertTrue(found, "Expected NEXT_TOKEN from FINAL to TYPE_ID(int)");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.graphbuilder.builder.StructuralEdgeBuilderTest"`
Expected: compilation error.

- [ ] **Step 3: Implement StructuralEdgeBuilder**

The key insight: scan consecutive vertex pairs. If both are modifiers → KEYWORD_CHAIN. If first is modifier/FINAL and second is TYPE_ID → NEXT_TOKEN. If LOCAL_VAR_DECL is followed by LOCAL_VAR_ACCESS in a for-each → NEXT_TOKEN.

```java
package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.*;
import org.eclipse.jdt.core.dom.*;

import java.util.List;
import java.util.Set;

import static com.graphbuilder.model.EdgeCategory.*;
import static com.graphbuilder.model.TokenVertexCategory.*;

public class StructuralEdgeBuilder {

    private static final Set<TokenVertexCategory> MODIFIERS = Set.of(
        PUBLIC, PRIVATE, PROTECTED, STATIC, FINAL, ABSTRACT,
        NATIVE, SYNCHRONIZED, TRANSIENT, VOLATILE, DEFAULT
    );

    public void build(BuildContext context) {
        List<ITokenVertex> vertices = context.graph().vertices();

        for (int i = 0; i < vertices.size() - 1; i++) {
            ITokenVertex current = vertices.get(i);
            ITokenVertex next = vertices.get(i + 1);

            // KEYWORD_CHAIN: consecutive modifiers
            if (MODIFIERS.contains(current.category()) && MODIFIERS.contains(next.category())) {
                context.addEdge(current, next, KEYWORD_CHAIN);
                continue;
            }

            // NEXT_TOKEN: last modifier/keyword → TYPE_ID
            if (MODIFIERS.contains(current.category()) && next.category() == TYPE_IDENTIFIER) {
                context.addEdge(current, next, NEXT_TOKEN);
                continue;
            }

            // NEXT_TOKEN: PUBLIC/modifier → TYPE_ID (for method return type)
            if (current.category() == PUBLIC && next.category() == TYPE_IDENTIFIER) {
                context.addEdge(current, next, NEXT_TOKEN);
                continue;
            }
        }

        // NEXT_TOKEN for for-each: LOCAL_VAR_DECL → LOCAL_VAR_ACCESS (iterable)
        context.compilationUnit().accept(new ASTVisitor() {
            @Override
            public boolean visit(EnhancedForStatement node) {
                ITokenVertex varDecl = findVertexForName(context, node.getParameter().getName(), LOCAL_VAR_DECLARATION);
                ITokenVertex iterableAccess = context.firstVertexInRange(node.getExpression());
                if (varDecl != null && iterableAccess != null) {
                    context.addEdge(varDecl, iterableAccess, NEXT_TOKEN);
                }
                return true;
            }
        });
    }

    private ITokenVertex findVertexForName(BuildContext context, SimpleName name, TokenVertexCategory category) {
        return context.graph().vertices().stream()
            .filter(v -> v.category() == category &&
                         v.documentOffset() == name.getStartPosition())
            .findFirst().orElse(null);
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "com.graphbuilder.builder.StructuralEdgeBuilderTest"`
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "Added StructuralEdgeBuilder"
```

---

### Task 7: DeclarationEdgeBuilder

**Files:**
- Create: `src/main/java/com/graphbuilder/builder/DeclarationEdgeBuilder.java`
- Test: `src/test/java/com/graphbuilder/builder/DeclarationEdgeBuilderTest.java`

- [ ] **Step 1: Write tests**

```java
package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.*;
import com.graphbuilder.parser.JdtParser;
import org.junit.jupiter.api.Test;

import static com.graphbuilder.model.EdgeCategory.*;
import static com.graphbuilder.model.TokenVertexCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class DeclarationEdgeBuilderTest {

    private BuildContext buildGraph(String source) {
        var parser = new JdtParser();
        var cu = parser.parse(source, "Test.java");
        var graph = new AsgGraph();
        var ctx = new BuildContext(cu, graph, "Test.java");
        new VertexBuilder().build(ctx);
        new StructuralEdgeBuilder().build(ctx);
        new DeclarationEdgeBuilder().build(ctx);
        return ctx;
    }

    @Test
    void declaringClassToName() {
        var ctx = buildGraph("class Foo {}");
        assertTrue(hasEdge(ctx, CLASS, "class", CLASS_DECLARATION, "Foo", DECLARING));
    }

    @Test
    void declaringTypeToFieldName() {
        var ctx = buildGraph("class Foo { String name; }");
        assertTrue(hasEdge(ctx, TYPE_IDENTIFIER, "String", FIELD_VAR_DECLARATION, "name", DECLARING));
    }

    @Test
    void declaringTypeToMethodName() {
        var ctx = buildGraph("class Foo { void bar() {} }");
        assertTrue(hasEdge(ctx, TYPE_IDENTIFIER, "void", METHOD_DECLARATION, "bar", DECLARING));
    }

    @Test
    void attributeClassToMembers() {
        var ctx = buildGraph("class Foo { private String name; public void bar() {} }");
        assertTrue(hasEdge(ctx, CLASS_DECLARATION, "Foo", PRIVATE, "private", ATTRIBUTE));
        assertTrue(hasEdge(ctx, CLASS_DECLARATION, "Foo", PUBLIC, "public", ATTRIBUTE));
    }

    @Test
    void formalParameter() {
        var ctx = buildGraph("class Foo { void m(String a) {} }");
        assertTrue(hasEdge(ctx, METHOD_DECLARATION, "m", TYPE_IDENTIFIER, "String", FORMAL_PARAMETER));
    }

    @Test
    void nextDeclarationChain() {
        var ctx = buildGraph("class Foo { void m(String a, int b) {} }");
        assertTrue(hasEdge(ctx, PARAM_VAR_DECLARATION, "a", TYPE_IDENTIFIER, "int", NEXT_DECLARATION));
    }

    private boolean hasEdge(BuildContext ctx, TokenVertexCategory srcCat, String srcVal,
                            TokenVertexCategory tgtCat, String tgtVal, EdgeCategory edgeCat) {
        return ctx.graph().edges().stream().anyMatch(e ->
            e.source().category() == srcCat && e.source().value().equals(srcVal) &&
            e.target().category() == tgtCat && e.target().value().equals(tgtVal) &&
            e.category() == edgeCat);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.graphbuilder.builder.DeclarationEdgeBuilderTest"`
Expected: compilation error.

- [ ] **Step 3: Implement DeclarationEdgeBuilder**

```java
package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.*;
import org.eclipse.jdt.core.dom.*;

import java.util.List;
import java.util.Set;

import static com.graphbuilder.model.EdgeCategory.*;
import static com.graphbuilder.model.TokenVertexCategory.*;

public class DeclarationEdgeBuilder {

    private static final Set<TokenVertexCategory> DECLARATION_TYPES = Set.of(
        CLASS_DECLARATION, INTERFACE_DECLARATION, ENUM_DECLARATION, ANNOTATION_DECLARATION,
        METHOD_DECLARATION, CONSTRUCTOR_DECLARATION,
        FIELD_VAR_DECLARATION, LOCAL_VAR_DECLARATION, PARAM_VAR_DECLARATION, LAMBDA_VAR_DECLARATION
    );

    private static final Set<TokenVertexCategory> DECLARING_SOURCES = Set.of(
        CLASS, INTERFACE, ENUM, AT, TYPE_IDENTIFIER
    );

    public void build(BuildContext context) {
        buildDeclaringEdges(context);
        context.compilationUnit().accept(new DeclarationVisitor(context));
    }

    private void buildDeclaringEdges(BuildContext context) {
        List<ITokenVertex> vertices = context.graph().vertices();
        for (int i = 1; i < vertices.size(); i++) {
            ITokenVertex current = vertices.get(i);
            ITokenVertex prev = vertices.get(i - 1);
            if (DECLARATION_TYPES.contains(current.category()) && DECLARING_SOURCES.contains(prev.category())) {
                context.addEdge(prev, current, DECLARING);
            }
        }
    }

    private static class DeclarationVisitor extends ASTVisitor {
        private final BuildContext context;

        DeclarationVisitor(BuildContext context) {
            this.context = context;
        }

        @Override
        public boolean visit(TypeDeclaration node) {
            handleTypeBody(node.getName(), node.bodyDeclarations());
            return true;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            handleTypeBody(node.getName(), node.bodyDeclarations());
            return true;
        }

        private void handleTypeBody(SimpleName typeName, List<?> bodyDeclarations) {
            ITokenVertex typeDecl = findByOffset(context, typeName.getStartPosition(),
                Set.of(CLASS_DECLARATION, INTERFACE_DECLARATION, ENUM_DECLARATION, ANNOTATION_DECLARATION));
            if (typeDecl == null) return;

            for (Object member : bodyDeclarations) {
                BodyDeclaration bd = (BodyDeclaration) member;
                ITokenVertex firstVertex = context.firstVertexInRange(bd);
                if (firstVertex != null) {
                    context.addEdge(typeDecl, firstVertex, ATTRIBUTE);
                }
            }
        }

        @Override
        public boolean visit(MethodDeclaration node) {
            ITokenVertex methodDecl = findByOffset(context, node.getName().getStartPosition(),
                Set.of(METHOD_DECLARATION, CONSTRUCTOR_DECLARATION));
            if (methodDecl == null) return true;

            // FORMAL_PARAMETER: method → each param type
            List<?> params = node.parameters();
            for (int i = 0; i < params.size(); i++) {
                SingleVariableDeclaration param = (SingleVariableDeclaration) params.get(i);
                ITokenVertex paramType = context.firstVertexInRange(param.getType());
                if (paramType != null) {
                    context.addEdge(methodDecl, paramType, FORMAL_PARAMETER);
                }

                // NEXT_DECLARATION: chain consecutive params
                if (i < params.size() - 1) {
                    ITokenVertex paramDecl = findByOffset(context, param.getName().getStartPosition(),
                        Set.of(PARAM_VAR_DECLARATION));
                    SingleVariableDeclaration nextParam = (SingleVariableDeclaration) params.get(i + 1);
                    ITokenVertex nextType = context.firstVertexInRange(nextParam.getType());
                    if (paramDecl != null && nextType != null) {
                        context.addEdge(paramDecl, nextType, NEXT_DECLARATION);
                    }
                }
            }
            return true;
        }

        private ITokenVertex findByOffset(BuildContext context, int offset, Set<TokenVertexCategory> categories) {
            return context.graph().vertices().stream()
                .filter(v -> v.documentOffset() == offset && categories.contains(v.category()))
                .findFirst().orElse(null);
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "com.graphbuilder.builder.DeclarationEdgeBuilderTest"`
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "Added DeclarationEdgeBuilder"
```

---

### Task 8: FlowEdgeBuilder

**Files:**
- Create: `src/main/java/com/graphbuilder/builder/FlowEdgeBuilder.java`
- Test: `src/test/java/com/graphbuilder/builder/FlowEdgeBuilderTest.java`

- [ ] **Step 1: Write tests**

```java
package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.*;
import com.graphbuilder.parser.JdtParser;
import org.junit.jupiter.api.Test;

import static com.graphbuilder.model.EdgeCategory.*;
import static com.graphbuilder.model.TokenVertexCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class FlowEdgeBuilderTest {

    private BuildContext buildGraph(String source) {
        var parser = new JdtParser();
        var cu = parser.parse(source, "Test.java");
        var graph = new AsgGraph();
        var ctx = new BuildContext(cu, graph, "Test.java");
        new VertexBuilder().build(ctx);
        new StructuralEdgeBuilder().build(ctx);
        new DeclarationEdgeBuilder().build(ctx);
        new FlowEdgeBuilder().build(ctx);
        return ctx;
    }

    @Test
    void statementFromMethod() {
        var ctx = buildGraph("""
            class Foo {
                String name;
                void m() { name.toString(); }
            }
            """);
        assertTrue(hasEdge(ctx, METHOD_DECLARATION, "m", FIELD_VAR_ACCESS, "name", STATEMENT));
    }

    @Test
    void controlFlowScope() {
        var ctx = buildGraph("""
            import java.util.List;
            import java.util.ArrayList;
            class Foo {
                void m() {
                    List<String> items = new ArrayList<>();
                    for (String item : items) {}
                }
            }
            """);
        assertTrue(hasEdge(ctx, METHOD_DECLARATION, "m", FOR, "for", CONTROL_FLOW_SCOPE));
    }

    @Test
    void assignFieldAccess() {
        var ctx = buildGraph("""
            class Foo {
                String name;
                void m(String x) { name = x; }
            }
            """);
        assertTrue(hasEdge(ctx, FIELD_VAR_ACCESS, "name", PARAM_VAR_ACCESS, "x", ASSIGN));
    }

    @Test
    void assignLocalVarDecl() {
        var ctx = buildGraph("""
            import java.util.ArrayList;
            class Foo {
                void m() { Object x = new ArrayList<>(); }
            }
            """);
        assertTrue(hasEdge(ctx, LOCAL_VAR_DECLARATION, "x", NEW, "new", ASSIGN));
    }

    @Test
    void callObjectToMethod() {
        var ctx = buildGraph("""
            class Foo {
                String name;
                void m() { name.toString(); }
            }
            """);
        assertTrue(hasEdge(ctx, FIELD_VAR_ACCESS, "name", METHOD_INVOCATION, "toString", CALL));
    }

    @Test
    void callChained() {
        var ctx = buildGraph("""
            class Foo {
                String name;
                void m() { name.toString().hashCode(); }
            }
            """);
        assertTrue(hasEdge(ctx, METHOD_INVOCATION, "toString", METHOD_INVOCATION, "hashCode", CALL));
    }

    @Test
    void argumentToMethod() {
        var ctx = buildGraph("""
            import java.util.List;
            import java.util.ArrayList;
            class Foo {
                void m() {
                    List<String> items = new ArrayList<>();
                    items.add("x");
                }
            }
            """);
        assertTrue(hasEdge(ctx, METHOD_INVOCATION, "add", STRING_LITERAL, "x", ARGUMENT));
    }

    @Test
    void creationNewToConstructor() {
        var ctx = buildGraph("""
            import java.util.ArrayList;
            class Foo { Object x = new ArrayList<>(); }
            """);
        assertTrue(hasEdge(ctx, NEW, "new", CONSTRUCTOR_INVOCATION, "ArrayList", CREATION));
    }

    @Test
    void operationBinaryArithmetic() {
        var ctx = buildGraph("""
            class Foo {
                void m(int a) { int x = a - 1; }
            }
            """);
        assertTrue(hasEdge(ctx, PARAM_VAR_ACCESS, "a", INTEGER_LITERAL, "1", OPERATION));
    }

    @Test
    void statementFromLambda() {
        var ctx = buildGraph("""
            import java.util.List;
            import java.util.ArrayList;
            class Foo {
                void m() {
                    List<String> items = new ArrayList<>();
                    items.forEach(x -> { x.toString(); });
                }
            }
            """);
        assertTrue(hasEdge(ctx, LAMBDA_VAR_DECLARATION, "x", LAMBDA_VAR_ACCESS, "x", STATEMENT));
    }

    @Test
    void forArgumentType() {
        var ctx = buildGraph("""
            import java.util.List;
            import java.util.ArrayList;
            class Foo {
                void m() {
                    List<String> items = new ArrayList<>();
                    for (String item : items) {}
                }
            }
            """);
        assertTrue(hasEdge(ctx, FOR, "for", TYPE_IDENTIFIER, "String", ARGUMENT));
    }

    private boolean hasEdge(BuildContext ctx, TokenVertexCategory srcCat, String srcVal,
                            TokenVertexCategory tgtCat, String tgtVal, EdgeCategory edgeCat) {
        return ctx.graph().edges().stream().anyMatch(e ->
            e.source().category() == srcCat && e.source().value().equals(srcVal) &&
            e.target().category() == tgtCat && e.target().value().equals(tgtVal) &&
            e.category() == edgeCat);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.graphbuilder.builder.FlowEdgeBuilderTest"`
Expected: compilation error.

- [ ] **Step 3: Implement FlowEdgeBuilder**

```java
package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.*;
import org.eclipse.jdt.core.dom.*;

import java.util.List;
import java.util.Set;

import static com.graphbuilder.model.EdgeCategory.*;
import static com.graphbuilder.model.TokenVertexCategory.*;

public class FlowEdgeBuilder {

    private static final Set<TokenVertexCategory> CONTROL_FLOW_CATEGORIES = Set.of(
        FOR, IF, WHILE, DO, SWITCH, TRY, SYNCHRONIZED
    );

    private BuildContext context;

    public void build(BuildContext context) {
        this.context = context;
        context.compilationUnit().accept(new FlowVisitor());
    }

    private class FlowVisitor extends ASTVisitor {

        // --- STATEMENT and CONTROL_FLOW_SCOPE ---

        @Override
        public boolean visit(MethodDeclaration node) {
            ITokenVertex methodDecl = findDeclVertex(node.getName(),
                Set.of(METHOD_DECLARATION, CONSTRUCTOR_DECLARATION));
            if (methodDecl == null || node.getBody() == null) return true;
            emitBodyEdges(methodDecl, node.getBody().statements());
            return true;
        }

        @Override
        public boolean visit(EnhancedForStatement node) {
            ITokenVertex forVertex = context.findVertex(node, FOR);
            if (forVertex == null) return true;

            // ARGUMENT: FOR → parameter type
            ITokenVertex paramType = context.firstVertexInRange(node.getParameter().getType());
            context.addEdge(forVertex, paramType, ARGUMENT);

            // STATEMENT: FOR → first vertex of each body statement
            if (node.getBody() instanceof Block block) {
                emitBodyEdges(forVertex, block.statements());
            } else {
                ITokenVertex bodyFirst = context.firstVertexInRange(node.getBody());
                if (bodyFirst != null) {
                    context.addEdge(forVertex, bodyFirst, STATEMENT);
                }
            }
            return true;
        }

        @Override
        public boolean visit(ForStatement node) {
            ITokenVertex forVertex = context.findVertex(node, FOR);
            if (forVertex == null || node.getBody() == null) return true;
            if (node.getBody() instanceof Block block) {
                emitBodyEdges(forVertex, block.statements());
            }
            return true;
        }

        @Override
        public boolean visit(IfStatement node) {
            ITokenVertex ifVertex = context.findVertex(node, IF);
            if (ifVertex == null) return true;
            ITokenVertex thenFirst = context.firstVertexInRange(node.getThenStatement());
            context.addEdge(ifVertex, thenFirst, STATEMENT);
            if (node.getElseStatement() != null) {
                ITokenVertex elseFirst = context.firstVertexInRange(node.getElseStatement());
                context.addEdge(ifVertex, elseFirst, STATEMENT);
            }
            return true;
        }

        @Override
        public boolean visit(WhileStatement node) {
            ITokenVertex whileVertex = context.findVertex(node, WHILE);
            if (whileVertex == null || node.getBody() == null) return true;
            if (node.getBody() instanceof Block block) {
                emitBodyEdges(whileVertex, block.statements());
            }
            return true;
        }

        @Override
        public boolean visit(DoStatement node) {
            ITokenVertex doVertex = context.findVertex(node, DO);
            if (doVertex == null || node.getBody() == null) return true;
            if (node.getBody() instanceof Block block) {
                emitBodyEdges(doVertex, block.statements());
            }
            return true;
        }

        @Override
        public boolean visit(TryStatement node) {
            ITokenVertex tryVertex = context.findVertex(node, TRY);
            if (tryVertex == null) return true;
            if (node.getBody() != null) {
                emitBodyEdges(tryVertex, node.getBody().statements());
            }
            return true;
        }

        @Override
        public boolean visit(LambdaExpression node) {
            // Find the lambda var decl vertex (parameter name)
            ITokenVertex lambdaDecl = null;
            for (Object param : node.parameters()) {
                if (param instanceof VariableDeclarationFragment vdf) {
                    lambdaDecl = findDeclVertex(vdf.getName(), Set.of(LAMBDA_VAR_DECLARATION));
                } else if (param instanceof SingleVariableDeclaration svd) {
                    lambdaDecl = findDeclVertex(svd.getName(), Set.of(LAMBDA_VAR_DECLARATION, PARAM_VAR_DECLARATION));
                }
                break; // use first param for STATEMENT edges
            }
            if (lambdaDecl == null) return true;

            ASTNode body = node.getBody();
            if (body instanceof Block block) {
                for (Object stmt : block.statements()) {
                    ITokenVertex stmtFirst = context.firstVertexInRange((ASTNode) stmt);
                    if (stmtFirst != null) {
                        context.addEdge(lambdaDecl, stmtFirst, STATEMENT);
                    }
                }
            } else {
                ITokenVertex bodyFirst = context.firstVertexInRange(body);
                context.addEdge(lambdaDecl, bodyFirst, STATEMENT);
            }
            return true;
        }

        private void emitBodyEdges(ITokenVertex owner, List<?> statements) {
            for (Object stmt : statements) {
                Statement s = (Statement) stmt;
                ITokenVertex stmtFirst = context.firstVertexInRange(s);
                if (stmtFirst == null) continue;

                if (CONTROL_FLOW_CATEGORIES.contains(stmtFirst.category())) {
                    context.addEdge(owner, stmtFirst, CONTROL_FLOW_SCOPE);
                } else {
                    context.addEdge(owner, stmtFirst, STATEMENT);
                }
            }
        }

        // --- ASSIGN ---

        @Override
        public boolean visit(Assignment node) {
            ITokenVertex lhs = context.firstVertexInRange(node.getLeftHandSide());
            ITokenVertex rhs = context.firstVertexInRange(node.getRightHandSide());
            context.addEdge(lhs, rhs, ASSIGN);
            return true;
        }

        @Override
        public boolean visit(VariableDeclarationFragment node) {
            if (node.getInitializer() == null) return true;
            ITokenVertex decl = findDeclVertex(node.getName(),
                Set.of(FIELD_VAR_DECLARATION, LOCAL_VAR_DECLARATION, LAMBDA_VAR_DECLARATION));
            ITokenVertex init = context.firstVertexInRange(node.getInitializer());
            context.addEdge(decl, init, ASSIGN);
            return true;
        }

        // --- CALL ---

        @Override
        public boolean visit(MethodInvocation node) {
            ITokenVertex methodVertex = findDeclVertex(node.getName(), Set.of(METHOD_INVOCATION));

            // CALL: expression → method
            Expression expr = node.getExpression();
            if (expr != null && methodVertex != null) {
                ITokenVertex objectVertex;
                if (expr instanceof MethodInvocation innerMi) {
                    objectVertex = findDeclVertex(innerMi.getName(), Set.of(METHOD_INVOCATION));
                } else {
                    objectVertex = context.firstVertexInRange(expr);
                }
                context.addEdge(objectVertex, methodVertex, CALL);
            }

            // ARGUMENT: method → arg1, arg1 → arg2, ...
            if (methodVertex != null) {
                emitArgumentChain(methodVertex, node.arguments());
            }
            return true;
        }

        // --- CREATION ---

        @Override
        public boolean visit(ClassInstanceCreation node) {
            List<ITokenVertex> vertices = context.verticesInRange(node);
            ITokenVertex newVertex = vertices.stream().filter(v -> v.category() == NEW).findFirst().orElse(null);
            ITokenVertex ctorVertex = vertices.stream().filter(v -> v.category() == CONSTRUCTOR_INVOCATION).findFirst().orElse(null);
            context.addEdge(newVertex, ctorVertex, CREATION);

            // Arguments to constructor
            if (ctorVertex != null && !node.arguments().isEmpty()) {
                emitArgumentChain(ctorVertex, node.arguments());
            }
            return true;
        }

        // --- OPERATION ---

        @Override
        public boolean visit(InfixExpression node) {
            ITokenVertex left = context.firstVertexInRange(node.getLeftOperand());
            ITokenVertex right = context.firstVertexInRange(node.getRightOperand());
            context.addEdge(left, right, OPERATION);

            // Extended operands (a + b + c)
            ITokenVertex prev = right;
            for (Object ext : node.extendedOperands()) {
                ITokenVertex extVertex = context.firstVertexInRange((Expression) ext);
                context.addEdge(prev, extVertex, OPERATION);
                prev = extVertex;
            }
            return true;
        }

        @Override
        public boolean visit(PrefixExpression node) {
            ITokenVertex operand = context.firstVertexInRange(node.getOperand());
            if (operand != null) {
                // For prefix ops, the vertex IS the operation
            }
            return true;
        }

        // --- Helpers ---

        private void emitArgumentChain(ITokenVertex method, List<?> arguments) {
            ITokenVertex prev = method;
            for (Object arg : arguments) {
                ITokenVertex argVertex = context.firstVertexInRange((ASTNode) arg);
                if (argVertex != null) {
                    context.addEdge(prev, argVertex, ARGUMENT);
                    prev = argVertex;
                }
            }
        }

        private ITokenVertex findDeclVertex(SimpleName name, Set<TokenVertexCategory> categories) {
            int offset = name.getStartPosition();
            return context.graph().vertices().stream()
                .filter(v -> v.documentOffset() == offset && categories.contains(v.category()))
                .findFirst().orElse(null);
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "com.graphbuilder.builder.FlowEdgeBuilderTest"`
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "Added FlowEdgeBuilder"
```

---

### Task 9: TypeEdgeBuilder

**Files:**
- Create: `src/main/java/com/graphbuilder/builder/TypeEdgeBuilder.java`
- Test: `src/test/java/com/graphbuilder/builder/TypeEdgeBuilderTest.java`

- [ ] **Step 1: Write tests**

```java
package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.*;
import com.graphbuilder.parser.JdtParser;
import org.junit.jupiter.api.Test;

import static com.graphbuilder.model.EdgeCategory.*;
import static com.graphbuilder.model.TokenVertexCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class TypeEdgeBuilderTest {

    private BuildContext buildGraph(String source) {
        var parser = new JdtParser();
        var cu = parser.parse(source, "Test.java");
        var graph = new AsgGraph();
        var ctx = new BuildContext(cu, graph, "Test.java");
        new VertexBuilder().build(ctx);
        new StructuralEdgeBuilder().build(ctx);
        new DeclarationEdgeBuilder().build(ctx);
        new FlowEdgeBuilder().build(ctx);
        new TypeEdgeBuilder().build(ctx);
        return ctx;
    }

    @Test
    void genericTypeArgument() {
        var ctx = buildGraph("""
            import java.util.List;
            class Foo { List<String> items; }
            """);
        assertTrue(hasEdge(ctx, TYPE_IDENTIFIER, "List", TYPE_IDENTIFIER, "String", GENERIC));
    }

    @Test
    void ancestorExtends() {
        var ctx = buildGraph("""
            class Foo {}
            class Bar extends Foo {}
            """);
        // Bar's TYPE_ID → Foo's TYPE_ID via ANCESTOR
        boolean found = ctx.graph().edges().stream().anyMatch(e ->
            e.category() == ANCESTOR &&
            e.target().category() == TYPE_IDENTIFIER && e.target().value().equals("Foo"));
        assertTrue(found);
    }

    private boolean hasEdge(BuildContext ctx, TokenVertexCategory srcCat, String srcVal,
                            TokenVertexCategory tgtCat, String tgtVal, EdgeCategory edgeCat) {
        return ctx.graph().edges().stream().anyMatch(e ->
            e.source().category() == srcCat && e.source().value().equals(srcVal) &&
            e.target().category() == tgtCat && e.target().value().equals(tgtVal) &&
            e.category() == edgeCat);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.graphbuilder.builder.TypeEdgeBuilderTest"`
Expected: compilation error.

- [ ] **Step 3: Implement TypeEdgeBuilder**

```java
package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.*;
import org.eclipse.jdt.core.dom.*;

import java.util.List;
import java.util.Set;

import static com.graphbuilder.model.EdgeCategory.*;
import static com.graphbuilder.model.TokenVertexCategory.*;

public class TypeEdgeBuilder {

    private BuildContext context;

    public void build(BuildContext context) {
        this.context = context;
        context.compilationUnit().accept(new TypeVisitor());
    }

    private class TypeVisitor extends ASTVisitor {

        // --- GENERIC ---

        @Override
        public boolean visit(ParameterizedType node) {
            ITokenVertex outerType = context.firstVertexInRange(node.getType());
            for (Object typeArg : node.typeArguments()) {
                Type argType = (Type) typeArg;
                ITokenVertex innerType = context.firstVertexInRange(argType);
                if (outerType != null && innerType != null) {
                    context.addEdge(outerType, innerType, GENERIC);
                }
            }
            return true;
        }

        // --- ANCESTOR ---

        @Override
        public boolean visit(TypeDeclaration node) {
            ITokenVertex classDecl = findByOffset(node.getName().getStartPosition(),
                Set.of(CLASS_DECLARATION, INTERFACE_DECLARATION));
            if (classDecl == null) return true;

            // extends
            if (node.getSuperclassType() != null) {
                ITokenVertex superType = context.firstVertexInRange(node.getSuperclassType());
                if (superType != null) {
                    context.addEdge(classDecl, superType, ANCESTOR);
                }
            }

            // implements
            List<?> interfaces = node.superInterfaceTypes();
            ITokenVertex prev = null;
            for (Object iface : interfaces) {
                ITokenVertex ifaceType = context.firstVertexInRange((Type) iface);
                if (ifaceType != null) {
                    context.addEdge(classDecl, ifaceType, ANCESTOR);
                    if (prev != null) {
                        context.addEdge(prev, ifaceType, NEXT_ANCESTOR);
                    }
                    prev = ifaceType;
                }
            }
            return true;
        }

        // --- TYPE_ONTOLOGY ---

        @Override
        public boolean visit(InstanceofExpression node) {
            ITokenVertex instanceOf = context.findVertex(node, INSTANCE_OF);
            ITokenVertex type = context.firstVertexInRange(node.getRightOperand());
            context.addEdge(instanceOf, type, TYPE_ONTOLOGY);

            // VARIABLE_ONTOLOGY: variable → type in instanceof
            ITokenVertex variable = context.firstVertexInRange(node.getLeftOperand());
            context.addEdge(variable, instanceOf, VARIABLE_ONTOLOGY);
            return true;
        }

        // --- IMPORTS ---

        @Override
        public boolean visit(ImportDeclaration node) {
            // Import vertices would need to be created in VertexBuilder first
            // For now, handled if IMPORT vertices exist
            return true;
        }

        private ITokenVertex findByOffset(int offset, Set<TokenVertexCategory> categories) {
            return context.graph().vertices().stream()
                .filter(v -> v.documentOffset() == offset && categories.contains(v.category()))
                .findFirst().orElse(null);
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "com.graphbuilder.builder.TypeEdgeBuilderTest"`
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "Added TypeEdgeBuilder"
```

---

### Task 10: GraphBuilder Facade

**Files:**
- Create: `src/main/java/com/graphbuilder/GraphBuilder.java`

- [ ] **Step 1: Implement GraphBuilder**

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
        String combined = sb.toString();
        return buildFromSource(combined, javaFiles.getFirst().toString());
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/graphbuilder/GraphBuilder.java
git commit -m "Added GraphBuilder facade"
```

---

### Task 11: Integration Test

**Files:**
- Create: `src/test/java/com/graphbuilder/GraphBuilderIntegrationTest.java`
- Create: `src/test/resources/reference-example.java`

- [ ] **Step 1: Create reference example source file**

Save to `src/test/resources/reference-example.java`:

```java
package com.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.io.File;
import java.util.stream.Collectors;

class FileCollector {
    private String dirName;
    private final List<File> files = new ArrayList<>();

    public void collect(Path directory) {
        dirName = directory.getFileName().toString();
        List<Path> filePathes = new ArrayList<>();
        for (Path filePathe : filePathes) {
            files.add(filePathe.toFile());
        }
    }
}

class StreamFileCollector {
    public void collect(Path directory, int depth) {
        List<Path> filePathes = new ArrayList<>();
        Stream<Path> stream = filePathes.stream();
        stream.forEach(path -> {
                collect(path, depth - 1);
            });
    }
}
```

- [ ] **Step 2: Write integration test**

```java
package com.graphbuilder;

import com.graphbuilder.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static com.graphbuilder.model.TokenVertexCategory.*;
import static com.graphbuilder.model.EdgeCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphBuilderIntegrationTest {

    private static AsgGraph graph;

    @BeforeAll
    static void buildGraph() throws IOException {
        String source = Files.readString(Path.of("src/test/resources/reference-example.java"));
        var builder = new GraphBuilder();
        graph = builder.buildFromSource(source, "reference-example.java");
    }

    @Test
    void fileCollectorClassVertices() {
        // CLASS(class) + CLASS_DECL(FileCollector) should exist
        assertTrue(hasVertex(CLASS, "class"));
        assertTrue(hasVertex(CLASS_DECLARATION, "FileCollector"));
    }

    @Test
    void fileCollectorFieldVertices() {
        assertTrue(hasVertex(PRIVATE, "private"));
        assertTrue(hasVertex(TYPE_IDENTIFIER, "String"));
        assertTrue(hasVertex(FIELD_VAR_DECLARATION, "dirName"));
        assertTrue(hasVertex(FINAL, "final"));
        assertTrue(hasVertex(TYPE_IDENTIFIER, "List"));
        assertTrue(hasVertex(TYPE_IDENTIFIER, "File"));
        assertTrue(hasVertex(FIELD_VAR_DECLARATION, "files"));
        assertTrue(hasVertex(NEW, "new"));
        assertTrue(hasVertex(CONSTRUCTOR_INVOCATION, "ArrayList"));
    }

    @Test
    void fileCollectorMethodVertices() {
        assertTrue(hasVertex(PUBLIC, "public"));
        assertTrue(hasVertex(TYPE_IDENTIFIER, "void"));
        assertTrue(hasVertex(METHOD_DECLARATION, "collect"));
        assertTrue(hasVertex(TYPE_IDENTIFIER, "Path"));
        assertTrue(hasVertex(PARAM_VAR_DECLARATION, "directory"));
    }

    @Test
    void fileCollectorMethodBodyVertices() {
        assertTrue(hasVertex(FIELD_VAR_ACCESS, "dirName"));
        assertTrue(hasVertex(PARAM_VAR_ACCESS, "directory"));
        assertTrue(hasVertex(METHOD_INVOCATION, "getFileName"));
        assertTrue(hasVertex(METHOD_INVOCATION, "toString"));
        assertTrue(hasVertex(LOCAL_VAR_DECLARATION, "filePathes"));
        assertTrue(hasVertex(FOR, "for"));
        assertTrue(hasVertex(LOCAL_VAR_DECLARATION, "filePathe"));
        assertTrue(hasVertex(FIELD_VAR_ACCESS, "files"));
        assertTrue(hasVertex(METHOD_INVOCATION, "add"));
        assertTrue(hasVertex(METHOD_INVOCATION, "toFile"));
    }

    @Test
    void streamFileCollectorVertices() {
        assertTrue(hasVertex(CLASS_DECLARATION, "StreamFileCollector"));
        assertTrue(hasVertex(PARAM_VAR_DECLARATION, "depth"));
        assertTrue(hasVertex(TYPE_IDENTIFIER, "Stream"));
        assertTrue(hasVertex(LOCAL_VAR_DECLARATION, "stream"));
        assertTrue(hasVertex(METHOD_INVOCATION, "forEach"));
        assertTrue(hasVertex(LAMBDA_VAR_DECLARATION, "path"));
        assertTrue(hasVertex(LAMBDA_VAR_ACCESS, "path"));
        assertTrue(hasVertex(PARAM_VAR_ACCESS, "depth"));
        assertTrue(hasVertex(INTEGER_LITERAL, "1"));
    }

    // --- Edge tests ---

    @Test
    void declaringEdges() {
        assertTrue(hasEdge(CLASS, "class", CLASS_DECLARATION, "FileCollector", DECLARING));
        assertTrue(hasEdge(TYPE_IDENTIFIER, "String", FIELD_VAR_DECLARATION, "dirName", DECLARING));
        assertTrue(hasEdge(TYPE_IDENTIFIER, "void", METHOD_DECLARATION, "collect", DECLARING));
    }

    @Test
    void attributeEdges() {
        assertTrue(hasEdge(CLASS_DECLARATION, "FileCollector", PRIVATE, "private", ATTRIBUTE));
        assertTrue(hasEdge(CLASS_DECLARATION, "FileCollector", PUBLIC, "public", ATTRIBUTE));
    }

    @Test
    void callEdges() {
        assertTrue(hasEdge(PARAM_VAR_ACCESS, "directory", METHOD_INVOCATION, "getFileName", CALL));
        assertTrue(hasEdge(METHOD_INVOCATION, "getFileName", METHOD_INVOCATION, "toString", CALL));
        assertTrue(hasEdge(FIELD_VAR_ACCESS, "files", METHOD_INVOCATION, "add", CALL));
        assertTrue(hasEdge(LOCAL_VAR_ACCESS, "filePathe", METHOD_INVOCATION, "toFile", CALL));
    }

    @Test
    void assignEdges() {
        assertTrue(hasEdge(FIELD_VAR_ACCESS, "dirName", PARAM_VAR_ACCESS, "directory", ASSIGN));
        assertTrue(hasEdge(FIELD_VAR_DECLARATION, "files", NEW, "new", ASSIGN));
    }

    @Test
    void genericEdges() {
        assertTrue(hasEdge(TYPE_IDENTIFIER, "List", TYPE_IDENTIFIER, "File", GENERIC));
        assertTrue(hasEdge(TYPE_IDENTIFIER, "List", TYPE_IDENTIFIER, "Path", GENERIC));
    }

    @Test
    void creationEdges() {
        assertTrue(hasEdge(NEW, "new", CONSTRUCTOR_INVOCATION, "ArrayList", CREATION));
    }

    @Test
    void statementEdges() {
        assertTrue(hasEdge(LAMBDA_VAR_DECLARATION, "path", METHOD_INVOCATION, "collect", STATEMENT));
    }

    @Test
    void controlFlowScopeEdges() {
        assertTrue(hasEdge(METHOD_DECLARATION, "collect", FOR, "for", CONTROL_FLOW_SCOPE));
    }

    @Test
    void operationEdges() {
        assertTrue(hasEdge(PARAM_VAR_ACCESS, "depth", INTEGER_LITERAL, "1", OPERATION));
    }

    @Test
    void formalParamEdges() {
        assertTrue(hasEdge(METHOD_DECLARATION, "collect", TYPE_IDENTIFIER, "Path", FORMAL_PARAMETER));
    }

    @Test
    void nextDeclEdges() {
        // StreamFileCollector.collect(Path directory, int depth)
        // PARAM_VAR_DECL(directory) → TYPE_ID(int) : NEXT_DECL
        assertTrue(hasEdge(PARAM_VAR_DECLARATION, "directory", TYPE_IDENTIFIER, "int", NEXT_DECLARATION));
    }

    @Test
    void argumentEdges() {
        assertTrue(hasEdge(METHOD_INVOCATION, "forEach", LAMBDA_VAR_DECLARATION, "path", ARGUMENT));
    }

    @Test
    void dotExporterProducesValidDot() {
        var exporter = new com.graphbuilder.export.DotExporter();
        String dot = exporter.export(graph);
        assertTrue(dot.startsWith("strict digraph G {"));
        assertTrue(dot.contains("CLASS_DECL"));
        assertTrue(dot.contains("DECLARING"));
        assertTrue(dot.endsWith("}\n"));
    }

    // --- Helpers ---

    private boolean hasVertex(TokenVertexCategory category, String value) {
        return graph.vertices().stream()
            .anyMatch(v -> v.category() == category && v.value().equals(value));
    }

    private boolean hasEdge(TokenVertexCategory srcCat, String srcVal,
                            TokenVertexCategory tgtCat, String tgtVal, EdgeCategory edgeCat) {
        return graph.edges().stream().anyMatch(e ->
            e.source().category() == srcCat && e.source().value().equals(srcVal) &&
            e.target().category() == tgtCat && e.target().value().equals(tgtVal) &&
            e.category() == edgeCat);
    }
}
```

- [ ] **Step 3: Run integration tests**

Run: `./gradlew test --tests "com.graphbuilder.GraphBuilderIntegrationTest"`
Expected: all pass. Fix any failures — these tests are the acceptance criteria. Debug by printing `DotExporter.export(graph)` and comparing with the reference DOT.

- [ ] **Step 4: Commit**

```bash
git add src/test/
git commit -m "Added integration test with reference example"
```

---

### Task 12: CLI

**Files:**
- Create: `src/main/java/com/graphbuilder/cli/Main.java`

- [ ] **Step 1: Implement Main**

```java
package com.graphbuilder.cli;

import com.graphbuilder.GraphBuilder;
import com.graphbuilder.export.DotExporter;
import com.graphbuilder.model.AsgGraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: graph-builder <file.java|directory> [-o output.dot]");
            System.exit(1);
        }

        List<Path> inputFiles = new ArrayList<>();
        Path outputFile = null;

        for (int i = 0; i < args.length; i++) {
            if ("-o".equals(args[i]) && i + 1 < args.length) {
                outputFile = Path.of(args[++i]);
            } else {
                Path path = Path.of(args[i]);
                if (Files.isDirectory(path)) {
                    try (var stream = Files.walk(path)) {
                        stream.filter(p -> p.toString().endsWith(".java"))
                              .forEach(inputFiles::add);
                    } catch (IOException e) {
                        System.err.println("Error reading directory: " + e.getMessage());
                        System.exit(1);
                    }
                } else {
                    inputFiles.add(path);
                }
            }
        }

        if (inputFiles.isEmpty()) {
            System.err.println("No .java files found");
            System.exit(1);
        }

        try {
            var builder = new GraphBuilder();
            AsgGraph graph = builder.buildFromFiles(inputFiles);

            var exporter = new DotExporter();
            if (outputFile != null) {
                exporter.exportToFile(graph, outputFile);
                System.out.println("Graph written to " + outputFile);
            } else {
                System.out.println(exporter.export(graph));
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
```

- [ ] **Step 2: Verify CLI works**

Run:
```bash
./gradlew run --args="src/test/resources/reference-example.java"
```
Expected: DOT output printed to stdout.

- [ ] **Step 3: Build fat JAR and test**

Run:
```bash
./gradlew jar
java -jar build/libs/graph-builder-0.1.0.jar src/test/resources/reference-example.java
```
Expected: same DOT output.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/graphbuilder/cli/Main.java
git commit -m "Added CLI entry point"
```

---

### Task 13: Final Verification

- [ ] **Step 1: Run all tests**

Run: `./gradlew test`
Expected: all tests pass.

- [ ] **Step 2: Run CLI on reference example and inspect output**

Run: `./gradlew run --args="src/test/resources/reference-example.java" > output.dot`

Compare `output.dot` with the reference DOT from the spec. Every vertex and edge from the reference should be present. Vertex IDs may differ — the important thing is the graph structure (vertex categories/values and edge categories between them).

- [ ] **Step 3: Commit any final fixes**

```bash
git add -A
git commit -m "Finalized Graph-Builder v0.1.0"
```
