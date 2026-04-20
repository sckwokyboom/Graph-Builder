"""Render the ASG adjacency matrix as a heatmap with legend labels.

Usage:
    python scripts/visualize_matrix.py graph.csv           # shows window
    python scripts/visualize_matrix.py graph.csv -o fig.png  # writes PNG

Each cell is colored by its edge-category code. Axis ticks are labeled with
``index: CATEGORY value`` so you can read off which vertex each row/column is.
"""
import argparse
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
from matplotlib.colors import BoundaryNorm, ListedColormap
from matplotlib.patches import Patch

EDGE_CATEGORIES = [
    "NONE",
    "ASSIGN", "ARGUMENT", "ATTRIBUTE", "ANCESTOR", "CALL", "CREATION",
    "CONTROL_FLOW_SCOPE", "DECLARING", "FORMAL_PARAMETER", "GENERIC",
    "IMPORTS", "KEYWORD_CHAIN", "NEXT_TOKEN", "NEXT_DECLARATION",
    "NEXT_ANCESTOR", "OPERATION", "STATEMENT", "TYPE_ONTOLOGY",
    "VARIABLE_ONTOLOGY", "SYNTAX_LINK",
]


def load(csv_path: Path):
    matrix = np.loadtxt(csv_path, delimiter=",", dtype=np.int64)
    legend_path = csv_path.with_name(csv_path.stem + ".legend.tsv")
    with open(legend_path) as f:
        next(f)
        legend = [line.rstrip("\n").split("\t") for line in f]
    return matrix, legend


def tick_label(row, max_value_len=12):
    index, _id, category, value = row
    value = value if len(value) <= max_value_len else value[: max_value_len - 1] + "…"
    return f"{index}: {category} {value!r}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv", type=Path)
    ap.add_argument("-o", "--output", type=Path, help="save figure to file instead of showing")
    args = ap.parse_args()

    matrix, legend = load(args.csv)
    n = matrix.shape[0]

    present_codes = sorted({int(c) for c in np.unique(matrix) if c != 0})

    base = plt.get_cmap("tab20", max(20, len(EDGE_CATEGORIES) - 1))
    colors = ["#ffffff"] + [base(i) for i in range(len(EDGE_CATEGORIES) - 1)]
    cmap = ListedColormap(colors)
    norm = BoundaryNorm(np.arange(len(EDGE_CATEGORIES) + 1) - 0.5, cmap.N)

    fig_size = max(8, n * 0.18)
    fig, ax = plt.subplots(figsize=(fig_size + 4, fig_size))
    ax.imshow(matrix, cmap=cmap, norm=norm, interpolation="nearest")

    labels = [tick_label(row) for row in legend]
    ax.set_xticks(range(n))
    ax.set_yticks(range(n))
    ax.set_xticklabels(labels, rotation=90, fontsize=6)
    ax.set_yticklabels(labels, fontsize=6)
    ax.set_xlabel("target vertex")
    ax.set_ylabel("source vertex")
    ax.set_title(f"ASG adjacency matrix ({n}×{n}, {int((matrix != 0).sum())} edges)")

    ax.set_xticks(np.arange(-0.5, n, 1), minor=True)
    ax.set_yticks(np.arange(-0.5, n, 1), minor=True)
    ax.grid(which="minor", color="#dddddd", linewidth=0.3)
    ax.tick_params(which="minor", length=0)

    handles = [
        Patch(facecolor=cmap(code), edgecolor="#888888",
              label=f"{code} {EDGE_CATEGORIES[code]}")
        for code in present_codes
    ]
    ax.legend(handles=handles, title="edge category",
              bbox_to_anchor=(1.02, 1), loc="upper left", fontsize=8)

    fig.tight_layout()

    if args.output:
        fig.savefig(args.output, dpi=150, bbox_inches="tight")
        print(f"wrote {args.output}")
    else:
        plt.show()


if __name__ == "__main__":
    main()
