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
   * Creates a new builder instance.
   *
   * @param expression expression to compile (non-null)
   * @param provenance provenance code (non-null)
   * @return builder instance
   */
  public static CompileRequestBuilder of(Expr expression, ProvenanceCode provenance) {
    return new CompileRequestBuilder(expression, provenance);
  }

  /**
   * Sets the operation type dimension (e.g. {@link OperationTypes#HARVEST}, {@link
   * OperationTypes#UPDATE}).
   *
   * @param operationType upper-case operation type; {@code null} falls back to provenance-level
   *     defaults
   * @return this builder
   */
  public CompileRequestBuilder forOperationType(String operationType) {
    this.operationType = operationType;
    return this;
  }

  /**
   * Sets the endpoint name used to select API templates (e.g. {@link EndpointNames#DETAIL}).
   *
   * @param endpointName endpoint name, automatically upper-cased
   * @return this builder
   */
  public CompileRequestBuilder forOperation(String endpointName) {
    this.endpointName = endpointName;
    return this;
  }

  /**
   * Applies custom compile options.
   *
   * @param options compile options; defaults are used when {@code null}
   * @return this builder
   */
  public CompileRequestBuilder withOptions(CompileOptions options) {
    this.options = options != null ? options : CompileOptions.defaults();
    return this;
  }

  /**
   * Enables or disables strict validation mode.
   *
   * @param strict {@code true} for strict mode
   * @return this builder
   */
  public CompileRequestBuilder withStrict(boolean strict) {
    this.options = this.options.withStrict(strict);
    return this;
  }

  /**
   * Sets a maximum rendered query length.
   *
   * @param maxQueryLength maximum length; {@code 0} means no limit
   * @return this builder
   */
  public CompileRequestBuilder withMaxQueryLength(int maxQueryLength) {
    this.options = this.options.withMaxQueryLength(maxQueryLength);
    return this;
  }

  /**
   * Specifies the timezone for rendering.
   *
   * @param timezone timezone identifier (e.g. {@code "UTC"})
   * @return this builder
   */
  public CompileRequestBuilder withTimezone(String timezone) {
    this.options = this.options.withTimezone(timezone);
    return this;
  }

  /**
   * Toggles render tracing.
   *
   * @param traceEnabled {@code true} to capture detailed trace information
   * @return this builder
   */
  public CompileRequestBuilder withTraceEnabled(boolean traceEnabled) {
    this.options = this.options.withTraceEnabled(traceEnabled);
    return this;
  }

  /**
   * Builds the immutable {@link CompileRequest}.
   *
   * @return compile request
   */
  public CompileRequest build() {
    return new CompileRequest(expression, provenance, operationType, endpointName, options);
  }
}
