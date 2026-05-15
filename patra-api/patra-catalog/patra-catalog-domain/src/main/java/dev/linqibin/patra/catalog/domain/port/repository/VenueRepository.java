package dev.linqibin.patra.catalog.domain.port.repository;

import dev.linqibin.patra.catalog.domain.model.aggregate.VenueAggregate;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenueIndexingHistory;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenueMesh;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenueRelation;
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
///
/// **补充数据管理**：
///
/// 本接口同时管理与载体关联的补充数据（不属于聚合边界，但通过 Repository 统一访问）：
///
/// - VenuePublicationStats：年度发文统计
/// - VenueMesh：MeSH 主题词（来自 NLM LSIOU）
/// - VenueRelation：期刊关联关系（来自 NLM LSIOU）
/// - VenueIndexingHistory：索引历史（来自 NLM LSIOU）
///
/// **主要使用场景**：
///
/// - PubMed LSIOU 数据导入（批量写入/更新）
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
  /// **适用场景**：PubMed LSIOU 导入时新增 Venue 记录
  ///
  /// **设计说明**：
  ///
  /// - 纯 INSERT 语义
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
  /// 用于「乐观插入」场景中的降级处理：当批量插入因 ISSN-L 唯一约束冲突失败时，
  /// 调用此方法查询已存在的 ISSN-L，然后过滤重复数据后重新插入。
  ///
  /// @param issnLs 待检查的 ISSN-L 集合（不能为 null，可以为空）
  /// @return 数据库中已存在的 ISSN-L 集合（永不为 null）
  Set<String> findExistingIssnLs(Collection<String> issnLs);

  // ========== LSIOU 导入相关方法 ==========

  /// 根据 ISSN-L 批量查找载体聚合根。
  ///
  /// 用于 LSIOU 导入时的匹配查询（优先级最高）。
  /// 返回的聚合根包含完整的子实体（标识符、MeSH、关联、索引历史）。
  ///
  /// @param issnLs ISSN-L 集合（不能为 null，可以为空）
  /// @return ISSN-L 到聚合根的映射（永不为 null）
  Map<String, VenueAggregate> findByIssnLs(Collection<String> issnLs);

  /// 根据 NLM ID 批量查找载体聚合根。
  ///
  /// 用于 LSIOU 导入时的匹配查询（优先级次高）。
  /// 返回的聚合根包含完整的子实体。
  ///
  /// @param nlmIds NLM ID 集合（不能为 null，可以为空）
  /// @return NLM ID 到聚合根的映射（永不为 null）
  Map<String, VenueAggregate> findByNlmIds(Collection<String> nlmIds);

  /// 根据 ISSN（Print 或 Electronic）批量查找载体聚合根。
  ///
  /// 用于 LSIOU 导入时的匹配查询（优先级最低）。
  /// 从 `cat_venue_identifier` 表反查，返回完整的聚合根。
  ///
  /// @param issns ISSN 集合（不能为 null，可以为空）
  /// @return ISSN 到聚合根的映射（永不为 null）
  Map<String, VenueAggregate> findByIssns(Collection<String> issns);

  /// 批量增量更新载体聚合根。
  ///
  /// 基于标识符差异，执行精准的增量更新，避免全量覆盖。
  ///
  /// **更新策略**：
  ///
  /// - **主表**：更新聚合根字段
  /// - **标识符**：基于集合差异计算，删除已移除的、插入新增的（真正的增量更新）
  ///
  /// **事务说明**：
  ///
  /// - 方法本身不管理事务，由调用方（Application 层）控制事务边界
  /// - 建议按批次调用（如每 500 条一批），避免长事务
  ///
  /// @param aggregates 聚合根列表（可以为 null 或空，直接返回）
  void updateBatch(List<VenueAggregate> aggregates);

  // ========== 补充数据管理（关联数据，不属于聚合边界） ==========

  /// 批量替换年度指标（删除旧数据后插入新数据）。
  ///
  /// 年度指标包含每年的发文数量等统计信息。
  ///
  /// @param yearlyMetricsByVenueId Map，key 为 venueId，value 为要设置的年度指标列表
  void replaceYearlyMetricsBatch(Map<Long, List<VenuePublicationStats>> yearlyMetricsByVenueId);

  /// 批量替换 MeSH 主题词（删除旧数据后插入新数据）。
  ///
  /// @param meshTermsByVenueId Map，key 为 venueId，value 为要设置的 MeSH 主题词列表
  void replaceMeshTermsBatch(Map<Long, List<VenueMesh>> meshTermsByVenueId);

  /// 批量替换期刊关联关系（删除旧数据后插入新数据）。
  ///
  /// @param relationsByVenueId Map，key 为 venueId，value 为要设置的关联关系列表
  void replaceRelationsBatch(Map<Long, List<VenueRelation>> relationsByVenueId);

  /// 批量替换索引历史（删除旧数据后插入新数据）。
  ///
  /// @param historiesByVenueId Map，key 为 venueId，value 为要设置的索引历史列表
  void replaceIndexingHistoriesBatch(Map<Long, List<VenueIndexingHistory>> historiesByVenueId);

  /// 批量替换 PubMed 相关数据（MeSH、关联关系、索引历史）。
  ///
  /// 用于 LSIOU 导入场景，在同一次调用中更新所有 PubMed 相关数据。
  /// 内部实现依次调用各个 replace 方法。
  ///
  /// @param meshTermsByVenueId MeSH 主题词
  /// @param relationsByVenueId 关联关系
  /// @param historiesByVenueId 索引历史
  default void replacePubmedDataBatch(
      Map<Long, List<VenueMesh>> meshTermsByVenueId,
      Map<Long, List<VenueRelation>> relationsByVenueId,
      Map<Long, List<VenueIndexingHistory>> historiesByVenueId) {
    replaceMeshTermsBatch(meshTermsByVenueId);
    replaceRelationsBatch(relationsByVenueId);
    replaceIndexingHistoriesBatch(historiesByVenueId);
  }
}
