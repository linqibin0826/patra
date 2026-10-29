package dev.patra.catalog.infra.author.persistence.dao;

import dev.patra.catalog.infra.author.persistence.entity.AuthorOrcidEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 作者 ORCID JPA Repository。
///
/// **职责**：
///
/// - 提供 ORCID 实体的 CRUD 操作
/// - 支持按 ORCID、作者 ID 查询
/// - 支持批量删除操作
///
/// @author linqibin
/// @since 0.1.0
public interface AuthorOrcidDao extends JpaRepository<AuthorOrcidEntity, Long> {

  /// 根据 ORCID 查询实体。
  ///
  /// @param orcid ORCID 标识符
  /// @return ORCID 实体（可选）
  Optional<AuthorOrcidEntity> findByOrcid(String orcid);

  /// 检查 ORCID 是否已存在。
  ///
  /// @param orcid ORCID 标识符
  /// @return true 如果已存在
  boolean existsByOrcid(String orcid);

  /// 根据作者 ID 查询所有 ORCID。
  ///
  /// @param authorId 作者 ID
  /// @return ORCID 实体列表
  List<AuthorOrcidEntity> findByAuthorId(Long authorId);

  /// 根据作者 ID 删除所有 ORCID。
  ///
  /// @param authorId 作者 ID
  @Modifying
  @Query("DELETE FROM AuthorOrcidEntity o WHERE o.author.id = :authorId")
  void deleteByAuthorId(@Param("authorId") Long authorId);

  /// 根据作者 ID 统计 ORCID 数量。
  ///
  /// @param authorId 作者 ID
  /// @return ORCID 数量
  long countByAuthorId(Long authorId);

  /// 查询作者的主要 ORCID。
  ///
  /// @param authorId 作者 ID
  /// @return 主要 ORCID 实体（可选）
  @Query("SELECT o FROM AuthorOrcidEntity o WHERE o.author.id = :authorId AND o.primary = true")
  Optional<AuthorOrcidEntity> findPrimaryByAuthorId(@Param("authorId") Long authorId);

  /// 批量查询 orcid → authorId 投影（仅 ACTIVE 且未软删的作者可被软关联）。
  ///
  /// 软删除条件无需显式书写：`AuthorEntity` 继承 `SoftDeletableJpaEntity`，
  /// Hibernate `@SoftDelete` 会为 `cat_author` 的表引用自动追加 `deleted_at IS NULL`。
  ///
  /// @param orcids 归一化 ORCID 集合；调用方须保证单次规模有界（PG 绑定参数上限 65535），
  ///     超大 chunk 须自行分片
  /// @return 命中的投影列表；未命中的 ORCID 不产生行；`uk_orcid` 全局唯一保证每个 ORCID 至多一行
  @Query(
      """
      SELECT o.orcid AS orcid, a.id AS authorId
      FROM AuthorOrcidEntity o JOIN o.author a
      WHERE o.orcid IN :orcids AND a.status = 'ACTIVE'
      """)
  List<OrcidAuthorIdView> findAuthorIdsByOrcidIn(@Param("orcids") Collection<String> orcids);
}
