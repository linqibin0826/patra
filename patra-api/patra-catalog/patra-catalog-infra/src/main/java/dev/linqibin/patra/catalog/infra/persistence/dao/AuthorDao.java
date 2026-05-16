package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.AuthorEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 作者 JPA Repository。
///
/// **职责**：
///
/// - 提供作者实体的 CRUD 操作
/// - 支持按姓名规范化格式、状态、数据来源查询
/// - 支持批量操作（继承自 JpaRepository）
///
/// **设计说明**：
///
/// - 适配 PubMed Computed Authors 数据源
/// - `normalizedKey` 是姓名规范化格式（非唯一），查询可能返回多条记录
/// - ORCID 全局唯一，查询返回单条记录
///
/// @author linqibin
/// @since 0.1.0
public interface AuthorDao extends JpaRepository<AuthorEntity, Long> {

  // ========== 姓名规范化格式查询 ==========

  /// 根据姓名规范化格式查询作者。
  ///
  /// 同一格式下可能有多个不同的已消歧作者。
  ///
  /// @param normalizedKey 姓名规范化格式（如 "SMITH+R"）
  /// @return 匹配的作者列表（可能为空）
  List<AuthorEntity> findByNormalizedKey(String normalizedKey);

  /// 检查姓名规范化格式下是否有作者。
  ///
  /// @param normalizedKey 姓名规范化格式
  /// @return true 如果有匹配的作者
  boolean existsByNormalizedKey(String normalizedKey);

  /// 批量根据姓名规范化格式查询作者。
  ///
  /// @param normalizedKeys 姓名规范化格式集合
  /// @return 作者实体列表
  List<AuthorEntity> findByNormalizedKeyIn(Collection<String> normalizedKeys);

  // ========== 状态查询 ==========

  /// 根据状态查询作者（分页）。
  ///
  /// @param status 状态（ACTIVE/MERGED/INACTIVE）
  /// @param pageable 分页参数
  /// @return 作者分页结果
  Page<AuthorEntity> findByStatus(String status, Pageable pageable);

  /// 查询活跃状态的作者（分页）。
  ///
  /// @param pageable 分页参数
  /// @return 活跃作者分页结果
  @Query("SELECT a FROM AuthorEntity a WHERE a.status = 'ACTIVE'")
  Page<AuthorEntity> findActiveAuthors(Pageable pageable);

  // ========== 数据来源查询 ==========

  /// 根据数据来源代码查询作者（分页）。
  ///
  /// @param provenanceCode 数据来源代码（PUBMED/ORCID/OPENALEX/MANUAL）
  /// @param pageable 分页参数
  /// @return 作者分页结果
  Page<AuthorEntity> findByProvenanceCode(String provenanceCode, Pageable pageable);

  // ========== 名称搜索 ==========

  /// 根据展示名称模糊查询作者。
  ///
  /// @param displayName 展示名称（模糊匹配）
  /// @param pageable 分页参数
  /// @return 作者分页结果
  Page<AuthorEntity> findByDisplayNameContainingIgnoreCase(String displayName, Pageable pageable);

  // ========== 统计查询 ==========

  /// 检查表中是否有数据。
  ///
  /// @return true 如果有数据
  @Query("SELECT COUNT(a) > 0 FROM AuthorEntity a")
  boolean hasAnyData();

  /// 统计各状态的作者数量。
  ///
  /// @param status 状态
  /// @return 数量
  long countByStatus(String status);

  /// 统计各数据来源的作者数量。
  ///
  /// @param provenanceCode 数据来源代码
  /// @return 数量
  long countByProvenanceCode(String provenanceCode);

  // ========== 关联查询 ==========

  /// 根据 ORCID 查询作者（通过子表关联）。
  ///
  /// @param orcid ORCID 标识符
  /// @return 作者实体（可选）
  @Query("SELECT a FROM AuthorEntity a JOIN a.orcids o WHERE o.orcid = :orcid")
  Optional<AuthorEntity> findByOrcid(@Param("orcid") String orcid);

  /// 检查 ORCID 是否已存在（通过子表关联）。
  ///
  /// @param orcid ORCID 标识符
  /// @return true 如果已存在
  @Query("SELECT COUNT(a) > 0 FROM AuthorEntity a JOIN a.orcids o WHERE o.orcid = :orcid")
  boolean existsByOrcid(@Param("orcid") String orcid);

  /// 批量查询已存在的 ORCID（通过子表关联）。
  ///
  /// 用于批量导入时的去重检查，一次查询替代 N 次单条查询。
  ///
  /// @param orcids 待检查的 ORCID 集合
  /// @return 数据库中已存在的 ORCID 列表
  @Query("SELECT o.orcid FROM AuthorOrcidEntity o WHERE o.orcid IN :orcids")
  List<String> findExistingOrcids(@Param("orcids") Collection<String> orcids);

  /// 批量查询通过任一 ORCID 匹配的作者（通过子表关联）。
  ///
  /// 用于批量导入时检测跨批次 ORCID 重复，并获取已存在的作者以便合并名字变体。
  ///
  /// **性能优化**：
  ///
  /// - 使用 `JOIN FETCH a.orcids` 和 `LEFT JOIN FETCH a.nameVariants` 一次性加载所有子集合
  /// - 避免 N+1 问题（Entity 集合使用 Set 类型支持多集合同时 FETCH）
  /// - 使用子查询确保 DISTINCT 正确工作
  ///
  /// @param orcids 待检查的 ORCID 集合
  /// @return 匹配的作者实体列表（不重复）
  @Query(
      """
      SELECT DISTINCT a FROM AuthorEntity a
      JOIN FETCH a.orcids
      LEFT JOIN FETCH a.nameVariants
      WHERE a.id IN (
          SELECT DISTINCT ao.author.id FROM AuthorOrcidEntity ao WHERE ao.orcid IN :orcids
      )
      """)
  List<AuthorEntity> findByOrcidIn(@Param("orcids") Collection<String> orcids);
}
