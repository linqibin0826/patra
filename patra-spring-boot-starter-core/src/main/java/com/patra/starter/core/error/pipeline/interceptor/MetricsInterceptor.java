package com.patra.starter.core.error.pipeline.interceptor;

import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.observation.ErrorObservationRecorder;
import com.patra.starter.core.error.pipeline.ResolutionInterceptor;
import com.patra.starter.core.error.pipeline.ResolutionInvocation;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 错误解析拦截器 - 指标记录。
 *
 * <p>记录错误解析管道的时间信息、慢调用检测和聚合指标,用于性能监控和观测。
 *
 * <p>执行优先级: {@link Ordered#LOWEST_PRECEDENCE} - 10,确保在其他拦截器之后执行以获取完整耗时。
 *
 * @author Patra Team
 * @since 2.0
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class MetricsInterceptor implements ResolutionInterceptor {

  private final ErrorObservationRecorder observationRecorder;
  private final ErrorProperties.ObservationProperties observationProperties;

  /**
   * 构造指标拦截器。
   *
   * @param observationRecorder 观测记录器
   * @param observationProperties 观测配置属性
   */
  public MetricsInterceptor(
      ErrorObservationRecorder observationRecorder,
      ErrorProperties.ObservationProperties observationProperties) {
    this.observationRecorder = observationRecorder;
    this.observationProperties = observationProperties;
  }

  /**
   * 拦截错误解析调用,记录性能指标。
   *
   * <p>记录内容:
   *
   * <ul>
   *   <li>解析耗时(毫秒)
   *   <li>慢调用标记(超过阈值)
   *   <li>异常类型和错误码
   * </ul>
   *
   * @param exception 待解析的异常
   * @param invocation 解析调用链
   * @return 解析结果
   */
  @Override
  public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
    long start = System.nanoTime();
    ErrorResolution resolution = invocation.proceed(exception);
    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    boolean slow = durationMs >= observationProperties.getSlowThresholdMs();

    observationRecorder.recordResolution(exception, resolution, durationMs, slow);

    if (slow && observationProperties.isLogSlowResolution()) {
      log.warn(
          "检测到慢错误解析: {} 毫秒, 异常类型={}, 错误码={}",
          durationMs,
          exception == null ? "空异常" : exception.getClass().getSimpleName(),
          resolution.errorCode().code());
    }
    return resolution;
  }
}
