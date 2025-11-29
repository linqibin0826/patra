package com.patra.starter.core.error.spi;

import java.util.Optional;

/// SPI 接口,用于从执行上下文中检索当前追踪标识符。
///
/// 实现类可以查询 MDC、请求 Header 或其他追踪系统(如 OpenTelemetry、Zipkin)。
///
/// 使用示例:
///
/// ```java
/// @Component
/// public class MyTraceProvider implements TraceProvider {
///   @Override
///   public Optional<String> getCurrentTraceId() {
///     // 从 MDC 提取追踪 ID（由 OpenTelemetry Agent 自动注入）
///     return Optional.ofNullable(MDC.get("traceId"));
/// ```
///
/// @author linqibin
/// @since 0.1.0
public interface TraceProvider {

  /// 从当前执行上下文中提取追踪标识符。
  ///
  /// @return 如果可用则返回追踪标识符;否则返回空
  Optional<String> getCurrentTraceId();
}
