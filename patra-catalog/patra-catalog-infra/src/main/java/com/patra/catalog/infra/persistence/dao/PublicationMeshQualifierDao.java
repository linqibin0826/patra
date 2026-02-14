package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.PublicationMeshQualifierEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 文献-MeSH 限定词关联 DAO。
///
/// 提供文献 MeSH 限定词数据的持久化操作。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationMeshQualifierDao
    extends JpaRepository<PublicationMeshQualifierEntity, Long> {

  /// 根据标引 ID 查询所有限定词。
  ///
  /// @param headingId 文献 MeSH 标引 ID
  /// @return 限定词列表
  List<PublicationMeshQualifierEntity> findByPublicationMeshHeadingId(Long headingId);

  /// 根据标引 ID 列表批量查询限定词。
  ///
  /// @param headingIds 文献 MeSH 标引 ID 列表
  /// @return 限定词列表
  List<PublicationMeshQualifierEntity> findByPublicationMeshHeadingIdIn(List<Long> headingIds);

  /// 根据标引 ID 删除所有限定词。
  ///
  /// @param headingId 文献 MeSH 标引 ID
  @Modifying
  @Query(
      "DELETE FROM PublicationMeshQualifierEntity e WHERE e.publicationMeshHeadingId = :headingId")
  void deleteByPublicationMeshHeadingId(@Param("headingId") Long headingId);

  /// 根据标引 ID 列表批量删除限定词。
  ///
  /// @param headingIds 文献 MeSH 标引 ID 列表
  @Modifying
  @Query(
      "DELETE FROM PublicationMeshQualifierEntity e WHERE e.publicationMeshHeadingId IN :headingIds")
  void deleteByPublicationMeshHeadingIdIn(@Param("headingIds") List<Long> headingIds);
}
