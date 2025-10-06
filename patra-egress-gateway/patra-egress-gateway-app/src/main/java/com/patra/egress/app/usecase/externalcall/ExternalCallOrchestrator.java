package com.patra.egress.app.usecase.externalcall;

import com.patra.egress.domain.model.aggregate.ResilienceConfigAggregate;
import com.patra.egress.domain.model.vo.*;
import com.patra.egress.domain.port.ConfigPort;
import com.patra.egress.domain.port.HttpClientPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * External call orchestrator implementation
 * Simplified version without resilience capabilities (rate limiting, retry, circuit breaker)
 * These will be added in Task 6
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalCallOrchestrator implements ExternalCallUseCase {

    private final ConfigPort configPort;
    private final HttpClientPort httpClientPort;
    private final ResponseEnvelopeBuilder responseEnvelopeBuilder = new ResponseEnvelopeBuilder();

    @Override
    public ExternalCallResult execute(ExternalCallCommand command) {
        // Generate trace ID
        String traceId = UUID.randomUUID().toString();

        log.info("[EGRESS][APP] External call started: url={} method={} traceId={}",
                command.request().url(), command.request().method(), traceId);

        Instant startTime = Instant.now();

        try {
            // 1. Load and merge resilience config
            ResilienceConfig mergedConfig = loadAndMergeConfig(command, traceId);

            // 2. Call external service (without resilience capabilities for now)
            HttpResponse response = httpClientPort.call(command.request(), mergedConfig);

            // 3. Build response envelope
            ResponseEnvelope envelope = buildResponseEnvelope(response, mergedConfig);

            // 4. Calculate duration
            Duration duration = Duration.between(startTime, Instant.now());

            log.info("[EGRESS][APP] External call completed: statusCode={} duration={}ms traceId={}",
                    response.statusCode(), duration.toMillis(), traceId);

            // 5. Return result (retry count = 0 for now, will be updated in Task 6)
            return new ExternalCallResult(envelope, duration, 0, traceId);

        } catch (Exception e) {
            Duration duration = Duration.between(startTime, Instant.now());
            log.error("[EGRESS][APP] External call failed: url={} duration={}ms error={} traceId={}",
                    command.request().url(), duration.toMillis(), e.getMessage(), traceId, e);
            throw e;
        }
    }

    /**
     * Load system config and merge with caller config
     */
    private ResilienceConfig loadAndMergeConfig(ExternalCallCommand command, String traceId) {
        // Load system default and max config
        ResilienceConfigAggregate aggregate = ResilienceConfigAggregate.loadSystemConfig(configPort);

        // If caller provided config, merge it (not exceeding max values)
        if (command.callerConfig() != null) {
            log.debug("[EGRESS][APP] Merging caller config with system config: traceId={}", traceId);

            ResilienceConfig mergedConfig = aggregate.mergeWithCallerConfig(command.callerConfig());

            // Log if caller config exceeded max values
            if (!command.callerConfig().equals(mergedConfig)) {
                log.warn("[EGRESS][APP] Caller config exceeded system max values, using capped values: traceId={}",
                        traceId);
            }

            return mergedConfig;
        } else {
            log.debug("[EGRESS][APP] No caller config provided, using system default: traceId={}", traceId);
            return aggregate.getSystemDefaultConfig();
        }
    }

    /**
     * Build response envelope from HTTP response
     */
    private ResponseEnvelope buildResponseEnvelope(HttpResponse response, ResilienceConfig config) {
        // Create a simple rate limit status (actual rate limiting will be added in Task 6)
        RateLimitStatus rateLimitStatus = new RateLimitStatus(
                config.rateLimit(),
                config.rateLimit(),  // No actual limiting yet, so remaining = limit
                Duration.ZERO,
                null  // No external rate limit info yet
        );

        // Generate retry advice from response
        RetryAdvice retryAdvice = RetryAdvice.fromResponse(response, config);

        return responseEnvelopeBuilder.build(
                response,
                rateLimitStatus,
                retryAdvice,
                config.responseHeaderWhitelist()
        );
    }
}
