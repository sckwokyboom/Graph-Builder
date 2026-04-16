package com.graphbuilder.builder;

import com.graphbuilder.context.BuildContext;
import com.graphbuilder.model.TokenVertexCategory;
import org.eclipse.jdt.core.dom.*;

import static com.graphbuilder.model.TokenVertexCategory.*;

public class VertexBuilder {
    private BuildContext context;

    public void build(BuildContext context) {
        this.context = context;
        context.compilationUnit().accept(new Visitor());
    }

    private class Visitor extends ASTVisitor {

        @Override
        public boolean visit(TypeDeclaration node) {
            TokenVertexCategory keyword = node.isInterface() ? INTERFACE : CLASS;
            context.addVertex(keyword, node.isInterface() ? "interface" : "class", node);
            return true;
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            context.addVertex(ENUM, "enum", node);
            return true;
        }

        @Override
        public boolean visit(AnnotationTypeDeclaration node) {
            context.addVertex(AT, "@", node);
            return true;
        }

        @Override
        public boolean visit(EnumConstantDeclaration node) {
            context.addVertex(ENUM_CONSTANT_DECLARATION, node.getName().getIdentifier(), node);
            return false;
        }

        @Override
        public boolean visit(Modifier node) {
            TokenVertexCategory cat = switch (node.getKeyword().toString()) {
                case "public" -> PUBLIC;
                case "private" -> PRIVATE;
                case "protected" -> PROTECTED;
                case "static" -> STATIC;
                case "final" -> FINAL;
                case "abstract" -> ABSTRACT;
                case "native" -> NATIVE;
                case "synchronized" -> SYNCHRONIZED;
                case "transient" -> TRANSIENT;
                case "volatile" -> VOLATILE;
                case "default" -> DEFAULT;
                default -> null;
            };
            if (cat != null) {
                context.addVertex(cat, node.getKeyword().toString(), node);
            }
            return false;
        }

        @Override
        public boolean visit(PrimitiveType node) {
            context.addVertex(TYPE_IDENTIFIER, node.getPrimitiveTypeCode().toString(), node);
            return false;
        }

        @Override
        public boolean visit(SimpleName node) {
            ASTNode parent = node.getParent();

            // Class/Interface/Enum/Annotation declaration name
            if (parent instanceof TypeDeclaration td && td.getName() == node) {
                TokenVertexCategory cat = td.isInterface() ? INTERFACE_DECLARATION : CLASS_DECLARATION;
                context.addVertex(cat, node.getIdentifier(), node);
                return false;
            }
            if (parent instanceof EnumDeclaration ed && ed.getName() == node) {
                context.addVertex(ENUM_DECLARATION, node.getIdentifier(), node);
                return false;
            }
            if (parent instanceof AnnotationTypeDeclaration atd && atd.getName() == node) {
                context.addVertex(ANNOTATION_DECLARATION, node.getIdentifier(), node);
                return false;
            }

            // Method declaration name
            if (parent instanceof MethodDeclaration md && md.getName() == node) {
                context.addVertex(md.isConstructor() ? CONSTRUCTOR_DECLARATION : METHOD_DECLARATION,
                        node.getIdentifier(), node);
                return false;
            }

            // Method invocation name
            if (parent instanceof MethodInvocation mi && mi.getName() == node) {
                context.addVertex(METHOD_INVOCATION, node.getIdentifier(), node);
                return false;
            }

            // Constructor type name (new Foo())
            if (isConstructorTypeName(node)) {
                context.addVertex(CONSTRUCTOR_INVOCATION, node.getIdentifier(), node);
                return false;
            }

            // Type name in type contexts
            if (parent instanceof SimpleType) {
                context.addVertex(TYPE_IDENTIFIER, node.getIdentifier(), node);
                return false;
            }

            // Variable declaration name
            if (parent instanceof VariableDeclarationFragment vdf && vdf.getName() == node) {
                ASTNode gp = vdf.getParent();
                if (gp instanceof FieldDeclaration) {
                    context.addVertex(FIELD_VAR_DECLARATION, node.getIdentifier(), node);
                } else if (gp instanceof LambdaExpression) {
                    context.addVertex(LAMBDA_VAR_DECLARATION, node.getIdentifier(), node);
                } else {
                    context.addVertex(LOCAL_VAR_DECLARATION, node.getIdentifier(), node);
                }
                return false;
            }

            // Parameter declaration name
            if (parent instanceof SingleVariableDeclaration svd && svd.getName() == node) {
                ASTNode gp = svd.getParent();
                if (gp instanceof EnhancedForStatement || gp instanceof CatchClause) {
                    context.addVertex(LOCAL_VAR_DECLARATION, node.getIdentifier(), node);
                } else {
                    context.addVertex(PARAM_VAR_DECLARATION, node.getIdentifier(), node);
                }
                return false;
            }

            // Method reference
            if (parent instanceof ExpressionMethodReference ||
                    parent instanceof TypeMethodReference ||
                    parent instanceof SuperMethodReference) {
                context.addVertex(METHOD_REFERENCE, node.getIdentifier(), node);
                return false;
            }

            // Variable reference -- use binding
            IBinding binding = node.resolveBinding();
            if (binding instanceof IVariableBinding vb) {
                if (vb.isField()) {
                    context.addVertex(FIELD_VAR_ACCESS, node.getIdentifier(), node);
                } else if (isLambdaParameter(vb)) {
                    context.addVertex(LAMBDA_VAR_ACCESS, node.getIdentifier(), node);
                } else if (vb.isParameter()) {
                    context.addVertex(PARAM_VAR_ACCESS, node.getIdentifier(), node);
                } else {
                    context.addVertex(LOCAL_VAR_ACCESS, node.getIdentifier(), node);
                }
                return false;
            }

            // Fallback for unresolved variable references
            if (binding == null && isVariableReference(node)) {
                context.addVertex(LOCAL_VAR_ACCESS, node.getIdentifier(), node);
            }
            return false;
        }

