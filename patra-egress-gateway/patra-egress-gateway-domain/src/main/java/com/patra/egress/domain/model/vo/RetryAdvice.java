package com.patra.egress.domain.model.vo;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Value object describing whether an outbound call should be retried and when.
 *
 * @param retryable      indicates if a retry is recommended
 * @param suggestedDelay backoff duration before attempting the retry
 * @param reason         rationale for the recommendation
 * @author linqibin
 * @since 0.1.0
 */
public record RetryAdvice(
    boolean retryable,
    Duration suggestedDelay,
    String reason
) {
    /**
     * Create a default recommendation indicating the request should not be retried.
     *
     * @return {@link RetryAdvice} representing a non-retryable outcome
     */
    public static RetryAdvice notRetryable() {
        return new RetryAdvice(false, Duration.ZERO, "Not retryable");
    }

    /**
     * Build a retry recommendation based on the provider response and resilience configuration.
     *
     * @param response HTTP response returned by the provider
     * @param config   resilience configuration applied to the call
     * @return retry recommendation tailored to the response
     */
    public static RetryAdvice fromResponse(HttpResponse response, ResilienceConfig config) {
        int statusCode = response.statusCode();
        
        // Treat 429 Too Many Requests and 503 Service Unavailable as transient errors.
        if (statusCode == 429 || statusCode == 503) {
            Duration delay = extractRetryAfter(response.headers())
                .orElse(config.retryBackoff());
            String reason = statusCode == 429 
                ? "Rate limited" 
                : "Service unavailable";
            return new RetryAdvice(true, delay, reason);
        }
        
        // All other 5xx responses are considered retryable server errors.
        if (statusCode >= 500) {
            return new RetryAdvice(true, config.retryBackoff(), "Server error");
        }
        
        // Propagate retry advice for 408 Request Timeout as well.
        if (statusCode == 408) {
            return new RetryAdvice(true, config.retryBackoff(), "Request timeout");
        }
        
        // Other status codes are not retried.
        return new RetryAdvice(false, Duration.ZERO, "Not retryable");
    }
    
    /**
     * Extract the {@code Retry-After} header in a case-insensitive manner.
     *
     * @param headers response headers
     * @return optional delay derived from the header
     */
    private static java.util.Optional<Duration> extractRetryAfter(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return java.util.Optional.empty();
        }

        List<String> retryAfterValues = getHeaderIgnoreCase(headers, "Retry-After");
        if (retryAfterValues == null || retryAfterValues.isEmpty()) {
            return java.util.Optional.empty();
        }

        String retryAfter = retryAfterValues.get(0);

        try {
            // First attempt to parse the header as a numeric second value.
            long seconds = Long.parseLong(retryAfter);
            return java.util.Optional.of(Duration.ofSeconds(seconds));
        } catch (NumberFormatException e) {
            // If the header is not numeric, it may use HTTP date syntax, which is intentionally not parsed yet.
            return java.util.Optional.empty();
        }
    }

    /**
     * Retrieve a header value using a case-insensitive lookup.
     *
     * @param headers    response headers
     * @param headerName header key to retrieve
     * @return list of header values or {@code null} when absent
     */
    private static List<String> getHeaderIgnoreCase(Map<String, List<String>> headers, String headerName) {
        // Prefer an exact key match for performance reasons.
        List<String> values = headers.get(headerName);
        if (values != null) {
            return values;
        }

        // Fall back to a case-insensitive iteration when casing differs.
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(headerName)) {
                return entry.getValue();
            }
        }

        return null;
    }
}
