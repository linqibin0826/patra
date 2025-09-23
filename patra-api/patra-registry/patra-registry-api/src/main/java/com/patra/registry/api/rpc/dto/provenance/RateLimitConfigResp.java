package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * 限流与并发（Rate Limit & Concurrency）配置响应 DTO。<br>
 * <p>对应表：reg_prov_rate_limit_cfg。约束请求节奏，避免触发供应商限流或本地资源过载。</p>
 * 字段说明：
 * <ul>
 *   <li>{@code id} 主键。</li>
 *   <li>{@code provenanceId} 来源 ID。</li>
 *   <li>{@code scopeCode} 作用域。</li>
 *   <li>{@code taskType} 任务类型。</li>
 *   <li>{@code taskTypeKey} 任务子键。</li>
 *   <li>{@code effectiveFrom} 生效起。</li>
 *   <li>{@code effectiveTo} 生效止。</li>
 *   <li>{@code rateTokensPerSecond} 令牌发放速率（每秒请求数基线）。</li>
 *   <li>{@code burstBucketCapacity} 突发桶容量（允许瞬时突发的最大额外令牌）。</li>
 *   <li>{@code maxConcurrentRequests} 最大并发 HTTP 请求数（全局或该来源内）。</li>
 *   <li>{@code perCredentialQpsLimit} 单凭证级 QPS 限制（负载均衡多 Key）。</li>
 *   <li>{@code bucketGranularityScopeCode} 桶粒度：GLOBAL / PROVENANCE / ENDPOINT / CREDENTIAL。</li>
 *   <li>{@code smoothingWindowMillis} 平滑窗口毫秒（对瞬时尖峰做平滑）。</li>
 *   <li>{@code respectServerRateHeader} 是否解析服务器返回的速率控制头（如 X-RateLimit-Reset）。</li>
 *   <li>{@code endpointId} 限制绑定的端点（可选）。</li>
 *   <li>{@code credentialName} 限制绑定的凭证（细粒度策略）。</li>
 * </ul>
 */
public record RateLimitConfigResp(
        /** 主键 ID */
        Long id,
        /** 来源 ID */
        Long provenanceId,
        /** 作用域编码 */
        String scopeCode,
        /** 任务类型 */
        String taskType,
        /** 任务子键 */
        String taskTypeKey,
        /** 生效起 */
        Instant effectiveFrom,
        /** 生效止（不含） */
        Instant effectiveTo,
        /** 每秒令牌速率 */
        Integer rateTokensPerSecond,
        /** 突发桶容量 */
        Integer burstBucketCapacity,
        /** 最大并发请求数 */
        Integer maxConcurrentRequests,
        /** 单凭证 QPS 限制 */
        Integer perCredentialQpsLimit,
        /** 桶粒度作用域编码 */
        String bucketGranularityScopeCode,
        /** 平滑窗口毫秒 */
        Integer smoothingWindowMillis,
        /** 是否遵守服务器速率头 */
        boolean respectServerRateHeader,
        /** 绑定端点 ID */
        Long endpointId,
        /** 绑定凭证名 */
        String credentialName
) {
}
