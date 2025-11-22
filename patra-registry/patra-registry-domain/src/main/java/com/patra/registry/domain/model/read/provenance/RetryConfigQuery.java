package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/// 重试配置查询视图。
///
/// 用于查询重试策略配置的读优化投影。包含最大重试次数、退避策略、延迟参数、熔断阈值等重试相关配置。
///
/// @author linqibin
/// @since 0.1.0
public record RetryConfigQuery(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    Integer maxRetryTimes,
    String backoffPolicyTypeCode,
    Integer initialDelayMillis,
    Integer maxDelayMillis,
    Double expMultiplierValue,
    Double jitterFactorRatio,
    String retryHttpStatusJson,
    String giveupHttpStatusJson,
    boolean retryOnNetworkError,
    Integer circuitBreakThreshold,
    Integer circuitCooldownMillis) {
  /// 规范构造器,强制执行重试配置查询视图的验证规则。
  ///
  /// 验证规则：
  ///
  /// - 重试配置 ID 必须为正数
  ///   - 数据源 ID 必须为正数
  ///   - 退避策略类型代码不能为空
  ///   - 生效时间不能为 null
  ///   - 字符串字段自动执行 trim 操作
  ///
  /// @throws DomainValidationException 如果验证失败
  public RetryConfigQuery {
    if (id == null || id <= 0) {
      throw new DomainValidationException("重试配置ID必须为正数");
    }
    if (provenanceId == null || provenanceId <= 0) {
      throw new DomainValidationException("来源ID必须为正数");
    }
    if (backoffPolicyTypeCode == null || backoffPolicyTypeCode.isBlank()) {
      throw new DomainValidationException("退避策略类型代码不能为空");
    }
    if (effectiveFrom == null) {
      throw new DomainValidationException("生效时间不能为null");
    }
    operationType = operationType != null ? operationType.trim() : null;
    backoffPolicyTypeCode = backoffPolicyTypeCode.trim();
  }
}
