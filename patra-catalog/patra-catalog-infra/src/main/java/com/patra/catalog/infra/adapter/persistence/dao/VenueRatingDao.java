package com.patra.catalog.infra.adapter.persistence.dao;

import com.patra.catalog.domain.model.enums.RatingSystem;
import com.patra.catalog.infra.adapter.persistence.entity.VenueRatingEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 载体评级 JPA Repository。
///
/// **职责**：
///
/// - 提供载体评级实体的 CRUD 操作
/// - 支持按载体、年份、评价体系查询
/// - 支持按分区、影响力分数筛选
/// - 支持批量操作（继承自 JpaRepository）
///
/// @author linqibin
/// @since 0.1.0
public interface VenueRatingDao extends JpaRepository<VenueRatingEntity, Long> {

  /// 根据载体 ID、年份、评价体系查询评级（业务唯一键）。
  ///
  /// @param venueId 载体 ID
  /// @param year 年份
  /// @param ratingSystem 评价体系
  /// @return 评级实体（可选）
  Optional<VenueRatingEntity> findByVenueIdAndYearAndRatingSystem(
      Long venueId, Short year, RatingSystem ratingSystem);

  /// 根据载体 ID 查询所有评级。
  ///
  /// @param venueId 载体 ID
  /// @return 评级实体列表
  List<VenueRatingEntity> findByVenueId(Long venueId);

  /// 根据载体 ID 和评价体系查询评级。
  ///
  /// @param venueId 载体 ID
  /// @param ratingSystem 评价体系
  /// @return 评级实体列表
  List<VenueRatingEntity> findByVenueIdAndRatingSystem(Long venueId, RatingSystem ratingSystem);

  /// 根据年份和评价体系查询评级。
  ///
  /// @param year 年份
  /// @param ratingSystem 评价体系
  /// @return 评级实体列表
  List<VenueRatingEntity> findByYearAndRatingSystem(Short year, RatingSystem ratingSystem);

  /// 检查是否存在指定的评级记录（业务唯一键）。
  ///
  /// @param venueId 载体 ID
  /// @param year 年份
  /// @param ratingSystem 评价体系
  /// @return true 如果存在
  boolean existsByVenueIdAndYearAndRatingSystem(
      Long venueId, Short year, RatingSystem ratingSystem);

  /// 删除载体的所有评级记录。
  ///
  /// @param venueId 载体 ID
  void deleteByVenueId(Long venueId);

  /// 检查表中是否有数据。
  ///
  /// @return true 如果有数据
  @Query("SELECT COUNT(r) > 0 FROM VenueRatingEntity r")
  boolean hasAnyData();

  /// 根据载体 ID 和年份查询评级。
  ///
  /// @param venueId 载体 ID
  /// @param year 年份
  /// @return 评级实体列表
  List<VenueRatingEntity> findByVenueIdAndYear(Long venueId, Short year);

  /// 根据多个载体 ID 查询评级。
  ///
  /// @param venueIds 载体 ID 列表
  /// @return 评级实体列表
  List<VenueRatingEntity> findByVenueIdIn(java.util.Collection<Long> venueIds);

  /// 根据评价体系和分区查询评级。
  ///
  /// @param ratingSystem 评价体系
  /// @param quartile 分区
  /// @return 评级实体列表
  List<VenueRatingEntity> findByRatingSystemAndQuartile(RatingSystem ratingSystem, String quartile);

  /// 查询载体最新年份的评级。
  ///
  /// @param venueId 载体 ID
  /// @param ratingSystem 评价体系
  /// @return 最新评级（可选）
  @Query(
      """
      SELECT r FROM VenueRatingEntity r
      WHERE r.venueId = :venueId AND r.ratingSystem = :ratingSystem
      ORDER BY r.year DESC
      LIMIT 1
      """)
  Optional<VenueRatingEntity> findLatestByVenueIdAndRatingSystem(
      @Param("venueId") Long venueId, @Param("ratingSystem") RatingSystem ratingSystem);
}
