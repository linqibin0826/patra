package dev.linqibin.patra.starter.expr.compiler;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.expr.*;
import dev.linqibin.patra.starter.expr.compiler.model.CompileRequest;
import dev.linqibin.patra.starter.expr.compiler.model.CompileRequestBuilder;
import dev.linqibin.patra.starter.expr.compiler.model.CompileResult;

/// 表达式编译器,将领域表达式编译为数据源特定的查询参数。
///
/// 编译器使用标准键(std_key)而非数据源特定参数名,通过数据源映射将标准键转换为提供商参数。
///
/// @author linqibin
/// @since 0.1.0
public interface ExprCompiler {

  /// 将表达式编译为提供商特定的查询文本和参数绑定。
  ///
  /// @param request 编译请求
  /// @return 编译结果
  CompileResult compile(CompileRequest request);

  /// 便捷重载方法,使用默认编译选项和 SEARCH 操作类型。
  ///
  /// @param expression 待编译的表达式
  /// @param provenance 标识数据源的溯源代码
  /// @return 编译结果
  default CompileResult compile(Expr expression, ProvenanceCode provenance) {
    CompileRequest request = CompileRequestBuilder.of(expression, provenance).build();
    return compile(request);
  }

  /// 便捷重载方法,选择自定义操作类型,同时保持默认选项。
  ///
  /// @param expression 待编译的表达式
  /// @param provenance 标识数据源的溯源代码
  /// @param operationType 操作切片(如 HARVEST/UPDATE;`null` 时使用溯源默认值)
  /// @return 编译结果
  default CompileResult compile(Expr expression, ProvenanceCode provenance, String operationType) {
    CompileRequest request =
        CompileRequestBuilder.of(expression, provenance).forOperationType(operationType).build();
    return compile(request);
  }

  /// 便捷重载方法,同时指定操作类型和端点名称。
  ///
  /// @param expression 待编译的表达式
  /// @param provenance 标识数据源的溯源代码
  /// @param operationType 操作切片(如 HARVEST/UPDATE;`null` 时使用溯源默认值)
  /// @param endpointName 端点标识符
  /// @return 编译结果
  default CompileResult compile(
      Expr expression, ProvenanceCode provenance, String operationType, String endpointName) {
    CompileRequest request =
        CompileRequestBuilder.of(expression, provenance)
            .forOperationType(operationType)
            .forOperation(endpointName)
            .build();
    return compile(request);
  }
}
