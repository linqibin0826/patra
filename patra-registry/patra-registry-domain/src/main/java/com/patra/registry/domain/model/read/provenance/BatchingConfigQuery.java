package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * 批处理配置查询视图。
 *
 * <p>用于查询批处理策略配置的读优化投影。定义了详情获取时的批量处理参数和限制。
 *
 * @author linqibin
 * @since 0.1.0
 */
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
