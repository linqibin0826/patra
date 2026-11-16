package dev.patra.catalog.infra.publication.persistence.dao;

import dev.patra.catalog.infra.publication.persistence.entity.PublicationEntity;
import dev.patra.catalog.infra.publication.persistence.entity.PublicationMetadataEntity;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/// PubMed 增量同步专用的读写语句。
///
/// 同步要看见已删除的文献（覆盖时复活、按删除列表标删），覆盖更新又只能改 PubMed 管的列：
/// 用托管实体改字段时 Hibernate 会按载入时的值写回整行，覆盖并发运行的抓取 / 解析任务在这期间写入的
/// `is_oa`、`has_full_text`。所以这里都是批量语句，`@Modifying` 的两个属性保证执行前落库攒着的改动、
/// 执行后清掉过时的托管实体。写主表的语句带操作人参数，同步由定时任务触发，操作人为空。
///
/// @author linqibin
/// @since 0.21.0
public interface PublicationSyncDao extends Repository<PublicationEntity, Long> {

  /// 同步目标查询：由请求的 PMID（`:pmids`，`text[]`）驱动，分别左连文献表与来源表，两边都走 PMID 索引；
  /// 两边都没有的 PMID 不返回。
  String SYNC_TARGETS_SQL =
      """
      SELECT p.id, k.pmid, p.doi, COALESCE(p.deleted_at IS NOT NULL, false), s.source_rank
        FROM unnest(CAST(:pmids AS text[])) AS k(pmid)
        LEFT JOIN cat_publication p ON p.pmid = k.pmid
        LEFT JOIN cat_publication_source s ON s.pmid = k.pmid
       WHERE p.id IS NOT NULL OR s.pmid IS NOT NULL
      """;

  /// 按 PMID 查同步目标，含已删除的文献和只有来源行的墓碑。
  ///
  /// @param pmids PMID
  /// @return 每行依次是文献 ID（墓碑为空）、PMID、DOI、是否已删除、来源位置（没有来源行为空）
  @Query(value = SYNC_TARGETS_SQL, nativeQuery = true)
  List<Object[]> findSyncTargets(@Param("pmids") String[] pmids);

  /// 按 DOI 原值精确查文献 ID，含已删除的行。
  ///
  /// @param dois DOI
  /// @return 每行依次是 DOI、文献 ID
  @Query("SELECT p.doi, p.id FROM PublicationEntity p WHERE p.doi IN :dois")
  List<Object[]> findIdsByDois(@Param("dois") Collection<String> dois);

  /// 本地重算的候选：有原文、文献未删除的来源行，按 PMID 升序键集分页（PMID 按文本比较，只求稳定不求数值序）。
  ///
  /// @param afterPmid 上一页最后一个 PMID；第一页为 null
  /// @param limit 每页条数
  /// @return 每行依次是 PMID、原文、来源文件、来源位置
  @Query(
      value =
          """
          SELECT s.pmid, s.raw_xml, s.source_file, s.source_rank
            FROM cat_publication_source s
            JOIN cat_publication p ON p.pmid = s.pmid
           WHERE s.raw_xml IS NOT NULL AND p.deleted_at IS NULL
             AND (CAST(:after AS text) IS NULL OR s.pmid > CAST(:after AS text))
           ORDER BY s.pmid
           LIMIT :limit
          """,
      nativeQuery = true)
  List<Object[]> findReparseCandidates(@Param("after") String afterPmid, @Param("limit") int limit);

