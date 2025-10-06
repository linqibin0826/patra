package com.patra.egress.api.dto;

/**
 * External rate limit information DTO
 *
 * <p>Contains rate limit information extracted from external service response headers
 * (X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset).</p>
 *
 * @param limit rate limit cap from external service
 * @param remaining remaining quota from external service
 * @param resetTimestamp Unix timestamp when rate limit resets
 * @author linqibin
 * @since 0.1.0
 */
public record ExternalRateLimitInfoDTO(
    Integer limit,
    Integer remaining,
    Long resetTimestamp
) {
}
