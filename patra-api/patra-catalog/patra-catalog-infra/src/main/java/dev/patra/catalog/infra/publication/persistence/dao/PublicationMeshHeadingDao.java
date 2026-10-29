package dev.patra.catalog.infra.publication.persistence.dao;

import dev.patra.catalog.infra.publication.persistence.entity.PublicationMeshHeadingEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 文献-MeSH 标引关联 DAO。
///
/// 提供文献 MeSH 标引数据的持久化操作。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationMeshHeadingDao
    extends JpaRepository<PublicationMeshHeadingEntity, Long> {

  /// 根据出版物 ID 查询所有 MeSH 标引。
  ///
  /// @param publicationId 出版物 ID
  /// @return MeSH 标引列表
  List<PublicationMeshHeadingEntity> findByPublicationId(Long publicationId);

  /// 根据出版物 ID 列表批量查询 MeSH 标引。
  ///
  /// @param publicationIds 出版物 ID 列表
  /// @return MeSH 标引列表
  List<PublicationMeshHeadingEntity> findByPublicationIdIn(List<Long> publicationIds);

  /// 根据出版物 ID 删除所有 MeSH 标引。
  ///
  /// @param publicationId 出版物 ID
  @Modifying
  @Query("DELETE FROM PublicationMeshHeadingEntity e WHERE e.publicationId = :publicationId")
  void deleteByPublicationId(@Param("publicationId") Long publicationId);

  /// 检查出版物是否有 MeSH 标引。
  ///
  /// @param publicationId 出版物 ID
  /// @return 如果存在标引返回 true
  boolean existsByPublicationId(Long publicationId);

  /// 按文献 ID 批量删除（增量同步覆盖更新时整批替换）。
  ///
  /// @param publicationIds 文献 ID
  /// @return 删除行数
  @Modifying
  @Query("DELETE FROM PublicationMeshHeadingEntity e WHERE e.publicationId IN :publicationIds")
  int deleteByPublicationIdIn(@Param("publicationIds") Collection<Long> publicationIds);
}
