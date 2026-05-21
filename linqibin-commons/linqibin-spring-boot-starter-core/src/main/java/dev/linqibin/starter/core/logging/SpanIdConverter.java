package dev.linqibin.starter.core.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.Map;
import org.slf4j.MDC;

/// 用于提取跨度 ID 的自定义 Logback 转换器。
///
/// 支持两种 MDC 键格式（按优先级顺序）：
/// 1. **OTel Java Agent**：读取 `span_id` 键（snake_case）
/// 2. **Micrometer Tracing**（回退）：读取 `spanId` 键（camelCase）
///
/// 在 logback.xml 中的使用：
///
/// ```xml
/// <conversionRule conversionWord="spanId"
///     converterClass="dev.linqibin.starter.core.logging.SpanIdConverter"/>
/// <pattern>... [spanId:%spanId] ...</pattern>
/// ```
///
/// @since 0.1.0
public class SpanIdConverter extends ClassicConverter {

  /// OTel Java Agent 在 MDC 中存储 span_id 的键名（snake_case）
  private static final String OTEL_AGENT_SPAN_ID_KEY = "span_id";

  /// Micrometer Tracing 在 MDC 中存储 spanId 的键名（camelCase）
  private static final String MICROMETER_SPAN_ID_KEY = "spanId";

  /// 无追踪上下文时的默认返回值
  private static final String EMPTY_SPAN_ID = "N/A";

  /// 将日志事件转换为跨度 ID 字符串。
  ///
  /// 优先从事件的 MDC 快照读取 OTel Agent 的 span_id，
  /// 如果不存在则回退到 Micrometer Tracing 的 spanId。
  ///
  /// @param event 日志事件
  /// @return 跨度 ID，如果不可用则返回 "N/A"
  @Override
  public String convert(ILoggingEvent event) {
    // 从事件的 MDC 快照读取（优于 MDC.get()，因为日志格式化时当前线程的 MDC 可能已被清理）
    Map<String, String> mdcMap = event.getMDCPropertyMap();

    // 1. 优先尝试 OTel Java Agent 键（snake_case）
    String spanId = mdcMap.get(OTEL_AGENT_SPAN_ID_KEY);
    if (isValidSpanId(spanId)) {
      return spanId;
    }

    // 2. 回退到 Micrometer Tracing 键（camelCase）
    spanId = mdcMap.get(MICROMETER_SPAN_ID_KEY);
    if (isValidSpanId(spanId)) {
      return spanId;
    }

    // 3. 备选：尝试从当前线程 MDC 读取（异步场景）
    spanId = MDC.get(OTEL_AGENT_SPAN_ID_KEY);
    if (isValidSpanId(spanId)) {
      return spanId;
    }

    spanId = MDC.get(MICROMETER_SPAN_ID_KEY);
    if (isValidSpanId(spanId)) {
      return spanId;
    }

    return EMPTY_SPAN_ID;
  }

  /// 检查跨度 ID 是否有效（非空且非默认值）
  private boolean isValidSpanId(String spanId) {
    return spanId != null && !spanId.isEmpty() && !EMPTY_SPAN_ID.equals(spanId);
  }
}
