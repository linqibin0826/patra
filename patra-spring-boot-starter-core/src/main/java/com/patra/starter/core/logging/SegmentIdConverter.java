package com.patra.starter.core.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.Map;
import org.slf4j.MDC;

/// 用于提取段 ID 的自定义 Logback 转换器。
///
/// **注意**：OpenTelemetry 没有 "segment" 概念，此转换器显示 traceId 的前 16 位
/// 作为简化标识符，方便日志阅读和快速识别。
///
/// 支持两种 MDC 键格式（按优先级顺序）：
/// 1. **OTel Java Agent**：读取 `trace_id` 键（snake_case）
/// 2. **Micrometer Tracing**（回退）：读取 `traceId` 键（camelCase）
///
/// 在 logback.xml 中的使用：
///
/// ```xml
/// <conversionRule conversionWord="segmentId"
///     converterClass="com.patra.starter.core.logging.SegmentIdConverter"/>
/// <pattern>... [segmentId:%segmentId] ...</pattern>
/// ```
///
/// @since 0.1.0
public class SegmentIdConverter extends ClassicConverter {

  /// OTel Java Agent 在 MDC 中存储 trace_id 的键名（snake_case）
  private static final String OTEL_AGENT_TRACE_ID_KEY = "trace_id";

  /// Micrometer Tracing 在 MDC 中存储 traceId 的键名（camelCase）
  private static final String MICROMETER_TRACE_ID_KEY = "traceId";

  /// 无追踪上下文时的默认返回值
  private static final String EMPTY_SEGMENT_ID = "N/A";

  /// OTel traceId 截取长度（前 16 位，作为简化显示）
  private static final int SEGMENT_DISPLAY_LENGTH = 16;

  /// 将日志事件转换为段 ID 字符串。
  ///
  /// 返回 traceId 的前 16 位作为简化显示（因为 OTel 没有 segment 概念）。
  ///
  /// @param event 日志事件
  /// @return 段 ID（traceId 前 16 位），如果不可用则返回 "N/A"
  @Override
  public String convert(ILoggingEvent event) {
    // 从事件的 MDC 快照读取（优于 MDC.get()，因为日志格式化时当前线程的 MDC 可能已被清理）
    Map<String, String> mdcMap = event.getMDCPropertyMap();

    // 1. 优先尝试 OTel Java Agent 键（snake_case）
    String traceId = mdcMap.get(OTEL_AGENT_TRACE_ID_KEY);
    if (isValidId(traceId)) {
      return truncateToSegment(traceId);
    }

    // 2. 回退到 Micrometer Tracing 键（camelCase）
    traceId = mdcMap.get(MICROMETER_TRACE_ID_KEY);
    if (isValidId(traceId)) {
      return truncateToSegment(traceId);
    }

    // 3. 备选：尝试从当前线程 MDC 读取（异步场景）
    traceId = MDC.get(OTEL_AGENT_TRACE_ID_KEY);
    if (isValidId(traceId)) {
      return truncateToSegment(traceId);
    }

    traceId = MDC.get(MICROMETER_TRACE_ID_KEY);
    if (isValidId(traceId)) {
      return truncateToSegment(traceId);
    }

    return EMPTY_SEGMENT_ID;
  }

  /// 将 traceId 截断为前 16 位作为简化显示
  private String truncateToSegment(String traceId) {
    if (traceId.length() >= SEGMENT_DISPLAY_LENGTH) {
      return traceId.substring(0, SEGMENT_DISPLAY_LENGTH);
    }
    return traceId;
  }

  /// 检查 ID 是否有效（非空且非默认值）
  private boolean isValidId(String id) {
    return id != null && !id.isEmpty() && !EMPTY_SEGMENT_ID.equals(id);
  }
}
