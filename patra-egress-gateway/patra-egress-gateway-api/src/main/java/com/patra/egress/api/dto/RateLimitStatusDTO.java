package com.patra.egress.api.dto;

/**
 * Rate limit status DTO
 *
 * <p>Represents both gateway-level and external service rate limit status.</p>
 *
 * @param limit gateway rate limit cap
 * @param remaining gateway remaining quota
 * @param resetAfterSeconds seconds until rate limit resets
 * @param externalInfo external service rate limit information (may be null)
 * @author linqibin
 * @since 0.1.0
 */
public record RateLimitStatusDTO(
    int limit,
    int remaining,
    long resetAfterSeconds,
    ExternalRateLimitInfoDTO externalInfo
) {
}
