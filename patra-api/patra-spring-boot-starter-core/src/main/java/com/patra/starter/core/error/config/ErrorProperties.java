package com.patra.starter.core.error.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台级错误处理的配置属性。
 *
 * <p>控制错误码上下文、错误解析行为、观测和断路器设置。
 */
@Data
@ConfigurationProperties(prefix = "patra.error")
public class ErrorProperties {

  /** 是否启用平台级错误解析。 */
  private boolean enabled = true;

  /** 必需的错误码上下文前缀（例如，REG、INGEST）。 */
  private String contextPrefix;

  /** 核心解析行为。 */
  private EngineProperties engine = new EngineProperties();

  /** 观测和指标配置。 */
  private ObservationProperties observation = new ObservationProperties();

  /** 断路器配置。 */
  private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

  @Data
  public static class EngineProperties {
    /** 遍历原因链的最大深度，以避免深度递归。 */
    private int maxCauseDepth = 10;

    /** 是否启用基于特征的语义映射。 */
    private boolean enableTraitMapping = true;

    /** 是否启用类名启发式。 */
    private boolean enableNamingHeuristic = true;
  }

  @Data
  public static class ObservationProperties {
    /** 是否启用 Micrometer 观测和结构化日志。 */
    private boolean enabled = true;

    /** 慢解析阈值（毫秒）。 */
    private long slowThresholdMs = 200L;

    /** 是否为慢解析记录 WARN 日志。 */
    private boolean logSlowResolution = true;
  }

  @Data
  public static class CircuitBreakerProperties {
    /** 是否启用断路器保护。 */
    private boolean enabled = true;

    /** 失败率阈值（百分比 0-100）。 */
    private float failureRateThreshold = 50.0f;

    /** 断路器窗口中考虑的最少调用次数。 */
    private int minimumNumberOfCalls = 20;

    /** 滑动窗口大小。 */
    private int slidingWindowSize = 50;

    /** 断路器处于半开状态时允许的调用数。 */
    private int permittedCallsInHalfOpenState = 5;

    /** 断路器打开时的等待时长。 */
    private Duration waitDurationInOpenState = Duration.ofSeconds(30);
  }
}
