package com.graphbuilder.model;

public interface ITokenVertex {
    int id();
    TokenVertexCategory category();
    String value();
    int documentOffset();
    int line();
    int column();
    String sourcePath();
}
