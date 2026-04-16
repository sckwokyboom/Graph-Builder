package com.graphbuilder.model;

public enum EdgeCategory {
    ASSIGN("ASSIGN"),
    ARGUMENT("ARGUMENT"),
    ATTRIBUTE("ATTRIBUTE"),
    ANCESTOR("ANCESTOR"),
    CALL("CALL"),
    CREATION("CREATION"),
    CONTROL_FLOW_SCOPE("CONTROL_FLOW_SCOPE"),
    DECLARING("DECLARING"),
    FORMAL_PARAMETER("FORMAL_PARAM"),
    GENERIC("GENERIC"),
    IMPORTS("IMPORTS"),
    KEYWORD_CHAIN("KEYWORD_CHAIN"),
    NEXT_TOKEN("NEXT_TOKEN"),
    NEXT_DECLARATION("NEXT_DECL"),
    NEXT_ANCESTOR("NEXT_ANCESTOR"),
    OPERATION("OPERATION"),
    STATEMENT("STATEMENT"),
    TYPE_ONTOLOGY("TYPE_ONTOLOGY"),
    VARIABLE_ONTOLOGY("VARIABLE_ONTOLOGY"),
    SYNTAX_LINK("SYNTAX_LINK");

    private final String dotLabel;

    EdgeCategory(String dotLabel) {
        this.dotLabel = dotLabel;
    }

    public String dotLabel() {
        return dotLabel;
    }
}
