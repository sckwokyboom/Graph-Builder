package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.EdgeCategory;
import com.graphbuilder.model.ITokenVertex;
import com.graphbuilder.model.TokenVertexCategory;
import org.eclipse.jdt.core.dom.*;

import java.util.List;
import java.util.Set;

import static com.graphbuilder.model.TokenVertexCategory.*;

/**
 * Builds structural edges: NEXT_TOKEN, KEYWORD_CHAIN, SYNTAX_LINK.
 *
 * <p>KEYWORD_CHAIN links consecutive modifier keywords (e.g., PRIVATE -> FINAL).
 * NEXT_TOKEN links the last modifier/keyword to the following type identifier,
 * and for-each loop variable declarations to their iterable expression.
 */
public class StructuralEdgeBuilder {

    private static final Set<TokenVertexCategory> MODIFIER_CATEGORIES = Set.of(
            PUBLIC, PRIVATE, PROTECTED, STATIC, FINAL, ABSTRACT,
            NATIVE, SYNCHRONIZED, TRANSIENT, VOLATILE, DEFAULT
    );

    public void build(BuildContext context) {
        buildModifierChains(context);
        buildForEachNextToken(context);
    }

    /**
     * Scans consecutive vertex pairs to build KEYWORD_CHAIN and NEXT_TOKEN edges
     * for modifier sequences.
     *
     * Pattern: [modifier1] [modifier2] ... [TYPE_ID]
     * - modifier1 -> modifier2 : KEYWORD_CHAIN
     * - last_modifier -> TYPE_ID : NEXT_TOKEN
     */
    private void buildModifierChains(BuildContext context) {
        List<ITokenVertex> vertices = context.graph().vertices();
        for (int i = 0; i < vertices.size() - 1; i++) {
            ITokenVertex current = vertices.get(i);
            ITokenVertex next = vertices.get(i + 1);

            if (!isModifier(current)) continue;

            if (isModifier(next)) {
                // consecutive modifiers: KEYWORD_CHAIN
                context.addEdge(current, next, EdgeCategory.KEYWORD_CHAIN);
            } else if (next.category() == TYPE_IDENTIFIER) {
                // last modifier -> type: NEXT_TOKEN
                context.addEdge(current, next, EdgeCategory.NEXT_TOKEN);
            }
        }
    }

    /**
     * For enhanced-for loops: LOCAL_VAR_DECL -> iterable's first vertex : NEXT_TOKEN
     *
     * Pattern: for (Type x : iterable) { ... }
     * Produces: LOCAL_VAR_DECL(x) -> first_vertex_of(iterable) : NEXT_TOKEN
     */
    private void buildForEachNextToken(BuildContext context) {
        context.compilationUnit().accept(new ASTVisitor() {
            @Override
            public boolean visit(EnhancedForStatement node) {
                SingleVariableDeclaration param = node.getParameter();
                Expression iterable = node.getExpression();

                ITokenVertex declVertex = context.findVertex(param.getName(), LOCAL_VAR_DECLARATION);
                ITokenVertex iterableVertex = context.firstVertexInRange(iterable);

                if (declVertex != null && iterableVertex != null) {
                    context.addEdge(declVertex, iterableVertex, EdgeCategory.NEXT_TOKEN);
                }
                return true;
            }
        });
    }

    private boolean isModifier(ITokenVertex vertex) {
        return MODIFIER_CATEGORIES.contains(vertex.category());
    }
}
