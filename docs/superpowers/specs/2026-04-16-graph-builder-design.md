# Graph-Builder Design Spec

## Overview

Java 23+ tool that builds Abstract Semantic Graphs (ASG) from Java source code. An ASG unifies control flow, data flow, and syntactic structure into a single directed graph with typed vertices and categorized edges. Primary use case: retriever for an Agentic IDE — finding chains (e.g. return type → paramTypes/fieldTypes) for LLM context augmentation.

## Decisions

- **Parser:** Eclipse JDT — full Java 23+ support, type-resolved bindings, pure Java dependency
- **Architecture:** Multi-pass pipeline — isolated builders per edge category, independently testable and extensible
- **Output:** DOT format (strict digraph)
- **Build system:** Gradle (Kotlin DSL)
- **Delivery:** API library + thin CLI wrapper

## 1. Core Model

### TokenVertexCategory

Single flat enum containing all ~60 vertex types from the ASG specification:

```
// Control flow
ANNOTATION_DECLARATION, ARROW, BREAK, CASE, CATCH, CLASS_DECLARATION,
CONSTRUCTOR_DECLARATION, CONTINUE, DO, ELSE, ENUM_DECLARATION,
ENUM_CONSTANT_DECLARATION, FINALLY, GOTO, IF, INTERFACE_DECLARATION,
FIELD_VAR_DECLARATION, FOR, LAMBDA_VAR_DECLARATION, LOCAL_VAR_DECLARATION,
METHOD_DECLARATION, PARAM_VAR_DECLARATION, SWITCH, SYNCHRONIZED, THROW, TRY, WHILE

// Data flow
ARITHMETIC_ASSIGN, ARRAY_CONSTRUCTOR_INVOCATION, BINARY_ARITHMETICS,
BINARY_BIT, BINARY_LOGICS, BIT_ASSIGN, BIT_LOGIC, BOOLEAN_LITERAL,
CHAR_LITERAL, CONSTRUCTOR_INVOCATION, COMPARISON, DOUBLE_LITERAL,
EQUAL, EQUAL_EQUAL, FLOAT_LITERAL, GREATER, INSTANCE_OF, INTEGER_LITERAL,
FIELD_VAR_ACCESS, METHOD_INVOCATION, METHOD_REFERENCE, LAMBDA_VAR_ACCESS,
LESS, LOCAL_VAR_ACCESS, LONG_LITERAL, NULL, PARAM_VAR_ACCESS, RETURN,
STRING_LITERAL, UNARY_ARITHMETICS, UNARY_LOGICS

// Syntax
AT, ABSTRACT, ARRAY_TYPE_IDENTIFIER, ASSERT, CLASS, COLON, COLON_COLON,
COMMA, CONST, DEFAULT, DOT_TOKEN, ELLIPSIS, ENUM, EXTENDS, FINAL,
IMPLEMENTS, IMPORT, INTERFACE, TYPE_IDENTIFIER, LBRACE, LBRACK, LPAREN,
NATIVE, NEW, PACKAGE, PRIVATE, PROTECTED, PUBLIC, RBRACE, RBRACK, RPAREN,
QUESTION, SEMICOLON, STATIC, SUPER, THIS, THROWS, TILDE, TRANSIENT, VOLATILE
```

### EdgeCategory

Enum of ~20 edge types:

```
ASSIGN, ARGUMENT, ATTRIBUTE, ANCESTOR, CALL, CREATION,
CONTROL_FLOW_SCOPE, DECLARING, FORMAL_PARAMETER, GENERIC, IMPORTS,
KEYWORD_CHAIN, NEXT_TOKEN, NEXT_DECLARATION, NEXT_ANCESTOR, OPERATION,
STATEMENT, TYPE_ONTOLOGY, VARIABLE_ONTOLOGY, SYNTAX_LINK
```

### ITokenVertex

Interface for graph vertices:

