package com.patra.starter.feign.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties that govern Feign error decoding behaviour, tolerant mode, and observability.
 */
@Data
@ConfigurationProperties(prefix = "patra.feign.problem")
public class FeignErrorProperties {

    /** Enable or disable the error decoder as a whole. */
    private boolean enabled = true;

    /** Enable tolerant mode (wrap non-ProblemDetail payloads in {@code RemoteCallException}). */
    private boolean tolerant = true;

    /** Maximum number of bytes to read from the downstream error response. */
    private int maxErrorBodySize = 64 * 1024;

    /** Include stack traces in tolerant-mode responses (debug use only). */
    private boolean includeStackTrace = false;

    /** Observation/metrics configuration. */
    private ObservationProperties observation = new ObservationProperties();

    /** Nested configuration for observation thresholds and logging. */
    @Data
    public static class ObservationProperties {
        /** Enable the observation recorder. */
        private boolean enabled = true;
        /** Log and tag parsing operations slower than this threshold (milliseconds). */
        private long slowParsingThresholdMs = 150;
        /** Emit a log line when parsing crosses the slow threshold. */
        private boolean logSlowParsing = true;
        /** Log and tag response-body reads slower than this threshold (milliseconds). */
        private long slowBodyReadingThresholdMs = 80;
        /** Emit a log line when reading the body crosses the slow threshold. */
        private boolean logSlowBodyReading = true;
        /** Emit an informational log when tolerant mode is invoked. */
        private boolean logTolerantUsage = true;
    }
}
