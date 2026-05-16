package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.VenuePublicationStatsEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 载体年度发文统计 JPA Repository。
///
/// **职责**：
///
/// - 提供 VenuePublicationStatsEntity 的 CRUD 操作
/// - 支持按 venueId 批量查询和删除
/// - 支持按年份查询和统计
///
/// @author linqibin
/// @since 0.1.0
public interface VenuePublicationStatsDao extends JpaRepository<VenuePublicationStatsEntity, Long> {

  /// 根据载体 ID 查找所有年度统计。
  ///
  /// @param venueId 载体 ID
  /// @return 年度统计实体列表
  List<VenuePublicationStatsEntity> findByVenueId(Long venueId);

  /// 根据载体 ID 列表批量查找年度统计。
  ///
  /// @param venueIds 载体 ID 列表
  /// @return 年度统计实体列表
  List<VenuePublicationStatsEntity> findByVenueIdIn(Collection<Long> venueIds);

  /// 根据载体 ID 删除所有年度统计。
  ///
  /// @param venueId 载体 ID
  /// @return 删除的记录数
  @Modifying
  int deleteByVenueId(Long venueId);

  /// 根据载体 ID 列表批量删除年度统计。
  ///
  /// @param venueIds 载体 ID 列表
  /// @return 删除的记录数
  @Modifying
  @Query("DELETE FROM VenuePublicationStatsEntity e WHERE e.venueId IN :venueIds")
  int deleteByVenueIdIn(@Param("venueIds") Collection<Long> venueIds);
}
