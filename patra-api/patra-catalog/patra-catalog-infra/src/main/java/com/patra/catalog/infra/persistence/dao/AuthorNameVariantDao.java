package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.AuthorNameVariantEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 作者名字变体 JPA Repository。
///
/// **职责**：
///
/// - 提供名字变体实体的 CRUD 操作
/// - 支持按作者 ID、姓氏查询
/// - 支持批量删除操作
///
/// @author linqibin
/// @since 0.1.0
public interface AuthorNameVariantDao extends JpaRepository<AuthorNameVariantEntity, Long> {

  /// 根据作者 ID 查询所有名字变体。
  ///
  /// @param authorId 作者 ID
  /// @return 名字变体列表
  List<AuthorNameVariantEntity> findByAuthorId(Long authorId);

  /// 根据姓氏模糊查询名字变体。
  ///
  /// @param lastName 姓氏（模糊匹配）
  /// @param pageable 分页参数
  /// @return 名字变体分页结果
  Page<AuthorNameVariantEntity> findByLastNameContainingIgnoreCase(
      String lastName, Pageable pageable);

  /// 根据作者 ID 删除所有名字变体。
  ///
  /// @param authorId 作者 ID
  @Modifying
  @Query("DELETE FROM AuthorNameVariantEntity v WHERE v.author.id = :authorId")
  void deleteByAuthorId(@Param("authorId") Long authorId);

  /// 根据作者 ID 统计名字变体数量。
  ///
  /// @param authorId 作者 ID
  /// @return 名字变体数量
  long countByAuthorId(Long authorId);
}
