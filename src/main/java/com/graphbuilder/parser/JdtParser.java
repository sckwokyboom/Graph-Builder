package com.graphbuilder.parser;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import java.util.Map;

public class JdtParser {
    public CompilationUnit parse(String sourceCode, String unitName) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(sourceCode.toCharArray());
        parser.setUnitName(unitName);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setEnvironment(null, null, null, true);

        Map<String, String> options = Map.of(
            "org.eclipse.jdt.core.compiler.source", "23",
            "org.eclipse.jdt.core.compiler.compliance", "23",
            "org.eclipse.jdt.core.compiler.codegen.targetPlatform", "23"
        );
        parser.setCompilerOptions(options);

        return (CompilationUnit) parser.createAST(null);
    }
}
