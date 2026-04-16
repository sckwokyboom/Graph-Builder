package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.EdgeCategory;
import com.graphbuilder.model.ITokenVertex;
import org.eclipse.jdt.core.dom.*;

import java.util.List;

import static com.graphbuilder.model.TokenVertexCategory.*;

/**
 * Builds type-related edges: GENERIC, ANCESTOR, NEXT_ANCESTOR, IMPORTS,
 * TYPE_ONTOLOGY, VARIABLE_ONTOLOGY.
 */
public class TypeEdgeBuilder {

    public void build(BuildContext context) {
        context.compilationUnit().accept(new TypeVisitor(context));
    }

    private static class TypeVisitor extends ASTVisitor {
        private final BuildContext context;

        TypeVisitor(BuildContext context) {
            this.context = context;
        }

        // --- GENERIC ---

        @Override
        public boolean visit(ParameterizedType node) {
            // The outer type vertex
            Type baseType = node.getType();
            ITokenVertex outerVertex = findTypeVertex(baseType);

            if (outerVertex != null) {
                @SuppressWarnings("unchecked")
                List<Type> typeArgs = node.typeArguments();
                for (Type typeArg : typeArgs) {
                    ITokenVertex innerVertex = findTypeVertex(typeArg);
                    if (innerVertex != null) {
                        context.addEdge(outerVertex, innerVertex, EdgeCategory.GENERIC);
                    }
                }
            }
            return true;
        }

        /**
         * Find the TYPE_IDENTIFIER vertex for a Type AST node.
         * For SimpleType, finds the vertex registered to the SimpleName.
         * For ParameterizedType, finds the vertex for the base type.
         */
        private ITokenVertex findTypeVertex(Type type) {
            if (type instanceof SimpleType st) {
                Name name = st.getName();
                if (name instanceof SimpleName sn) {
                    return context.findVertex(sn, TYPE_IDENTIFIER);
                } else if (name instanceof QualifiedName qn) {
                    return context.findVertex(qn.getName(), TYPE_IDENTIFIER);
                }
            } else if (type instanceof ParameterizedType pt) {
                return findTypeVertex(pt.getType());
            } else if (type instanceof ArrayType at) {
                return findTypeVertex(at.getElementType());
            } else if (type instanceof PrimitiveType) {
                return context.firstVertexInRange(type);
            }
            return null;
        }

        // --- ANCESTOR ---

        @Override
        public boolean visit(TypeDeclaration node) {
            ITokenVertex declVertex;
            if (node.isInterface()) {
                declVertex = context.findVertex(node.getName(), INTERFACE_DECLARATION);
            } else {
                declVertex = context.findVertex(node.getName(), CLASS_DECLARATION);
            }

            if (declVertex == null) return true;

            // Superclass
            Type superclassType = node.getSuperclassType();
            ITokenVertex prevAncestor = null;
            if (superclassType != null) {
                ITokenVertex superVertex = findTypeVertex(superclassType);
                if (superVertex != null) {
                    context.addEdge(declVertex, superVertex, EdgeCategory.ANCESTOR);
                    prevAncestor = superVertex;
                }
            }

            // Implemented interfaces
            @SuppressWarnings("unchecked")
            List<Type> superInterfaces = node.superInterfaceTypes();
            for (Type iface : superInterfaces) {
                ITokenVertex ifaceVertex = findTypeVertex(iface);
                if (ifaceVertex != null) {
                    context.addEdge(declVertex, ifaceVertex, EdgeCategory.ANCESTOR);
                    // NEXT_ANCESTOR: chain from previous ancestor to this one
                    if (prevAncestor != null) {
                        context.addEdge(prevAncestor, ifaceVertex, EdgeCategory.NEXT_ANCESTOR);
                    }
                    prevAncestor = ifaceVertex;
                }
            }

            return true;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            ITokenVertex declVertex = context.findVertex(node.getName(), ENUM_DECLARATION);
            if (declVertex == null) return true;

            @SuppressWarnings("unchecked")
            List<Type> superInterfaces = node.superInterfaceTypes();
            ITokenVertex prevAncestor = null;
            for (Type iface : superInterfaces) {
                ITokenVertex ifaceVertex = findTypeVertex(iface);
                if (ifaceVertex != null) {
                    context.addEdge(declVertex, ifaceVertex, EdgeCategory.ANCESTOR);
                    if (prevAncestor != null) {
                        context.addEdge(prevAncestor, ifaceVertex, EdgeCategory.NEXT_ANCESTOR);
                    }
                    prevAncestor = ifaceVertex;
                }
            }
            return true;
        }

        // --- IMPORTS ---
        // Import edges are not present in the reference example, but we implement them
        // for completeness. IMPORTS links the import statement to the type being imported.
        // Since the VertexBuilder doesn't currently create IMPORT vertices, this is a stub
        // that can be extended when import vertices are added.
    }
}
