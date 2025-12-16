package com.patra.catalog.infra.persistence.jpa;

import com.patra.catalog.infra.persistence.jpa.entity.VenueIndexingHistoryEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 载体索引历史 JPA Repository。
///
/// **职责**：
///
/// - 提供 VenueIndexingHistoryEntity 的 CRUD 操作
/// - 支持按 venueId 批量查询和删除
/// - 支持按索引来源和状态筛选
///
/// @author linqibin
/// @since 0.1.0
public interface VenueIndexingHistoryJpaRepository
    extends JpaRepository<VenueIndexingHistoryEntity, Long> {

  /// 根据载体 ID 查找所有索引历史。
  ///
  /// @param venueId 载体 ID
  /// @return 索引历史实体列表
  List<VenueIndexingHistoryEntity> findByVenueId(Long venueId);

  /// 根据载体 ID 列表批量查找索引历史。
  ///
  /// @param venueIds 载体 ID 列表
  /// @return 索引历史实体列表
  List<VenueIndexingHistoryEntity> findByVenueIdIn(Collection<Long> venueIds);

  /// 根据载体 ID 删除所有索引历史。
  ///
  /// @param venueId 载体 ID
  /// @return 删除的记录数
  @Modifying
  int deleteByVenueId(Long venueId);

  /// 根据载体 ID 列表批量删除索引历史。
  ///
  /// @param venueIds 载体 ID 列表
  /// @return 删除的记录数
  @Modifying
  @Query("DELETE FROM VenueIndexingHistoryEntity e WHERE e.venueId IN :venueIds")
  int deleteByVenueIdIn(@Param("venueIds") Collection<Long> venueIds);
}
