package com.patra.ingest.infra.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;
import com.patra.expr.json.ExprJsonCodec;
import com.patra.ingest.domain.model.vo.ExprCompilationRequest;
import com.patra.ingest.domain.model.vo.ExprCompilationResult;
import com.patra.ingest.domain.port.ExpressionCompilerPort;
import com.patra.starter.expr.compiler.ExprCompiler;
import com.patra.starter.expr.compiler.model.CompileRequest;
import com.patra.starter.expr.compiler.model.CompileRequestBuilder;
import com.patra.starter.expr.compiler.model.CompileResult;
import com.patra.starter.expr.compiler.model.ValidationReport;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Expression compiler port implementation.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Convert domain ExprCompilationRequest to starter CompileRequest
 *   <li>Deserialize JSON expression snapshot to Expr object
 *   <li>Invoke ExprCompiler from patra-spring-boot-starter-expr
 *   <li>Convert CompileResult back to domain ExprCompilationResult
 * </ul>
 *
 * <p>Design principles:
 *
 * <ul>
 *   <li>Isolate JSON-to-Expr conversion in infrastructure layer
 *   <li>Keep domain layer independent of expression implementation details
 *   <li>Use ExprJsonCodec from patra-expr-kernel for stable serialization
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpressionCompilerPortImpl implements ExpressionCompilerPort {

  private final ExprCompiler exprCompiler;
  private final ObjectMapper objectMapper;

  /**
   * Compile expression from domain request.
   *
   * @param request domain compilation request
   * @return domain compilation result
   */
  @Override
  public ExprCompilationResult compile(ExprCompilationRequest request) {
    if (log.isDebugEnabled()) {
      log.debug(
          "compiling expression provenance={} endpoint={} exprLength={}",
          request.provenanceCode(),
          request.endpointName(),
          request.rawExpression() != null ? request.rawExpression().length() : 0);
    }
    try {
      // 1. Parse JSON expression to Expr object using ExprJsonCodec
      Expr expression = ExprJsonCodec.fromJson(request.rawExpression());

      // 2. Convert provenanceCode string to enum
      ProvenanceCode provenanceCode = ProvenanceCode.valueOf(request.provenanceCode());

      // 3. Build CompileRequest using builder
      // Pass endpointName directly; CompileRequest normalizes null/blank to "SEARCH"
      CompileRequest compileRequest =
          CompileRequestBuilder.of(expression, provenanceCode)
              .forOperation(request.endpointName())
              .build();

      // 4. Invoke ExprCompiler
      CompileResult compileResult = exprCompiler.compile(compileRequest);

      // 5. Convert CompileResult to ExprCompilationResult
      ExprCompilationResult result = convertToExprCompilationResult(compileResult);
      if (log.isDebugEnabled()) {
        log.debug(
            "expression compilation completed provenance={} endpoint={}, isValid={}, query={}",
            request.provenanceCode(),
            request.endpointName(),
            result.isValid(),
            result.query() != null
                ? result.query().substring(0, Math.min(100, result.query().length()))
                : "null");
      }
      return result;

    } catch (Exception e) {
      log.error("expression compilation failed: {}", e.getMessage(), e);
      return ExprCompilationResult.failure("Expression compilation failed: " + e.getMessage());
    }
  }

  /**
   * Convert CompileResult to ExprCompilationResult.
   *
   * @param result compile result from starter
   * @return domain compilation result
   */
  private ExprCompilationResult convertToExprCompilationResult(CompileResult result) {
    ValidationReport report = result.report();

    // Extract error and warning messages
    String errors =
        report.errors().isEmpty()
            ? null
            : report.errors().stream()
                .map(issue -> issue.code() + ": " + issue.message())
                .collect(Collectors.joining("; "));

    String warnings =
        report.warnings().isEmpty()
            ? null
            : report.warnings().stream()
                .map(issue -> issue.code() + ": " + issue.message())
                .collect(Collectors.joining("; "));

    // If compilation failed, return failure result
    if (!report.ok()) {
      return ExprCompilationResult.failure(errors);
    }

    // Convert Map<String, String> params to JsonNode
    JsonNode paramsJson = objectMapper.valueToTree(result.params());

    // Convert Expr normalized to JSON string
    String normalizedExpression = ExprJsonCodec.toJson(result.normalized());

    return ExprCompilationResult.success(
        result.query(), paramsJson, normalizedExpression, warnings);
  }
}
