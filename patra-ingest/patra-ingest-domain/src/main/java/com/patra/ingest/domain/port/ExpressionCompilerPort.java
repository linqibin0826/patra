package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.vo.ExprCompilationRequest;
import com.patra.ingest.domain.model.vo.ExprCompilationResult;

/**
 * 表达式编译端口（Port）。
 * <p>将原始表达式编译为数据源可执行的查询与参数。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ExpressionCompilerPort {

    /**
     * Compile expression.
     * <p>
     * Implementation delegates to patra-spring-boot-starter-expr module's ExprCompiler.
     * The infra layer implementation (ExpressionCompilerPortImpl) handles:
     * <ul>
     *   <li>Converting JSON expression snapshot to Expr object using ExprJsonCodec</li>
     *   <li>Building CompileRequest with appropriate options</li>
     *   <li>Invoking ExprCompiler.compile()</li>
     *   <li>Converting CompileResult back to domain ExprCompilationResult</li>
     * </ul>
     * </p>
     *
     * @param request compilation request
     * @return compilation result (with success/failure flag, query, params, error messages, etc.)
     */
    ExprCompilationResult compile(ExprCompilationRequest request);
}
