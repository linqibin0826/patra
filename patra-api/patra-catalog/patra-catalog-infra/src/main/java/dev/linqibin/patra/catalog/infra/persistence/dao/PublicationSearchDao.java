package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.adapter.read.PublicationFacetCountRow;
import dev.linqibin.patra.catalog.infra.adapter.read.PublicationLastSyncedRow;
import dev.linqibin.patra.catalog.infra.adapter.read.PublicationSearchRow;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationEntity;
import java.util.List;
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

  /// 每篇文献的证据等级 rank：按五档类型数组取 max，无命中为 0（UNKNOWN）。数组由适配器从
  /// [dev.linqibin.patra.catalog.domain.model.vo.publication.EvidenceLevel#typeValuesOf] 传入，
  /// SQL 不含类型字符串。
  String EVIDENCE_RANK =
      """
      COALESCE((SELECT max(CASE
          WHEN lower(trim(pt.type_value)) = ANY((:lvl5)::text[]) THEN 5
          WHEN lower(trim(pt.type_value)) = ANY((:lvl4)::text[]) THEN 4
          WHEN lower(trim(pt.type_value)) = ANY((:lvl3)::text[]) THEN 3
          WHEN lower(trim(pt.type_value)) = ANY((:lvl2)::text[]) THEN 2
          WHEN lower(trim(pt.type_value)) = ANY((:lvl1)::text[]) THEN 1
          ELSE 0 END)
        FROM cat_publication_type pt WHERE pt.publication_id = p.id), 0)""";

  /// 共享 WHERE：`p` 为 `cat_publication`。尾段是普通字符串——文本块开头三引号后必须紧跟换行。
  String WHERE =
      """
      WHERE p.deleted_at IS NULL
        AND (:keyword IS NULL OR regexp_replace(p.title, :tagPattern, '', 'gi')
                                 ILIKE CONCAT('%', :keyword, '%') ESCAPE '!')
        AND (:author IS NULL OR EXISTS (SELECT 1 FROM cat_publication_author pa
               WHERE pa.publication_id = p.id
                 AND pa.display_name ILIKE CONCAT('%', :author, '%') ESCAPE '!'))
        AND (:pmid IS NULL OR p.pmid = :pmid)
        AND (:doi IS NULL OR lower(p.doi) = :doi)
        AND (:yearFrom IS NULL OR p.publication_year >= :yearFrom)
        AND (:yearTo IS NULL OR p.publication_year <= :yearTo)
        AND ((:venueIds)::bigint[] IS NULL OR p.venue_id = ANY((:venueIds)::bigint[]))
        AND ((:types)::text[] IS NULL OR EXISTS (SELECT 1 FROM cat_publication_type pt
               WHERE pt.publication_id = p.id AND lower(trim(pt.type_value)) = ANY((:types)::text[])))
        AND (:isOpenAccess IS NULL OR p.is_oa = :isOpenAccess)
        AND ((:languages)::text[] IS NULL OR p.language_base = ANY((:languages)::text[]))
        AND ((:evidenceRanks)::int[] IS NULL OR
      """
          + EVIDENCE_RANK
          + " = ANY((:evidenceRanks)::int[]))\n";

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

  /// 排序：`:sortMode` 为
  /// [dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchSort] 名称。
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
  /// @param keyword 已 LIKE 转义的标题关键词，null 不过滤
  /// @param tagPattern 内联标记白名单正则（InlineMarkup.TAG_PATTERN）
  /// @param sortMode 排序枚举名（LATEST / YEAR / CITED）
  /// @param pageable 仅携带分页，排序内嵌 SQL
  /// @return 当前页投影
  @Query(
      value = SELECT_LIST + WHERE + ORDER_BY,
      countQuery = "SELECT count(*) FROM cat_publication p " + WHERE,
      nativeQuery = true)
  Page<PublicationSearchRow> findSearchPage(
      @Param("keyword") String keyword,
      @Param("tagPattern") String tagPattern,
      @Param("author") String author,
      @Param("pmid") String pmid,
      @Param("doi") String doi,
      @Param("yearFrom") Integer yearFrom,
      @Param("yearTo") Integer yearTo,
      @Param("venueIds") Long[] venueIds,
      @Param("types") String[] types,
      @Param("isOpenAccess") Boolean isOpenAccess,
      @Param("languages") String[] languages,
      @Param("evidenceRanks") Integer[] evidenceRanks,
      @Param("lvl5") String[] lvl5,
      @Param("lvl4") String[] lvl4,
      @Param("lvl3") String[] lvl3,
      @Param("lvl2") String[] lvl2,
      @Param("lvl1") String[] lvl1,
      @Param("sortMode") String sortMode,
      Pageable pageable);

  /// 满足条件的文献总数（facets 的 total 也用它）。
  ///
  /// @param keyword 已 LIKE 转义的标题关键词，null 不过滤
  /// @param tagPattern 内联标记白名单正则
  /// @return 总数
  @Query(value = "SELECT count(*) FROM cat_publication p " + WHERE, nativeQuery = true)
  long countMatching(
      @Param("keyword") String keyword,
      @Param("tagPattern") String tagPattern,
      @Param("author") String author,
      @Param("pmid") String pmid,
      @Param("doi") String doi,
      @Param("yearFrom") Integer yearFrom,
      @Param("yearTo") Integer yearTo,
      @Param("venueIds") Long[] venueIds,
      @Param("types") String[] types,
      @Param("isOpenAccess") Boolean isOpenAccess,
      @Param("languages") String[] languages,
      @Param("evidenceRanks") Integer[] evidenceRanks,
      @Param("lvl5") String[] lvl5,
      @Param("lvl4") String[] lvl4,
      @Param("lvl3") String[] lvl3,
      @Param("lvl2") String[] lvl2,
      @Param("lvl1") String[] lvl1);

  /// 年份 facet（调用方把 yearFrom / yearTo 传 null 实现 drill-down），年份降序。参数同 [#countMatching]。
  @Query(
      value =
          "SELECT p.publication_year::text AS \"value\", count(*) AS \"count\" FROM cat_publication p "
              + WHERE
              + " AND p.publication_year IS NOT NULL GROUP BY p.publication_year ORDER BY p.publication_year DESC",
      nativeQuery = true)
  List<PublicationFacetCountRow> facetYears(
      @Param("keyword") String keyword,
      @Param("tagPattern") String tagPattern,
      @Param("author") String author,
      @Param("pmid") String pmid,
      @Param("doi") String doi,
      @Param("yearFrom") Integer yearFrom,
      @Param("yearTo") Integer yearTo,
      @Param("venueIds") Long[] venueIds,
      @Param("types") String[] types,
      @Param("isOpenAccess") Boolean isOpenAccess,
      @Param("languages") String[] languages,
      @Param("evidenceRanks") Integer[] evidenceRanks,
      @Param("lvl5") String[] lvl5,
      @Param("lvl4") String[] lvl4,
      @Param("lvl3") String[] lvl3,
      @Param("lvl2") String[] lvl2,
      @Param("lvl1") String[] lvl1);

  /// 类型 facet（调用方把 types 传 null），按 distinct 文献数降序、类型名升序。别名 `t` 避免与 WHERE 子查询的 `pt` 混淆。
  @Query(
      value =
          "SELECT t.type_value AS \"value\", count(DISTINCT p.id) AS \"count\" FROM cat_publication p "
              + "JOIN cat_publication_type t ON t.publication_id = p.id "
              + WHERE
              + " GROUP BY t.type_value ORDER BY count(DISTINCT p.id) DESC, t.type_value ASC",
      nativeQuery = true)
  List<PublicationFacetCountRow> facetTypes(
      @Param("keyword") String keyword,
      @Param("tagPattern") String tagPattern,
      @Param("author") String author,
      @Param("pmid") String pmid,
      @Param("doi") String doi,
      @Param("yearFrom") Integer yearFrom,
      @Param("yearTo") Integer yearTo,
      @Param("venueIds") Long[] venueIds,
      @Param("types") String[] types,
      @Param("isOpenAccess") Boolean isOpenAccess,
      @Param("languages") String[] languages,
      @Param("evidenceRanks") Integer[] evidenceRanks,
      @Param("lvl5") String[] lvl5,
      @Param("lvl4") String[] lvl4,
      @Param("lvl3") String[] lvl3,
      @Param("lvl2") String[] lvl2,
      @Param("lvl1") String[] lvl1);

  /// 证据等级 facet（调用方把 evidenceRanks 传 null）：外层按 rank 分组，rank 由适配器映射回枚举并补齐 6 项。
  @Query(
      value =
          "SELECT r.rank::text AS \"value\", count(*) AS \"count\" FROM (SELECT "
              + EVIDENCE_RANK
              + " AS rank FROM cat_publication p "
              + WHERE
              + ") r GROUP BY r.rank ORDER BY r.rank DESC",
      nativeQuery = true)
  List<PublicationFacetCountRow> facetEvidence(
      @Param("keyword") String keyword,
      @Param("tagPattern") String tagPattern,
      @Param("author") String author,
      @Param("pmid") String pmid,
      @Param("doi") String doi,
      @Param("yearFrom") Integer yearFrom,
      @Param("yearTo") Integer yearTo,
      @Param("venueIds") Long[] venueIds,
      @Param("types") String[] types,
      @Param("isOpenAccess") Boolean isOpenAccess,
      @Param("languages") String[] languages,
      @Param("evidenceRanks") Integer[] evidenceRanks,
      @Param("lvl5") String[] lvl5,
      @Param("lvl4") String[] lvl4,
      @Param("lvl3") String[] lvl3,
      @Param("lvl2") String[] lvl2,
      @Param("lvl1") String[] lvl1);

  /// 语言 facet（调用方把 languages 传 null），计数降序、语言码升序。
  @Query(
      value =
          "SELECT p.language_base AS \"value\", count(*) AS \"count\" FROM cat_publication p "
              + WHERE
              + " AND p.language_base IS NOT NULL GROUP BY p.language_base ORDER BY count(*) DESC, p.language_base ASC",
      nativeQuery = true)
  List<PublicationFacetCountRow> facetLanguages(
      @Param("keyword") String keyword,
      @Param("tagPattern") String tagPattern,
      @Param("author") String author,
      @Param("pmid") String pmid,
      @Param("doi") String doi,
      @Param("yearFrom") Integer yearFrom,
      @Param("yearTo") Integer yearTo,
      @Param("venueIds") Long[] venueIds,
      @Param("types") String[] types,
      @Param("isOpenAccess") Boolean isOpenAccess,
      @Param("languages") String[] languages,
      @Param("evidenceRanks") Integer[] evidenceRanks,
      @Param("lvl5") String[] lvl5,
      @Param("lvl4") String[] lvl4,
      @Param("lvl3") String[] lvl3,
      @Param("lvl2") String[] lvl2,
      @Param("lvl1") String[] lvl1);

  /// 全库最后采集时间（不带任何 filter）。
  @Query(
      value =
          "SELECT max(p.last_synced_at) AS \"lastSyncedAt\" FROM cat_publication p WHERE p.deleted_at IS NULL",
      nativeQuery = true)
  PublicationLastSyncedRow findLastSyncedAt();
}