- `int id()` — global sequential ID (0, 1, 2...)
- `TokenVertexCategory category()` — vertex type
- `String value()` — textual value from source (`"FileCollector"`, `"private"`, `"0"`)
- `int documentOffset()` — character position from file start
- `int line()` — source line number
- `int column()` — source column number
- `String sourcePath()` — path to source file
### TokenVertex

Record implementing `ITokenVertex`. Immutable. Traversal state (`visited`) is tracked externally by graph traversal algorithms, not stored in the vertex.

### AsgEdge

Record: `ITokenVertex source`, `ITokenVertex target`, `EdgeCategory category`.

### AsgGraph

Container holding ordered `List<ITokenVertex>` and `List<AsgEdge>`. Provides lookup by id and queries by category.

## 2. Pipeline

Input: Java source → Eclipse JDT `CompilationUnit` → chain of builders enriches `AsgGraph`.

### Phase 0: JdtParser

Wraps Eclipse JDT `ASTParser`. Configures Java 23+ language level, optional classpath for resolved bindings. Returns `CompilationUnit`.

### Phase 1: VertexBuilder

Single `ASTVisitor` pass. Maps JDT nodes to `ITokenVertex` instances:

| JDT Node | Vertex(es) Created |
|---|---|
| `TypeDeclaration` | `CLASS` + `CLASS_DECLARATION` |
| `MethodDeclaration` | modifier + `TYPE_IDENTIFIER`(return) + `METHOD_DECLARATION` |
| `FieldDeclaration` | modifier(s) + `TYPE_IDENTIFIER` + `FIELD_VAR_DECLARATION` |
| `VariableDeclarationStatement` | `TYPE_IDENTIFIER` + `LOCAL_VAR_DECLARATION` |
| `SingleVariableDeclaration` (param) | `TYPE_IDENTIFIER` + `PARAM_VAR_DECLARATION` |
| `SimpleName` (in expression) | `FIELD_VAR_ACCESS` / `LOCAL_VAR_ACCESS` / `PARAM_VAR_ACCESS` (via binding) |
| `MethodInvocation` | `METHOD_INVOCATION` |
| `ClassInstanceCreation` | `NEW` + `CONSTRUCTOR_INVOCATION` |
| `LambdaExpression` | `LAMBDA_VAR_DECLARATION` |
| `InfixExpression` | `BINARY_ARITHMETICS` / `COMPARISON` / `BINARY_LOGICS` etc. |
| Literals | `INTEGER_LITERAL`, `STRING_LITERAL`, `BOOLEAN_LITERAL`, etc. |
| Modifiers | `PUBLIC`, `PRIVATE`, `STATIC`, `FINAL`, etc. |
| `ParameterizedType` | outer `TYPE_IDENTIFIER` + inner `TYPE_IDENTIFIER` (generic arg) |
| Control flow statements | `FOR`, `IF`, `WHILE`, `TRY`, `SWITCH`, etc. |

Each vertex gets a sequential `id`, `documentOffset`/`line`/`column` from the JDT node.

### Phase 2: StructuralEdgeBuilder

- **NEXT_TOKEN** — sequential connection between vertices within same expression/declaration
- **KEYWORD_CHAIN** — modifier chains: `PRIVATE` → `FINAL`, `PUBLIC` → `STATIC`
- **SYNTAX_LINK** — connections to syntax vertices (brackets, dots, commas)

### Phase 3: DeclarationEdgeBuilder

- **DECLARING** — type to name: `TYPE_ID` → `METHOD_DECL`, `TYPE_ID` → `FIELD_VAR_DECL`, `CLASS` → `CLASS_DECL`
- **ATTRIBUTE** — `CLASS_DECL` to its members (fields, methods as keyword-chains starting with modifier)
- **FORMAL_PARAMETER** — `METHOD_DECL` to parameter type vertices
- **NEXT_DECLARATION** — chains between parameters: `PARAM_VAR_DECL` → next `TYPE_ID`

### Phase 4: FlowEdgeBuilder

