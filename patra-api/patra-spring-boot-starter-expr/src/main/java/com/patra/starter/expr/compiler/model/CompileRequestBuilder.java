package com.patra.starter.expr.compiler.model;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;
import java.util.Objects;

/**
 * {@link CompileRequest} 的流式构建器，具有合理的默认值和便利的辅助方法。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * CompileRequest request = CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
 *     .forOperationType(OperationTypes.UPDATE)
 *     .forOperation(EndpointNames.SEARCH)
 *     .withOptions(CompileOptions.defaults().withStrict(false))
 *     .build();
 * }</pre>
 */
public class CompileRequestBuilder {

  private final Expr expression;
  private final ProvenanceCode provenance;
  private String operationType;
  private String endpointName = EndpointNames.SEARCH; // 默认为搜索
  private CompileOptions options = CompileOptions.defaults();

  private CompileRequestBuilder(Expr expression, ProvenanceCode provenance) {
    this.expression = Objects.requireNonNull(expression, "expression 不能为空");
    this.provenance = Objects.requireNonNull(provenance, "provenance 不能为空");
  }

  /**
   * 创建一个新的构建器实例。
   *
   * @param expression 要编译的表达式 (非空)
   * @param provenance Provenance 代码 (非空)
   * @return 构建器实例
   */
  public static CompileRequestBuilder of(Expr expression, ProvenanceCode provenance) {
    return new CompileRequestBuilder(expression, provenance);
  }

  /**
   * 设置操作类型维度 (例如 {@link OperationTypes#HARVEST}, {@link OperationTypes#UPDATE})。
   *
   * @param operationType 大写的操作类型;{@code null} 则回退到 Provenance 级别的默认值
   * @return 本构建器
   */
  public CompileRequestBuilder forOperationType(String operationType) {
    this.operationType = operationType;
    return this;
  }

  /**
   * 设置用于选择 API 模板的端点名称 (例如 {@link EndpointNames#DETAIL})。
   *
   * @param endpointName 端点名称,自动转为大写
   * @return 本构建器
   */
  public CompileRequestBuilder forOperation(String endpointName) {
    this.endpointName = endpointName;
    return this;
  }

  /**
   * 应用自定义编译选项。
   *
   * @param options 编译选项;{@code null} 时使用默认值
   * @return 本构建器
   */
  public CompileRequestBuilder withOptions(CompileOptions options) {
    this.options = options != null ? options : CompileOptions.defaults();
    return this;
  }

  /**
   * 启用或禁用严格验证模式。
   *
   * @param strict {@code true} 表示严格模式
   * @return 本构建器
   */
  public CompileRequestBuilder withStrict(boolean strict) {
    this.options = this.options.withStrict(strict);
    return this;
  }

  /**
   * 设置最大渲染查询长度。
   *
   * @param maxQueryLength 最大长度;{@code 0} 表示无限制
   * @return 本构建器
   */
  public CompileRequestBuilder withMaxQueryLength(int maxQueryLength) {
    this.options = this.options.withMaxQueryLength(maxQueryLength);
    return this;
  }

  /**
   * 指定渲染时使用的时区。
   *
   * @param timezone 时区标识符 (例如 {@code "UTC"})
   * @return 本构建器
   */
  public CompileRequestBuilder withTimezone(String timezone) {
    this.options = this.options.withTimezone(timezone);
    return this;
  }

  /**
   * 切换渲染跟踪。
   *
   * @param traceEnabled {@code true} 表示捕获详细的跟踪信息
   * @return 本构建器
   */
  public CompileRequestBuilder withTraceEnabled(boolean traceEnabled) {
    this.options = this.options.withTraceEnabled(traceEnabled);
    return this;
  }

  /**
   * 构建不可变的 {@link CompileRequest}。
   *
   * @return 编译请求
   */
  public CompileRequest build() {
    return new CompileRequest(expression, provenance, operationType, endpointName, options);
  }
}
