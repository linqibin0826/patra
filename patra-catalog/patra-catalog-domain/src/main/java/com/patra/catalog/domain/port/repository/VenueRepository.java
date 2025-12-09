package com.patra.catalog.domain.port.repository;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.vo.venue.VenueIndexingHistory;
import com.patra.catalog.domain.model.vo.venue.VenueMesh;
import com.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import com.patra.catalog.domain.model.vo.venue.VenueRelation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 载体聚合根仓储接口（领域层定义，基础设施层实现）。
///
/// **聚合边界**：
///
/// - VenueAggregate：聚合根
/// - VenueIdentifier：值对象集合（保护 ISSN-L 唯一性不变量）
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 以聚合根为操作单位，保持一致性边界
/// - 聚合相关的所有数据通过此 Repository 统一维护
///
/// **补充数据管理**：
///
/// 此 Repository 同时管理与聚合关联的补充数据：
///
/// - **yearlyMetrics**：年度发文统计（来自 OpenAlex）
/// - **meshTerms**：MeSH 主题词（来自 NLM Serfile）
/// - **relations**：期刊关联关系（来自 NLM Serfile）
/// - **indexingHistories**：索引历史（来自 NLM Serfile）
///
/// **主要使用场景**：
///
/// - OpenAlex Sources S3 数据初始化导入（批量写入）
/// - PubMed Serfile 数据富化导入（批量更新）
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

  // ========== 补充数据管理（年度指标、MeSH、关联关系、索引历史） ==========

  // --- 年度指标（OpenAlex 数据） ---

  /// 批量查询年度指标。
  ///
  /// @param venueIds Venue ID 集合
  /// @return Map，key 为 venueId，value 为该 Venue 的年度指标列表
  Map<Long, List<VenuePublicationStats>> findYearlyMetricsByVenueIds(Collection<Long> venueIds);

  /// 批量替换年度指标（删除旧数据后插入新数据）。
  ///
  /// @param metricsByVenueId Map，key 为 venueId，value 为要设置的年度指标列表
  void replaceYearlyMetricsBatch(Map<Long, List<VenuePublicationStats>> metricsByVenueId);

  // --- MeSH 主题词（Serfile 数据） ---

  /// 批量查询 MeSH 主题词。
  ///
  /// @param venueIds Venue ID 集合
  /// @return Map，key 为 venueId，value 为该 Venue 的 MeSH 主题词列表
  Map<Long, List<VenueMesh>> findMeshTermsByVenueIds(Collection<Long> venueIds);

  /// 批量替换 MeSH 主题词（删除旧数据后插入新数据）。
  ///
  /// @param meshTermsByVenueId Map，key 为 venueId，value 为要设置的 MeSH 主题词列表
  void replaceMeshTermsBatch(Map<Long, List<VenueMesh>> meshTermsByVenueId);

  // --- 期刊关联关系（Serfile 数据） ---

  /// 批量查询期刊关联关系。
  ///
  /// @param venueIds Venue ID 集合
  /// @return Map，key 为 venueId，value 为该 Venue 的关联关系列表
  Map<Long, List<VenueRelation>> findRelationsByVenueIds(Collection<Long> venueIds);

  /// 批量替换期刊关联关系（删除旧数据后插入新数据）。
  ///
  /// @param relationsByVenueId Map，key 为 venueId，value 为要设置的关联关系列表
  void replaceRelationsBatch(Map<Long, List<VenueRelation>> relationsByVenueId);

  // --- 索引历史（Serfile 数据） ---

  /// 批量查询索引历史。
  ///
  /// @param venueIds Venue ID 集合
  /// @return Map，key 为 venueId，value 为该 Venue 的索引历史列表
  Map<Long, List<VenueIndexingHistory>> findIndexingHistoriesByVenueIds(Collection<Long> venueIds);

  /// 批量替换索引历史（删除旧数据后插入新数据）。
  ///
  /// @param historiesByVenueId Map，key 为 venueId，value 为要设置的索引历史列表
  void replaceIndexingHistoriesBatch(Map<Long, List<VenueIndexingHistory>> historiesByVenueId);

  // --- 便捷方法：Serfile 数据批量替换 ---

  /// 批量替换 Serfile 相关数据（MeSH、关联关系、索引历史）。
  ///
  /// 用于 Serfile 导入场景，在同一次调用中更新所有 Serfile 相关数据。
  ///
  /// @param meshTermsByVenueId MeSH 主题词
  /// @param relationsByVenueId 关联关系
  /// @param historiesByVenueId 索引历史
  void replaceSerfileDataBatch(
      Map<Long, List<VenueMesh>> meshTermsByVenueId,
      Map<Long, List<VenueRelation>> relationsByVenueId,
      Map<Long, List<VenueIndexingHistory>> historiesByVenueId);
}
