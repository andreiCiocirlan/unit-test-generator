package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.StatementContext;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class StatementContextResolver {

    /**
     * Build a StatementContext for the given node by walking its ancestor
     * chain up to (but not including) the enclosing MethodDeclaration.
     */
    public StatementContext resolve(Node node) {

        List<String> ifConditions = new ArrayList<>();
        int tryDepth = 0;
        int loopDepth = 0;
        boolean insideCatch = false;
        String caughtExceptionType = "";

        // Collect ancestors innermost-first, then reverse for outermost-first.
        List<Node> ancestors = new ArrayList<>();
        Node current = node.getParentNode().orElse(null);
        while (current != null) {
            ancestors.add(current);
            current = current.getParentNode().orElse(null);
        }
        Collections.reverse(ancestors);

        for (Node ancestor : ancestors) {

            if (ancestor instanceof IfStmt ifStmt) {
                ifConditions.add(ifStmt.getCondition().toString());
                continue;
            }

            if (ancestor instanceof TryStmt) {
                tryDepth++;
                continue;
            }

            if (ancestor instanceof CatchClause catchClause) {
                insideCatch = true;
                caughtExceptionType = catchClause
                        .getParameter()
                        .getType()
                        .asString();
                continue;
            }

            if (ancestor instanceof ForStmt
                    || ancestor instanceof ForEachStmt
                    || ancestor instanceof WhileStmt
                    || ancestor instanceof DoStmt) {
                loopDepth++;
            }
        }

        return new StatementContext(
                List.copyOf(ifConditions),
                tryDepth,
                loopDepth,
                insideCatch,
                caughtExceptionType
        );
    }
}