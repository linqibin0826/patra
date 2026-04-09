package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.ScopusRatingEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// Scopus 期刊指标 JPA Repository。
///
/// **职责**：
///
/// - 提供 ScopusRatingEntity 的 CRUD 操作
/// - 支持按业务唯一键 `(venueId, year)` 查询（upsert 查重）
/// - 支持按 venueId 批量查询（ReadAdapter 批量组装用）
/// - 支持查找最新年份评级
///
/// @author linqibin
/// @since 0.1.0
public interface ScopusRatingDao extends JpaRepository<ScopusRatingEntity, Long> {

  /// 按业务唯一键查找 Scopus 评级。
  ///
  /// @param venueId 期刊 ID
  /// @param year 评级年份
  /// @return Scopus 评级实体
  Optional<ScopusRatingEntity> findByVenueIdAndYear(Long venueId, Short year);

  /// 查找某期刊的所有 Scopus 评级（全部年份）。
  ///
  /// @param venueId 期刊 ID
  /// @return Scopus 评级列表
  List<ScopusRatingEntity> findByVenueId(Long venueId);

  /// 批量查找多个期刊的 Scopus 评级（ReadAdapter 分页组装用）。
  ///
  /// @param venueIds 期刊 ID 列表
  /// @return Scopus 评级列表
  List<ScopusRatingEntity> findByVenueIdIn(Collection<Long> venueIds);

  /// 查找某期刊已有的评级年份集合（仅 year 列投影，Writer 过滤用）。
  ///
  /// @param venueId 期刊 ID
  /// @return 已有评级年份集合
  @Query("SELECT r.year FROM ScopusRatingEntity r WHERE r.venueId = :venueId")
  Set<Short> findYearsByVenueId(@Param("venueId") Long venueId);

  /// 查找某期刊最新年份的 Scopus 评级。
  ///
  /// @param venueId 期刊 ID
  /// @return 最新年份的 Scopus 评级
  @Query(
      """
      SELECT r FROM ScopusRatingEntity r
      WHERE r.venueId = :venueId
      ORDER BY r.year DESC
      LIMIT 1
      """)
  Optional<ScopusRatingEntity> findLatestByVenueId(@Param("venueId") Long venueId);
}
