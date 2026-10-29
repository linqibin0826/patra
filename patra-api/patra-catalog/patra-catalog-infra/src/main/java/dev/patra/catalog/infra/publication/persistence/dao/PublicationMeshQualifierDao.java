package dev.patra.catalog.infra.publication.persistence.dao;

import dev.patra.catalog.infra.publication.persistence.entity.PublicationMeshQualifierEntity;
import java.util.Collection;
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

  /// 按文献 ID 批量删除这些文献的 MeSH 标引下的全部限定词。
  ///
  /// 限定词表只关联标引 ID，需经 `cat_publication_mesh_heading` 找到文献，必须先于标引删除。
  ///
  /// @param publicationIds 文献 ID
  /// @return 删除行数
  @Modifying
  @Query(
      value =
          "DELETE FROM cat_publication_mesh_qualifier q USING cat_publication_mesh_heading h"
              + " WHERE q.publication_mesh_heading_id = h.id AND h.publication_id IN (:publicationIds)",
      nativeQuery = true)
  int deleteByPublicationIdIn(@Param("publicationIds") Collection<Long> publicationIds);
}
