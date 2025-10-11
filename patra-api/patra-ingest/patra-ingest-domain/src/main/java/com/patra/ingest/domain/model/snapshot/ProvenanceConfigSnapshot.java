package com.patra.ingest.domain.model.snapshot;

import java.time.Instant;

/**
 * Aggregated snapshot of provenance configuration.
 * <p>Combines multiple registry sub-domain tables into a single immutable view so a scheduler execution observes a consistent, replayable configuration.</p>
 * <p>Selection rule: for each dimension choose the latest row where {@code NOW()} falls within {@code [effective_from, effective_to)} and {@code lifecycle=ACTIVE} and {@code deleted=0} (ordered by {@code effective_from DESC}, {@code id DESC}). Credential dimensions may return multiple rows.</p>
 *
 * <p>Included dimensions (table → nested record):
 * reg_provenance → ProvenanceInfo; reg_prov_window_offset_cfg → WindowOffsetConfig;
 * reg_prov_pagination_cfg → PaginationConfig; reg_prov_http_cfg → HttpConfig; reg_prov_batching_cfg → BatchingConfig;
 * reg_prov_retry_cfg → RetryConfig; reg_prov_rate_limit_cfg → RateLimitConfig.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceConfigSnapshot(
        /* Source metadata */ ProvenanceInfo provenance,
        /* Time window / incremental offset (nullable) */ WindowOffsetConfig windowOffset,
        /* Pagination / cursor (nullable) */ PaginationConfig pagination,
        /* HTTP policy (nullable) */ HttpConfig http,
        /* Batching / request shaping (nullable) */ BatchingConfig batching,
        /* Retry and backoff (nullable) */ RetryConfig retry,
        /* Rate limiting and concurrency (nullable) */ RateLimitConfig rateLimit
) {

    /**
     * Provenance metadata (reg_provenance).
     * Dictionary: lifecycle_status = DRAFT|ACTIVE|DEPRECATED|RETIRED
     */
    public record ProvenanceInfo(
            /* Primary key ID */ Long id,
            /* Provenance code (globally unique, for example pubmed/crossref) */ String code,
            /* Human-readable provenance name */ String name,
            /* Default base URL (used when HTTP config does not override) */ String baseUrlDefault,
            /* Default timezone (IANA, e.g., UTC/Asia/Shanghai) */ String timezoneDefault,
            /* Documentation / official URL (for debugging references) */ String docsUrl,
            /* Active flag (quick toggle) */ boolean active,
            /* Lifecycle status (lifecycle_status: DRAFT|ACTIVE|DEPRECATED|RETIRED) */ String lifecycleStatusCode
    ) {
    }

    /**
     * Window & Offset configuration (reg_prov_window_offset_cfg).
     * Dictionary: window_mode = SLIDING|CALENDAR; time_unit = SECOND|MINUTE|HOUR|DAY; offset_type = DATE|ID|COMPOSITE; lifecycle_status as above.
     */
    public record WindowOffsetConfig(
            /* Primary key ID */ Long id,
            /* Provenance ID */ Long provenanceId,
            /* Operation type (nullable) */ String operationType,
            /* Effective from (inclusive) */ Instant effectiveFrom,
            /* Effective to (exclusive; NULL=long-term) */ Instant effectiveTo,
            /* Window mode (window_mode: SLIDING|CALENDAR) */ String windowModeCode,
            /* Window length value (e.g., 1/7/30) */ Integer windowSizeValue,
            /* Window length unit (time_unit: SECOND|MINUTE|HOUR/DAY) */ String windowSizeUnitCode,
            /* CALENDAR alignment granularity (HOUR|DAY|WEEK|MONTH, nullable) */ String calendarAlignTo,
            /* Lookback length value (compensate for delayed data) */ Integer lookbackValue,
            /* Lookback length unit (time_unit) */ String lookbackUnitCode,
            /* Window overlap length value (late arrival coverage) */ Integer overlapValue,
            /* Window overlap unit (time_unit) */ String overlapUnitCode,
            /* Watermark lag seconds (out-of-order allowed delay) */ Integer watermarkLagSeconds,
            /* Offset type (offset_type: DATE|ID|COMPOSITE) */ String offsetTypeCode,
            /* Offset field or JSONPath (interpreted per offset type) */ String offsetFieldName,
            /* DATE offset format (e.g., ISO_INSTANT/epochMillis/yyyyMMdd) */ String offsetDateFormat,
            /* Default incremental date field (when multiple date candidates) */ String defaultDateFieldName,
            /* Max IDs per window (may re-split if exceeded) */ Integer maxIdsPerWindow,
            /* Max window span seconds (forced split if exceeded) */ Integer maxWindowSpanSeconds
    ) {
    }

    /**
     * Pagination / Cursor / Token / Scroll configuration (reg_prov_pagination_cfg).
     * Dictionary: pagination_mode = PAGE_NUMBER|CURSOR|TOKEN|SCROLL; lifecycle_status as above.
     */
    public record PaginationConfig(
            /* Primary key ID */ Long id,
            /* Provenance ID */ Long provenanceId,
            /* Operation type (nullable) */ String operationType,
            /* Effective from (inclusive) */ Instant effectiveFrom,
            /* Effective to (exclusive; NULL=long-term) */ Instant effectiveTo,
            /* Pagination mode (pagination_mode: PAGE_NUMBER|CURSOR|TOKEN|SCROLL) */ String paginationModeCode,
            /* Page size value (PAGE_NUMBER/SCROLL mode, NULL=app default) */ Integer pageSizeValue,
            /* Max pages per execution (NULL=no limit or upper control) */ Integer maxPagesPerExecution,
            /* Sort field parameter name (e.g., sort) */ String sortFieldParamName,
            /* Sort direction (1=ASC, 0=DESC) */ Integer sortingDirection
    ) {
    }

    /**
     * HTTP policy configuration (reg_prov_http_cfg).
     * Dictionary: retry_after_policy = IGNORE|RESPECT|CLAMP; lifecycle_status as above.
     */
    public record HttpConfig(
            /* Primary key ID */ Long id,
            /* Provenance ID */ Long provenanceId,
            /* Operation type (nullable) */ String operationType,
            /* Effective from (inclusive) */ Instant effectiveFrom,
            /* Effective to (exclusive; NULL=long-term) */ Instant effectiveTo,
            /* Default Headers JSON (merged at runtime) */ String defaultHeadersJson,
            /* Connect timeout milliseconds */ Integer timeoutConnectMillis,
            /* Read timeout milliseconds */ Integer timeoutReadMillis,
            /* Total timeout milliseconds (overall request cap) */ Integer timeoutTotalMillis,
            /* Whether to verify TLS certificates */ boolean tlsVerifyEnabled,
            /* Proxy address (supports http(s)/socks5) */ String proxyUrlValue,
            /* Retry-After handling policy (retry_after_policy: IGNORE|RESPECT|CLAMP) */ String retryAfterPolicyCode,
            /* Retry-After max wait cap milliseconds (CLAMP/RESPECT) */ Integer retryAfterCapMillis,
            /* Idempotency Header name (e.g., Idempotency-Key) */ String idempotencyHeaderName,
            /* Idempotency key TTL seconds (if client/server supports) */ Integer idempotencyTtlSeconds
    ) {
    }

    /**
     * Batching & request shaping configuration (reg_prov_batching_cfg).
     * Dictionary: lifecycle_status as above.
     */
    public record BatchingConfig(
            /* Primary key ID */ Long id,
            /* Provenance ID */ Long provenanceId,
            /* Operation type (nullable) */ String operationType,
            /* Effective from (inclusive) */ Instant effectiveFrom,
            /* Effective to (exclusive; NULL=long-term) */ Instant effectiveTo,
            /* Detail fetch batch size (NULL=app default) */ Integer detailFetchBatchSize,
            /* IDs parameter name (nullable) */ String idsParamName,
            /* IDs join delimiter (default comma) */ String idsJoinDelimiter,
            /* Max IDs per request (hard limit) */ Integer maxIdsPerRequest
    ) {
    }

    /**
     * Retry and backoff configuration (reg_prov_retry_cfg).
     * Dictionary: scope = SOURCE|TASK; backoff_policy_type = FIXED|EXP|EXP_JITTER|DECOR_JITTER; lifecycle_status as above.
     */
    public record RetryConfig(
            /* Primary key ID */ Long id,
            /* Provenance ID */ Long provenanceId,
            /* Operation type discriminator (nullable) */ String operationType,
            /* Effective from (inclusive) */ Instant effectiveFrom,
            /* Effective to (exclusive; NULL=long-term) */ Instant effectiveTo,
            /* Max retry attempts (NULL=default; 0=disabled) */ Integer maxRetryTimes,
            /* Backoff policy (FIXED|EXP|EXP_JITTER|DECOR_JITTER) */ String backoffPolicyTypeCode,
            /* Initial delay milliseconds (first retry) */ Integer initialDelayMillis,
            /* Max delay per retry */ Integer maxDelayMillis,
            /* Exponential multiplier (EXP family) */ Double expMultiplierValue,
            /* Jitter factor ratio (0~1) */ Double jitterFactorRatio,
            /* Retry HTTP status list JSON */ String retryHttpStatusJson,
            /* Give-up HTTP status list JSON */ String giveupHttpStatusJson,
            /* Retry on network errors */ boolean retryOnNetworkError,
            /* Circuit breaker threshold (consecutive failures) */ Integer circuitBreakThreshold,
            /* Circuit breaker cool-down milliseconds */ Integer circuitCooldownMillis
    ) {
    }

    /**
     * Rate limiting and concurrency (reg_prov_rate_limit_cfg).
     */
    public record RateLimitConfig(
            /* Primary key ID */ Long id,
            /* Provenance ID */ Long provenanceId,
            /* Operation type discriminator (nullable) */ String operationType,
            /* Effective from (inclusive) */ Instant effectiveFrom,
            /* Effective to (exclusive; NULL=long-term) */ Instant effectiveTo,
            /* Max concurrent HTTP requests (NULL=engine default) */ Integer maxConcurrentRequests,
            /* Per credential QPS cap (nullable) */ Integer perCredentialQpsLimit
    ) {
    }

    // Credential dimension removed from snapshot.
}
