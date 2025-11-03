package com.patra.registry.api.dto.provenance;

/**
 * 聚合数据源配置响应 DTO,包含所有配置维度。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>provenance - 基础数据源元数据
 *   <li>windowOffset - 时间窗口和偏移配置(可为 null)
 *   <li>pagination - 分页或游标配置(可为 null)
 *   <li>http - HTTP 基础配置(可为 null)
 *   <li>batching - 批处理和请求整形配置(可为 null)
 *   <li>retry - 重试和退避配置(可为 null)
 *   <li>rateLimit - 速率限制和并发配置(可为 null)
 * </ol>
 *
 * @param provenance 数据源元数据
 * @param windowOffset 时间窗口偏移配置
 * @param pagination 分页配置
 * @param http HTTP 配置
 * @param batching 批处理配置
 * @param retry 重试配置
 * @param rateLimit 速率限制配置
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceConfigResp(
    ProvenanceResp provenance,
    WindowOffsetResp windowOffset,
    PaginationConfigResp pagination,
    HttpConfigResp http,
    BatchingConfigResp batching,
    RetryConfigResp retry,
    RateLimitConfigResp rateLimit) {}
