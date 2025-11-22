package com.patra.catalog.domain.port;

/// MeSH 主题词聚合根仓储接口(领域层定义,基础设施层实现)。
///
/// **设计原则**：
///
/// - 接口在Domain层定义,确保领域层独立
///   - 实现在Infrastructure层,遵循依赖倒置原则(DIP)
///   - 聚合内实体(TreeNumber/EntryTerm/Concept)分开加载
///   - 提供树形查询、全文检索等专门方法
///
/// @author linqibin
/// @since 0.1.0
public interface MeshDescriptorPort {
  // 方法待添加
}
