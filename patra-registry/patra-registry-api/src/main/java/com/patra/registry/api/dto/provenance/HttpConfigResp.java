package com.patra.registry.api.dto.provenance;

import java.time.Instant;

/**
 * 数据源 API 调用的基线 HTTP 配置。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>id - HTTP 配置行的主标识符
 *   <li>provenanceId - 拥有该配置的数据源
 *   <li>operationType - 操作类型鉴别器(如 HARVEST/UPDATE)
 *   <li>effectiveFrom - 配置生效的时间戳
 *   <li>effectiveTo - 配置保持有效的截止时间戳
 *   <li>defaultHeadersJson - 应用于出站调用的序列化默认头部
 *   <li>timeoutConnectMillis - 连接超时(毫秒)
 *   <li>timeoutReadMillis - 读取超时(毫秒)
 *   <li>timeoutTotalMillis - 整体请求超时(毫秒)
 *   <li>tlsVerifyEnabled - 是否启用 TLS 证书验证
 *   <li>proxyUrlValue - 用于出站流量的可选代理 URL
 *   <li>retryAfterPolicyCode - 处理 Retry-After 头部时应用的策略
 *   <li>retryAfterCapMillis - 遵守 Retry-After 时的最大等待时间(毫秒)
 *   <li>idempotencyHeaderName - 用于幂等性跟踪的注入头部名称
 *   <li>idempotencyTtlSeconds - 提供者侧幂等性令牌的预期 TTL
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record HttpConfigResp(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    String defaultHeadersJson,
    Integer timeoutConnectMillis,
    Integer timeoutReadMillis,
    Integer timeoutTotalMillis,
    boolean tlsVerifyEnabled,
    String proxyUrlValue,
    String retryAfterPolicyCode,
    Integer retryAfterCapMillis,
    String idempotencyHeaderName,
    Integer idempotencyTtlSeconds) {}
