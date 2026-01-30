package com.patra.catalog.infra.adapter.persistence.dao;

import com.patra.catalog.infra.adapter.persistence.entity.PublicationSupplMeshEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 文献-补充 MeSH 概念关联 DAO。
///
/// 提供文献与 SCR（补充概念记录）关联数据的持久化操作。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationSupplMeshDao extends JpaRepository<PublicationSupplMeshEntity, Long> {

  /// 根据出版物 ID 查询所有补充 MeSH 概念。
  ///
  /// @param publicationId 出版物 ID
  /// @return 补充 MeSH 概念列表
  List<PublicationSupplMeshEntity> findByPublicationId(Long publicationId);

  /// 根据出版物 ID 列表批量查询补充 MeSH 概念。
  ///
  /// @param publicationIds 出版物 ID 列表
  /// @return 补充 MeSH 概念列表
  List<PublicationSupplMeshEntity> findByPublicationIdIn(List<Long> publicationIds);

  /// 根据出版物 ID 删除所有补充 MeSH 概念。
  ///
  /// @param publicationId 出版物 ID
  @Modifying
  @Query("DELETE FROM PublicationSupplMeshEntity e WHERE e.publicationId = :publicationId")
  void deleteByPublicationId(@Param("publicationId") Long publicationId);

  /// 检查出版物是否有补充 MeSH 概念。
  ///
  /// @param publicationId 出版物 ID
  /// @return 如果存在返回 true
  boolean existsByPublicationId(Long publicationId);
}