        // --- Control flow ---
        @Override
        public boolean visit(EnhancedForStatement node) {
            context.addVertex(FOR, "for", node);
            return true;
        }

        @Override
        public boolean visit(ForStatement node) {
            context.addVertex(FOR, "for", node);
            return true;
        }

        @Override
        public boolean visit(IfStatement node) {
            context.addVertex(IF, "if", node);
            return true;
        }

        @Override
        public boolean visit(WhileStatement node) {
            context.addVertex(WHILE, "while", node);
            return true;
        }

        @Override
        public boolean visit(DoStatement node) {
            context.addVertex(DO, "do", node);
            return true;
        }

        @Override
        public boolean visit(SwitchStatement node) {
            context.addVertex(SWITCH, "switch", node);
            return true;
        }

        @Override
        public boolean visit(SwitchExpression node) {
            context.addVertex(SWITCH, "switch", node);
            return true;
        }

        @Override
        public boolean visit(TryStatement node) {
            context.addVertex(TRY, "try", node);
            return true;
        }

        @Override
        public boolean visit(CatchClause node) {
            context.addVertex(CATCH, "catch", node);
            return true;
        }

        @Override
        public boolean visit(SynchronizedStatement node) {
            context.addVertex(SYNCHRONIZED, "synchronized", node);
            return true;
        }

        @Override
        public boolean visit(ThrowStatement node) {
            context.addVertex(THROW, "throw", node);
            return true;
        }

        @Override
        public boolean visit(BreakStatement node) {
            context.addVertex(BREAK, "break", node);
            return true;
        }

        @Override
        public boolean visit(ContinueStatement node) {
            context.addVertex(CONTINUE, "continue", node);
            return true;
        }

        @Override
        public boolean visit(ReturnStatement node) {
            context.addVertex(RETURN, "return", node);
            return true;
        }

        @Override
        public boolean visit(AssertStatement node) {
            context.addVertex(ASSERT, "assert", node);
            return true;
        }

        // --- Expressions ---
        @Override
        public boolean visit(ClassInstanceCreation node) {
            context.addVertex(NEW, "new", node);
            return true;
        }

        @Override
        public boolean visit(ArrayCreation node) {
            context.addVertex(NEW, "new", node);
            return true;
        }

        @Override
        public boolean visit(InstanceofExpression node) {
            context.addVertex(INSTANCE_OF, "instanceof", node);
            return true;
        }

        @Override
        public boolean visit(ThisExpression node) {
            context.addVertex(THIS, "this", node);
            return false;
        }

        @Override
        public boolean visit(SuperFieldAccess node) {
            context.addVertex(SUPER, "super", node);
            return true;
        }

        @Override
        public boolean visit(SuperMethodInvocation node) {
            context.addVertex(SUPER, "super", node);
            return true;
        }

        // --- Literals ---
        @Override
        public boolean visit(NumberLiteral node) {
            String token = node.getToken();
            TokenVertexCategory cat;
            if (token.endsWith("L") || token.endsWith("l")) {
                cat = LONG_LITERAL;
            } else if (token.endsWith("F") || token.endsWith("f")) {
                cat = FLOAT_LITERAL;
            } else if (token.endsWith("D") || token.endsWith("d") || token.contains(".") || token.contains("e") || token.contains("E")) {
                cat = DOUBLE_LITERAL;
            } else {
                cat = INTEGER_LITERAL;
            }
            context.addVertex(cat, token, node);
            return false;
        }

        @Override
        public boolean visit(StringLiteral node) {
            context.addVertex(STRING_LITERAL, node.getLiteralValue(), node);
            return false;
        }

        @Override
        public boolean visit(CharacterLiteral node) {
            context.addVertex(CHAR_LITERAL, String.valueOf(node.charValue()), node);
            return false;
        }

        @Override
        public boolean visit(BooleanLiteral node) {
            context.addVertex(BOOLEAN_LITERAL, String.valueOf(node.booleanValue()), node);
            return false;
        }

        @Override
        public boolean visit(NullLiteral node) {
            context.addVertex(NULL_LITERAL, "null", node);
            return false;
        }

        // --- Helpers ---
        private boolean isConstructorTypeName(SimpleName node) {
            ASTNode current = node.getParent();
            while (current instanceof Type) {
                current = current.getParent();
            }
            return current instanceof ClassInstanceCreation;
        }

        private boolean isLambdaParameter(IVariableBinding vb) {
            IMethodBinding method = vb.getDeclaringMethod();
            return method != null && method.getDeclaringMember() != null;
        }

        private boolean isVariableReference(SimpleName node) {
            ASTNode parent = node.getParent();
            return parent instanceof Assignment ||
                    parent instanceof InfixExpression ||
                    (parent instanceof MethodInvocation mi && mi.getExpression() == node) ||
                    parent instanceof PrefixExpression ||
                    parent instanceof PostfixExpression;
        }
    }
}
