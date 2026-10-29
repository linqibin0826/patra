package dev.patra.catalog.infra.publication.persistence.dao;

import dev.patra.catalog.infra.publication.persistence.entity.PublicationOaLocationEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 文献开放获取位置 JPA Repository。
///
/// **职责**：
///
/// - 提供 PublicationOaLocationEntity 的 CRUD 操作
/// - 支持按 publicationId 批量查询和删除
/// - 支持按 OA 状态、位置类型查询
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationOaLocationDao extends JpaRepository<PublicationOaLocationEntity, Long> {

  /// 根据文献 ID 查找所有 OA 位置。
  ///
  /// @param publicationId 文献 ID
  /// @return OA 位置实体列表
  List<PublicationOaLocationEntity> findByPublicationId(Long publicationId);

  /// 根据文献 ID 列表批量查找 OA 位置。
  ///
  /// @param publicationIds 文献 ID 列表
  /// @return OA 位置实体列表
  List<PublicationOaLocationEntity> findByPublicationIdIn(Collection<Long> publicationIds);

  /// 根据文献 ID 查找最佳 OA 位置。
  ///
  /// @param publicationId 文献 ID
  /// @return 最佳 OA 位置实体
  Optional<PublicationOaLocationEntity> findByPublicationIdAndIsBestTrue(Long publicationId);

  /// 根据 OA 状态查找所有 OA 位置。
  ///
  /// @param oaStatus OA 状态
  /// @return OA 位置实体列表
  List<PublicationOaLocationEntity> findByOaStatus(String oaStatus);

  /// 根据位置类型查找所有 OA 位置。
  ///
  /// @param locationType 位置类型
  /// @return OA 位置实体列表
  List<PublicationOaLocationEntity> findByLocationType(String locationType);

  /// 根据文献 ID 删除所有 OA 位置。
  ///
  /// @param publicationId 文献 ID
  /// @return 删除的记录数
  @Modifying
  int deleteByPublicationId(Long publicationId);

  /// 根据文献 ID 列表批量删除 OA 位置。
  ///
  /// @param publicationIds 文献 ID 列表
  /// @return 删除的记录数
  @Modifying
  @Query("DELETE FROM PublicationOaLocationEntity e WHERE e.publicationId IN :publicationIds")
  int deleteByPublicationIdIn(@Param("publicationIds") Collection<Long> publicationIds);

  /// 按文献与位置类型批量删除（JPQL 批量语句立即执行，不像派生删除那样在 flush 时排到插入之后）。
  ///
  /// @param publicationId 文献 ID
  /// @param locationType 位置类型的存储代码，如 `pubmed_central`
  /// @return 删除行数
  @Modifying
  @Query(
      "DELETE FROM PublicationOaLocationEntity e"
          + " WHERE e.publicationId = :publicationId AND e.locationType = :locationType")
  int deleteByPublicationIdAndLocationType(
      @Param("publicationId") Long publicationId, @Param("locationType") String locationType);
}
