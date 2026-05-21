package dev.linqibin.patra.starter.provenance.common.config;

/// 限流配置记录
///
/// 定义访问 Provenance 数据源时的流量控制参数,包括并发请求数限制和基于凭证的 QPS 限制。 用于防止触发上游 API 的速率限制,确保稳定的数据采集。
///
/// @param maxConcurrentRequests 同时活跃的并发操作数量上限(并发守卫)
/// @param perCredentialQpsLimit 每个凭证集的期望 QPS 限制
/// @author linqibin
/// @since 0.1.0
public record RateLimitConfig(Integer maxConcurrentRequests, Integer perCredentialQpsLimit) {}
