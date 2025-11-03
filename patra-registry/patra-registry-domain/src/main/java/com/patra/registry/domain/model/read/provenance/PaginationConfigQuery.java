package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * 分页配置查询视图。
 *
 * <p>用于查询分页策略配置的读优化投影。定义了分页模式、页大小、排序参数等分页相关配置。
 *
 * @author linqibin
 * @since 0.1.0
 */
public record PaginationConfigQuery(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    String paginationModeCode,
    Integer pageSizeValue,
    Integer maxPagesPerExecution,
    String sortFieldParamName,
    Integer sortingDirection) {
  public PaginationConfigQuery {
    if (id == null || id <= 0) {
      throw new DomainValidationException("分页配置ID必须为正数");
    }
    if (provenanceId == null || provenanceId <= 0) {
      throw new DomainValidationException("来源ID必须为正数");
    }
    if (paginationModeCode == null || paginationModeCode.isBlank()) {
      throw new DomainValidationException("分页模式代码不能为空");
    }
    if (effectiveFrom == null) {
      throw new DomainValidationException("生效时间不能为null");
    }
    operationType = operationType != null ? operationType.trim() : null;
    paginationModeCode = paginationModeCode.trim();
    sortFieldParamName = sortFieldParamName != null ? sortFieldParamName.trim() : null;
  }
}
