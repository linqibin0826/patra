package com.patra.starter.core.error.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 分布式追踪支持的配置属性。
 *
 * <p>配置前缀: {@code patra.tracing}
 *
 * <p>用于从 HTTP Header 中提取追踪 ID,支持多种分布式追踪系统的 Header 格式。
 *
 * @author Patra Team
 * @since 2.0
 */
@Data
@ConfigurationProperties(prefix = "patra.tracing")
public class TracingProperties {

  /**
   * 用于解析追踪标识符的 HTTP Header 名称列表(按优先级顺序)。
   *
   * <p>默认支持以下 Header:
   *
   * <ul>
   *   <li>traceId - 通用追踪 ID Header
   *   <li>X-B3-TraceId - Zipkin/B3 追踪格式
   *   <li>traceparent - W3C Trace Context 标准
   * </ul>
   */
  private List<String> headerNames = List.of("traceId", "X-B3-TraceId", "traceparent");
}
