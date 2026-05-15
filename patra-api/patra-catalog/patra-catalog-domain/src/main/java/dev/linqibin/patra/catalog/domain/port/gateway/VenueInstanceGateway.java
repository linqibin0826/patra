package dev.linqibin.patra.catalog.domain.port.gateway;

import dev.linqibin.patra.catalog.domain.model.aggregate.VenueInstanceAggregate;
import dev.linqibin.patra.catalog.domain.model.vo.venueinstance.JournalInstanceParams;

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
public interface VenueInstanceGateway {

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
  /// @param params 期刊实例参数（包含 venueId、volume、issue、出版日期等）
  /// @return 期刊实例聚合根（永不为 null）
  VenueInstanceAggregate findOrCreateJournalInstance(JournalInstanceParams params);
}
