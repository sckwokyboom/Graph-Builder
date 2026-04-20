# Graph Builder

Builds an Abstract Semantic Graph (ASG) from Java source using Eclipse JDT.
Vertices are tokens (identifiers, keywords, literals, declarations) and edges
encode relationships between them — declaring, calling, assigning, control flow,
and so on. Storage is backed by [JGraphT](https://jgrapht.org/)'s
`DirectedAcyclicGraph`, so cycles are rejected at insertion time.

## Build and test

```bash
./gradlew build
./gradlew test
```

## Exporting graphs

The CLI takes a single `.java` file or a directory and writes one or both of:
- `-o graph.dot` — Graphviz DOT, for visual inspection.
- `-m graph.csv` — a dense adjacency matrix in CSV (see below).

If neither is given, DOT is printed to stdout.

### DOT

```bash
./gradlew run --args='src/test/resources/reference-example.java -o graph.dot'
dot -Tpng graph.dot -o graph.png
```

### Adjacency matrix

```bash
./gradlew run --args='src/test/resources/reference-example.java -m graph.csv'
```

This writes two files:
- `graph.csv` — an `N×N` matrix of integers (comma-separated, one row per line).
  `matrix[i][j]` holds the edge-category code from vertex `i` to vertex `j`, or
  `0` when there is no edge. Rows and columns are ordered by vertex id.
- `graph.legend.tsv` — a tab-separated table `index / id / category / value` so
  you can read off which vertex each row represents.

Edge-category codes are declared in
[`EdgeCategory`](src/main/java/com/graphbuilder/model/EdgeCategory.java):
`ASSIGN=1`, `ARGUMENT=2`, `ATTRIBUTE=3`, `ANCESTOR=4`, `CALL=5`, `CREATION=6`,
`CONTROL_FLOW_SCOPE=7`, `DECLARING=8`, `FORMAL_PARAMETER=9`, `GENERIC=10`,
`IMPORTS=11`, `KEYWORD_CHAIN=12`, `NEXT_TOKEN=13`, `NEXT_DECLARATION=14`,
`NEXT_ANCESTOR=15`, `OPERATION=16`, `STATEMENT=17`, `TYPE_ONTOLOGY=18`,
`VARIABLE_ONTOLOGY=19`, `SYNTAX_LINK=20`.

### Loading and visualizing in Python

Two small helpers live under [`scripts/`](scripts):

```bash
pip install -r scripts/requirements.txt

# Print shape, density, and the first few edges with their categories.
python scripts/load_matrix.py graph.csv

# Heatmap colored by edge category, with vertex labels on both axes.
python scripts/visualize_matrix.py graph.csv -o graph.png
```

Minimal loader, if you'd rather write your own:

```python
import numpy as np
import torch

matrix = torch.from_numpy(
    np.loadtxt("graph.csv", delimiter=",", dtype=np.int64)
)
# matrix.shape == (N, N), matrix.dtype == torch.int64

with open("graph.legend.tsv") as f:
    next(f)  # skip header
    legend = [line.rstrip("\n").split("\t") for line in f]
# legend[i] == [index, id, category, value]
```

The matrix is dense, which is fine for small examples but wasteful for larger
graphs — most cells will be `0`. For real workloads a sparse COO representation
(`edge_index [2, E]` + `edge_attr [E]`, the PyTorch Geometric convention) is a
better fit; not implemented yet.
