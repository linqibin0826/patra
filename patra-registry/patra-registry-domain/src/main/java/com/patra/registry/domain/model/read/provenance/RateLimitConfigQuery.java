package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/// 速率限制配置查询视图。
///
/// 用于查询速率限制配置的读优化投影。定义了最大并发请求数和每凭证QPS限制等流量控制参数。
///
/// @author linqibin
/// @since 0.1.0
public record RateLimitConfigQuery(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    Integer maxConcurrentRequests,
    Integer perCredentialQpsLimit) {
  /// 规范构造器,强制执行速率限制配置查询视图的验证规则。
  ///
  /// 验证规则：
  ///
  /// - 限流配置 ID 必须为正数
  ///   - 数据源 ID 必须为正数
  ///   - 生效时间不能为 null
  ///   - 字符串字段自动执行 trim 操作
  ///
  /// @throws DomainValidationException 如果验证失败
  public RateLimitConfigQuery {
    if (id == null || id <= 0) {
      throw new DomainValidationException("限流配置ID必须为正数");
    }
    if (provenanceId == null || provenanceId <= 0) {
      throw new DomainValidationException("来源ID必须为正数");
    }
    if (effectiveFrom == null) {
      throw new DomainValidationException("生效时间不能为null");
    }
    operationType = operationType != null ? operationType.trim() : null;
  }
}
