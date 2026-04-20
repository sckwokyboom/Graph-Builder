package com.graphbuilder.model;

import org.jgrapht.graph.DefaultEdge;

public class AsgEdge extends DefaultEdge {

    private final EdgeCategory category;
    private int insertionOrder;

    public AsgEdge(EdgeCategory category) {
        this.category = category;
    }

    /** Set by {@link AsgGraph} immediately after insertion; used for stable ordering. */
    void setInsertionOrder(int order) {
        this.insertionOrder = order;
    }

    public int insertionOrder() {
        return insertionOrder;
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
