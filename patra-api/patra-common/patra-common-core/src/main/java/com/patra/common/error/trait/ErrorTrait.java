package com.patra.common.error.trait;

/// 异常的语义分类特征,用于在不同异常类型间实现一致的错误处理。
///
/// 这些特征被错误解析算法用于将异常映射到适当的 HTTP 状态码和错误响应。
///
/// **设计原则**:
///
/// - 表达业务语义而非技术细节（如 NOT_FOUND 而非 HTTP_404）
/// - 与传输层无关（可用于 HTTP、gRPC、消息队列等）
/// - 支持自定义扩展（业务可实现此接口定义领域特定 trait）
///
/// **标准实现**: {@link StandardErrorTrait} 提供了常用的语义特征。
///
/// **自定义示例**:
///
/// ```java
/// public enum CustomErrorTrait implements ErrorTrait {
///     EXTERNAL_SERVICE_TIMEOUT,
///     DATA_CONSISTENCY_ERROR,
///     RATE_LIMIT_EXCEEDED
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see StandardErrorTrait
/// @see HasErrorTraits
public interface ErrorTrait {

  /// 返回特征的唯一名称。
  ///
  /// 用于日志记录、指标标签和调试。
  ///
  /// @return 特征名称（推荐使用 UPPER_SNAKE_CASE）
  String name();
}
