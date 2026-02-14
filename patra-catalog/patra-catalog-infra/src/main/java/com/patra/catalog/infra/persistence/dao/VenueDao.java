package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.VenueEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 出版载体 JPA Repository。
///
/// **职责**：
///
/// - 提供 VenueEntity 的 CRUD 操作
/// - 支持各种查询方式（ISSN-L、NLM ID、OpenAlex ID 等）
///
/// @author linqibin
/// @since 0.1.0
public interface VenueDao extends JpaRepository<VenueEntity, Long> {

  /// 检查是否存在任何数据。
  ///
  /// @return 如果存在数据则返回 true
  @Query("SELECT COUNT(v) > 0 FROM VenueEntity v")
  boolean hasAnyData();

  /// 根据 ISSN-L 查找载体。
  ///
  /// @param issnL Linking ISSN
  /// @return 载体实体
  Optional<VenueEntity> findByIssnL(String issnL);

  /// 根据 ISSN-L 列表批量查找载体。
  ///
  /// @param issnLs ISSN-L 列表
  /// @return 载体实体列表
  List<VenueEntity> findByIssnLIn(Collection<String> issnLs);

  /// 根据 NLM ID 查找载体。
  ///
  /// @param nlmId NLM 唯一标识符
  /// @return 载体实体
  Optional<VenueEntity> findByNlmId(String nlmId);

  /// 根据 NLM ID 列表批量查找载体。
  ///
  /// @param nlmIds NLM ID 列表
  /// @return 载体实体列表
  List<VenueEntity> findByNlmIdIn(Collection<String> nlmIds);

  /// 根据 OpenAlex ID 查找载体。
  ///
  /// @param openalexId OpenAlex Source ID
  /// @return 载体实体
  Optional<VenueEntity> findByOpenalexId(String openalexId);

  /// 批量查询已存在的 ISSN-L。
  ///
  /// @param issnLs ISSN-L 列表
  /// @return 数据库中已存在的 ISSN-L 列表
  @Query("SELECT v.issnL FROM VenueEntity v WHERE v.issnL IN :issnLs")
  List<String> findExistingIssnLs(@Param("issnLs") Collection<String> issnLs);

  /// 根据 ID 列表批量查找载体。
  ///
  /// @param ids 载体 ID 列表
  /// @return 载体实体列表
  List<VenueEntity> findByIdIn(Collection<Long> ids);

  /// 分页查询期刊列表。
  ///
  /// 查询条件：
  ///
  /// - 固定 `venueType=JOURNAL`
  /// - 关键词为空时返回全部期刊
  /// - 关键词非空时按 `displayName` 前缀匹配，或 `issnL` / `nlmId` 精确匹配
  ///
  /// @param keyword 关键词（可空），同时用于名称前缀匹配和 ISSN-L/NLM ID 精确匹配
  /// @param pageable 分页参数
  /// @return 期刊分页结果
  @Query(
      """
      SELECT v FROM VenueEntity v
      WHERE v.venueType = 'JOURNAL'
        AND (
          :keyword IS NULL
          OR LOWER(v.displayName) LIKE LOWER(CONCAT(:keyword, '%'))
          OR v.issnL = :keyword
          OR v.nlmId = :keyword
        )
      """)
  Page<VenueEntity> findJournalPage(@Param("keyword") String keyword, Pageable pageable);
}
