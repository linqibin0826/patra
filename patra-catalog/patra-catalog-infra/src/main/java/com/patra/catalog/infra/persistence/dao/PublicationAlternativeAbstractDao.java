package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.PublicationAlternativeAbstractEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 文献翻译摘要 JPA Repository。
///
/// **职责**：
///
/// - 提供 PublicationAlternativeAbstractEntity 的 CRUD 操作
/// - 支持按 publicationId 批量查询和删除
/// - 支持按语言代码查询
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationAlternativeAbstractDao
    extends JpaRepository<PublicationAlternativeAbstractEntity, Long> {

  /// 根据文献 ID 查找所有翻译摘要。
  ///
  /// @param publicationId 文献 ID
  /// @return 翻译摘要实体列表
  List<PublicationAlternativeAbstractEntity> findByPublicationId(Long publicationId);

  /// 根据文献 ID 列表批量查找翻译摘要。
  ///
  /// @param publicationIds 文献 ID 列表
  /// @return 翻译摘要实体列表
  List<PublicationAlternativeAbstractEntity> findByPublicationIdIn(Collection<Long> publicationIds);

  /// 根据文献 ID 和语言代码查找翻译摘要列表。
  ///
  /// @param publicationId 文献 ID
  /// @param languageCode 语言代码
  /// @return 翻译摘要实体列表
  List<PublicationAlternativeAbstractEntity> findByPublicationIdAndLanguageCode(
      Long publicationId, String languageCode);

  /// 根据文献 ID、语言代码和来源类型查找翻译摘要。
  ///
  /// @param publicationId 文献 ID
  /// @param languageCode 语言代码
  /// @param sourceType 来源类型
  /// @return 翻译摘要实体
  Optional<PublicationAlternativeAbstractEntity> findByPublicationIdAndLanguageCodeAndSourceType(
      Long publicationId, String languageCode, String sourceType);

  /// 根据语言代码查找所有翻译摘要。
  ///
  /// @param languageCode 语言代码
  /// @return 翻译摘要实体列表
  List<PublicationAlternativeAbstractEntity> findByLanguageCode(String languageCode);

  /// 根据文献 ID 删除所有翻译摘要。
  ///
  /// @param publicationId 文献 ID
  /// @return 删除的记录数
  @Modifying
  int deleteByPublicationId(Long publicationId);

  /// 根据文献 ID 列表批量删除翻译摘要。
  ///
  /// @param publicationIds 文献 ID 列表
  /// @return 删除的记录数
  @Modifying
  @Query(
      "DELETE FROM PublicationAlternativeAbstractEntity e WHERE e.publicationId IN :publicationIds")
  int deleteByPublicationIdIn(@Param("publicationIds") Collection<Long> publicationIds);
}
