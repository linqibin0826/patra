package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * HTTP 基础通信策略配置响应 DTO。<br>
 * <p>对应表：reg_prov_http_cfg。定义单来源/任务在 HTTP 层面的默认细节。</p>
 * 字段说明：
 * <ul>
 *   <li>{@code id} 主键。</li>
 *   <li>{@code provenanceId} 来源 ID。</li>
 *   <li>{@code scopeCode} 作用域。</li>
 *   <li>{@code taskType} 任务类型。</li>
 *   <li>{@code taskTypeKey} 任务子键。</li>
 *   <li>{@code effectiveFrom} 生效起。</li>
 *   <li>{@code effectiveTo} 生效止。</li>
 *   <li>{@code baseUrlOverride} 覆盖默认 baseUrl（空则使用 provenance.baseUrlDefault）。</li>
 *   <li>{@code defaultHeadersJson} 默认公共 Header JSON（例如 Accept/User-Agent/Cache-Control 等）。</li>
 *   <li>{@code timeoutConnectMillis} 连接建立超时（毫秒）。</li>
 *   <li>{@code timeoutReadMillis} 单次读取 / 响应体获取超时（毫秒）。</li>
 *   <li>{@code timeoutTotalMillis} 整个请求生命周期上限（毫秒）。</li>
 *   <li>{@code tlsVerifyEnabled} 是否启用 TLS 证书校验（禁用仅限调试）。</li>
 *   <li>{@code proxyUrlValue} 代理服务器 URL（http(s)://host:port）。</li>
 *   <li>{@code acceptCompressEnabled} 是否声明 Accept-Encoding: gzip 等以接受压缩。</li>
 *   <li>{@code preferHttp2Enabled} 是否优先使用 HTTP/2（客户端/服务端均支持时）。</li>
 *   <li>{@code retryAfterPolicyCode} 是否遵守服务端 Retry-After 头策略（IGNORE / HONOR / CAP）。</li>
 *   <li>{@code retryAfterCapMillis} 当策略为 CAP 时的最大等待毫秒数。</li>
 *   <li>{@code idempotencyHeaderName} 幂等性键 Header 名称（例如 Idempotency-Key）。为空表示不注入。</li>
 *   <li>{@code idempotencyTtlSeconds} 幂等 Key 服务端期望缓存的时间（秒），供生成策略参考。</li>
 * </ul>
 */
public record HttpConfigResp(
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
        /** 覆盖的基础 URL */
        String baseUrlOverride,
        /** 默认 Header JSON */
        String defaultHeadersJson,
        /** 连接超时毫秒 */
        Integer timeoutConnectMillis,
        /** 读取超时毫秒 */
        Integer timeoutReadMillis,
        /** 总超时毫秒 */
        Integer timeoutTotalMillis,
        /** 是否校验 TLS */
        boolean tlsVerifyEnabled,
        /** 代理 URL */
        String proxyUrlValue,
        /** 是否接受压缩 */
        boolean acceptCompressEnabled,
        /** 是否偏好 HTTP/2 */
        boolean preferHttp2Enabled,
        /** Retry-After 策略编码 */
        String retryAfterPolicyCode,
        /** Retry-After 等待上限毫秒 */
        Integer retryAfterCapMillis,
        /** 幂等 Header 名 */
        String idempotencyHeaderName,
        /** 幂等 Key TTL 秒 */
        Integer idempotencyTtlSeconds
) {
}
