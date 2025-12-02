package com.patra.catalog.domain.port;

import java.util.List;
import java.util.Optional;

/// 载体聚合根仓储接口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - Venue、VenueIdentifier、VenueMetrics 分开管理
/// - 提供批量操作方法支持大规模数据导入
///
/// **主要使用场景**：
///
/// 1. OpenAlex Sources S3 数据导入（批量写入）
/// 2. 按标识符查询载体（OpenAlex ID、ISSN-L）
/// 3. 文献关联时查找载体
///
/// @author linqibin
/// @since 0.1.0
public interface VenueRepository {

  // ========================================
  // 基本 CRUD 操作
  // ========================================

  /// 按 ID 查询载体。
  ///
  /// @param id 载体 ID
  /// @return 载体，如果不存在返回空
  Optional<VenueData> findById(Long id);

  /// 按 OpenAlex ID 查询载体。
  ///
  /// @param openalexId OpenAlex ID（格式：S1234567890）
  /// @return 载体，如果不存在返回空
  Optional<VenueData> findByOpenalexId(String openalexId);

  /// 按 ISSN-L 查询载体。
  ///
  /// @param issnL Linking ISSN
  /// @return 载体，如果不存在返回空
  Optional<VenueData> findByIssnL(String issnL);

  /// 保存载体（新增或更新）。
  ///
  /// @param venue 载体数据
  /// @return 保存后的载体（包含生成的 ID）
  VenueData save(VenueData venue);

  // ========================================
  // 批量操作（支持大规模导入）
  // ========================================

  /// 批量保存载体。
  ///
  /// @param venues 载体列表
  /// @return 保存后的载体列表
  List<VenueData> saveAll(List<VenueData> venues);

  /// 批量保存载体标识符。
  ///
  /// @param identifiers 标识符列表
  void saveIdentifiers(List<VenueIdentifierData> identifiers);

  /// 批量保存载体年度指标。
  ///
  /// @param metrics 年度指标列表
  void saveMetrics(List<VenueMetricsData> metrics);

  // ========================================
  // 标识符查询
  // ========================================

  /// 按标识符类型和值查询载体 ID。
  ///
  /// @param identifierType 标识符类型（如 ISSN、NLM、MAG）
  /// @param identifierValue 标识符值
  /// @return 载体 ID，如果不存在返回空
  Optional<Long> findVenueIdByIdentifier(String identifierType, String identifierValue);

  /// 查询载体的所有标识符。
  ///
  /// @param venueId 载体 ID
  /// @return 标识符列表
  List<VenueIdentifierData> findIdentifiersByVenueId(Long venueId);

  // ========================================
  // 指标查询
  // ========================================

  /// 查询载体的年度指标。
  ///
  /// @param venueId 载体 ID
  /// @return 年度指标列表（按年份降序）
  List<VenueMetricsData> findMetricsByVenueId(Long venueId);

  /// 查询载体某年的指标。
  ///
  /// @param venueId 载体 ID
  /// @param year 年份
  /// @return 该年指标，如果不存在返回空
  Optional<VenueMetricsData> findMetricsByVenueIdAndYear(Long venueId, int year);

  // ========================================
  // 数据传输对象（纯数据载体，无行为）
  // ========================================

  /// 载体数据传输对象。
  ///
  /// 用于 Repository 层传递载体数据，避免领域对象与持久化耦合。
  record VenueData(
      Long id,
      String venueType,
      String displayName,
      String abbreviatedTitle,
      String homepageUrl,
      String openalexId,
      String issnL,
      String hostOrganizationId,
      String hostOrganizationName,
      String countryCode,
      Boolean isOa,
      Boolean isInDoaj,
      Boolean isCore,
      Integer worksCount,
      Integer citedByCount,
      Integer hIndex,
      Integer i10Index,
      String provenanceCode,
      Long version) {}

  /// 载体标识符数据传输对象。
  record VenueIdentifierData(
      Long id, Long venueId, String identifierType, String identifierValue, Boolean isPrimary) {}

  /// 载体年度指标数据传输对象。
  record VenueMetricsData(
      Long id,
      Long venueId,
      int year,
      Integer worksCount,
      Integer citedByCount,
      Integer oaWorksCount) {}
}
