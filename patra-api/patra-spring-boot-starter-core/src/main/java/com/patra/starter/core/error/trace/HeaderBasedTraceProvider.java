package com.patra.starter.core.error.trace;

import com.patra.starter.core.error.config.TracingProperties;
import com.patra.starter.core.error.spi.TraceProvider;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/** 基于 MDC 的 {@link TraceProvider}，从配置的头部名称中提取追踪标识符。 */
@Slf4j
public class HeaderBasedTraceProvider implements TraceProvider {

  /** 追踪配置属性。 */
  private final TracingProperties tracingProperties;

  public HeaderBasedTraceProvider(TracingProperties tracingProperties) {
    this.tracingProperties = tracingProperties;
  }

  @Override
  public Optional<String> getCurrentTraceId() {
    for (String headerName : tracingProperties.getHeaderNames()) {
      String traceId = MDC.get(headerName);
      if (traceId != null && !traceId.trim().isEmpty()) {
        log.debug("从 MDC 键 '{}' 找到追踪 ID '{}'", headerName, traceId);
        return Optional.of(traceId.trim());
      }
    }

    log.debug("在 MDC 中未找到任何已配置头部名称的追踪 ID：{}", tracingProperties.getHeaderNames());
    return Optional.empty();
  }
}
