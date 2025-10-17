package com.patra.starter.logging.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logback filter that implements sampling for high-frequency DEBUG and TRACE logs.
 *
 * <p>Implements edge case handling for high-throughput scenarios where DEBUG/TRACE logging could
 * impact performance.
 *
 * <h3>Purpose:</h3>
 *
 * Prevents log flooding by sampling DEBUG and TRACE logs when they exceed a configurable threshold.
 * For example, if threshold is 100 logs/second, only 1 out of every N logs will be written when the
 * rate exceeds this limit.
 *
 * <h3>Functionality:</h3>
 *
 * <ul>
 *   <li>Tracks log rates per logger name
 *   <li>Applies sampling only when rate exceeds threshold
 *   <li>INFO, WARN, ERROR logs are NEVER sampled (always logged)
 *   <li>Sampling rate adjusts dynamically based on actual log rate
 *   <li>Resets counters periodically to adapt to changing load
 * </ul>
 *
 * <h3>Configuration Example:</h3>
 *
 * <pre>{@code
 * <configuration>
 *     <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
 *         <filter class="com.patra.starter.logging.filter.SamplingFilter">
 *             <thresholdLogsPerSecond>100</thresholdLogsPerSecond>
 *             <samplingRate>10</samplingRate>
 *             <windowDuration>PT1S</windowDuration>
 *         </filter>
 *         <encoder>
 *             <pattern>%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} [%thread] %-5level %logger{36} - %msg%n</pattern>
 *         </encoder>
 *     </appender>
 * </configuration>
 * }</pre>
 *
 * <h3>Configuration Properties:</h3>
 *
 * <ul>
 *   <li><strong>thresholdLogsPerSecond</strong>: Maximum logs per second before sampling kicks in
 *       (default: 100)
 *   <li><strong>samplingRate</strong>: Keep 1 out of every N logs when sampling (default: 10, i.e.,
 *       10% of logs)
 *   <li><strong>windowDuration</strong>: Time window for rate calculation (default: PT1S = 1
 *       second)
 * </ul>
 *
 * <h3>Behavior Example:</h3>
 *
 * <pre>
 * Scenario: Threshold = 100 logs/sec, Sampling Rate = 10
 *
 * - At 50 logs/sec: All DEBUG/TRACE logs written (below threshold)
 * - At 150 logs/sec: Only 1 out of 10 DEBUG/TRACE logs written (above threshold)
 * - At 1000 logs/sec: Only 1 out of 10 DEBUG/TRACE logs written (100 logs/sec effective)
 * - INFO/WARN/ERROR: Always written regardless of rate
 * </pre>
 *
 * <h3>Performance Considerations:</h3>
 *
 * <ul>
 *   <li>Very low overhead (<1ms per log decision)
 *   <li>Uses concurrent data structures for thread-safety
 *   <li>Automatic cleanup of stale logger entries
 *   <li>Suitable for production high-throughput systems
 * </ul>
 *
 * @since 0.1.0 (Phase 6 - User Story 4)
 */
public class SamplingFilter extends Filter<ILoggingEvent> {

  // Configuration properties
  private int thresholdLogsPerSecond = 100; // Default: 100 logs/sec
  private int samplingRate = 10; // Default: Keep 1 out of 10 logs (10%)
  private Duration windowDuration = Duration.ofSeconds(1); // Default: 1 second window

  // Tracking state per logger
  private final ConcurrentHashMap<String, LoggerRateTracker> loggerTrackers =
      new ConcurrentHashMap<>();

  // Cleanup interval to remove stale trackers
  private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(5);
  private final AtomicLong lastCleanupTime = new AtomicLong(System.currentTimeMillis());

