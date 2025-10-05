package com.patra.registry.domain.model.aggregate;

import com.patra.registry.domain.exception.DomainValidationException;
import com.patra.registry.domain.model.vo.provenance.BatchingConfig;
import com.patra.registry.domain.model.vo.provenance.HttpConfig;
import com.patra.registry.domain.model.vo.provenance.PaginationConfig;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import com.patra.registry.domain.model.vo.provenance.RetryConfig;
import com.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;

import java.util.Optional;

/**
 * Provenance configuration aggregate: a consolidated read-only view over
 * provenance and multiple configuration dimensions.
 *
 * <p>Used on the CQRS read side to represent the effective configuration at a
 * point in time, including HTTP policy, retry and rate limit settings, etc.</p>
 *
 * <p>Scope precedence: TASK-specific slices override SOURCE-level defaults.</p>
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
        RateLimitConfig rateLimit
) {
    public ProvenanceConfiguration {
        DomainValidationException.nonNull(provenance, "Provenance");
    }

    /** 是否配置了窗口策略。 */
    public boolean hasWindowOffset() {
        return windowOffset != null;
    }

    /** 是否配置了分页策略。 */
    public boolean hasPagination() {
        return pagination != null;
    }

    /** 是否配置了HTTP策略。 */
    public boolean hasHttpConfig() {
        return http != null;
    }

    /** 是否配置了批处理策略。 */
    public boolean hasBatching() {
        return batching != null;
    }

    /** 是否配置了重试策略。 */
    public boolean hasRetry() {
        return retry != null;
    }

    /** 是否配置了限流策略。 */
    public boolean hasRateLimit() {
        return rateLimit != null;
    }

    // Credential dimension has been removed from the aggregate.

    /** 是否为完整配置（包含所有必要的配置维度）。 */
    public boolean isComplete() {
        return provenance != null && provenance.isActive();
    }
}
