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
 * Builds flow-related edges: STATEMENT, CONTROL_FLOW_SCOPE, ASSIGN, CALL, ARGUMENT, OPERATION, CREATION.
 */
public class FlowEdgeBuilder {

    private static final Set<TokenVertexCategory> CONTROL_FLOW_CATEGORIES = Set.of(
            FOR, IF, WHILE, DO, SWITCH, TRY, CATCH, SYNCHRONIZED
    );

    public void build(BuildContext context) {
        context.compilationUnit().accept(new FlowVisitor(context));
    }

    private static class FlowVisitor extends ASTVisitor {
        private final BuildContext context;

        FlowVisitor(BuildContext context) {
            this.context = context;
        }

        // --- STATEMENT and CONTROL_FLOW_SCOPE ---

        @Override
        public boolean visit(MethodDeclaration node) {
            TokenVertexCategory declCat = node.isConstructor() ? CONSTRUCTOR_DECLARATION : METHOD_DECLARATION;
            ITokenVertex methodVertex = context.findVertex(node.getName(), declCat);
            if (methodVertex != null && node.getBody() != null) {
                emitBodyStatements(methodVertex, node.getBody().statements());
            }
            return true;
        }

        @Override
        public boolean visit(EnhancedForStatement node) {
            ITokenVertex forVertex = context.findVertex(node, FOR);
            if (forVertex != null) {
                // FOR -> parameter type : ARGUMENT
                SingleVariableDeclaration param = node.getParameter();
                ITokenVertex paramTypeVertex = context.firstVertexInRange(param.getType());
                if (paramTypeVertex != null) {
                    context.addEdge(forVertex, paramTypeVertex, EdgeCategory.ARGUMENT);
                }
                // FOR -> body statements
                Statement body = node.getBody();
                if (body instanceof Block block) {
                    emitBodyStatements(forVertex, block.statements());
                } else {
                    emitSingleStatement(forVertex, body);
                }
            }
            return true;
        }

        @Override
        public boolean visit(ForStatement node) {
            ITokenVertex forVertex = context.findVertex(node, FOR);
            if (forVertex != null) {
                Statement body = node.getBody();
                if (body instanceof Block block) {
                    emitBodyStatements(forVertex, block.statements());
                } else {
                    emitSingleStatement(forVertex, body);
                }
            }
            return true;
        }

        @Override
        public boolean visit(WhileStatement node) {
            ITokenVertex whileVertex = context.findVertex(node, WHILE);
            if (whileVertex != null) {
                Statement body = node.getBody();
                if (body instanceof Block block) {
                    emitBodyStatements(whileVertex, block.statements());
                } else {
                    emitSingleStatement(whileVertex, body);
                }
            }
            return true;
        }

        @Override
        public boolean visit(IfStatement node) {
            ITokenVertex ifVertex = context.findVertex(node, IF);
            if (ifVertex != null) {
                Statement thenStmt = node.getThenStatement();
                if (thenStmt instanceof Block block) {
                    emitBodyStatements(ifVertex, block.statements());
                } else {
                    emitSingleStatement(ifVertex, thenStmt);
                }
                Statement elseStmt = node.getElseStatement();
                if (elseStmt != null) {
                    if (elseStmt instanceof Block block) {
                        emitBodyStatements(ifVertex, block.statements());
                    } else {
                        emitSingleStatement(ifVertex, elseStmt);
                    }
                }
            }
            return true;
        }

        @Override
        public boolean visit(DoStatement node) {
            ITokenVertex doVertex = context.findVertex(node, DO);
            if (doVertex != null) {
                Statement body = node.getBody();
                if (body instanceof Block block) {
                    emitBodyStatements(doVertex, block.statements());
                } else {
                    emitSingleStatement(doVertex, body);
                }
            }
            return true;
        }

        // Handle lambda bodies that produce STATEMENT edges from lambda var decl
        @Override
        public boolean visit(LambdaExpression node) {
            // Find the lambda parameter declaration vertex
            ITokenVertex lambdaDeclVertex = findLambdaParamVertex(node);
            if (lambdaDeclVertex != null) {
                ASTNode body = node.getBody();
                if (body instanceof Block block) {
                    @SuppressWarnings("unchecked")
                    List<Statement> stmts = block.statements();
                    emitBodyStatements(lambdaDeclVertex, stmts);
                } else if (body instanceof Expression expr) {
                    // Expression lambda: the body is a single expression
                    ITokenVertex exprVertex = context.firstVertexInRange(expr);
                    if (exprVertex != null) {
                        context.addEdge(lambdaDeclVertex, exprVertex, EdgeCategory.STATEMENT);
                    }
                }
            }
            return true;
        }

