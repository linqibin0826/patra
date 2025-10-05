package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * 限流与并发（Rate Limit & Concurrency）配置响应 DTO。<br>
 * <p>对应表：reg_prov_rate_limit_cfg。约束请求节奏，避免触发供应商限流或本地资源过载。</p>
 * 字段说明：
 * <ul>
 *   <li>{@code id} 主键。</li>
 *   <li>{@code provenanceId} 来源 ID。</li>
 *   <li>{@code operationType} 操作类型。</li>
 *   <li>{@code effectiveFrom} 生效起。</li>
 *   <li>{@code effectiveTo} 生效止。</li>
 *   <li>{@code maxConcurrentRequests} 最大并发 HTTP 请求数。</li>
 *   <li>{@code perCredentialQpsLimit} 单凭证级 QPS 限制。</li>
 * </ul>
 */
public record RateLimitConfigResp(
        /** 主键 ID */
        Long id,
        /** 来源 ID */
        Long provenanceId,
        /** 操作类型 */
        String operationType,
        /** 生效起 */
        Instant effectiveFrom,
        /** 生效止（不含） */
        Instant effectiveTo,
        /** 最大并发请求数 */
        Integer maxConcurrentRequests,
        /** 单凭证 QPS 限制 */
        Integer perCredentialQpsLimit
) {
}
