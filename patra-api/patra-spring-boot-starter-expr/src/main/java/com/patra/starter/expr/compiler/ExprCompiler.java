package com.patra.starter.expr.compiler;

import com.patra.expr.*;
import com.patra.starter.expr.compiler.model.CompileRequest;
import com.patra.starter.expr.compiler.model.CompileResult;

import java.util.List;

public interface ExprCompiler {

    CompileResult compile(CompileRequest request);

    default boolean containsAtom(Expr expression) {
        return containsAtomInternal(expression);
    }

    private static boolean containsAtomInternal(Expr expr) {
        if (expr instanceof Atom) {
            return true;
        }
        if (expr instanceof And(List<Expr> children)) {
            return children.stream().anyMatch(ExprCompiler::containsAtomInternal);
        }
        if (expr instanceof Or(List<Expr> children)) {
            return children.stream().anyMatch(ExprCompiler::containsAtomInternal);
        }
        if (expr instanceof Not(Expr child)) {
            return containsAtomInternal(child);
        }
        return false;
    }
}
