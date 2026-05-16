package dev.linqibin.patra.registry.domain.model.read.provenance;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;

/// 来源配置查询视图。
///
/// 用于查询完整来源配置聚合的读优化投影。整合了来源元数据及其所有相关配置(窗口偏移、分页、HTTP、批处理、重试、限流)。
///
/// @author linqibin
/// @since 0.1.0
public record ProvenanceConfigQuery(
    ProvenanceQuery provenance,
    WindowOffsetQuery windowOffset,
    PaginationConfigQuery pagination,
    HttpConfigQuery http,
    BatchingConfigQuery batching,
    RetryConfigQuery retry,
    RateLimitConfigQuery rateLimit) {
  /// 规范构造器,强制执行数据源配置查询视图的验证规则。
  ///
  /// 验证规则：
  ///
  /// - 数据源元数据不能为 null（必需字段）
  ///   - 各维度配置（窗口偏移、分页、HTTP、批处理、重试、限流）为可选字段
  ///
  /// @throws DomainValidationException 如果数据源元数据为 null
  public ProvenanceConfigQuery {
    if (provenance == null) {
      throw new DomainValidationException("来源信息不能为null");
    }
  }
}
