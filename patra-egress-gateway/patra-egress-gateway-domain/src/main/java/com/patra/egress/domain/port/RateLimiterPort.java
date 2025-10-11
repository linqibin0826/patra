package com.patra.egress.domain.port;

import com.patra.egress.domain.model.vo.RateLimitStatus;

/**
 * Domain port that exposes the rate limiting capability to the application layer.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface RateLimiterPort {
    
    /**
     * Attempt to acquire permission to execute under the configured rate.
     *
     * @param key       rate limiter identifier
     * @param rateLimit allowed requests per second
     * @return {@code true} when the request is permitted; {@code false} when throttled
     */
    boolean tryAcquire(String key, int rateLimit);
    
    /**
     * Retrieve the current rate limit status for the given key.
     *
     * @param key rate limiter identifier
     * @return status snapshot containing remaining quota and provider hints
     */
    RateLimitStatus getStatus(String key);
}
