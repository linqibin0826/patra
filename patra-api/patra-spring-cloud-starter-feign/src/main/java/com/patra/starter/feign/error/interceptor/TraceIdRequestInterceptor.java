package com.patra.starter.feign.error.interceptor;

import com.patra.starter.core.error.config.TracingProperties;
import com.patra.starter.core.error.spi.TraceProvider;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign 拦截器,向下游传播当前跟踪标识符
 *
 * <p>使用来自 {@link TracingProperties} 的可配置头名称以及 {@link TraceProvider} SPI,
 * 使其能够适配不同的跟踪系统。拦截器从执行上下文读取当前 trace ID 并将其写入第一个配置的 header。
 *
 * <p><b>工作流程:</b>
 *
 * <ol>
 *   <li>从 TraceProvider 获取当前跟踪标识符
 *   <li>使用配置的 header 名称(或默认 'traceId')
 *   <li>将跟踪标识符注入到出站请求 header
 *   <li>失败时仅记录警告,不影响请求执行
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class TraceIdRequestInterceptor implements RequestInterceptor {

  private final TraceProvider traceProvider;
  private final TracingProperties tracingProperties;

  public TraceIdRequestInterceptor(
      TraceProvider traceProvider, TracingProperties tracingProperties) {
    this.traceProvider = traceProvider;
    this.tracingProperties = tracingProperties;
  }

  /** 将跟踪标识符注入到出站请求 header */
  @Override
  public void apply(RequestTemplate template) {
    try {
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      if (traceId.isPresent()) {
        String headerName = getTraceHeaderName();
        template.header(headerName, traceId.get());

        log.debug(
            "Added TraceId to request: {}={} url={}", headerName, traceId.get(), template.url());
      } else {
        log.debug("No TraceId available, url={}", template.url());
      }

    } catch (Exception e) {
      // Do not fail request if trace propagation fails
      log.warn("Failed to propagate TraceId, url={}, error={}", template.url(), e.getMessage());
    }
  }

  /**
   * 获取用于跟踪传播的 header 名称
   *
   * @return 第一个配置的值或默认值
   */
  private String getTraceHeaderName() {
    if (tracingProperties.getHeaderNames() != null
        && !tracingProperties.getHeaderNames().isEmpty()) {
      return tracingProperties.getHeaderNames().get(0);
    }

    // Fall back to a conventional header name when nothing is configured.
    return "traceId";
  }
}
