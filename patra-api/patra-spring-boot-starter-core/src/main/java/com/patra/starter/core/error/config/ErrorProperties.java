package com.patra.starter.core.error.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// 平台级错误处理的配置属性。
/// 
/// 配置前缀: `patra.error`
/// 
/// 控制错误码上下文、错误解析行为、观测和断路器设置。
/// 
/// @author Patra Team
/// @since 2.0
@Data
@ConfigurationProperties(prefix = "patra.error")
public class ErrorProperties {

  /// 是否启用平台级错误解析,默认: true
  private boolean enabled = true;

  /// 必需的错误码上下文前缀(例如 REG、INGEST),用于构建统一错误码格式: {prefix}:{code}
  private String contextPrefix;

  /// 核心解析引擎行为配置
  private EngineProperties engine = new EngineProperties();

  /// 观测和指标配置
  private ObservationProperties observation = new ObservationProperties();

  /// 断路器配置
  private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

  /// 错误解析引擎配置
  @Data
  public static class EngineProperties {
    /// 遍历原因链的最大深度,避免深度递归导致的性能问题,默认: 10
    private int maxCauseDepth = 10;

    /// 是否启用基于特征的语义映射(如 ErrorCodeLike 接口),默认: true
    private boolean enableTraitMapping = true;

    /// 是否启用类名启发式(如 IllegalArgumentException → ILLEGAL_ARGUMENT),默认: true
    private boolean enableNamingHeuristic = true;
  }

  /// 错误观测配置
  @Data
  public static class ObservationProperties {
    /// 是否启用 Micrometer 观测和结构化日志,默认: true
    private boolean enabled = true;

    /// 慢解析阈值(毫秒),超过此阈值的解析将被记录为慢操作,默认: 200ms
    private long slowThresholdMs = 200L;

    /// 是否为慢解析记录 WARN 级别日志,默认: true
    private boolean logSlowResolution = true;
  }

  /// 熔断器配置
  @Data
  public static class CircuitBreakerProperties {
    /// 是否启用熔断器保护错误处理管道,默认: true
    private boolean enabled = true;

    /// 失败率阈值(百分比 0-100),达到此阈值时触发断路器打开,默认: 50.0
    private float failureRateThreshold = 50.0f;

    /// 触发熔断器所需的最小调用次数,默认: 20
    private int minimumNumberOfCalls = 20;

    /// 滑动窗口大小,用于计算失败率的样本数量,默认: 50
    private int slidingWindowSize = 50;

    /// 熔断器处于半开状态时允许的探测调用数,默认: 5
    private int permittedCallsInHalfOpenState = 5;

    /// 熔断器打开后等待多久进入半开状态,默认: 30秒
    private Duration waitDurationInOpenState = Duration.ofSeconds(30);
  }
}
