package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

/**
 * Provenance configuration query view.
 *
 * <p>Read-optimized projection for querying complete provenance configuration aggregate.
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceConfigQuery(
    ProvenanceQuery provenance,
    WindowOffsetQuery windowOffset,
    PaginationConfigQuery pagination,
    HttpConfigQuery http,
    BatchingConfigQuery batching,
    RetryConfigQuery retry,
    RateLimitConfigQuery rateLimit) {
  public ProvenanceConfigQuery {
    if (provenance == null) {
      throw new DomainValidationException("Provenance cannot be null");
    }
  }
}
