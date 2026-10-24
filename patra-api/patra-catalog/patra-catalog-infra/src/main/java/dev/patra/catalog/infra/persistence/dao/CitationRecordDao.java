package dev.patra.catalog.infra.persistence.dao;

import dev.patra.catalog.infra.adapter.read.CitationAuthorRow;
import dev.patra.catalog.infra.adapter.read.CitationRecordRow;
import dev.patra.catalog.infra.persistence.entity.PublicationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 引用记录 native query DAO：一批两条 SQL（主行、作者），不过滤之外的软删由主行负责。
///
/// @author linqibin
/// @since 0.1.0
public interface CitationRecordDao extends JpaRepository<PublicationEntity, Long> {

  /// 按主键批量查主行：文献 + 期刊 + 期次 + PMCID / PII 子查询，排除软删。
  ///
  /// @param ids 文献主键数组
  /// @return 投影行
  @Query(
      value =
          """
          SELECT p.id AS "id", p.pmid AS "pmid", p.doi AS "doi", p.title AS "title",
                 (SELECT i.identifier_value FROM cat_publication_identifier i
                   WHERE i.publication_id = p.id AND i.type = 'pmc' ORDER BY i.id ASC LIMIT 1) AS "pmcid",
                 (SELECT i.identifier_value FROM cat_publication_identifier i
                   WHERE i.publication_id = p.id AND i.type = 'pii' ORDER BY i.id ASC LIMIT 1) AS "pii",
                 v.title AS "journalTitle",
                 p.journal_iso_abbreviation AS "isoAbbreviation",
                 v.abbreviated_title AS "medlineAbbreviation",
                 vi.volume AS "volume", vi.issue AS "issue",
                 p.medline_pgn AS "medlinePgn", p.start_page AS "startPage", p.end_page AS "endPage",
                 p.publication_year AS "publicationYear",
                 p.publication_month AS "publicationMonth",
                 p.publication_day AS "publicationDay",
                 p.language_code AS "language"
          FROM cat_publication p
          LEFT JOIN cat_venue v ON v.id = p.venue_id
          LEFT JOIN cat_venue_instance vi ON vi.id = p.venue_instance_id
          WHERE p.id = ANY((:ids)::bigint[]) AND p.deleted_at IS NULL
          """,
      nativeQuery = true)
  List<CitationRecordRow> findRecords(@Param("ids") Long[] ids);

  /// 按文献主键批量查作者，按文献、`author_order` 排序。
  ///
  /// @param ids 文献主键数组
  /// @return 投影行
  @Query(
      value =
          """
          SELECT pa.publication_id AS "publicationId", pa.last_name AS "family",
                 pa.fore_name AS "given", pa.initials AS "initials",
                 pa.collective_name AS "collective"
          FROM cat_publication_author pa
          WHERE pa.publication_id = ANY((:ids)::bigint[])
          ORDER BY pa.publication_id, pa.author_order, pa.id
          """,
      nativeQuery = true)
  List<CitationAuthorRow> findAuthors(@Param("ids") Long[] ids);
}
