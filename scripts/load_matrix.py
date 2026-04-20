"""Load an exported ASG adjacency matrix into a PyTorch tensor.

Usage:
    python scripts/load_matrix.py graph.csv
"""
import argparse
from pathlib import Path

import numpy as np
import torch


def load(csv_path: Path):
    matrix = torch.from_numpy(
        np.loadtxt(csv_path, delimiter=",", dtype=np.int64)
    )
    legend_path = csv_path.with_name(csv_path.stem + ".legend.tsv")
    with open(legend_path) as f:
        next(f)
        legend = [line.rstrip("\n").split("\t") for line in f]
    return matrix, legend


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv", type=Path)
    args = ap.parse_args()

    matrix, legend = load(args.csv)
    n = matrix.shape[0]
    edges = int((matrix != 0).sum())

    print(f"matrix: shape={tuple(matrix.shape)} dtype={matrix.dtype}")
    print(f"vertices: {n}   edges: {edges}   density: {edges / (n * n):.3%}")
    print(f"legend: {len(legend)} rows; first 3 = {legend[:3]}")

    rows, cols = torch.nonzero(matrix, as_tuple=True)
    print("\nfirst 5 edges (row -> col : code):")
    for r, c in zip(rows[:5].tolist(), cols[:5].tolist()):
        rv = legend[r]
        cv = legend[c]
        print(f"  {r:3d} ({rv[2]} {rv[3]!r}) -> {c:3d} ({cv[2]} {cv[3]!r}) : {int(matrix[r, c])}")


if __name__ == "__main__":
    main()
