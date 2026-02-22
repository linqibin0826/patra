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
/// - 支持各种查询方式（ISSN-L、NLM ID 等）
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
  /// 查询条件（AND 关系，空值忽略）：
  ///
  /// - 固定 `venueType=JOURNAL`
  /// - `keyword`：title 或 titleZh 前缀模糊匹配
  /// - `countryCode`：国家编码精确匹配
  /// - `issnL`：ISSN-L 精确匹配
  /// - `nlmId`：NLM ID 精确匹配
  ///
  /// @param keyword title/titleZh 前缀搜索关键词（可空）
  /// @param countryCode 国家编码（可空）
  /// @param issnL ISSN-L（可空）
  /// @param nlmId NLM ID（可空）
  /// @param pageable 分页参数
  /// @return 期刊分页结果
  @Query(
      """
      SELECT v FROM VenueEntity v
      WHERE v.venueType = 'JOURNAL'
        AND (:keyword IS NULL
             OR LOWER(v.title) LIKE LOWER(CONCAT(:keyword, '%'))
             OR LOWER(v.titleZh) LIKE LOWER(CONCAT(:keyword, '%')))
        AND (:countryCode IS NULL OR v.countryCode = :countryCode)
        AND (:issnL IS NULL OR v.issnL = :issnL)
        AND (:nlmId IS NULL OR v.nlmId = :nlmId)
      """)
  Page<VenueEntity> findJournalPage(
      @Param("keyword") String keyword,
      @Param("countryCode") String countryCode,
      @Param("issnL") String issnL,
      @Param("nlmId") String nlmId,
      Pageable pageable);
}
