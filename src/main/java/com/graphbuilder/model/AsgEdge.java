package com.graphbuilder.model;

public record AsgEdge(
    ITokenVertex source,
    ITokenVertex target,
    EdgeCategory category
) {}
