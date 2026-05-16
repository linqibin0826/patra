package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationMetadataEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 文献元数据 JPA Repository。
///
/// **职责**：
///
/// - 提供 PublicationMetadataEntity 的 CRUD 操作
/// - 支持按 publicationId 查询和删除（1:1 关系）
/// - 支持按索引状态、数据来源查询
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationMetadataDao extends JpaRepository<PublicationMetadataEntity, Long> {

  /// 根据文献 ID 查找元数据。
  ///
  /// @param publicationId 文献 ID
  /// @return 元数据实体
  Optional<PublicationMetadataEntity> findByPublicationId(Long publicationId);

  /// 根据文献 ID 列表批量查找元数据。
  ///
  /// @param publicationIds 文献 ID 列表
  /// @return 元数据实体列表
  List<PublicationMetadataEntity> findByPublicationIdIn(Collection<Long> publicationIds);

  /// 根据索引状态查找元数据。
  ///
  /// @param indexingStatus 索引状态
  /// @return 元数据实体列表
  List<PublicationMetadataEntity> findByIndexingStatus(String indexingStatus);

  /// 根据数据来源查找元数据。
  ///
  /// @param dataSource 数据来源
  /// @return 元数据实体列表
  List<PublicationMetadataEntity> findByDataSource(String dataSource);

  /// 根据导入批次查找元数据。
  ///
  /// @param importBatch 导入批次
  /// @return 元数据实体列表
  List<PublicationMetadataEntity> findByImportBatch(String importBatch);

  /// 根据文献 ID 删除元数据。
  ///
  /// @param publicationId 文献 ID
  /// @return 删除的记录数
  @Modifying
  int deleteByPublicationId(Long publicationId);

  /// 根据文献 ID 列表批量删除元数据。
  ///
  /// @param publicationIds 文献 ID 列表
  /// @return 删除的记录数
  @Modifying
  @Query("DELETE FROM PublicationMetadataEntity e WHERE e.publicationId IN :publicationIds")
  int deleteByPublicationIdIn(@Param("publicationIds") Collection<Long> publicationIds);
}
