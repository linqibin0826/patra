package com.patra.catalog.infra.persistence.jpa;

import com.patra.catalog.infra.persistence.jpa.entity.AuthorEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 作者 JPA Repository。
///
/// **职责**：
///
/// - 提供作者实体的 CRUD 操作
/// - 支持按 ORCID、邮箱、去重键查询
/// - 支持批量操作（继承自 JpaRepository）
///
/// @author linqibin
/// @since 0.1.0
public interface AuthorJpaRepository extends JpaRepository<AuthorEntity, Long> {

  /// 根据 ORCID 查询作者。
  ///
  /// @param orcid ORCID 标识符
  /// @return 作者实体（可选）
  Optional<AuthorEntity> findByOrcid(String orcid);

  /// 根据邮箱查询作者。
  ///
  /// @param email 邮箱地址
  /// @return 作者实体列表（邮箱可能重复）
  List<AuthorEntity> findByEmail(String email);

  /// 根据去重键查询作者。
  ///
  /// @param dedupKey 去重键
  /// @return 作者实体列表
  List<AuthorEntity> findByDedupKey(String dedupKey);

  /// 检查 ORCID 是否已存在。
  ///
  /// @param orcid ORCID 标识符
  /// @return true 如果已存在
  boolean existsByOrcid(String orcid);

  /// 检查表中是否有数据。
  ///
  /// @return true 如果有数据
  @Query("SELECT COUNT(a) > 0 FROM AuthorEntity a")
  boolean hasAnyData();

  /// 根据姓名模糊查询作者。
  ///
  /// @param lastName 姓氏（模糊匹配）
  /// @return 作者实体列表
  @Query("SELECT a FROM AuthorEntity a WHERE a.name.lastName LIKE %:lastName%")
  List<AuthorEntity> findByLastNameContaining(@Param("lastName") String lastName);

  /// 查询有效的作者。
  ///
  /// @return 有效作者列表
  List<AuthorEntity> findByValidTrue();

  /// 根据 Researcher ID 查询作者。
  ///
  /// @param researcherId Researcher ID
  /// @return 作者实体（可选）
  Optional<AuthorEntity> findByResearcherId(String researcherId);

  /// 根据 Scopus ID 查询作者。
  ///
  /// @param scopusId Scopus ID
  /// @return 作者实体（可选）
  Optional<AuthorEntity> findByScopusId(String scopusId);
}