        private ITokenVertex findLambdaParamVertex(LambdaExpression node) {
            @SuppressWarnings("unchecked")
            List<VariableDeclaration> params = node.parameters();
            if (params.isEmpty()) return null;

            // Return the last parameter's declaration vertex (lambda edge source)
            VariableDeclaration lastParam = params.get(params.size() - 1);
            if (lastParam instanceof VariableDeclarationFragment vdf) {
                return context.findVertex(vdf.getName(), LAMBDA_VAR_DECLARATION);
            } else if (lastParam instanceof SingleVariableDeclaration svd) {
                return context.findVertex(svd.getName(), PARAM_VAR_DECLARATION);
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private void emitBodyStatements(ITokenVertex parent, List<?> statements) {
            for (Object s : statements) {
                Statement stmt = (Statement) s;
                emitSingleStatement(parent, stmt);
            }
        }

        private void emitSingleStatement(ITokenVertex parent, Statement stmt) {
            ITokenVertex firstVertex = context.firstVertexInRange(stmt);
            if (firstVertex != null) {
                if (CONTROL_FLOW_CATEGORIES.contains(firstVertex.category())) {
                    context.addEdge(parent, firstVertex, EdgeCategory.CONTROL_FLOW_SCOPE);
                } else {
                    context.addEdge(parent, firstVertex, EdgeCategory.STATEMENT);
                }
            }
        }

        // --- ASSIGN ---

        @Override
        public boolean visit(Assignment node) {
            ITokenVertex lhsVertex = context.firstVertexInRange(node.getLeftHandSide());
            ITokenVertex rhsVertex = context.firstVertexInRange(node.getRightHandSide());
            if (lhsVertex != null && rhsVertex != null) {
                context.addEdge(lhsVertex, rhsVertex, EdgeCategory.ASSIGN);
            }
            return true;
        }

        @Override
        public boolean visit(VariableDeclarationFragment node) {
            if (node.getInitializer() != null) {
                // Determine the var declaration category based on parent
                TokenVertexCategory declCat = getVarDeclCategory(node);
                ITokenVertex declVertex = context.findVertex(node.getName(), declCat);
                ITokenVertex initVertex = context.firstVertexInRange(node.getInitializer());
                if (declVertex != null && initVertex != null) {
                    context.addEdge(declVertex, initVertex, EdgeCategory.ASSIGN);
                }
            }
            return true;
        }

        private TokenVertexCategory getVarDeclCategory(VariableDeclarationFragment vdf) {
            ASTNode parent = vdf.getParent();
            if (parent instanceof FieldDeclaration) return FIELD_VAR_DECLARATION;
            if (parent instanceof LambdaExpression) return LAMBDA_VAR_DECLARATION;
            return LOCAL_VAR_DECLARATION;
        }

        // --- CALL ---

        @Override
        public boolean visit(MethodInvocation node) {
            Expression expr = node.getExpression();
            ITokenVertex methodVertex = context.findVertex(node.getName(), METHOD_INVOCATION);

            if (expr != null && methodVertex != null) {
                if (expr instanceof MethodInvocation innerMi) {
                    // Chained: inner method -> outer method
                    ITokenVertex innerVertex = context.findVertex(innerMi.getName(), METHOD_INVOCATION);
                    if (innerVertex != null) {
                        context.addEdge(innerVertex, methodVertex, EdgeCategory.CALL);
                    }
                } else {
                    // Direct: var -> method
                    ITokenVertex exprVertex = context.firstVertexInRange(expr);
                    if (exprVertex != null) {
                        context.addEdge(exprVertex, methodVertex, EdgeCategory.CALL);
                    }
                }
            }

            // Arguments: chained method -> arg1, arg1 -> arg2, ...
            if (methodVertex != null) {
                emitChainedArguments(methodVertex, node.arguments());
            }

            return true;
        }

        @Override
        public boolean visit(SuperMethodInvocation node) {
            ITokenVertex superVertex = context.findVertex(node, SUPER);
            ITokenVertex methodVertex = context.findVertex(node.getName(), METHOD_INVOCATION);
            if (superVertex != null && methodVertex != null) {
                context.addEdge(superVertex, methodVertex, EdgeCategory.CALL);
            }
            if (methodVertex != null) {
                emitChainedArguments(methodVertex, node.arguments());
            }
            return true;
        }

        // --- ARGUMENT (chained) ---

        @SuppressWarnings("unchecked")
        private void emitChainedArguments(ITokenVertex source, List<?> arguments) {
            ITokenVertex prev = source;
            for (Object arg : arguments) {
                Expression argExpr = (Expression) arg;
                ITokenVertex argVertex = context.firstVertexInRange(argExpr);
                if (argVertex != null) {
                    context.addEdge(prev, argVertex, EdgeCategory.ARGUMENT);
                    prev = argVertex;
                }
            }
        }

        // --- CREATION ---

        @Override
        public boolean visit(ClassInstanceCreation node) {
            ITokenVertex newVertex = context.findVertex(node, NEW);
            // The constructor invocation name is in the type
            Type type = node.getType();
            ITokenVertex ctorVertex = findConstructorInvocationVertex(type);
            if (newVertex != null && ctorVertex != null) {
                context.addEdge(newVertex, ctorVertex, EdgeCategory.CREATION);
            }

            // Constructor arguments
            if (ctorVertex != null) {
                emitChainedArguments(ctorVertex, node.arguments());
            }

            return true;
        }

        private ITokenVertex findConstructorInvocationVertex(Type type) {
            if (type instanceof SimpleType st) {
                // For simple types like ArrayList, the name is a SimpleName
                Name name = st.getName();
                if (name instanceof SimpleName sn) {
                    return context.findVertex(sn, CONSTRUCTOR_INVOCATION);
                } else if (name instanceof QualifiedName qn) {
                    return context.findVertex(qn.getName(), CONSTRUCTOR_INVOCATION);
                }
            } else if (type instanceof ParameterizedType pt) {
                return findConstructorInvocationVertex(pt.getType());
            }
            return null;
        }

        // --- OPERATION ---

        @Override
        public boolean visit(InfixExpression node) {
            ITokenVertex leftVertex = context.firstVertexInRange(node.getLeftOperand());
            ITokenVertex rightVertex = context.firstVertexInRange(node.getRightOperand());
            if (leftVertex != null && rightVertex != null) {
                context.addEdge(leftVertex, rightVertex, EdgeCategory.OPERATION);
            }
            return true;
        }
    }
}
