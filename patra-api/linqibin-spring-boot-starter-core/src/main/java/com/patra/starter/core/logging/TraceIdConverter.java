package com.patra.starter.core.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.Map;
import org.slf4j.MDC;

/// 用于提取追踪 ID 的自定义 Logback 转换器。
///
/// 支持两种 MDC 键格式（按优先级顺序）：
/// 1. **OTel Java Agent**：读取 `trace_id` 键（snake_case）
/// 2. **Micrometer Tracing**（回退）：读取 `traceId` 键（camelCase）
///
/// 在 logback.xml 中的使用：
///
/// ```xml
/// <conversionRule conversionWord="traceId"
///     converterClass="com.patra.starter.core.logging.TraceIdConverter"/>
/// <pattern>... [traceId:%traceId] ...</pattern>
/// ```
///
/// @since 0.1.0
public class TraceIdConverter extends ClassicConverter {

  /// OTel Java Agent 在 MDC 中存储 trace_id 的键名（snake_case）
  private static final String OTEL_AGENT_TRACE_ID_KEY = "trace_id";

  /// Micrometer Tracing 在 MDC 中存储 traceId 的键名（camelCase）
  private static final String MICROMETER_TRACE_ID_KEY = "traceId";

  /// 无追踪上下文时的默认返回值
  private static final String EMPTY_TRACE_ID = "N/A";

  /// 将日志事件转换为追踪 ID 字符串。
  ///
  /// 优先从事件的 MDC 快照读取 OTel Agent 的 trace_id，
  /// 如果不存在则回退到 Micrometer Tracing 的 traceId。
  ///
  /// @param event 日志事件
  /// @return 追踪 ID，如果不可用则返回 "N/A"
  @Override
  public String convert(ILoggingEvent event) {
    // 从事件的 MDC 快照读取（优于 MDC.get()，因为日志格式化时当前线程的 MDC 可能已被清理）
    Map<String, String> mdcMap = event.getMDCPropertyMap();

    // 1. 优先尝试 OTel Java Agent 键（snake_case）
    String traceId = mdcMap.get(OTEL_AGENT_TRACE_ID_KEY);
    if (isValidTraceId(traceId)) {
      return traceId;
    }

    // 2. 回退到 Micrometer Tracing 键（camelCase）
    traceId = mdcMap.get(MICROMETER_TRACE_ID_KEY);
    if (isValidTraceId(traceId)) {
      return traceId;
    }

    // 3. 备选：尝试从当前线程 MDC 读取（异步场景）
    traceId = MDC.get(OTEL_AGENT_TRACE_ID_KEY);
    if (isValidTraceId(traceId)) {
      return traceId;
    }

    traceId = MDC.get(MICROMETER_TRACE_ID_KEY);
    if (isValidTraceId(traceId)) {
      return traceId;
    }

    return EMPTY_TRACE_ID;
  }

  /// 检查追踪 ID 是否有效（非空且非默认值）
  private boolean isValidTraceId(String traceId) {
    return traceId != null && !traceId.isEmpty() && !EMPTY_TRACE_ID.equals(traceId);
  }
}
