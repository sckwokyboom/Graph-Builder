"""Collapse edge-category codes to a plain 0/1 adjacency matrix.

Loads the CSV into a torch tensor, replaces every value greater than 1 with 1,
and either prints a summary or writes a new CSV. Useful when you want the graph
structure without edge typing.

Usage:
    python scripts/binarize_matrix.py graph.csv              # summary only
    python scripts/binarize_matrix.py graph.csv -o bin.csv   # also save CSV
"""
import argparse
from pathlib import Path

import numpy as np
import torch


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv", type=Path)
    ap.add_argument("-o", "--output", type=Path, help="write binary matrix to this CSV")
    args = ap.parse_args()

    matrix = torch.from_numpy(
        np.loadtxt(args.csv, delimiter=",", dtype=np.int64)
    )

    binary = torch.where(matrix > 1, torch.ones_like(matrix), matrix)

    n = binary.shape[0]
    edges = int(binary.sum())
    print(f"matrix: shape={tuple(binary.shape)} dtype={binary.dtype}")
    print(f"edges: {edges}   unique values: {sorted(torch.unique(binary).tolist())}")

    if args.output:
        np.savetxt(args.output, binary.numpy(), fmt="%d", delimiter=",")
        print(f"wrote {args.output}")


if __name__ == "__main__":
    main()
