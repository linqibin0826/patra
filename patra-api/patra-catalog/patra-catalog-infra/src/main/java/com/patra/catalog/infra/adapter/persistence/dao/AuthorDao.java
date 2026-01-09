package com.patra.catalog.infra.adapter.persistence.dao;

import com.patra.catalog.infra.adapter.persistence.entity.AuthorEntity;
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
/// - 支持按业务键（normalizedKey）、状态、数据来源查询
/// - 支持批量操作（继承自 JpaRepository）
///
/// **设计说明**：
///
/// - 适配 PubMed Computed Authors 数据源
/// - 使用 `normalizedKey` 作为业务键查询
///
/// @author linqibin
/// @since 0.1.0
public interface AuthorDao extends JpaRepository<AuthorEntity, Long> {

  // ========== 业务键查询 ==========

  /// 根据规范化标识（业务键）查询作者。
  ///
  /// @param normalizedKey 规范化标识（如 "Lu+Z"）
  /// @return 作者实体（可选）
  Optional<AuthorEntity> findByNormalizedKey(String normalizedKey);

  /// 检查规范化标识是否已存在。
  ///
  /// @param normalizedKey 规范化标识
  /// @return true 如果已存在
  boolean existsByNormalizedKey(String normalizedKey);

  /// 批量根据规范化标识查询作者。
  ///
  /// @param normalizedKeys 规范化标识集合
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
}
