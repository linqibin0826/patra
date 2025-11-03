package com.patra.registry.api.dto.provenance;

import java.time.Instant;

/**
 * 速率限制和并发配置。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>id - 速率限制配置行的主标识符
 *   <li>provenanceId - 拥有该配置的数据源
 *   <li>operationType - 配置应用的操作类型鉴别器
 *   <li>effectiveFrom - 配置生效的时间戳
 *   <li>effectiveTo - 配置保持有效的截止时间戳
 *   <li>maxConcurrentRequests - 允许的最大并发 HTTP 请求数
 *   <li>perCredentialQpsLimit - 每个凭证强制执行的 QPS 限制
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record RateLimitConfigResp(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    Integer maxConcurrentRequests,
    Integer perCredentialQpsLimit) {}
