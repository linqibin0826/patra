package com.patra.ingest.app.model.registry;

import com.patra.common.enums.ProvenanceCode;

import java.time.ZoneId;
import java.util.Map;

/**
 * 来自 registry 的来源配置快照（中立模型）
 * 仅用于 app 编排
 */
public record ProvenanceConfigSnapshot(
        long provenanceId,
        ProvenanceCode provenanceCode,      // 外部是 enum，这里用 String 去耦（或自定义内部枚举）
        ZoneId timezone,            // 外部是 String timezone，这里提升为 ZoneId
        boolean enableAccess,
        String baseUrl,
        String dateFieldDefault,    // 默认日期字段（如 updatedAt/publishedAt）

        RetryPolicy retryPolicy,          // 重试策略：max/backoff/jitter
        RateLimitPolicy rateLimitPolicy,  // 限流策略：每秒令牌
        PagingPolicy pagingPolicy,        // 分页/批量策略
        WindowPolicy windowPolicy,        // 窗口/切片策略

        Map<String, String> publicHeaders // 公共请求头（不含敏感认证头）
) {

    /**
     * 重试策略
     */
    public record RetryPolicy(
            int maxAttempts,        // 对应 retryMax
            int backoffMs,          // 对应 backoffMs
            double jitter           // 对应 retryJitter
    ) {
    }

    /**
     * 限流策略
     */
    public record RateLimitPolicy(
            int permitsPerSecond    // 对应 rateLimitPerSec
    ) {
    }

    /**
     * 分页/批量策略
     */
    public record PagingPolicy(
            int searchPageSize,     // 对应 searchPageSize
            int fetchBatchSize      // 对应 fetchBatchSize
    ) {
    }

    /**
     * 窗口/切片策略
     */
    public record WindowPolicy(
            int overlapDays,            // 对应 overlapDays（切片窗口重叠天数，避免数据断层）
            int maxSearchIdsPerWindow   // 对应 maxSearchIdsPerWindow（每窗口最多检索的ID数量）
    ) {
    }

}
