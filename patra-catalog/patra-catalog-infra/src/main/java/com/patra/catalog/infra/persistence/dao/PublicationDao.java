package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.PublicationEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 文献出版物 JPA Repository。
///
/// 提供文献出版物的数据库访问操作。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationDao extends JpaRepository<PublicationEntity, Long> {

  /// 根据 PMID 查找文献。
  ///
  /// @param pmid PubMed ID
  /// @return 匹配的文献
  Optional<PublicationEntity> findByPmid(String pmid);

  /// 根据 DOI 查找文献。
  ///
  /// @param doi Digital Object Identifier
  /// @return 匹配的文献
  Optional<PublicationEntity> findByDoi(String doi);

  /// 根据 PMID 或 DOI 查找文献（任一匹配即可）。
  ///
  /// 用于导入时的去重检查，避免重复导入同一文献。
  ///
  /// @param pmid PubMed ID（可为 null）
  /// @param doi Digital Object Identifier（可为 null）
  /// @return 匹配的文献
  @Query(
      """
      SELECT e FROM PublicationEntity e
      WHERE (:pmid IS NOT NULL AND e.pmid = :pmid)
         OR (:doi IS NOT NULL AND e.doi = :doi)
      """)
  Optional<PublicationEntity> findByPmidOrDoi(@Param("pmid") String pmid, @Param("doi") String doi);

  /// 检查 PMID 是否已存在。
  ///
  /// @param pmid PubMed ID
  /// @return true 如果存在
  boolean existsByPmid(String pmid);

  /// 检查 DOI 是否已存在。
  ///
  /// @param doi Digital Object Identifier
  /// @return true 如果存在
  boolean existsByDoi(String doi);

  /// 按 PMID 集合批量查询文献。
  ///
  /// @param pmids PMID 集合
  /// @return 命中的文献实体列表
  List<PublicationEntity> findByPmidIn(Collection<String> pmids);

  /// 按 DOI 集合批量查询文献。
  ///
  /// @param dois DOI 集合
  /// @return 命中的文献实体列表
  List<PublicationEntity> findByDoiIn(Collection<String> dois);

  /// 按 PMID/DOI 集合批量查询文献（任一匹配即可）。
  ///
  /// @param pmids PMID 集合
  /// @param dois DOI 集合
  /// @return 命中的文献实体列表
  @Query(
      """
      SELECT e FROM PublicationEntity e
      WHERE e.pmid IN :pmids
         OR e.doi IN :dois
      """)
  List<PublicationEntity> findByPmidInOrDoiIn(
      @Param("pmids") Collection<String> pmids, @Param("dois") Collection<String> dois);

  /// 根据载体实例 ID 查找文献列表。
  ///
  /// @param venueInstanceId 载体实例 ID
  /// @return 文献列表
  List<PublicationEntity> findByVenueInstanceId(Long venueInstanceId);

  /// 根据载体 ID 查找文献列表。
  ///
  /// @param venueId 载体 ID
  /// @return 文献列表
  List<PublicationEntity> findByVenueId(Long venueId);

  /// 根据载体实例 ID 集合批量查找文献。
  ///
  /// @param venueInstanceIds 载体实例 ID 集合
  /// @return 文献列表
  List<PublicationEntity> findByVenueInstanceIdIn(Collection<Long> venueInstanceIds);

  /// 根据数据来源和出版年份查找文献。
  ///
  /// @param provenanceCode 数据来源代码
  /// @param publicationYear 出版年份
  /// @return 文献列表
  List<PublicationEntity> findByProvenanceCodeAndPublicationYear(
      String provenanceCode, Integer publicationYear);

  /// 统计指定载体的文献数量。
  ///
  /// @param venueId 载体 ID
  /// @return 文献数量
  long countByVenueId(Long venueId);

  /// 分页查询文献列表，支持多维度筛选。
  ///
  /// 所有筛选参数均可为 null（表示不筛选）。关键词使用包含匹配（`%keyword%`）。
  ///
  /// **关键词匹配契约**：
  ///
  /// - 大小写不敏感依赖 `cat_publication` 表级 collation `utf8mb4_0900_ai_ci`
  ///   （见 `V1.1.0__create_publication_aggregate.sql`）；未使用 `LOWER()`，
  ///   但因查询形态为 infix 匹配（`%keyword%`）本身即不可能命中 B-tree 前缀索引
  /// - **调用方契约**：`:keyword` 必须是已通过 `StringUtils.escapeLike()`
  ///   转义的字符串，查询内置 `ESCAPE '!'` 子句与之配套，防止用户输入的
  ///   `%` / `_` 被当作通配符
  ///
  /// @param keyword 标题关键词（已通过 `StringUtils.escapeLike` 转义，包含匹配，大小写不敏感）
  /// @param yearFrom 起始年份（含）
  /// @param yearTo 截止年份（含）
  /// @param languageBase 基础语种代码
  /// @param isOa 是否有 OA 版本
  /// @param oaStatus OA 状态
  /// @param venueId 载体 ID
  /// @param venueInstanceId 载体实例 ID
  /// @param pmid PubMed ID
  /// @param doi DOI
  /// @param provenanceCode 数据来源代码
  /// @param publicationStatus 出版状态
  /// @param pageable 分页参数
  /// @return 分页结果
  @Query(
      """
      SELECT p FROM PublicationEntity p
      WHERE (:keyword IS NULL OR p.title LIKE CONCAT('%', :keyword, '%') ESCAPE '!')
        AND (:yearFrom IS NULL OR p.publicationYear >= :yearFrom)
        AND (:yearTo IS NULL OR p.publicationYear <= :yearTo)
        AND (:languageBase IS NULL OR p.languageBase = :languageBase)
        AND (:isOa IS NULL OR p.isOa = :isOa)
        AND (:oaStatus IS NULL OR p.oaStatus = :oaStatus)
        AND (:venueId IS NULL OR p.venueId = :venueId)
        AND (:venueInstanceId IS NULL OR p.venueInstanceId = :venueInstanceId)
        AND (:pmid IS NULL OR p.pmid = :pmid)
        AND (:doi IS NULL OR p.doi = :doi)
        AND (:provenanceCode IS NULL OR p.provenanceCode = :provenanceCode)
        AND (:publicationStatus IS NULL OR p.publicationStatus = :publicationStatus)
      """)
  Page<PublicationEntity> findPublicationPage(
      @Param("keyword") String keyword,
      @Param("yearFrom") Integer yearFrom,
      @Param("yearTo") Integer yearTo,
      @Param("languageBase") String languageBase,
      @Param("isOa") Boolean isOa,
      @Param("oaStatus") String oaStatus,
      @Param("venueId") Long venueId,
      @Param("venueInstanceId") Long venueInstanceId,
      @Param("pmid") String pmid,
      @Param("doi") String doi,
      @Param("provenanceCode") String provenanceCode,
      @Param("publicationStatus") String publicationStatus,
      Pageable pageable);

  /// 根据载体实例 ID 删除所有关联文献。
  ///
  /// @param venueInstanceId 载体实例 ID
  /// @return 删除的记录数
  @Modifying
  @Query("DELETE FROM PublicationEntity e WHERE e.venueInstanceId = :venueInstanceId")
  int deleteByVenueInstanceId(@Param("venueInstanceId") Long venueInstanceId);

  /// 根据载体 ID 删除所有关联文献。
  ///
  /// @param venueId 载体 ID
  /// @return 删除的记录数
  @Modifying
  @Query("DELETE FROM PublicationEntity e WHERE e.venueId = :venueId")
  int deleteByVenueId(@Param("venueId") Long venueId);

  /// 按 venue 查询 Top N 高被引文献。
  ///
  /// **排序规则**：`citation_count DESC`（NULL 排在最后）、`publication_year DESC`。
  ///
  /// **软删除**：手动过滤 `deleted_at IS NULL`（使用 `nativeQuery`，不经 Hibernate `@SoftDelete` 拦截）。
  ///
  /// @param venueId 期刊 ID
  /// @param since   发表年下限（可为 null，表示不过滤）
  /// @param limit   返回条数上限
  /// @return 文献实体列表，已按 `citation_count DESC, publication_year DESC` 排序
  @Query(
      value =
          """
          SELECT * FROM cat_publication
          WHERE venue_id = :venueId
            AND (:since IS NULL OR publication_year >= :since)
            AND deleted_at IS NULL
          ORDER BY citation_count IS NULL, citation_count DESC, publication_year DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<PublicationEntity> findTopByVenue(
      @Param("venueId") Long venueId, @Param("since") Integer since, @Param("limit") int limit);
}
