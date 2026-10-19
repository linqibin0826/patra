package dev.patra.catalog.infra.persistence.dao;

import dev.patra.catalog.infra.adapter.read.PublicationAuthorRow;
import dev.patra.catalog.infra.adapter.read.PublicationDateRow;
import dev.patra.catalog.infra.adapter.read.PublicationDetailRow;
import dev.patra.catalog.infra.adapter.read.PublicationFundingRow;
import dev.patra.catalog.infra.adapter.read.PublicationKeywordRow;
import dev.patra.catalog.infra.adapter.read.PublicationMeshRow;
import dev.patra.catalog.infra.persistence.entity.PublicationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 文献详情 CQRS 读专用 DAO。
///
/// 所有查询均为 native query，返回投影接口。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationDetailDao extends JpaRepository<PublicationEntity, Long> {

  /// 按 ID 查询文献详情主行（含期刊名、摘要 LEFT JOIN）。
  ///
  /// 软删除过滤：`deleted_at IS NULL`。
  ///
  /// @param id 文献 ID
  /// @return 文献详情主行投影
  @Query(
      value =
          """
      SELECT p.id AS "id", p.title AS "title", p.original_title AS "originalTitle",
        p.venue_id AS "venueId", v.title AS "venueName", p.publication_year AS "publicationYear",
        a.abstract_type AS "abstractType", a.structured_sections::text AS "structuredSectionsJson",
        a.plain_text AS "abstractPlainText", p.doi AS "doi", p.pmid AS "pmid",
        (SELECT i.identifier_value FROM cat_publication_identifier i
           WHERE i.publication_id = p.id AND i.type = 'pmc' ORDER BY i.id ASC LIMIT 1) AS "pmcid",
        (SELECT i.identifier_value FROM cat_publication_identifier i
           WHERE i.publication_id = p.id AND i.type = 'pii' ORDER BY i.id ASC LIMIT 1) AS "pii",
        p.citation_count AS "citationCount", p.number_of_references AS "numberOfReferences",
        p.conflict_of_interest AS "conflictOfInterest", p.is_oa AS "isOa", p.oa_status AS "oaStatus",
        p.provenance_code AS "provenanceCode", m.full_text_url AS "fullTextUrl",
        (SELECT string_agg(pt.type_value, E'\\x1f' ORDER BY pt.type_order ASC NULLS LAST, pt.id ASC)
           FROM cat_publication_type pt WHERE pt.publication_id = p.id) AS "publicationTypesAgg",
        f.status AS "fullTextStatus", (f.pdf_object_key IS NOT NULL) AS "hasPdfObject",
        f.pdf_size AS "pdfSize", f.word_count AS "wordCount",
        EXISTS (SELECT 1 FROM cat_publication_type rt
                 WHERE rt.publication_id = p.id AND rt.type_id = 'D016441') AS "retracted"
      FROM cat_publication p
      LEFT JOIN cat_venue v ON v.id = p.venue_id
      LEFT JOIN cat_publication_abstract a ON a.publication_id = p.id
      LEFT JOIN cat_publication_metadata m ON m.publication_id = p.id
      LEFT JOIN cat_publication_fulltext f ON f.publication_id = p.id
      WHERE p.id = :id AND p.deleted_at IS NULL
      """,
      nativeQuery = true)
  Optional<PublicationDetailRow> findDetailById(@Param("id") long id);

  /// 按 publication_id 查询作者列表，按 author_order 升序。
  ///
  /// 作者为文献快照（行内 `display_name`），不受 cat_author 软删/合并影响。
  ///
  /// @param id 文献 ID
  /// @return 作者投影列表
  @Query(
      value =
          """
      SELECT pa.author_order AS "order", pa.is_first_author AS "first",
        pa.is_corresponding_author AS "corresponding", pa.display_name AS "name",
        (SELECT aff.affiliation_string FROM cat_publication_author_affiliation aff
           WHERE aff.pub_author_id = pa.id ORDER BY aff.affiliation_order ASC, aff.id ASC LIMIT 1) AS "affiliation"
      FROM cat_publication_author pa
      WHERE pa.publication_id = :id
      ORDER BY pa.author_order ASC, pa.id ASC
      """,
      nativeQuery = true)
  List<PublicationAuthorRow> findAuthorsByPublicationId(@Param("id") long id);

  /// 按 publication_id 查询 MeSH 标引列表，按 heading_order ASC、id ASC 排序。
  ///
  /// @param id 文献 ID
  /// @return MeSH 标引投影列表
  @Query(
      value =
          """
      SELECT mh.descriptor_ui AS "descriptorUi", d.name AS "term", mh.is_major_topic AS "major"
      FROM cat_publication_mesh_heading mh
      LEFT JOIN cat_mesh_descriptor d ON d.ui = mh.descriptor_ui
      WHERE mh.publication_id = :id
      ORDER BY mh.heading_order ASC NULLS LAST, mh.id ASC
      """,
      nativeQuery = true)
  List<PublicationMeshRow> findMeshHeadingsByPublicationId(@Param("id") long id);

  /// 按 publication_id 查询关键词列表，按 order_num ASC、id ASC 排序。
  ///
  /// @param id 文献 ID
  /// @return 关键词投影列表
  @Query(
      value =
          """
      SELECT k.term AS "term"
      FROM cat_publication_keyword pk
      JOIN cat_keyword k ON k.id = pk.keyword_id
      WHERE pk.publication_id = :id
      ORDER BY pk.order_num ASC NULLS LAST, pk.id ASC
      """,
      nativeQuery = true)
  List<PublicationKeywordRow> findKeywordsByPublicationId(@Param("id") long id);

  /// 按 publication_id 查询资助信息列表，按 funding_order ASC 排序。
  ///
  /// @param id 文献 ID
  /// @return 资助信息投影列表
  @Query(
      value =
          """
      SELECT f.funder_name_raw AS "funder", f.grant_id AS "grantId", f.country_raw AS "country"
      FROM cat_publication_funding f
      WHERE f.publication_id = :id
      ORDER BY f.funding_order ASC, f.id ASC
      """,
      nativeQuery = true)
  List<PublicationFundingRow> findFundingByPublicationId(@Param("id") long id);

  /// 按 publication_id 查询出版日期列表，按 order_num ASC、id ASC 排序。
  ///
  /// @param id 文献 ID
  /// @return 出版日期投影列表
  @Query(
      value =
          """
      SELECT d.date_type AS "type", d.date_value AS "date"
      FROM cat_publication_date d
      WHERE d.publication_id = :id
      ORDER BY d.order_num ASC NULLS LAST, d.id ASC
      """,
      nativeQuery = true)
  List<PublicationDateRow> findDatesByPublicationId(@Param("id") long id);
}
