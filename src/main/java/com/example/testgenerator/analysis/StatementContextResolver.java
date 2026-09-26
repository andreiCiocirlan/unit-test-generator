package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.StatementContext;
import com.example.testgenerator.analysis.model.StatementContext.BranchPosition;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.stmt.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class StatementContextResolver {

    public StatementContext resolve(Node node) {

        List<String> ifConditions = new ArrayList<>();
        int tryDepth = 0;
        int loopDepth = 0;
        boolean insideCatch = false;
        String caughtExceptionType = "";
        BranchPosition branchPosition = BranchPosition.NONE;

        List<Node> ancestors = new ArrayList<>();
        Node current = node.getParentNode().orElse(null);
        while (current != null) {
            ancestors.add(current);
            current = current.getParentNode().orElse(null);
        }
        Collections.reverse(ancestors);

        IfStmt nearestIf = null;

        for (Node ancestor : ancestors) {

            if (ancestor instanceof IfStmt ifStmt) {
                ifConditions.add(ifStmt.getCondition().toString());
                nearestIf = ifStmt;    // overwrite — later = innermost
                continue;
            }

            if (ancestor instanceof TryStmt) {
                tryDepth++;
                continue;
            }

            if (ancestor instanceof CatchClause catchClause) {
                insideCatch = true;
                caughtExceptionType = catchClause.getParameter().getType().asString();
                continue;
            }

            if (ancestor instanceof ForStmt
                    || ancestor instanceof ForEachStmt
                    || ancestor instanceof WhileStmt
                    || ancestor instanceof DoStmt) {
                loopDepth++;
            }
        }

        if (nearestIf != null) {
            branchPosition = positionInIf(node, nearestIf);
        }

        return new StatementContext(
                List.copyOf(ifConditions),
                tryDepth,
                loopDepth,
                insideCatch,
                caughtExceptionType,
                branchPosition
        );
    }

    /**
     * Determine whether `node` sits in the then-block or the else-block
     * of `ifStmt`. The else-block may itself be another IfStmt (an
     * else-if); in that case we report ELSE_IF.
     */
    private BranchPosition positionInIf(Node node, IfStmt ifStmt) {

        // Find the topmost descendant-of-ifStmt ancestor of `node`.
        Node child = node;
        Node parent = node.getParentNode().orElse(null);
        while (parent != null && parent != ifStmt) {
            child = parent;
            parent = parent.getParentNode().orElse(null);
        }
        if (parent != ifStmt) {
            return BranchPosition.NONE;
        }

        // Now `child` is the direct child of ifStmt that contains `node`.
        if (child == ifStmt.getThenStmt()) {
            return BranchPosition.THEN;
        }

        if (ifStmt.getElseStmt().isPresent()) {
            Node elseStmt = ifStmt.getElseStmt().get();
            if (child == elseStmt) {
                if (elseStmt instanceof IfStmt) {
                    return BranchPosition.ELSE_IF;
                }
                return BranchPosition.ELSE;
            }
        }

        return BranchPosition.NONE;
    }
}