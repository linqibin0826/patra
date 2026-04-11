package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.VenueEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

  /// 仅更新 `cat_venue.image_object_key` 列。
  ///
  /// **使用场景**：LetPub 富化在 Persister 的 `REQUIRES_NEW` 事务里写入封面对象键时，
  /// 并未加载 `VenueEntity` 聚合（只拿到 `VenueSnapshot` 投影），因此不能依赖
  /// dirty check——必须通过此显式 UPDATE 把封面键写回 `cat_venue`，避免为了改
  /// 一个字段而加载整张表行。
  ///
  /// @param id 载体 ID
  /// @param key 封面对象键（非空）
  /// @return 受影响行数
  @Modifying
  @Query("UPDATE VenueEntity v SET v.imageObjectKey = :key WHERE v.id = :id")
  int updateImageObjectKey(@Param("id") Long id, @Param("key") String key);

  /// 分页查询期刊列表，按 h-index 降序排列。
  ///
  /// 查询条件（AND 关系，空值忽略）：
  ///
  /// - 固定 `venueType=JOURNAL`
  /// - `keyword`：title 前缀模糊匹配
  ///   - 大小写不敏感依赖 `cat_venue` 表级 collation `utf8mb4_0900_ai_ci`
  ///     （见 `V1.0.0__create_venue_aggregate.sql`）；未显式使用 `LOWER()`
  ///     以便命中 `idx_title (title(100))` 前缀索引
  ///   - **调用方契约**：`:keyword` 必须是已通过 `StringUtils.escapeLike()`
  ///     转义的字符串，查询内置 `ESCAPE '!'` 子句与之配套，防止用户输入的
  ///     `%` / `_` 被当作通配符
  /// - `countryCode`：国家编码精确匹配
  /// - `issnL`：ISSN-L 精确匹配
  /// - `nlmId`：NLM ID 精确匹配
  ///
  /// 使用原生查询以支持 MySQL `JSON_EXTRACT` 函数从 `citation_metrics` JSON 列提取 h-index 进行排序。
  ///
  /// @param keyword title 前缀搜索关键词（可空）
  /// @param countryCode 国家编码（可空）
  /// @param issnL ISSN-L（可空）
  /// @param nlmId NLM ID（可空）
  /// @param pageable 分页参数（仅使用分页，排序由查询内置）
  /// @return 期刊分页结果
  @Query(
      value =
          """
      SELECT * FROM cat_venue v
      WHERE v.venue_type = 'JOURNAL'
        AND (:keyword IS NULL OR v.title LIKE CONCAT(:keyword, '%') ESCAPE '!')
        AND (:countryCode IS NULL OR v.country_code = :countryCode)
        AND (:issnL IS NULL OR v.issn_l = :issnL)
        AND (:nlmId IS NULL OR v.nlm_id = :nlmId)
      ORDER BY COALESCE(CAST(JSON_EXTRACT(v.citation_metrics, '$.hIndex') AS SIGNED), 0) DESC,
               v.id DESC
      """,
      countQuery =
          """
      SELECT COUNT(*) FROM cat_venue v
      WHERE v.venue_type = 'JOURNAL'
        AND (:keyword IS NULL OR v.title LIKE CONCAT(:keyword, '%') ESCAPE '!')
        AND (:countryCode IS NULL OR v.country_code = :countryCode)
        AND (:issnL IS NULL OR v.issn_l = :issnL)
        AND (:nlmId IS NULL OR v.nlm_id = :nlmId)
      """,
      nativeQuery = true)
  Page<VenueEntity> findJournalPage(
      @Param("keyword") String keyword,
      @Param("countryCode") String countryCode,
      @Param("issnL") String issnL,
      @Param("nlmId") String nlmId,
      Pageable pageable);
}
