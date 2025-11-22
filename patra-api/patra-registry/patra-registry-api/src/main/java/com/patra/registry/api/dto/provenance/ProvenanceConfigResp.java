package com.patra.registry.api.dto.provenance;

/// 聚合数据源配置响应 DTO,包含所有配置维度。
/// 
/// 字段说明:
/// 
/// @param provenance 数据源元数据
/// @param windowOffset 时间窗口偏移配置
/// @param pagination 分页配置
/// @param http HTTP 配置
/// @param batching 批处理配置
/// @param retry 重试配置
/// @param rateLimit 速率限制配置
/// @author linqibin
/// @since 0.1.0
public record ProvenanceConfigResp(
    ProvenanceResp provenance,
    WindowOffsetResp windowOffset,
    PaginationConfigResp pagination,
    HttpConfigResp http,
    BatchingConfigResp batching,
    RetryConfigResp retry,
    RateLimitConfigResp rateLimit) {}
