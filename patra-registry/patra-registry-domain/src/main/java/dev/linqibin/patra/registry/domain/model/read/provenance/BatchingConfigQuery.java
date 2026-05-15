package dev.linqibin.patra.registry.domain.model.read.provenance;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/// 批处理配置查询视图。
///
/// 用于查询批处理策略配置的读优化投影。定义了详情获取时的批量处理参数和限制。
///
/// @author linqibin
/// @since 0.1.0
public record BatchingConfigQuery(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    Integer detailFetchBatchSize,
    String idsParamName,
    String idsJoinDelimiter,
    Integer maxIdsPerRequest) {
  /// 规范构造器,强制执行批处理配置查询视图的验证规则。
  ///
  /// 验证规则：
  ///
  /// - 批处理配置 ID 必须为正数
  ///   - 来源 ID 必须为正数
  ///   - 生效时间不能为 null
  ///   - 字符串字段自动执行 trim 操作
  ///
  /// @throws DomainValidationException 如果验证失败
  public BatchingConfigQuery {
    if (id == null || id <= 0) {
      throw new DomainValidationException("批处理配置ID必须为正数");
    }
    if (provenanceId == null || provenanceId <= 0) {
      throw new DomainValidationException("来源ID必须为正数");
    }
    if (effectiveFrom == null) {
      throw new DomainValidationException("生效时间不能为null");
    }
    operationType = operationType != null ? operationType.trim() : null;
    idsParamName = idsParamName != null ? idsParamName.trim() : null;
    idsJoinDelimiter = idsJoinDelimiter != null ? idsJoinDelimiter.trim() : null;
  }
}
