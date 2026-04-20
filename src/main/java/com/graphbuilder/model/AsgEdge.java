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
