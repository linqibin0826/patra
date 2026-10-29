package dev.patra.catalog.infra.adapter.read;

import dev.patra.catalog.domain.model.read.portal.FacetCount;
import dev.patra.catalog.domain.model.read.portal.PortalPaperReadModel;
import dev.patra.catalog.domain.model.read.portal.PublicationSearchFacets;
import dev.patra.catalog.domain.model.read.portal.PublicationSearchFilter;
import dev.patra.catalog.domain.model.vo.publication.EvidenceLevel;
import dev.patra.catalog.domain.model.vo.publication.InlineMarkup;
import dev.patra.catalog.domain.model.vo.publication.PublicationDate;
import dev.patra.catalog.domain.port.read.PublicationSearchReadPort;
import dev.patra.catalog.domain.query.PageResult;
import dev.patra.catalog.domain.query.PagingParams;
import java.sql.Types;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Repository;

/// 文献检索 CQRS 读适配器。
///
/// 单条 SQL 组装列表项；负责 LIKE 转义、DOI 归一化、类型拆分 + [EvidenceLevel#classify]、
/// 摘要片段派生（[InlineMarkup]）。facets 采用 drill-down 语义。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class PublicationSearchReadAdapter implements PublicationSearchReadPort {

  /// 聚合列分隔符，与 SQL 的 `string_agg(..., E'\\x1f', ...)` 一致（U+001F）。
  private static final String DELIMITER = "\u001f";

  /// 摘要片段长度（码点）。
  private static final int SNIPPET_CODE_POINTS = 300;

  private static final String[] LVL5 = typeValues(EvidenceLevel.SYSTEMATIC_REVIEW);
  private static final String[] LVL4 = typeValues(EvidenceLevel.RANDOMIZED_CONTROLLED_TRIAL);
  private static final String[] LVL3 = typeValues(EvidenceLevel.COHORT_OR_CASE_CONTROL);
  private static final String[] LVL2 = typeValues(EvidenceLevel.NON_SYSTEMATIC_REVIEW);
  private static final String[] LVL1 = typeValues(EvidenceLevel.CASE_REPORT);

  /// 每篇文献的证据等级 rank：按五档类型数组取 max，无命中为 0（UNKNOWN）。数组由适配器从
  /// [dev.patra.catalog.domain.model.vo.publication.EvidenceLevel#typeValuesOf] 传入，
  /// SQL 不含类型字符串。
  private static final String EVIDENCE_RANK =
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
  private static final String WHERE =
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
  private static final String SELECT_LIST =
      """
      SELECT
        p.id AS "id",
        p.title AS "title",
        p.venue_id AS "venueId",
        v.title AS "venueName",
        p.publication_year AS "publicationYear",
        p.publication_month AS "publicationMonth",
        p.publication_day AS "publicationDay",
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
  /// [dev.patra.catalog.domain.model.read.portal.PublicationSearchSort] 名称。
  /// CITED 只按被引 + id（与原 feed 一致，同被引不看采集时间）；LATEST / YEAR 才用 last_synced_at。
  /// YEAR 依次按年、月、日降序且空值靠后：同一年里只到年的排在有月份的之后，同一月里只到月的排在有日的之后。
  private static final String ORDER_BY =
      """
      ORDER BY
        CASE WHEN :sortMode = 'CITED' THEN COALESCE(p.citation_count, -1) ELSE NULL END DESC,
        CASE WHEN :sortMode = 'YEAR' THEN p.publication_year ELSE NULL END DESC NULLS LAST,
        CASE WHEN :sortMode = 'YEAR' THEN p.publication_month ELSE NULL END DESC NULLS LAST,
        CASE WHEN :sortMode = 'YEAR' THEN p.publication_day ELSE NULL END DESC NULLS LAST,
        CASE WHEN :sortMode IN ('LATEST', 'YEAR') THEN p.last_synced_at ELSE NULL END DESC NULLS LAST,
        p.id DESC
      """;

  /// 满足条件的文献总数（列表分页与 facets 的 total、openAccess 共用）。
  private static final String COUNT = "SELECT count(*) FROM cat_publication p " + WHERE;

  /// 按 ID 批量取列表项（不排序，由调用方按请求顺序重排）。
  private static final String CARDS_BY_IDS =
      SELECT_LIST + "WHERE p.deleted_at IS NULL AND p.id = ANY((:ids)::bigint[])";

  /// 年份 facet（yearFrom / yearTo 传 null 实现 drill-down），年份降序。
  private static final String FACET_YEARS =
      "SELECT p.publication_year::text AS \"value\", count(*) AS \"count\" FROM cat_publication p "
          + WHERE
          + " AND p.publication_year IS NOT NULL GROUP BY p.publication_year ORDER BY p.publication_year DESC";

  /// 类型 facet（types 传 null），按 distinct 文献数降序、类型名升序。别名 `t` 避免与 WHERE 子查询的 `pt` 混淆。
  private static final String FACET_TYPES =
      "SELECT t.type_value AS \"value\", count(DISTINCT p.id) AS \"count\" FROM cat_publication p "
          + "JOIN cat_publication_type t ON t.publication_id = p.id "
          + WHERE
          + " GROUP BY t.type_value ORDER BY count(DISTINCT p.id) DESC, t.type_value ASC";

  /// 证据等级 facet（evidenceRanks 传 null）：外层按 rank 分组，rank 由适配器映射回枚举并补齐 6 项。
  private static final String FACET_EVIDENCE =
      "SELECT r.rank::text AS \"value\", count(*) AS \"count\" FROM (SELECT "
          + EVIDENCE_RANK
          + " AS rank FROM cat_publication p "
          + WHERE
          + ") r GROUP BY r.rank ORDER BY r.rank DESC";

  /// 语言 facet（languages 传 null），计数降序、语言码升序。
  private static final String FACET_LANGUAGES =
      "SELECT p.language_base AS \"value\", count(*) AS \"count\" FROM cat_publication p "
          + WHERE
          + " AND p.language_base IS NOT NULL GROUP BY p.language_base ORDER BY count(*) DESC, p.language_base ASC";

  /// 全库最后采集时间（不带任何筛选）。
  private static final String LAST_SYNCED_AT =
      "SELECT max(p.last_synced_at) AS \"lastSyncedAt\" FROM cat_publication p WHERE p.deleted_at IS NULL";

  private final JdbcClient jdbc;

  /// 一次检索的已绑定参数（LIKE 已转义、DOI 已归一化、列表已转数组）。facets 的 drill-down 靠把某维度置 null 实现。
  private record Bound(
      String keyword,
      String author,
      String pmid,
      String doi,
      Integer yearFrom,
      Integer yearTo,
      Long[] venueIds,
      String[] types,
      Boolean isOpenAccess,
      String[] languages,
      Integer[] evidenceRanks) {

    /// 由领域过滤参数构建。
    ///
    /// @param f 过滤参数
    /// @return 已绑定参数
    static Bound of(PublicationSearchFilter f) {
      return new Bound(
          escapeLike(f.keyword()),
          escapeLike(f.author()),
          blankToNull(f.pmid()),
          normalizeDoi(f.doi()),
          f.yearFrom(),
          f.yearTo(),
          toArray(f.venueIds(), Long[]::new),
          toArray(
              f.types().stream().map(t -> t.trim().toLowerCase(Locale.ROOT)).toList(),
              String[]::new),
          f.isOpenAccess(),
          toArray(f.languages(), String[]::new),
          toArray(f.evidenceLevels().stream().map(EvidenceLevel::rank).toList(), Integer[]::new));
    }

    /// 替换年份区间。
    ///
    /// @param from 起始年
    /// @param to 结束年
    /// @return 新参数
    Bound withYears(Integer from, Integer to) {
      return new Bound(
          keyword,
          author,
          pmid,
          doi,
          from,
          to,
          venueIds,
          types,
          isOpenAccess,
          languages,
          evidenceRanks);
    }

    /// 替换类型。
    ///
    /// @param newTypes 类型
    /// @return 新参数
    Bound withTypes(String[] newTypes) {
      return new Bound(
          keyword,
          author,
          pmid,
          doi,
          yearFrom,
          yearTo,
          venueIds,
          newTypes,
          isOpenAccess,
          languages,
          evidenceRanks);
    }

    /// 替换开放获取。
    ///
    /// @param oa 是否开放获取
    /// @return 新参数
    Bound withOpenAccess(Boolean oa) {
      return new Bound(
          keyword,
          author,
          pmid,
          doi,
          yearFrom,
          yearTo,
          venueIds,
          types,
          oa,
          languages,
          evidenceRanks);
    }

    /// 替换语言。
    ///
    /// @param newLanguages 语言
    /// @return 新参数
    Bound withLanguages(String[] newLanguages) {
      return new Bound(
          keyword,
          author,
          pmid,
          doi,
          yearFrom,
          yearTo,
          venueIds,
          types,
          isOpenAccess,
          newLanguages,
          evidenceRanks);
    }

    /// 替换证据等级。
    ///
    /// @param ranks 证据等级
    /// @return 新参数
    Bound withEvidenceRanks(Integer[] ranks) {
      return new Bound(
          keyword,
          author,
          pmid,
          doi,
          yearFrom,
          yearTo,
          venueIds,
          types,
          isOpenAccess,
          languages,
          ranks);
    }
  }

  @Override
  public PageResult<PortalPaperReadModel> search(
      PublicationSearchFilter filter, PagingParams paging) {
    Bound b = Bound.of(filter);
    PageRequest pageable = PageRequest.of(paging.page() - 1, paging.pageSize());
    List<PublicationSearchRow> content =
        bind(jdbc.sql(SELECT_LIST + WHERE + ORDER_BY + "LIMIT :limit OFFSET :offset"), b)
            .param("sortMode", filter.sort().name())
            .param("limit", pageable.getPageSize())
            .param("offset", pageable.getOffset())
            .query(PublicationSearchRow.class)
            .list();
    Page<PublicationSearchRow> page =
        PageableExecutionUtils.getPage(content, pageable, () -> count(b));
    List<PortalPaperReadModel> items = page.getContent().stream().map(this::toReadModel).toList();
    return PageResult.of(items, paging.page(), paging.pageSize(), page.getTotalElements());
  }

  @Override
  public PublicationSearchFacets facets(PublicationSearchFilter filter) {
    Bound b = Bound.of(filter);
    return PublicationSearchFacets.builder()
        .years(toFacets(facet(FACET_YEARS, b.withYears(null, null))))
        .types(toFacets(facet(FACET_TYPES, b.withTypes(null))))
        .evidence(toEvidenceFacets(facet(FACET_EVIDENCE, b.withEvidenceRanks(null))))
        .languages(toFacets(facet(FACET_LANGUAGES, b.withLanguages(null))))
        .openAccess(count(b.withOpenAccess(Boolean.TRUE)))
        .total(count(b))
        .lastSyncedAt(lastSyncedAt())
        .build();
  }

  /// 分组计数行（保持 SQL 排序）。
  ///
  /// @param sql 分组查询
  /// @param b 已绑定参数
  /// @return 分组行
  private List<PublicationFacetCountRow> facet(String sql, Bound b) {
    return bind(jdbc.sql(sql), b).query(PublicationFacetCountRow.class).list();
  }

  /// 满足条件的文献总数。
  ///
  /// @param b 已绑定参数
  /// @return 总数
  private long count(Bound b) {
    return bind(jdbc.sql(COUNT), b).query(Long.class).single();
  }

  /// 绑定检索参数。可空标量带上 SQL 类型，否则 PostgreSQL 推断不出 `:x IS NULL` 里参数的类型；
  /// 数组在 SQL 里自带类型转换。
  ///
  /// @param statement 语句
  /// @param b 已绑定参数
  /// @return 绑定后的语句
  private static StatementSpec bind(StatementSpec statement, Bound b) {
    return statement
        .param("keyword", b.keyword(), Types.VARCHAR)
        .param("tagPattern", InlineMarkup.TAG_PATTERN)
        .param("author", b.author(), Types.VARCHAR)
        .param("pmid", b.pmid(), Types.VARCHAR)
        .param("doi", b.doi(), Types.VARCHAR)
        .param("yearFrom", b.yearFrom(), Types.INTEGER)
        .param("yearTo", b.yearTo(), Types.INTEGER)
        .param("venueIds", b.venueIds())
        .param("types", b.types())
        .param("isOpenAccess", b.isOpenAccess(), Types.BOOLEAN)
        .param("languages", b.languages())
        .param("evidenceRanks", b.evidenceRanks())
        .param("lvl5", LVL5)
        .param("lvl4", LVL4)
        .param("lvl3", LVL3)
        .param("lvl2", LVL2)
        .param("lvl1", LVL1);
  }

  /// 投影行 → FacetCount 列表（保持 SQL 排序）。
  ///
  /// @param rows 投影行
  /// @return facet 列表
  private static List<FacetCount> toFacets(List<PublicationFacetCountRow> rows) {
    return rows.stream().map(r -> FacetCount.of(r.value(), r.count())).toList();
  }

  @Override
  public List<PortalPaperReadModel> findCardsByIds(Collection<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return jdbc
        .sql(CARDS_BY_IDS)
        .param("ids", ids.toArray(Long[]::new))
        .query(PublicationSearchRow.class)
        .list()
        .stream()
        .map(this::toReadModel)
        .toList();
  }

  /// rank 分组 → 固定 6 项（rank 降序，UNKNOWN 末尾），缺档补 0。
  ///
  /// @param rows rank 分组行（value 为 rank 文本）
  /// @return 6 项 facet
  private static List<FacetCount> toEvidenceFacets(List<PublicationFacetCountRow> rows) {
    Map<Integer, Long> byRank =
        rows.stream()
            .collect(
                Collectors.toMap(
                    r -> Integer.parseInt(r.value()), PublicationFacetCountRow::count));
    return List.of(
            EvidenceLevel.SYSTEMATIC_REVIEW,
            EvidenceLevel.RANDOMIZED_CONTROLLED_TRIAL,
            EvidenceLevel.COHORT_OR_CASE_CONTROL,
            EvidenceLevel.NON_SYSTEMATIC_REVIEW,
            EvidenceLevel.CASE_REPORT,
            EvidenceLevel.UNKNOWN)
        .stream()
        .map(level -> FacetCount.of(level.name(), byRank.getOrDefault(level.rank(), 0L)))
        .toList();
  }

  /// 全库最后采集时间；空库为 null。
  ///
  /// @return 时间或 null
  private Instant lastSyncedAt() {
    return jdbc.sql(LAST_SYNCED_AT).query(Instant.class).optional().orElse(null);
  }

  /// 投影行 → 读模型：类型聚合列拆开后第一个当 studyType、整个列表衍生证据等级；摘要原文派生片段。
  ///
  /// @param row 投影行
  /// @return 读模型
  private PortalPaperReadModel toReadModel(PublicationSearchRow row) {
    List<String> types = split(row.publicationTypesAgg());
    return PortalPaperReadModel.builder()
        .id(row.id())
        .title(row.title())
        .venueId(row.venueId())
        .venueName(row.venueName())
        .publicationYear(row.publicationYear())
        .publishedDate(
            PublicationDate.isoString(
                row.publicationYear(), row.publicationMonth(), row.publicationDay()))
        .authors(split(row.authorNames()))
        .citationCount(row.citationCount())
        .doi(row.doi())
        .pmid(row.pmid())
        .provenanceCode(row.provenanceCode())
        .studyType(types.isEmpty() ? null : types.get(0))
        .evidenceLevel(EvidenceLevel.classify(types))
        .abstractSnippet(toSnippet(row.abstractRaw()))
        .lastSyncedAt(row.lastSyncedAt())
        .build();
  }

  /// LIKE 通配转义，转义符 `!`，与 SQL `ESCAPE '!'` 配套；null / 空白 → null。
  ///
  /// @param raw 原始关键词
  /// @return 转义后的关键词或 null
  private static String escapeLike(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  /// DOI 归一化：去 `https://doi.org/` / `http://dx.doi.org/` 等 URL 前缀与 `doi:` 前缀，转小写；空白 → null。
  ///
  /// @param raw 原始 DOI
  /// @return 归一化 DOI 或 null
  private static String normalizeDoi(String raw) {
    String value = blankToNull(raw);
    if (value == null) {
      return null;
    }
    String lower = value.toLowerCase(Locale.ROOT);
    for (String prefix :
        List.of(
            "https://doi.org/",
            "http://doi.org/",
            "https://dx.doi.org/",
            "http://dx.doi.org/",
            "doi:")) {
      if (lower.startsWith(prefix)) {
        return lower.substring(prefix.length()).trim();
      }
    }
    return lower;
  }

  /// 证据等级 → 小写类型值数组（SQL 参数）。
  ///
  /// @param level 证据等级
  /// @return 类型值数组
  private static String[] typeValues(EvidenceLevel level) {
    return EvidenceLevel.typeValuesOf(level).toArray(String[]::new);
  }

  /// List → 数组；空列表转 null（SQL 里 `IS NULL` 短路为不过滤）。
  ///
  /// @param list 输入列表
  /// @param generator 数组构造器
  /// @param <T> 元素类型
  /// @return 数组或 null
  private static <T> T[] toArray(List<T> list, IntFunction<T[]> generator) {
    return list.isEmpty() ? null : list.toArray(generator);
  }

  /// 空白 → null，否则 trim。
  ///
  /// @param raw 原始字符串
  /// @return trim 后的字符串或 null
  private static String blankToNull(String raw) {
    return raw == null || raw.isBlank() ? null : raw.trim();
  }

  /// 摘要原文 → 可见纯文本前 300 码点；空白结果视为无摘要。
  ///
  /// @param abstractRaw 摘要原文（含内联标记），可为 null
  /// @return 片段或 null
  private static String toSnippet(String abstractRaw) {
    if (abstractRaw == null) {
      return null;
    }
    String plain =
        InlineMarkup.truncate(InlineMarkup.toPlainText(abstractRaw), SNIPPET_CODE_POINTS);
    return plain.isBlank() ? null : plain;
  }

  /// 拆分单元分隔符聚合列；null / 空白 → 空列表。
  ///
  /// @param agg 聚合列值
  /// @return 拆分结果
  private static List<String> split(String agg) {
    if (agg == null || agg.isBlank()) {
      return List.of();
    }
    return List.of(agg.split(DELIMITER, -1));
  }
}