  @Override
  public FilterReply decide(ILoggingEvent event) {
    Level level = event.getLevel();

    // NEVER sample INFO, WARN, ERROR - always log these
    if (level.isGreaterOrEqual(Level.INFO)) {
      return FilterReply.NEUTRAL;
    }

    // Only sample DEBUG and TRACE levels
    if (level.isGreaterOrEqual(Level.DEBUG)) {
      String loggerName = event.getLoggerName();
      LoggerRateTracker tracker = getOrCreateTracker(loggerName);

      if (tracker.shouldLog()) {
        return FilterReply.NEUTRAL; // Allow log
      } else {
        return FilterReply.DENY; // Sample out this log
      }
    }

    // Default: allow
    return FilterReply.NEUTRAL;
  }

  /**
   * Gets or creates a rate tracker for the given logger.
   *
   * @param loggerName Logger name
   * @return Rate tracker
   */
  private LoggerRateTracker getOrCreateTracker(String loggerName) {
    // Periodic cleanup of stale trackers
    long now = System.currentTimeMillis();
    if (now - lastCleanupTime.get() > CLEANUP_INTERVAL.toMillis()) {
      cleanupStaleTrackers(now);
      lastCleanupTime.set(now);
    }

    return loggerTrackers.computeIfAbsent(
        loggerName,
        k -> new LoggerRateTracker(thresholdLogsPerSecond, samplingRate, windowDuration));
  }

  /**
   * Removes trackers that haven't been used recently to prevent memory leak.
   *
   * @param now Current timestamp
   */
  private void cleanupStaleTrackers(long now) {
    loggerTrackers.entrySet().removeIf(entry -> entry.getValue().isStale(now));
  }

  // Getters and setters for configuration properties

  public void setThresholdLogsPerSecond(int thresholdLogsPerSecond) {
    this.thresholdLogsPerSecond = thresholdLogsPerSecond;
  }

  public void setSamplingRate(int samplingRate) {
    this.samplingRate = samplingRate;
  }

  public void setWindowDuration(String windowDuration) {
    this.windowDuration = Duration.parse(windowDuration);
  }

  /**
   * Tracks log rate and applies sampling for a single logger.
   *
   * <p>Thread-safe using atomic operations.
   */
  private static class LoggerRateTracker {

    private final int thresholdLogsPerSecond;
    private final int samplingRate;
    private final long windowDurationMillis;

    private final AtomicInteger logCount = new AtomicInteger(0);
    private final AtomicLong windowStartTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger sampleCounter = new AtomicInteger(0);
    private final AtomicLong lastAccessTime = new AtomicLong(System.currentTimeMillis());

    public LoggerRateTracker(
        int thresholdLogsPerSecond, int samplingRate, Duration windowDuration) {
      this.thresholdLogsPerSecond = thresholdLogsPerSecond;
      this.samplingRate = samplingRate;
      this.windowDurationMillis = windowDuration.toMillis();
    }

    /**
     * Determines if the current log should be written.
     *
     * @return true if log should be written, false if it should be sampled out
     */
    public boolean shouldLog() {
      long now = System.currentTimeMillis();
      lastAccessTime.set(now);

      // Check if we need to reset the window
      long windowStart = windowStartTime.get();
      if (now - windowStart > windowDurationMillis) {
        // Reset window
        logCount.set(0);
        windowStartTime.set(now);
        sampleCounter.set(0);
      }

      int currentCount = logCount.incrementAndGet();

      // Calculate current rate (logs per second)
      long elapsedMillis = now - windowStartTime.get();
      if (elapsedMillis == 0) {
        return true; // Avoid division by zero, allow log
      }

      double currentRate = (currentCount * 1000.0) / elapsedMillis;

      // If below threshold, log everything
      if (currentRate <= thresholdLogsPerSecond) {
        return true;
      }

      // Above threshold, apply sampling
      int counter = sampleCounter.incrementAndGet();
      return (counter % samplingRate) == 0; // Keep 1 out of every N logs
    }

    /**
     * Checks if this tracker is stale (not accessed recently).
     *
     * @param now Current timestamp
     * @return true if stale (not accessed in last 5 minutes)
     */
    public boolean isStale(long now) {
      return now - lastAccessTime.get() > Duration.ofMinutes(5).toMillis();
    }
  }
}
