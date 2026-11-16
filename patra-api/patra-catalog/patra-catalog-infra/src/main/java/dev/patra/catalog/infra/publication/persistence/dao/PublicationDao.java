package dev.patra.catalog.infra.publication.persistence.dao;

import dev.patra.catalog.infra.publication.persistence.entity.PublicationEntity;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

  /// 按 ID 查未删除的文献。
  ///
  /// @param id 文献 ID
  /// @return 匹配的文献
  Optional<PublicationEntity> findByIdAndDeletedAtIsNull(Long id);

  /// 根据 PMID 查找文献。
  ///
  /// @param pmid PubMed ID
  /// @return 匹配的文献
  Optional<PublicationEntity> findByPmidAndDeletedAtIsNull(String pmid);

  /// 根据 DOI 查找文献。
  ///
  /// @param doi Digital Object Identifier
  /// @return 匹配的文献
  Optional<PublicationEntity> findByDoiAndDeletedAtIsNull(String doi);

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
      WHERE e.deletedAt IS NULL
        AND ((:pmid IS NOT NULL AND e.pmid = :pmid)
          OR (:doi IS NOT NULL AND e.doi = :doi))
      """)
  Optional<PublicationEntity> findByPmidOrDoi(@Param("pmid") String pmid, @Param("doi") String doi);

  /// 检查 PMID 是否已存在。
  ///
  /// @param pmid PubMed ID
  /// @return true 如果存在
  boolean existsByPmidAndDeletedAtIsNull(String pmid);

  /// 检查 DOI 是否已存在。
  ///
  /// @param doi Digital Object Identifier
  /// @return true 如果存在
  boolean existsByDoiAndDeletedAtIsNull(String doi);

  /// 查出库里已占用的 PMID，包含已软删除的行。
  ///
  /// PMID 唯一索引也管着已删除的行，所以这里不加「未删除」条件，与唯一索引口径一致。
  ///
  /// @param pmids 待检查的 PMID 集合（不能为空集合）
  /// @return 已占用的 PMID
  @Query("SELECT e.pmid FROM PublicationEntity e WHERE e.pmid IN :pmids")
  List<String> findOccupiedPmids(@Param("pmids") Collection<String> pmids);

  /// 查出库里已占用的 DOI，包含已软删除的行。
  ///
  /// DOI 唯一索引也管着已删除的行，所以这里不加「未删除」条件，与唯一索引口径一致。
  ///
  /// @param dois 待检查的 DOI 集合（不能为空集合）
  /// @return 已占用的 DOI 原值
  @Query("SELECT e.doi FROM PublicationEntity e WHERE e.doi IN :dois")
  List<String> findOccupiedDois(@Param("dois") Collection<String> dois);

  /// 根据载体实例 ID 查找文献列表。
  ///
  /// @param venueInstanceId 载体实例 ID
  /// @return 文献列表
  List<PublicationEntity> findByVenueInstanceIdAndDeletedAtIsNull(Long venueInstanceId);

  /// 根据载体 ID 查找文献列表。
  ///
  /// @param venueId 载体 ID
  /// @return 文献列表
  List<PublicationEntity> findByVenueIdAndDeletedAtIsNull(Long venueId);

  /// 根据载体实例 ID 集合批量查找文献。
  ///
  /// @param venueInstanceIds 载体实例 ID 集合
  /// @return 文献列表
  List<PublicationEntity> findByVenueInstanceIdInAndDeletedAtIsNull(
      Collection<Long> venueInstanceIds);

  /// 根据数据来源和出版年份查找文献。
  ///
  /// @param provenanceCode 数据来源代码
  /// @param publicationYear 出版年份
  /// @return 文献列表
  List<PublicationEntity> findByProvenanceCodeAndPublicationYearAndDeletedAtIsNull(
      String provenanceCode, Integer publicationYear);

  /// 统计指定载体的文献数量。
  ///
  /// @param venueId 载体 ID
  /// @return 文献数量
  long countByVenueIdAndDeletedAtIsNull(Long venueId);

  /// 按 venue 查询 Top N 高被引文献。
  ///
  /// **排序规则**：`citation_count DESC`（NULL 排在最后）、`publication_year DESC`。
  ///
  /// 排除已删除的文献。
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
          ORDER BY citation_count DESC NULLS LAST, publication_year DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<PublicationEntity> findTopByVenue(
      @Param("venueId") Long venueId, @Param("since") Integer since, @Param("limit") int limit);

  /// 把文献标为开放获取（只会置为 `true`），并清掉持久化上下文里的旧值。
  ///
  /// @param id 文献 ID
  /// @return 更新行数
  @Modifying(clearAutomatically = true)
  @Query("UPDATE PublicationEntity p SET p.isOa = true WHERE p.id = :id")
  int markOpenAccess(@Param("id") Long id);

  /// 按 PMID 写一行原文缓存，已有则整行覆盖（位置、原文、解析时间）；`id` 只在新插入时用上。
  ///
  /// @param id 新行的雪花 ID
  /// @param pmid PMID
  /// @param file 来源文件名
  /// @param rank 来源位置
  /// @param raw 原文，墓碑行为空
  /// @param parsedAt 解析时间，可空
  /// @return 写入的行数
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO cat_publication_source (id, pmid, source_file, source_rank, raw_xml, parsed_at)
          VALUES (:id, :pmid, :file, :rank, :raw, :parsedAt)
          ON CONFLICT (pmid) DO UPDATE SET source_file = EXCLUDED.source_file,
              source_rank = EXCLUDED.source_rank, raw_xml = EXCLUDED.raw_xml,
              parsed_at = EXCLUDED.parsed_at
          """,
      nativeQuery = true)
  int upsertSource(
      @Param("id") long id,
      @Param("pmid") String pmid,
      @Param("file") String file,
      @Param("rank") int rank,
      @Param("raw") String raw,
      @Param("parsedAt") Timestamp parsedAt);

  /// 库里已有的 PMID（含已软删的行），写墓碑前筛候选用。
  ///
  /// @param pmids PMID
  /// @return 其中库里已有的
  @Query(
      value = "SELECT p.pmid FROM cat_publication p WHERE p.pmid = ANY(CAST(:pmids AS text[]))",
      nativeQuery = true)
  List<String> findExistingPmids(@Param("pmids") String[] pmids);

  /// 为删除列表写原文缓存的墓碑行（只有位置、没有原文）；已有的行只在新位置不早于旧位置时更新。
  ///
  /// @param ids 每个 PMID 一个新雪花 ID，与 `pmids` 一一对应
  /// @param pmids PMID，不能重复
  /// @param file 删除文件名
  /// @param rank 删除文件的来源位置
  /// @return 写入的行数
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO cat_publication_source (id, pmid, source_file, source_rank)
          SELECT u.id, u.pmid, :file, :rank
            FROM unnest(CAST(:ids AS bigint[]), CAST(:pmids AS text[])) AS u(id, pmid)
          ON CONFLICT (pmid) DO UPDATE SET source_file = EXCLUDED.source_file,
              source_rank = EXCLUDED.source_rank
          WHERE cat_publication_source.source_rank <= EXCLUDED.source_rank
          """,
      nativeQuery = true)
  int upsertTombstones(
      @Param("ids") Long[] ids,
      @Param("pmids") String[] pmids,
      @Param("file") String file,
      @Param("rank") int rank);
}
