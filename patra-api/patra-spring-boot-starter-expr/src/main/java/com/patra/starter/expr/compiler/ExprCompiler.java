package com.patra.starter.expr.compiler;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.*;
import com.patra.starter.expr.compiler.model.CompileRequest;
import com.patra.starter.expr.compiler.model.CompileRequestBuilder;
import com.patra.starter.expr.compiler.model.CompileResult;

import java.util.List;

public interface ExprCompiler {

    /**
     * Compiles an expression into provider-specific query text and parameter bindings.
     *
     * @param request compile request
     * @return compilation outcome
     */
    CompileResult compile(CompileRequest request);

    /**
     * Convenience overload that uses default compile options and the SEARCH operation type.
     *
     * @param expression expression to compile
     * @param provenance provenance code identifying the data source
     * @return compilation outcome
     */
    default CompileResult compile(Expr expression, ProvenanceCode provenance) {
        CompileRequest request = CompileRequestBuilder.of(expression, provenance).build();
        return compile(request);
    }

    /**
     * Convenience overload that selects a custom operation type while keeping default options.
     *
     * @param expression expression to compile
     * @param provenance provenance code identifying the data source
     * @param operationType operation slice (e.g. HARVEST/UPDATE; {@code null} defers to provenance defaults)
     * @return compilation outcome
     */
    default CompileResult compile(Expr expression, ProvenanceCode provenance, String operationType) {
        CompileRequest request = CompileRequestBuilder.of(expression, provenance)
            .forOperationType(operationType)
            .build();
        return compile(request);
    }

    /**
     * Convenience overload that specifies both operation type and endpoint name.
     *
     * @param expression expression to compile
     * @param provenance provenance code identifying the data source
     * @param operationType operation slice (e.g. HARVEST/UPDATE; {@code null} defers to provenance defaults)
     * @param endpointName endpoint identifier
     * @return compilation outcome
     */
    default CompileResult compile(Expr expression, ProvenanceCode provenance, String operationType, String endpointName) {
        CompileRequest request = CompileRequestBuilder.of(expression, provenance)
            .forOperationType(operationType)
            .forOperation(endpointName)
            .build();
        return compile(request);
    }

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
