package com.patra.catalog.domain.port.repository;

/// 作者聚合根仓储接口(领域层定义,基础设施层实现)。
///
/// **设计原则**：
///
/// - 接口在Domain层定义,确保领域层独立
///   - 实现在Infrastructure层,遵循依赖倒置原则(DIP)
///   - 提供去重查询、相似度匹配等专门方法
///   - 支持按标识符(ORCID/ResearcherID/ScopusID)查询
///
/// @author linqibin
/// @since 0.1.0
public interface AuthorRepository {
  // 方法待添加
}