  /// 按 `DeleteCitation` 标删文献，带来源位置守卫：来源表里有更新位置的 PMID 不删。
  ///
  /// @param pmids 被删除的 PMID（不重复）
  /// @param rank 删除文件的来源位置
  /// @param deletedAt 删除时间
  /// @param actorId 操作人
  /// @return 实际标删的行数
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE cat_publication p SET deleted_at = :deletedAt, updated_by = :actorId
           WHERE p.pmid IN (:pmids) AND p.deleted_at IS NULL
             AND NOT EXISTS (SELECT 1 FROM cat_publication_source s
                              WHERE s.pmid = p.pmid AND s.source_rank > :rank)
          """,
      nativeQuery = true)
  int markDeletedByPmids(
      @Param("pmids") Collection<String> pmids,
      @Param("rank") int rank,
      @Param("deletedAt") Timestamp deletedAt,
      @Param("actorId") Long actorId);

  /// 复活已删除的文献：覆盖更新前把这批里已删除的行恢复。
  ///
  /// @param ids 文献 ID
  /// @param actorId 操作人
  /// @return 实际复活的行数
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE PublicationEntity p SET p.deletedAt = NULL, p.updatedBy = :actorId
       WHERE p.id IN :ids AND p.deletedAt IS NOT NULL
      """)
  int revive(@Param("ids") Collection<Long> ids, @Param("actorId") Long actorId);

  /// 主表只改 PubMed 管的列，`is_oa`、`oa_status`、`citation_count`、`ext_data`、`record_remarks` 不动；
  /// 版本号手动加一，让持有旧版本的托管实体写回时撞乐观锁。
  ///
  /// @param id 文献 ID
  /// @param row 列值来源：与插入路径相同的 `toEntity` 转换结果，保证两条路径写出的值一致
  /// @param doi 要写入的 DOI
  /// @param syncedAt 本次同步时间；为 null 时保留原值（重处理、回填不刷新）
  /// @param actorId 操作人
  /// @return 更新的行数
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE PublicationEntity p SET
             p.provenanceCode = :#{#row.provenanceCode}, p.doi = :doi, p.title = :#{#row.title},
             p.originalTitle = :#{#row.originalTitle}, p.languageRaw = :#{#row.languageRaw},
             p.languageCode = :#{#row.languageCode},
             p.publicationStatus = :#{#row.publicationStatus}, p.mediaType = :#{#row.mediaType},
             p.publicationYear = :#{#row.publicationYear},
             p.publicationMonth = :#{#row.publicationMonth},
             p.publicationDay = :#{#row.publicationDay}, p.medlinePgn = :#{#row.medlinePgn},
             p.startPage = :#{#row.startPage}, p.endPage = :#{#row.endPage},
             p.journalIsoAbbreviation = :#{#row.journalIsoAbbreviation},
             p.authorsComplete = :#{#row.authorsComplete},
             p.numberOfReferences = :#{#row.numberOfReferences},
             p.conflictOfInterest = :#{#row.conflictOfInterest}, p.venueId = :#{#row.venueId},
             p.venueInstanceId = :#{#row.venueInstanceId},
             p.lastSyncedAt = COALESCE(:syncedAt, p.lastSyncedAt),
             p.updatedBy = :actorId,
             p.version = p.version + 1
       WHERE p.id = :id
      """)
  int updateImportColumns(
      @Param("id") long id,
      @Param("row") PublicationEntity row,
      @Param("doi") String doi,
      @Param("syncedAt") Instant syncedAt,
      @Param("actorId") Long actorId);

  /// metadata 只改七个 PubMed 列，`has_full_text`、`full_text_url` 等保留。
  ///
  /// @param row 列值来源，`publicationId` 定位要改的行
  /// @return 更新的行数；0 表示还没有 metadata 行
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE PublicationMetadataEntity m SET
             m.indexingStatus = :#{#row.indexingStatus},
             m.indexingMethod = :#{#row.indexingMethod},
             m.owner = :#{#row.owner}, m.citationSubset = :#{#row.citationSubset},
             m.dataSource = :#{#row.dataSource}, m.importBatch = :#{#row.importBatch},
             m.importDate = :#{#row.importDate}
       WHERE m.publicationId = :#{#row.publicationId}
      """)
  int updateMetadataImportColumns(@Param("row") PublicationMetadataEntity row);
}
