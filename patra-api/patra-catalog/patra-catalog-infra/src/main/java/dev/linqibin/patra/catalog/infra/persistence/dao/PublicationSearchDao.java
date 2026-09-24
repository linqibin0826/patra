package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.adapter.read.PublicationSearchRow;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 文献检索 native query DAO：列表 / count / facet 共用一段 [#WHERE] 常量。
///
/// 所有条件都是 `(:x IS NULL OR …)` 形式，facet 的 drill-down 靠调用方把本维度参数传 null 实现。
/// 常量是编译期常量（接口字段 + 文本块），可直接在 `@Query` 里拼接。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationSearchDao extends JpaRepository<PublicationEntity, Long> {

  /// 共享 WHERE：`p` 为 `cat_publication`。
  String WHERE =
      """
      WHERE p.deleted_at IS NULL
      """;

  /// 列表投影 SELECT + FROM（venue LEFT JOIN、abstract LEFT JOIN，作者 / 类型用相关子查询聚合）。
  String SELECT_LIST =
      """
      SELECT
        p.id AS "id",
        p.title AS "title",
        p.venue_id AS "venueId",
        v.title AS "venueName",
        p.publication_year AS "publicationYear",
        p.citation_count AS "citationCount",
        p.doi AS "doi",
        p.pmid AS "pmid",
        p.provenance_code AS "provenanceCode",
        (SELECT string_agg(pt.type_value, E'\\x1f' ORDER BY pt.type_order ASC NULLS LAST, pt.id ASC)
           FROM cat_publication_type pt WHERE pt.publication_id = p.id) AS "publicationTypesAgg",
        (SELECT string_agg(pa.display_name, E'\\x1f' ORDER BY pa.author_order)
           FROM cat_publication_author pa WHERE pa.publication_id = p.id) AS "authorNames",
        a.plain_text AS "abstractRaw",
        p.last_synced_at AS "lastSyncedAt"
      FROM cat_publication p
      LEFT JOIN cat_venue v ON v.id = p.venue_id
      LEFT JOIN cat_publication_abstract a ON a.publication_id = p.id
      """;

  /// 排序：`:sortMode` 为 [dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchSort]
  // 名称。
  /// CITED 只按被引 + id（与原 feed 一致，同被引不看采集时间）；LATEST / YEAR 才用 last_synced_at。
  String ORDER_BY =
      """
      ORDER BY
        CASE WHEN :sortMode = 'CITED' THEN COALESCE(p.citation_count, -1) ELSE NULL END DESC,
        CASE WHEN :sortMode = 'YEAR' THEN p.publication_year ELSE NULL END DESC NULLS LAST,
        CASE WHEN :sortMode IN ('LATEST', 'YEAR') THEN p.last_synced_at ELSE NULL END DESC NULLS LAST,
        p.id DESC
      """;

  /// 分页检索文献列表（单条 SQL，无 N+1）。
  ///
  /// @param sortMode 排序枚举名（LATEST / YEAR / CITED）
  /// @param pageable 仅携带分页，排序内嵌 SQL
  /// @return 当前页投影
  @Query(
      value = SELECT_LIST + WHERE + ORDER_BY,
      countQuery = "SELECT count(*) FROM cat_publication p " + WHERE,
      nativeQuery = true)
  Page<PublicationSearchRow> findSearchPage(@Param("sortMode") String sortMode, Pageable pageable);

  /// 满足条件的文献总数（facets 的 total 也用它）。
  ///
  /// @return 总数
  @Query(value = "SELECT count(*) FROM cat_publication p " + WHERE, nativeQuery = true)
  long countMatching();
}
