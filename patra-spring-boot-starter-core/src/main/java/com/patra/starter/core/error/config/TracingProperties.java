package com.patra.starter.core.error.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 分布式追踪支持的配置属性。 */
@Data
@ConfigurationProperties(prefix = "patra.tracing")
public class TracingProperties {

  /** 用于解析追踪标识符的头部名称（按优先级顺序）。 */
  private List<String> headerNames = List.of("traceId", "X-B3-TraceId", "traceparent");
}
