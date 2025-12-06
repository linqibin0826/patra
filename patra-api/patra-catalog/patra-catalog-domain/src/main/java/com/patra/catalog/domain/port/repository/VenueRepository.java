package com.patra.catalog.domain.port.repository;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 载体聚合根仓储接口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 以聚合根为操作单位，保持一致性边界
///
/// **主要使用场景**：
///
/// OpenAlex Sources S3 数据初始化导入（批量写入）
///
/// @author linqibin
/// @since 0.1.0
public interface VenueRepository {

  /// 检查是否存在任何载体数据。
  ///
  /// 用于「一次性初始化」导入前的数据存在性检查。
  /// 如果表中已有数据，导入操作应拒绝执行。
  ///
  /// @return 如果存在任何载体数据返回 true，否则返回 false
  boolean hasAnyData();

  /// 批量插入载体聚合根（包含标识符和年度指标）。
  ///
  /// **适用场景**：OpenAlex Sources 数据初始化导入
  ///
  /// **设计说明**：
  ///
  /// - 纯 INSERT 语义，用于「一次性初始化」场景
  /// - 自动生成主键 ID 并设置子表外键
  /// - 子表（identifiers、yearlyMetrics）随主表一起插入
  /// - 空的子集合会被安全跳过，不会导致失败
  ///
  /// **注意**：
  ///
  /// - 不支持 Upsert（更新已存在记录）
  /// - 如果存在主键冲突，操作会失败
  /// - 调用前应确保目标表为空或无冲突数据
  ///
  /// @param aggregates 聚合根列表（不能为 null，可以为空）
  void insertAll(List<VenueAggregate> aggregates);

  /// 批量查询已存在的 ISSN-L。
  ///
  /// 用于 ISSN-L 唯一约束冲突时的去重处理，过滤数据源中与数据库重复的记录。
  ///
  /// @param issnLs 待检查的 ISSN-L 集合（不能为 null，可以为空）
  /// @return 数据库中已存在的 ISSN-L 集合（永不为 null）
  Set<String> findExistingIssnLs(Collection<String> issnLs);

  // ========== Serfile 导入相关方法 ==========

  /// 根据 ISSN-L 批量查找载体聚合根。
  ///
  /// 用于 Serfile 导入时的匹配查询（优先级最高）。
  /// 返回的聚合根包含完整的子实体（标识符、MeSH、关联、索引历史）。
  ///
  /// @param issnLs ISSN-L 集合（不能为 null，可以为空）
  /// @return ISSN-L 到聚合根的映射（永不为 null）
  Map<String, VenueAggregate> findByIssnLs(Collection<String> issnLs);

  /// 根据 NLM ID 批量查找载体聚合根。
  ///
  /// 用于 Serfile 导入时的匹配查询（优先级次高）。
  /// 返回的聚合根包含完整的子实体。
  ///
  /// @param nlmIds NLM ID 集合（不能为 null，可以为空）
  /// @return NLM ID 到聚合根的映射（永不为 null）
  Map<String, VenueAggregate> findByNlmIds(Collection<String> nlmIds);

  /// 根据 ISSN（Print 或 Electronic）批量查找载体聚合根。
  ///
  /// 用于 Serfile 导入时的匹配查询（优先级最低）。
  /// 从 `cat_venue_identifier` 表反查，返回完整的聚合根。
  ///
  /// @param issns ISSN 集合（不能为 null，可以为空）
  /// @return ISSN 到聚合根的映射（永不为 null）
  Map<String, VenueAggregate> findByIssns(Collection<String> issns);

  /// 批量更新载体聚合根（含子实体）。
  ///
  /// **适用场景**：Serfile 导入时覆盖已有数据
  ///
  /// **更新策略**：
  ///
  /// - 主表字段：直接更新
  /// - 子实体（MeSH、关联、索引历史）：删除旧记录，插入新记录
  /// - 标识符：合并（不删除已有，只添加新的）
  ///
  /// **事务说明**：
  ///
  /// - 方法本身不管理事务，由调用方控制事务边界
  /// - 建议按批次调用（如每 500 条一批）
  ///
  /// @param aggregates 聚合根列表（不能为 null，可以为空）
  void updateBatch(List<VenueAggregate> aggregates);
}
