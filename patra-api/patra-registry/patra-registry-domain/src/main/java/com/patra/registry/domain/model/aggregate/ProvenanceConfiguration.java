package com.patra.registry.domain.model.aggregate;

import com.patra.registry.domain.exception.DomainValidationException;
import com.patra.registry.domain.model.vo.provenance.BatchingConfig;
import com.patra.registry.domain.model.vo.provenance.HttpConfig;
import com.patra.registry.domain.model.vo.provenance.PaginationConfig;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import com.patra.registry.domain.model.vo.provenance.RetryConfig;
import com.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;

/**
 * Provenance configuration aggregate root providing a consolidated read-only view over provenance
 * and multiple configuration dimensions.
 *
 * <p>Used on the CQRS read side to represent the effective configuration at a point in time,
 * including HTTP policy, retry, rate limit, and other operational settings.
 *
 * <p>Scope precedence: TASK-specific slices override SOURCE-level defaults.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>provenance - core provenance entity containing source metadata; never null
 *   <li>windowOffset - window offset configuration for time-based segmentation; nullable
 *   <li>pagination - pagination strategy configuration; nullable
 *   <li>http - HTTP client configuration including timeouts and headers; nullable
 *   <li>batching - batching configuration for detail fetch operations; nullable
 *   <li>retry - retry policy configuration with backoff strategy; nullable
 *   <li>rateLimit - rate limiting configuration for API throttling; nullable
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceConfiguration(
    Provenance provenance,
    WindowOffsetConfig windowOffset,
    PaginationConfig pagination,
    HttpConfig http,
    BatchingConfig batching,
    RetryConfig retry,
    RateLimitConfig rateLimit) {
  /**
   * Compact canonical constructor enforcing provenance non-null invariant.
   *
   * @throws DomainValidationException if provenance is null
   */
  public ProvenanceConfiguration {
    DomainValidationException.nonNull(provenance, "Provenance");
  }

  /**
   * Checks whether window offset configuration is present.
   *
   * @return true if window offset is configured
   */
  public boolean hasWindowOffset() {
    return windowOffset != null;
  }

  /**
   * Checks whether pagination configuration is present.
   *
   * @return true if pagination is configured
   */
  public boolean hasPagination() {
    return pagination != null;
  }

  /**
   * Checks whether HTTP configuration is present.
   *
   * @return true if HTTP config is present
   */
  public boolean hasHttpConfig() {
    return http != null;
  }

  /**
   * Checks whether batching configuration is present.
   *
   * @return true if batching is configured
   */
  public boolean hasBatching() {
    return batching != null;
  }

  /**
   * Checks whether retry configuration is present.
   *
   * @return true if retry policy is configured
   */
  public boolean hasRetry() {
    return retry != null;
  }

  /**
   * Checks whether rate limit configuration is present.
   *
   * @return true if rate limit is configured
   */
  public boolean hasRateLimit() {
    return rateLimit != null;
  }

  /**
   * Checks whether the configuration is complete and active.
   *
   * <p>A configuration is considered complete if the provenance is non-null and active. Individual
   * policy configurations (pagination, retry, etc.) are optional.
   *
   * @return true if provenance is present and active
   */
  public boolean isComplete() {
    return provenance != null && provenance.isActive();
  }
}
