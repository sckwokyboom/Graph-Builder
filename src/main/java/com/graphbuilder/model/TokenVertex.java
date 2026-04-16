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
