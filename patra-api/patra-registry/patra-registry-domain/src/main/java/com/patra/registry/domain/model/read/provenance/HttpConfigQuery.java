package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/// HTTP配置查询视图。
///
/// 用于查询HTTP客户端配置的读优化投影。包含超时设置、代理配置、重试策略等HTTP相关参数。
///
/// @author linqibin
/// @since 0.1.0
public record HttpConfigQuery(
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
    Integer idempotencyTtlSeconds) {
  public HttpConfigQuery {
    if (id == null || id <= 0) {
      throw new DomainValidationException("HTTP配置ID必须为正数");
    }
    if (provenanceId == null || provenanceId <= 0) {
      throw new DomainValidationException("来源ID必须为正数");
    }
    if (retryAfterPolicyCode == null || retryAfterPolicyCode.isBlank()) {
      throw new DomainValidationException("重试策略代码不能为空");
    }
    if (effectiveFrom == null) {
      throw new DomainValidationException("生效时间不能为null");
    }
    operationType = operationType != null ? operationType.trim() : null;
    proxyUrlValue = proxyUrlValue != null ? proxyUrlValue.trim() : null;
    retryAfterPolicyCode = retryAfterPolicyCode.trim();
    idempotencyHeaderName = idempotencyHeaderName != null ? idempotencyHeaderName.trim() : null;
  }
}
