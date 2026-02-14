package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.VenueMeshEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 载体 MeSH 主题词 JPA Repository。
///
/// **职责**：
///
/// - 提供 VenueMeshEntity 的 CRUD 操作
/// - 支持按 venueId 批量查询和删除
/// - 支持按 MeSH 描述符 UI 反查载体
///
/// @author linqibin
/// @since 0.1.0
public interface VenueMeshDao extends JpaRepository<VenueMeshEntity, Long> {

  /// 根据载体 ID 查找所有 MeSH 主题词。
  ///
  /// @param venueId 载体 ID
  /// @return MeSH 主题词实体列表
  List<VenueMeshEntity> findByVenueId(Long venueId);

  /// 根据载体 ID 列表批量查找 MeSH 主题词。
  ///
  /// @param venueIds 载体 ID 列表
  /// @return MeSH 主题词实体列表
  List<VenueMeshEntity> findByVenueIdIn(Collection<Long> venueIds);

  /// 根据载体 ID 删除所有 MeSH 主题词。
  ///
  /// @param venueId 载体 ID
  /// @return 删除的记录数
  @Modifying
  int deleteByVenueId(Long venueId);

  /// 根据载体 ID 列表批量删除 MeSH 主题词。
  ///
  /// @param venueIds 载体 ID 列表
  /// @return 删除的记录数
  @Modifying
  @Query("DELETE FROM VenueMeshEntity e WHERE e.venueId IN :venueIds")
  int deleteByVenueIdIn(@Param("venueIds") Collection<Long> venueIds);
}
