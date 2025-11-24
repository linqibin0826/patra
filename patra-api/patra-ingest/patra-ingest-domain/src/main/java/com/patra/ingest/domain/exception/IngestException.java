package com.patra.ingest.domain.exception;

import com.patra.common.error.DomainException;
import com.patra.common.error.trait.ErrorTrait;

/// 采集领域异常基类。
///
/// 封装显式的领域失败以及与领域紧密耦合的持久化或依赖问题。
///
/// **强制语义化**: 所有采集异常必须携带至少一个 {@link ErrorTrait}，明确表达业务语义。
///
/// **语义特征的作用**:
///
/// - 区分可重试和不可重试的错误（如 TIMEOUT vs RULE_VIOLATION）
/// - 在 Outbox 流或调度器回调中生成细粒度指标
/// - 自动映射到适当的 HTTP 状态码和错误响应
///
/// **使用示例**:
///
/// ```java
/// // 外部服务超时（可重试）
/// public class ExternalServiceTimeoutException extends IngestException {
///     public ExternalServiceTimeoutException(String service, Throwable cause) {
///         super(
///             "外部服务超时: " + service,
///             cause,
///             StandardErrorTrait.TIMEOUT,
///             StandardErrorTrait.DEP_UNAVAILABLE
///         );
///     }
/// }
///
/// // 数据验证失败（不可重试）
/// public class InvalidPlanConfigurationException extends IngestException {
///     public InvalidPlanConfigurationException(String reason) {
///         super(
///             "计划配置无效: " + reason,
///             StandardErrorTrait.RULE_VIOLATION
///         );
///     }
/// }
/// ```
///
/// **最佳实践**:
///
/// - 异常消息应包含关键上下文（计划键、数据源、操作、窗口）以加快调查
/// - 避免抛出通用的 RuntimeException，改为使用有意义的子类
/// - 根据错误可重试性选择合适的 trait
///
/// @author linqibin
/// @since 0.1.0
public abstract class IngestException extends DomainException {

  /// 使用消息和语义特征构造采集异常。
  ///
  /// @param message 详细消息
  /// @param traits 语义特征（至少提供一个）
  protected IngestException(String message, ErrorTrait... traits) {
    super(message, traits);
  }

  /// 使用消息、根本原因和语义特征构造采集异常。
  ///
  /// @param message 详细消息
  /// @param cause 根本原因
  /// @param traits 语义特征（至少提供一个）
  protected IngestException(String message, Throwable cause, ErrorTrait... traits) {
    super(message, cause, traits);
  }
}
