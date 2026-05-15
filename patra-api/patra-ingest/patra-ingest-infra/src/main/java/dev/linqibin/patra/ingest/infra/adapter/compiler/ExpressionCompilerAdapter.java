package dev.linqibin.patra.ingest.infra.adapter.compiler;

import com.patra.expr.Expr;
import com.patra.expr.json.ExprJsonCodec;
import dev.linqibin.patra.ingest.domain.model.vo.expression.ExprCompilationRequest;
import dev.linqibin.patra.ingest.domain.model.vo.expression.ExprCompilationResult;
import dev.linqibin.patra.ingest.domain.port.ExpressionCompilerPort;
import com.patra.starter.expr.compiler.ExprCompiler;
import com.patra.starter.expr.compiler.model.CompileRequest;
import com.patra.starter.expr.compiler.model.CompileRequestBuilder;
import com.patra.starter.expr.compiler.model.CompileResult;
import com.patra.starter.expr.compiler.model.ValidationReport;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/// 表达式编译器端口实现。
///
/// 职责:
///
/// - 将领域层的 ExprCompilationRequest 转换为 starter 的 CompileRequest
///   - 将 JSON 表达式快照反序列化为 Expr 对象
///   - 调用 patra-spring-boot-starter-expr 中的 ExprCompiler
///   - 将 CompileResult 转换回领域层的 ExprCompilationResult
///
/// 设计原则:
///
/// - 在基础设施层隔离 JSON 到 Expr 的转换
///   - 保持领域层与表达式实现细节的独立性
///   - 使用 patra-expr-kernel 的 ExprJsonCodec 进行稳定的序列化
///
/// @author linqibin
/// @since 0.1.0
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpressionCompilerAdapter implements ExpressionCompilerPort {

  private final ExprCompiler exprCompiler;
  private final ObjectMapper objectMapper;

  /// 从领域请求编译表达式。
  ///
  /// @param request 领域编译请求
  /// @return 领域编译结果
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
      // 1. 使用 ExprJsonCodec 将 JSON 表达式解析为 Expr 对象
      Expr expression = ExprJsonCodec.fromJson(request.rawExpression());

      // 2. 将 provenanceCode 字符串转换为枚举
      ProvenanceCode provenanceCode = ProvenanceCode.valueOf(request.provenanceCode());

      // 3. 使用构建器构建 CompileRequest
      // 直接传递 endpointName; CompileRequest 会将 null/空白归一化为 "SEARCH"
      CompileRequest compileRequest =
          CompileRequestBuilder.of(expression, provenanceCode)
              .forOperation(request.endpointName())
              .build();

      // 4. 调用 ExprCompiler
      CompileResult compileResult = exprCompiler.compile(compileRequest);

      // 5. 将 CompileResult 转换为 ExprCompilationResult
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

  /// 将 CompileResult 转换为 ExprCompilationResult。
  ///
  /// @param result starter 的编译结果
  /// @return 领域编译结果
  private ExprCompilationResult convertToExprCompilationResult(CompileResult result) {
    ValidationReport report = result.report();

    // 提取错误和警告信息
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

    // 如果编译失败,返回失败结果
    if (!report.ok()) {
      return ExprCompilationResult.failure(errors);
    }

    // 将 Map<String, String> params 转换为 JsonNode
    JsonNode paramsJson = objectMapper.valueToTree(result.params());

    // 将归一化的 Expr 转换为 JSON 字符串
    String normalizedExpression = ExprJsonCodec.toJson(result.normalized());

    return ExprCompilationResult.success(
        result.query(), paramsJson, normalizedExpression, warnings);
  }
}
