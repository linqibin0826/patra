package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

/**
 * 来源配置查询视图。
 *
 * <p>用于查询完整来源配置聚合的读优化投影。整合了来源元数据及其所有相关配置(窗口偏移、分页、HTTP、批处理、重试、限流)。
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceConfigQuery(
    ProvenanceQuery provenance,
    WindowOffsetQuery windowOffset,
    PaginationConfigQuery pagination,
    HttpConfigQuery http,
    BatchingConfigQuery batching,
    RetryConfigQuery retry,
    RateLimitConfigQuery rateLimit) {
  public ProvenanceConfigQuery {
    if (provenance == null) {
      throw new DomainValidationException("来源信息不能为null");
    }
  }
}
