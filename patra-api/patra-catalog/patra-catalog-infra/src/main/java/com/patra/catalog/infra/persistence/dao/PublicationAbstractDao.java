package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.PublicationAbstractEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 文献摘要 JPA Repository。
///
/// **职责**：
///
/// - 提供 PublicationAbstractEntity 的 CRUD 操作
/// - 支持按 publicationId 查询和删除（1:1 关系）
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationAbstractDao extends JpaRepository<PublicationAbstractEntity, Long> {

  /// 根据文献 ID 查找摘要。
  ///
  /// @param publicationId 文献 ID
  /// @return 摘要实体
  Optional<PublicationAbstractEntity> findByPublicationId(Long publicationId);

  /// 根据文献 ID 列表批量查找摘要。
  ///
  /// @param publicationIds 文献 ID 列表
  /// @return 摘要实体列表
  List<PublicationAbstractEntity> findByPublicationIdIn(Collection<Long> publicationIds);

  /// 根据文献 ID 删除摘要。
  ///
  /// @param publicationId 文献 ID
  /// @return 删除的记录数
  @Modifying
  int deleteByPublicationId(Long publicationId);

  /// 根据文献 ID 列表批量删除摘要。
  ///
  /// @param publicationIds 文献 ID 列表
  /// @return 删除的记录数
  @Modifying
  @Query("DELETE FROM PublicationAbstractEntity e WHERE e.publicationId IN :publicationIds")
  int deleteByPublicationIdIn(@Param("publicationIds") Collection<Long> publicationIds);
}
