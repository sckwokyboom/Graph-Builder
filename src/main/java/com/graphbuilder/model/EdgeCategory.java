package com.graphbuilder.model;

public enum EdgeCategory {
    ASSIGN("ASSIGN", 1),
    ARGUMENT("ARGUMENT", 2),
    ATTRIBUTE("ATTRIBUTE", 3),
    ANCESTOR("ANCESTOR", 4),
    CALL("CALL", 5),
    CREATION("CREATION", 6),
    CONTROL_FLOW_SCOPE("CONTROL_FLOW_SCOPE", 7),
    DECLARING("DECLARING", 8),
    FORMAL_PARAMETER("FORMAL_PARAM", 9),
    GENERIC("GENERIC", 10),
    IMPORTS("IMPORTS", 11),
    KEYWORD_CHAIN("KEYWORD_CHAIN", 12),
    NEXT_TOKEN("NEXT_TOKEN", 13),
    NEXT_DECLARATION("NEXT_DECL", 14),
    NEXT_ANCESTOR("NEXT_ANCESTOR", 15),
    OPERATION("OPERATION", 16),
    STATEMENT("STATEMENT", 17),
    TYPE_ONTOLOGY("TYPE_ONTOLOGY", 18),
    VARIABLE_ONTOLOGY("VARIABLE_ONTOLOGY", 19),
    SYNTAX_LINK("SYNTAX_LINK", 20);

    private final String dotLabel;
    private final int code;

    EdgeCategory(String dotLabel, int code) {
        this.dotLabel = dotLabel;
        this.code = code;
    }

    public String dotLabel() {
        return dotLabel;
    }

    public int code() {
        return code;
    }
}
