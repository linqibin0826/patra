package com.patra.ingest.domain.model.snapshot;

import java.time.Instant;

/**
 * 来源配置聚合快照（Domain Snapshot）。
 * <p>聚合 Registry 子域多维度配置于单一不可变视图，保证单次调度/执行期内配置一致性与可重放。</p>
 * <p>时间片选择规则统一：同维度按 NOW() 命中 [effective_from, effective_to) 且 lifecycle=ACTIVE 且 deleted=0 的最新一条（按 effective_from DESC,id DESC）。
 * 凭证维度可多条。</p>
 *
 * <p>包含的维度（表 → 领域嵌套 record）：
 * reg_provenance → ProvenanceInfo；reg_prov_window_offset_cfg → WindowOffsetConfig；
 * reg_prov_pagination_cfg → PaginationConfig；reg_prov_http_cfg → HttpConfig；reg_prov_batching_cfg → BatchingConfig；
 * reg_prov_retry_cfg → RetryConfig；reg_prov_rate_limit_cfg → RateLimitConfig。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceConfigSnapshot(
        /* 来源基础信息 */ ProvenanceInfo provenance,
        /* 时间窗口 / 增量指针（可空） */ WindowOffsetConfig windowOffset,
        /* 分页 / 游标（可空） */ PaginationConfig pagination,
        /* HTTP 策略（可空） */ HttpConfig http,
        /* 批量 / 请求成型（可空） */ BatchingConfig batching,
        /* 重试与退避（可空） */ RetryConfig retry,
        /* 限流与并发（可空） */ RateLimitConfig rateLimit
) {

    /**
     * 来源基础信息（reg_provenance）。
     * 字典：lifecycle_status = DRAFT|ACTIVE|DEPRECATED|RETIRED
     */
    public record ProvenanceInfo(
            /* 主键ID */ Long id,
            /* 来源编码（全局唯一稳定；如 pubmed / crossref） */ String code,
            /* 来源名称（人类可读） */ String name,
            /* 默认基础URL（端点 path 拼接基线，HTTP 配置未覆盖时生效） */ String baseUrlDefault,
            /* 默认时区（IANA，如 UTC/Asia/Shanghai） */ String timezoneDefault,
            /* 官方/文档 URL（调试引用） */ String docsUrl,
            /* 是否启用（快速开关） */ boolean active,
            /* 生命周期状态 (lifecycle_status: DRAFT|ACTIVE|DEPRECATED|RETIRED) */ String lifecycleStatusCode
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
     * 重试与退避（reg_prov_retry_cfg）。
     * 字典：scope = SOURCE|TASK；backoff_policy_type = FIXED|EXP|EXP_JITTER|DECOR_JITTER；lifecycle_status 同上。
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
