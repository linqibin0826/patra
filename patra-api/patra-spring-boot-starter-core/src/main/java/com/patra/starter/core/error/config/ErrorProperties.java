package com.patra.starter.core.error.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for platform-wide error handling.
 *
 * <p>Controls error-code context, resolution behavior, observation, and circuit-breaker settings.
 */
@Data
@ConfigurationProperties(prefix = "patra.error")
public class ErrorProperties {

  /** Whether platform-level error resolution is enabled. */
  private boolean enabled = true;

  /** Required error-code context prefix (e.g., REG, INGEST). */
  private String contextPrefix;

  /** Core resolution behavior. */
  private EngineProperties engine = new EngineProperties();

  /** Observation and metrics configuration. */
  private ObservationProperties observation = new ObservationProperties();

  /** Circuit-breaker configuration. */
  private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

  @Data
  public static class EngineProperties {
    /** Maximum depth to traverse the cause chain to avoid deep recursion. */
    private int maxCauseDepth = 10;

    /** Whether trait-based semantic mapping is enabled. */
    private boolean enableTraitMapping = true;

    /** Whether class-name heuristics are enabled. */
    private boolean enableNamingHeuristic = true;
  }

  @Data
  public static class ObservationProperties {
    /** Whether Micrometer observation and structured logging are enabled. */
    private boolean enabled = true;

    /** Slow-resolution threshold in milliseconds. */
    private long slowThresholdMs = 200L;

    /** Whether to log WARN entries for slow resolutions. */
    private boolean logSlowResolution = true;
  }

  @Data
  public static class CircuitBreakerProperties {
    /** Whether circuit-breaker protection is enabled. */
    private boolean enabled = true;

    /** Failure rate threshold (percentage 0–100). */
    private float failureRateThreshold = 50.0f;

    /** Minimum number of calls considered in the circuit window. */
    private int minimumNumberOfCalls = 20;

    /** Sliding-window size. */
    private int slidingWindowSize = 50;

    /** Number of permitted calls while half-open. */
    private int permittedCallsInHalfOpenState = 5;

    /** Wait duration while the breaker is open. */
    private Duration waitDurationInOpenState = Duration.ofSeconds(30);
  }
}