- **STATEMENT** — from `METHOD_DECL`/`FOR`/`LAMBDA_VAR_DECL` to first vertex of each statement in body
- **CONTROL_FLOW_SCOPE** — from `METHOD_DECL` to nested `FOR`/`IF`/`WHILE`/`TRY`...
- **ASSIGN** — left side to right side: `FIELD_VAR_ACCESS` → rhs vertex, `LOCAL_VAR_DECL` → `NEW`
- **CALL** — object/variable to invoked method: `LOCAL_VAR_ACCESS` → `METHOD_INV`
- **ARGUMENT** — from method/constructor/for to arguments
- **OPERATION** — arithmetic/logic between operands
- **CREATION** — `NEW` → `CONSTRUCTOR_INV`

### Phase 5: TypeEdgeBuilder

- **GENERIC** — `TYPE_ID(List)` → `TYPE_ID(File)`
- **TYPE_ONTOLOGY** — `EXTENDS`/`THROWS`/`INSTANCE_OF` → `TYPE_ID`
- **VARIABLE_ONTOLOGY** — variable access → type in `INSTANCE_OF`
- **ANCESTOR** — inheritance: child `TYPE_ID` → parent `TYPE_ID`
- **IMPORTS** — connections to `IMPORT` vertices

## 3. API

```java
public class GraphBuilder {
    AsgGraph buildFromSource(String sourceCode, String sourcePath);
    AsgGraph buildFromFile(Path javaFile);
    AsgGraph buildFromFiles(List<Path> javaFiles);
}

public class DotExporter {
    String export(AsgGraph graph);
    void exportToFile(AsgGraph graph, Path outputFile);
}
```

`GraphBuilder` orchestrates: JdtParser → VertexBuilder → StructuralEdgeBuilder → DeclarationEdgeBuilder → FlowEdgeBuilder → TypeEdgeBuilder.

## 4. CLI

```
java -jar graph-builder.jar MyFile.java              # stdout
java -jar graph-builder.jar MyFile.java -o out.dot    # file
java -jar graph-builder.jar src/A.java src/B.java     # multiple files
java -jar graph-builder.jar src/                      # recursive directory
```

Thin wrapper: parses args, calls `GraphBuilder` + `DotExporter`, outputs result. No framework — plain `main` with args parsing.

## 5. Project Structure

```
graph-builder/
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/java/com/graphbuilder/
│   ├── model/
│   │   ├── TokenVertexCategory.java
│   │   ├── EdgeCategory.java
│   │   ├── ITokenVertex.java
│   │   ├── TokenVertex.java
│   │   ├── AsgEdge.java
│   │   └── AsgGraph.java
│   ├── parser/
│   │   └── JdtParser.java
│   ├── builder/
│   │   ├── VertexBuilder.java
│   │   ├── StructuralEdgeBuilder.java
│   │   ├── DeclarationEdgeBuilder.java
│   │   ├── FlowEdgeBuilder.java
│   │   └── TypeEdgeBuilder.java
│   ├── export/
│   │   └── DotExporter.java
│   ├── GraphBuilder.java
│   └── cli/
│       └── Main.java
└── src/test/java/com/graphbuilder/
    ├── GraphBuilderTest.java
    ├── builder/
    │   ├── VertexBuilderTest.java
    │   ├── StructuralEdgeBuilderTest.java
    │   ├── DeclarationEdgeBuilderTest.java
    │   ├── FlowEdgeBuilderTest.java
    │   └── TypeEdgeBuilderTest.java
    └── export/
        └── DotExporterTest.java
```

## 6. Testing Strategy

**Key integration test:** Feed the reference example (FileCollector + StreamFileCollector) into `GraphBuilder`, compare DOT output against the reference DOT from the spec. This guarantees exact reproduction.

**Unit tests per builder:** Each builder tested in isolation — given a small Java snippet, verify that the expected vertices/edges are produced.

## 7. Dependencies

- **Eclipse JDT Core** (`org.eclipse.jdt.core`) — Java parsing and type resolution
- **JUnit 5** — testing
- No other runtime dependencies
