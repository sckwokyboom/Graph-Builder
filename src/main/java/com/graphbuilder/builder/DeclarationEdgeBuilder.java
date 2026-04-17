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
 * Builds declaration-related edges: DECLARING, ATTRIBUTE, FORMAL_PARAMETER, NEXT_DECLARATION.
 *
 * <p>DECLARING connects a type identifier or class keyword to its immediately following declaration name.
 * ATTRIBUTE connects a class/interface declaration to the first vertex of each body member.
 * FORMAL_PARAMETER connects a method declaration to each parameter's type vertex.
 * NEXT_DECLARATION chains consecutive parameter declarations.
 */
public class DeclarationEdgeBuilder {

    private static final Set<TokenVertexCategory> DECLARATION_CATEGORIES = Set.of(
            CLASS_DECLARATION, INTERFACE_DECLARATION, ENUM_DECLARATION, ANNOTATION_DECLARATION,
            METHOD_DECLARATION, CONSTRUCTOR_DECLARATION,
            FIELD_VAR_DECLARATION, LOCAL_VAR_DECLARATION, PARAM_VAR_DECLARATION, LAMBDA_VAR_DECLARATION
    );

    private static final Set<TokenVertexCategory> TYPE_KEYWORD_CATEGORIES = Set.of(
            CLASS, INTERFACE, ENUM
    );

    public void build(BuildContext context) {
        buildDeclaringEdges(context);
        buildAttributeEdges(context);
        buildFormalParameterEdges(context);
        buildNextDeclarationEdges(context);
    }

    /**
     * DECLARING edges: consecutive vertex pairs where vertex[i] is a TYPE_ID (or class keyword)
     * and vertex[i+1] is a declaration.
     *
     * Examples: TYPE_ID(String) -> FIELD_VAR_DECL(name), CLASS(class) -> CLASS_DECL(Foo)
     */
    private void buildDeclaringEdges(BuildContext context) {
        List<ITokenVertex> vertices = context.graph().vertices();
        for (int i = 0; i < vertices.size() - 1; i++) {
            ITokenVertex current = vertices.get(i);
            ITokenVertex next = vertices.get(i + 1);

            if (!DECLARATION_CATEGORIES.contains(next.category())) continue;

            if (current.category() == TYPE_IDENTIFIER || TYPE_KEYWORD_CATEGORIES.contains(current.category())) {
                context.addEdge(current, next, EdgeCategory.DECLARING);
            }
        }
    }

    /**
     * ATTRIBUTE edges: CLASS_DECL/INTERFACE_DECL/ENUM_DECL -> first vertex of each body declaration.
     *
     * For each type declaration, iterates over body declarations (fields, methods, inner types)
     * and links the declaration name to the first vertex in each member's source range.
     */
    private void buildAttributeEdges(BuildContext context) {
        context.compilationUnit().accept(new ASTVisitor() {
            @Override
            public boolean visit(TypeDeclaration node) {
                handleTypeBody(context, node.getName(), node.bodyDeclarations());
                return true;
            }

            @Override
            public boolean visit(EnumDeclaration node) {
                handleTypeBody(context, node.getName(), node.bodyDeclarations());
                return true;
            }

            @Override
            public boolean visit(AnnotationTypeDeclaration node) {
                handleTypeBody(context, node.getName(), node.bodyDeclarations());
                return true;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void handleTypeBody(BuildContext context, SimpleName typeName, List<?> bodyDeclarations) {
        TokenVertexCategory declCat = getDeclCategory(typeName);
        ITokenVertex declVertex = context.findVertex(typeName, declCat);
        if (declVertex == null) return;

        for (Object bd : bodyDeclarations) {
            BodyDeclaration bodyDecl = (BodyDeclaration) bd;
            ITokenVertex firstVertex = context.firstVertexInRange(bodyDecl);
            if (firstVertex != null) {
                context.addEdge(declVertex, firstVertex, EdgeCategory.ATTRIBUTE);
            }
        }
    }

    private TokenVertexCategory getDeclCategory(SimpleName typeName) {
        ASTNode parent = typeName.getParent();
        if (parent instanceof TypeDeclaration td) {
            return td.isInterface() ? INTERFACE_DECLARATION : CLASS_DECLARATION;
        }
        if (parent instanceof EnumDeclaration) return ENUM_DECLARATION;
        if (parent instanceof AnnotationTypeDeclaration) return ANNOTATION_DECLARATION;
        return CLASS_DECLARATION;
    }

    /**
     * FORMAL_PARAMETER edges: METHOD_DECL -> first parameter's type vertex only.
     *
     * Only the first parameter gets a direct FORMAL_PARAMETER edge from the method declaration;
     * subsequent parameters are reached via the NEXT_DECLARATION chain (PARAM_VAR_DECL -> next TYPE_ID).
     * This matches the reference example's graph structure.
     */
    private void buildFormalParameterEdges(BuildContext context) {
        context.compilationUnit().accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                TokenVertexCategory declCat = node.isConstructor() ? CONSTRUCTOR_DECLARATION : METHOD_DECLARATION;
                ITokenVertex methodVertex = context.findVertex(node.getName(), declCat);
                if (methodVertex == null) return true;

                @SuppressWarnings("unchecked")
                List<SingleVariableDeclaration> params = node.parameters();
                if (params.isEmpty()) return true;

                SingleVariableDeclaration firstParam = params.get(0);
                ITokenVertex paramTypeVertex = context.firstVertexInRange(firstParam);
                if (paramTypeVertex != null) {
                    context.addEdge(methodVertex, paramTypeVertex, EdgeCategory.FORMAL_PARAMETER);
                }
                return true;
            }
        });
    }

    /**
     * NEXT_DECLARATION edges: chains consecutive parameters.
     *
     * PARAM_VAR_DECL(a) -> TYPE_ID of next param : NEXT_DECLARATION
     */
    private void buildNextDeclarationEdges(BuildContext context) {
        context.compilationUnit().accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                @SuppressWarnings("unchecked")
                List<SingleVariableDeclaration> params = node.parameters();

                for (int i = 0; i < params.size() - 1; i++) {
                    SingleVariableDeclaration current = params.get(i);
                    SingleVariableDeclaration next = params.get(i + 1);

                    ITokenVertex currentDeclVertex = context.findVertex(current.getName(), PARAM_VAR_DECLARATION);
                    ITokenVertex nextTypeVertex = context.firstVertexInRange(next);

                    if (currentDeclVertex != null && nextTypeVertex != null) {
                        context.addEdge(currentDeclVertex, nextTypeVertex, EdgeCategory.NEXT_DECLARATION);
                    }
                }
                return true;
            }
        });
    }
}
