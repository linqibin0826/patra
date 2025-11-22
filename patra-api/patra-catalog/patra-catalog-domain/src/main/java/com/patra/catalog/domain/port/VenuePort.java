package com.patra.catalog.domain.port;

/// 载体聚合根仓储接口（领域层定义，基础设施层实现）。
/// 
/// **设计原则**：
/// 
/// - 接口在Domain层定义，确保领域层独立
///   - 实现在Infrastructure层，遵循依赖倒置原则（DIP）
///   - Venue和VenueInstance分开管理（避免性能问题）
///   - 提供关联查询方法满足业务需求
/// 
/// @author linqibin
/// @since 0.1.0
public interface VenuePort {
  // 方法待添加
}
