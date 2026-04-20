package com.graphbuilder.model;

/**
 * Thrown when adding an edge to {@link AsgGraph} would introduce a cycle.
 *
 * <p>The ASG is a DAG by construction; a cycle means the builder logic is wrong.
 * This exception preserves the offending edge (source, target, category) so the
 * failure points directly at the faulty builder, not at a post-hoc detection site.
 */
public class AsgCycleException extends RuntimeException {

    private final ITokenVertex source;
    private final ITokenVertex target;
    private final EdgeCategory category;

    public AsgCycleException(ITokenVertex source, ITokenVertex target, EdgeCategory category, Throwable cause) {
        super(buildMessage(source, target, category), cause);
        this.source = source;
        this.target = target;
        this.category = category;
    }

    public ITokenVertex source() { return source; }
    public ITokenVertex target() { return target; }
    public EdgeCategory category() { return category; }

    private static String buildMessage(ITokenVertex source, ITokenVertex target, EdgeCategory category) {
        return "Cycle detected when adding edge [" + category + "]"
            + " from vertex #" + source.id() + "(" + source.category() + " '" + source.value() + "' at "
            + source.sourcePath() + ":" + source.line() + ")"
            + " to vertex #" + target.id() + "(" + target.category() + " '" + target.value() + "' at "
            + target.sourcePath() + ":" + target.line() + ")";
    }
}
