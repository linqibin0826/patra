package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationIdentifierEntity;
import dev.linqibin.patra.common.model.enums.PublicationIdentifierType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 文献标识符 JPA Repository。
///
/// **职责**：
///
/// - 提供 PublicationIdentifierEntity 的 CRUD 操作
/// - 支持按 publicationId 批量查询和删除
/// - 支持按标识符类型和值反查文献
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationIdentifierDao extends JpaRepository<PublicationIdentifierEntity, Long> {

  /// 根据文献 ID 查找所有标识符。
  ///
  /// @param publicationId 文献 ID
  /// @return 标识符实体列表
  List<PublicationIdentifierEntity> findByPublicationId(Long publicationId);

  /// 根据文献 ID 列表批量查找标识符。
  ///
  /// @param publicationIds 文献 ID 列表
  /// @return 标识符实体列表
  List<PublicationIdentifierEntity> findByPublicationIdIn(Collection<Long> publicationIds);

  /// 根据标识符类型和值查找标识符实体。
  ///
  /// 用于按 PMID/DOI 反查文献。
  ///
  /// @param type 标识符类型枚举（如 PMID、DOI）
  /// @param values 标识符值列表
  /// @return 匹配的标识符实体列表
  List<PublicationIdentifierEntity> findByTypeAndValueIn(
      PublicationIdentifierType type, Collection<String> values);

  /// 根据文献 ID 删除所有标识符。
  ///
  /// @param publicationId 文献 ID
  /// @return 删除的记录数
  @Modifying
  int deleteByPublicationId(Long publicationId);

  /// 根据文献 ID 列表批量删除标识符。
  ///
  /// @param publicationIds 文献 ID 列表
  /// @return 删除的记录数
  @Modifying
  @Query("DELETE FROM PublicationIdentifierEntity e WHERE e.publicationId IN :publicationIds")
  int deleteByPublicationIdIn(@Param("publicationIds") Collection<Long> publicationIds);
}
