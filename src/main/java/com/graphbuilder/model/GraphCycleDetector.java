package com.graphbuilder.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects cycles in an AsgGraph. A well-formed ASG must be a DAG (Directed Acyclic Graph).
 *
 * <p>Uses iterative DFS with three colors (WHITE = unvisited, GRAY = in current path, BLACK = done)
 * to detect back edges, which indicate cycles. Iterative to avoid stack overflow on deep graphs.
 */
public final class GraphCycleDetector {

    private enum Color { WHITE, GRAY, BLACK }

    private GraphCycleDetector() {}

    /**
     * Returns true if the graph is a DAG (no cycles).
     */
    public static boolean isDag(AsgGraph graph) {
        return findCycle(graph).isEmpty();
    }

    /**
     * Returns the first cycle found as an ordered list of vertices (source → ... → source).
     * Returns an empty list if the graph is acyclic.
     */
    public static List<ITokenVertex> findCycle(AsgGraph graph) {
        Map<Integer, List<ITokenVertex>> adjacency = buildAdjacency(graph);
        Map<Integer, Color> colors = new HashMap<>();
        Map<Integer, ITokenVertex> parent = new HashMap<>();

        for (ITokenVertex vertex : graph.vertices()) {
            colors.put(vertex.id(), Color.WHITE);
        }

        for (ITokenVertex vertex : graph.vertices()) {
            if (colors.get(vertex.id()) != Color.WHITE) continue;
            List<ITokenVertex> cycle = dfsFindCycle(vertex, adjacency, colors, parent);
            if (!cycle.isEmpty()) return cycle;
        }
        return List.of();
    }

    private static Map<Integer, List<ITokenVertex>> buildAdjacency(AsgGraph graph) {
        Map<Integer, List<ITokenVertex>> adjacency = new HashMap<>();
        for (AsgEdge edge : graph.edges()) {
            adjacency.computeIfAbsent(edge.source().id(), k -> new ArrayList<>()).add(edge.target());
        }
        return adjacency;
    }

    /**
     * Iterative DFS that records the current path and reports back edges as cycles.
     * Uses an explicit stack of (vertex, iterator-over-unvisited-neighbors) frames.
     */
    private static List<ITokenVertex> dfsFindCycle(
            ITokenVertex start,
            Map<Integer, List<ITokenVertex>> adjacency,
            Map<Integer, Color> colors,
            Map<Integer, ITokenVertex> parent) {

        record Frame(ITokenVertex vertex, int neighborIndex) {}
        List<Frame> stack = new ArrayList<>();

        colors.put(start.id(), Color.GRAY);
        stack.add(new Frame(start, 0));

        while (!stack.isEmpty()) {
            Frame frame = stack.get(stack.size() - 1);
            List<ITokenVertex> neighbors = adjacency.getOrDefault(frame.vertex.id(), List.of());

            if (frame.neighborIndex >= neighbors.size()) {
                colors.put(frame.vertex.id(), Color.BLACK);
                stack.remove(stack.size() - 1);
                continue;
            }

            ITokenVertex neighbor = neighbors.get(frame.neighborIndex);
            stack.set(stack.size() - 1, new Frame(frame.vertex, frame.neighborIndex + 1));

            Color neighborColor = colors.get(neighbor.id());
            if (neighborColor == Color.GRAY) {
                // Back edge → cycle. Reconstruct path from neighbor through stack to frame.vertex.
                List<ITokenVertex> cycle = new ArrayList<>();
                boolean inCycle = false;
                for (Frame f : stack) {
                    if (!inCycle && f.vertex.id() == neighbor.id()) {
                        inCycle = true;
                    }
                    if (inCycle) {
                        cycle.add(f.vertex);
                    }
                }
                cycle.add(neighbor); // close the cycle
                return cycle;
            }
            if (neighborColor == Color.WHITE) {
                colors.put(neighbor.id(), Color.GRAY);
                parent.put(neighbor.id(), frame.vertex);
                stack.add(new Frame(neighbor, 0));
            }
            // BLACK → already fully explored, skip
        }
        return List.of();
    }
}
