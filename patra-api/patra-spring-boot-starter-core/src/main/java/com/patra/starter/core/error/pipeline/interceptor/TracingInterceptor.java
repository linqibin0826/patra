package com.patra.starter.core.error.pipeline.interceptor;

import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.pipeline.ResolutionInterceptor;
import com.patra.starter.core.error.pipeline.ResolutionInvocation;
import com.patra.starter.core.error.spi.TraceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 错误解析拦截器 - 追踪信息增强。
 *
 * <p>在调试日志中添加当前追踪标识符(trace ID),改善日志关联性和问题排查能力。
 *
 * <p>执行优先级: {@link Ordered#HIGHEST_PRECEDENCE},确保追踪信息最先被记录。
 *
 * @author Patra Team
 * @since 2.0
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TracingInterceptor implements ResolutionInterceptor {

  private final TraceProvider traceProvider;

  /**
   * 构造追踪拦截器。
   *
   * @param traceProvider 追踪标识符提供器
   */
  public TracingInterceptor(TraceProvider traceProvider) {
    this.traceProvider = traceProvider;
  }

  /**
   * 拦截错误解析调用,记录追踪信息。
   *
   * @param exception 待解析的异常
   * @param invocation 解析调用链
   * @return 解析结果
   */
  @Override
  public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
    traceProvider.getCurrentTraceId().ifPresent(traceId -> log.debug("错误解析已触发,追踪标识: {}", traceId));
    return invocation.proceed(exception);
  }
}
