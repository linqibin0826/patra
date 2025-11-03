package com.patra.starter.core.error.pipeline.interceptor;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.model.SimpleErrorCode;
import com.patra.starter.core.error.observation.ErrorObservationRecorder;
import com.patra.starter.core.error.pipeline.ResolutionInterceptor;
import com.patra.starter.core.error.pipeline.ResolutionInvocation;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 错误解析拦截器 - 熔断保护。
 *
 * <p>使用 Resilience4j 熔断器保护错误解析管道,防止级联故障。
 *
 * <p>当熔断器打开时,返回合成的 503 错误码({contextPrefix}:0503),避免系统雪崩。
 *
 * <p>执行优先级: {@link Ordered#HIGHEST_PRECEDENCE} + 10,确保在追踪拦截器之后执行。
 *
 * @author Papertrace Team
 * @since 2.0
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CircuitBreakerInterceptor implements ResolutionInterceptor {

  private final CircuitBreaker circuitBreaker;
  private final ErrorObservationRecorder observationRecorder;
  private final String contextPrefix;

  /**
   * 构造熔断拦截器。
   *
   * @param circuitBreaker Resilience4j 熔断器实例
   * @param observationRecorder 观测记录器
   * @param errorProperties 错误配置属性(用于获取上下文前缀)
   */
  public CircuitBreakerInterceptor(
      CircuitBreaker circuitBreaker,
      ErrorObservationRecorder observationRecorder,
      ErrorProperties errorProperties) {
    this.circuitBreaker = circuitBreaker;
    this.observationRecorder = observationRecorder;
    String prefix = errorProperties.getContextPrefix();
    this.contextPrefix = (prefix == null || prefix.isBlank()) ? "UNKNOWN" : prefix;
  }

  /**
   * 拦截错误解析调用,应用熔断保护。
   *
   * <p>当熔断器打开时:
   *
   * <ul>
   *   <li>记录熔断降级事件
   *   <li>返回 {contextPrefix}:0503 降级错误码
   *   <li>记录告警日志
   * </ul>
   *
   * @param exception 待解析的异常
   * @param invocation 解析调用链
   * @return 解析结果或降级响应
   */
  @Override
  public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
    try {
      return circuitBreaker.executeSupplier(() -> invocation.proceed(exception));
    } catch (CallNotPermittedException ex) {
      observationRecorder.recordCircuitBreakerFallback(exception);
      log.warn("错误解析期间熔断器已打开,使用降级错误码。原因: {}", ex.getMessage());
      ErrorCodeLike code = SimpleErrorCode.create(contextPrefix, "0503");
      return new ErrorResolution(code, code.httpStatus());
    }
  }
}
