package com.patra.starter.provenance.common.http;

/**
 * Provenance starter 的简单 HTTP 客户端使用的最小 HTTP 弹性配置
 *
 * <p>轻量级配置记录,用于在非网关模式下直接调用上游API时配置重试、超时和速率限制。
 *
 * @param timeoutSeconds 请求超时(秒)(null → 使用库默认值)
 * @param maxRetries 最大重试次数(null 或 <=0 → 不重试)
 * @param retryBackoffSeconds 重试间隔退避时间(秒)(null → 0)
 * @param rateLimitQps 可选的每凭证 QPS 限制(尽力而为,仅本地生效)
 * @author linqibin
 * @since 0.1.0
 */
public record HttpResilienceConfig(
    Long timeoutSeconds, Integer maxRetries, Long retryBackoffSeconds, Integer rateLimitQps) {}
