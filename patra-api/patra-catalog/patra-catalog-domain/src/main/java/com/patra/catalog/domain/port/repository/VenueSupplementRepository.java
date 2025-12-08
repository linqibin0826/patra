package com.patra.catalog.domain.port.repository;

import com.patra.catalog.domain.model.entity.VenueIndexingHistory;
import com.patra.catalog.domain.model.entity.VenueMesh;
import com.patra.catalog.domain.model.entity.VenuePublicationStats;
import com.patra.catalog.domain.model.entity.VenueRelation;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/// Venue 补充数据仓储接口。
///
/// **设计说明**：
///
/// 管理从 VenueAggregate 分离出的独立实体集合：
///
/// - **yearlyMetrics**：年度发文统计（来自 OpenAlex）
/// - **meshTerms**：MeSH 主题词（来自 NLM Serfile）
/// - **relations**：期刊关联关系（来自 NLM Serfile）
/// - **indexingHistories**：索引历史（来自 NLM Serfile）
///
/// 这些数据与 VenueAggregate 通过 `venueId` 关联，但不属于聚合边界内。
/// 它们没有需要保护的聚合级不变量，只是"补充信息"集合。
///
/// **使用场景**：
///
/// 1. OpenAlex 导入时，更新 yearlyMetrics
/// 2. Serfile 导入时，更新 meshTerms、relations、indexingHistories
///
/// **一致性保证**：
///
/// 调用方（Application 层）负责在同一事务中协调 VenueRepository 和
/// VenueSupplementRepository 的操作，确保数据一致性。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueSupplementRepository {

  // ========== 年度指标（OpenAlex 数据） ==========

  /// 批量查询年度指标。
  ///
  /// @param venueIds Venue ID 集合
  /// @return Map，key 为 venueId，value 为该 Venue 的年度指标列表
  Map<Long, List<VenuePublicationStats>> findYearlyMetricsByVenueIds(Collection<Long> venueIds);

  /// 批量替换年度指标（删除旧数据后插入新数据）。
  ///
  /// @param metricsByVenueId Map，key 为 venueId，value 为要设置的年度指标列表
  void replaceYearlyMetricsBatch(Map<Long, List<VenuePublicationStats>> metricsByVenueId);

  // ========== MeSH 主题词（Serfile 数据） ==========

  /// 批量查询 MeSH 主题词。
  ///
  /// @param venueIds Venue ID 集合
  /// @return Map，key 为 venueId，value 为该 Venue 的 MeSH 主题词列表
  Map<Long, List<VenueMesh>> findMeshTermsByVenueIds(Collection<Long> venueIds);

  /// 批量替换 MeSH 主题词（删除旧数据后插入新数据）。
  ///
  /// @param meshTermsByVenueId Map，key 为 venueId，value 为要设置的 MeSH 主题词列表
  void replaceMeshTermsBatch(Map<Long, List<VenueMesh>> meshTermsByVenueId);

  // ========== 期刊关联关系（Serfile 数据） ==========

  /// 批量查询期刊关联关系。
  ///
  /// @param venueIds Venue ID 集合
  /// @return Map，key 为 venueId，value 为该 Venue 的关联关系列表
  Map<Long, List<VenueRelation>> findRelationsByVenueIds(Collection<Long> venueIds);

  /// 批量替换期刊关联关系（删除旧数据后插入新数据）。
  ///
  /// @param relationsByVenueId Map，key 为 venueId，value 为要设置的关联关系列表
  void replaceRelationsBatch(Map<Long, List<VenueRelation>> relationsByVenueId);

  // ========== 索引历史（Serfile 数据） ==========

  /// 批量查询索引历史。
  ///
  /// @param venueIds Venue ID 集合
  /// @return Map，key 为 venueId，value 为该 Venue 的索引历史列表
  Map<Long, List<VenueIndexingHistory>> findIndexingHistoriesByVenueIds(Collection<Long> venueIds);

  /// 批量替换索引历史（删除旧数据后插入新数据）。
  ///
  /// @param historiesByVenueId Map，key 为 venueId，value 为要设置的索引历史列表
  void replaceIndexingHistoriesBatch(Map<Long, List<VenueIndexingHistory>> historiesByVenueId);

  // ========== 便捷方法 ==========

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
