package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationDateEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 文献日期 JPA Repository。
///
/// **职责**：
///
/// - 提供 PublicationDateEntity 的 CRUD 操作
/// - 支持按 publicationId 批量查询和删除
/// - 支持按日期类型查询
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationDateDao extends JpaRepository<PublicationDateEntity, Long> {

  /// 根据文献 ID 查找所有日期。
  ///
  /// @param publicationId 文献 ID
  /// @return 日期实体列表
  List<PublicationDateEntity> findByPublicationId(Long publicationId);

  /// 根据文献 ID 列表批量查找日期。
  ///
  /// @param publicationIds 文献 ID 列表
  /// @return 日期实体列表
  List<PublicationDateEntity> findByPublicationIdIn(Collection<Long> publicationIds);

  /// 根据文献 ID 和日期类型查找日期。
  ///
  /// @param publicationId 文献 ID
  /// @param dateType 日期类型
  /// @return 日期实体列表
  List<PublicationDateEntity> findByPublicationIdAndDateType(Long publicationId, String dateType);

  /// 根据文献 ID 删除所有日期。
  ///
  /// @param publicationId 文献 ID
  /// @return 删除的记录数
  @Modifying
  int deleteByPublicationId(Long publicationId);

  /// 根据文献 ID 列表批量删除日期。
  ///
  /// @param publicationIds 文献 ID 列表
  /// @return 删除的记录数
  @Modifying
  @Query("DELETE FROM PublicationDateEntity e WHERE e.publicationId IN :publicationIds")
  int deleteByPublicationIdIn(@Param("publicationIds") Collection<Long> publicationIds);
}
