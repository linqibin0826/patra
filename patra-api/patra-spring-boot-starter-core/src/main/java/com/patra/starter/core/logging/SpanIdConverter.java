package com.patra.starter.core.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;

/// 用于提取 SkyWalking 跨度 ID 的自定义 Logback 转换器。
///
/// 在 logback.xml 中的使用：
///
/// ```
///
/// &lt;conversionRule conversionWord="spanId"
///     converterClass="com.patra.starter.core.logging.SpanIdConverter"/&gt;
/// &lt;pattern&gt;... [spanId:%spanId] ...&lt;/pattern&gt;
///
/// ```
///
/// @since 0.1.0
public class SpanIdConverter extends ClassicConverter {

  private static final String EMPTY_SPAN_ID = "N/A";

  /// 将日志事件转换为 SkyWalking 跨度 ID 字符串。
  ///
  /// 从 SkyWalking 追踪 ID(格式: traceId.segmentId.spanId)中提取跨度 ID 部分。
  ///
  /// @param event 日志事件
  /// @return 跨度 ID,如果不可用则返回 "N/A"
  @Override
  public String convert(ILoggingEvent event) {
    try {
      String traceId = TraceContext.traceId();
      if (traceId == null || traceId.isEmpty() || "N/A".equals(traceId)) {
        return EMPTY_SPAN_ID;
      }
      // 提取跨度 ID（第二个点之后）
      // 格式：traceId.segmentId.spanId
      String[] parts = traceId.split("\\.");
      if (parts.length >= 3) {
        return parts[2];
      }
      return EMPTY_SPAN_ID;
    } catch (Exception e) {
      return EMPTY_SPAN_ID;
    }
  }
}
