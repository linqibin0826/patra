package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.aggregate.VenueInstanceAggregate;
import com.patra.catalog.domain.model.vo.venue.VenueId;

/// VenueInstance 服务端口（领域层定义，应用层实现）。
///
/// 提供 VenueInstance 的 findOrCreate 语义，用于文献导入时创建或复用载体实例。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保 Infrastructure 层可以依赖
/// - 实现在 Application 层，遵循六边形架构分层规则
/// - 支持并发安全的 findOrCreate 语义
///
/// **主要使用场景**：
///
/// Spring Batch Processor 在处理文献时，需要获取或创建对应的 VenueInstance。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueInstancePort {

  /// 查找或创建期刊实例。
  ///
  /// 如果指定的 (venueId, volume, issue, year) 组合已存在，返回现有实例；
  /// 否则创建新实例并保存。
  ///
  /// **并发处理**：
  ///
  /// 实现类应使用独立事务隔离并发创建。如果发生唯一约束冲突，
  /// 会重新查询返回已存在的实例。
  ///
  /// @param venueId 载体 ID（必填）
  /// @param volume 卷号（可为 null）
  /// @param issue 期号（可为 null）
  /// @param publicationYear 出版年份（必填）
  /// @param publicationMonth 出版月份（可选）
  /// @param publicationDay 出版日期（可选）
  /// @return 期刊实例聚合根（永不为 null）
  VenueInstanceAggregate findOrCreateJournalInstance(
      VenueId venueId,
      String volume,
      String issue,
      Integer publicationYear,
      Integer publicationMonth,
      Integer publicationDay);
}
