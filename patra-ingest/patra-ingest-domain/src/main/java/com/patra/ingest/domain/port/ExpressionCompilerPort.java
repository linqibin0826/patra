package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.vo.expression.ExprCompilationRequest;
import com.patra.ingest.domain.model.vo.expression.ExprCompilationResult;

/**
 * Port that compiles ingestion expressions into executable queries and parameters.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ExpressionCompilerPort {

  /**
   * Compile expression.
   *
   * <p>Implementation delegates to patra-spring-boot-starter-expr module's ExprCompiler. The infra
   * layer implementation (ExpressionCompilerPortImpl) handles:
   *
   * <ul>
   *   <li>Converting JSON expression snapshot to Expr object using ExprJsonCodec
   *   <li>Building CompileRequest with appropriate options
   *   <li>Invoking ExprCompiler.compile()
   *   <li>Converting CompileResult back to domain ExprCompilationResult
   * </ul>
   *
   * @param request compilation request
   * @return compilation result (with success/failure flag, query, params, error messages, etc.)
   */
  ExprCompilationResult compile(ExprCompilationRequest request);
}
