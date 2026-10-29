package dev.patra.catalog.infra.adapter.read;

import dev.patra.catalog.domain.model.read.portal.FacetCount;
import dev.patra.catalog.domain.model.read.portal.VenueBrowseFacets;
import dev.patra.catalog.domain.model.read.portal.VenueBrowseFilter;
import dev.patra.catalog.domain.model.read.portal.VenueBrowseReadModel;
import dev.patra.catalog.domain.port.read.VenueBrowseReadPort;
import dev.patra.catalog.domain.query.PageResult;
import dev.patra.catalog.domain.query.PagingParams;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Repository;

/// 期刊浏览检索 CQRS 读适配器。
///
/// 单次 LATERAL JOIN（venue + 最新年 JCR/CAS），支持多值 OR 过滤和多种排序，无 N+1。
/// `facets()` 采用 drill-down 语义：每个分组维度的计数忽略该维度自身的已选值。
/// 负责对原始 keyword 执行 LIKE 转义（`!` 为转义符），与 SQL `ESCAPE '!'` 配套。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class VenueBrowseReadAdapter implements VenueBrowseReadPort {

  /// 门户期刊卡片的投影与 FROM：浏览检索与按 ID 批量读卡片共用（以换行结束，后面直接拼 WHERE）。
  ///
  /// 最新年 JCR / CAS 各用 LATERAL 取一行；列别名与 [VenueBrowseRow] 的组件一一对应。
  private static final String CARD_SELECT =
      """
      SELECT v.id AS "id", v.title AS "name", v.abbreviated_title AS "abbr",
             v.image_object_key AS "coverObjectKey",
             jcr.impact_factor AS "impactFactor", jcr.jif_quartile AS "jcrQuartile", jcr.subject AS "jcrSubject",
             cas.major_category AS "casMajorCategory", cas.major_quartile AS "casMajorQuartile",
             cas.is_top_journal AS "casIsTop", v.country_code AS "countryCode",
             v.cited_by_count AS "citedByCount",
             (v.publication_profile -> 'publicationHistory' ->> 'startYear')::int AS "foundedYear",
             (v.open_access ->> 'isOa')::boolean AS "isOpenAccess",
             (v.open_access ->> 'isInDoaj')::boolean AS "isInDoaj", v.issn_l AS "issnL",
             v.nlm_id AS "nlmId"
      FROM cat_venue v
      LEFT JOIN LATERAL (SELECT r.impact_factor, r.jif_quartile, r.subject
          FROM cat_venue_jcr_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1) jcr ON TRUE
      LEFT JOIN LATERAL (SELECT r.major_category, r.major_quartile, r.is_top_journal
          FROM cat_venue_cas_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC, r.edition ASC LIMIT 1) cas ON TRUE
      """;

  /// 浏览检索的 WHERE 与 ORDER BY（以换行结束，后面拼分页）。
  private static final String BROWSE_WHERE_ORDER =
      """
      WHERE v.venue_type = 'JOURNAL' AND v.deleted_at IS NULL
        AND (:keyword IS NULL OR v.title ILIKE CONCAT(:keyword, '%') ESCAPE '!'
             OR v.abbreviated_title ILIKE CONCAT(:keyword, '%') ESCAPE '!')
        AND ((:subjects)::text[] IS NULL OR jcr.subject = ANY((:subjects)::text[]))
        AND ((:jcrQuartiles)::text[] IS NULL OR jcr.jif_quartile = ANY((:jcrQuartiles)::text[]))
        AND ((:casQuartiles)::text[] IS NULL OR cas.major_quartile = ANY((:casQuartiles)::text[]))
        AND (:casTop IS NULL OR cas.is_top_journal = :casTop)
        AND (:isOpenAccess IS NULL OR (v.open_access ->> 'isOa')::boolean = :isOpenAccess)
        AND (:doaj IS NULL OR (v.open_access ->> 'isInDoaj')::boolean = :doaj)
        AND ((:countryCodes)::text[] IS NULL OR v.country_code = ANY((:countryCodes)::text[]))
      ORDER BY
        CASE WHEN :sort = 'IMPACT_FACTOR' THEN COALESCE(jcr.impact_factor, 0) ELSE NULL END DESC,
        CASE WHEN :sort = 'CITED_BY' THEN COALESCE(v.cited_by_count, 0) ELSE NULL END DESC,
        CASE WHEN :sort = 'CAS_QUARTILE' THEN cas.major_quartile ELSE NULL END ASC NULLS LAST,
        CASE WHEN :sort = 'TITLE' THEN v.title ELSE NULL END ASC NULLS LAST,
        v.id DESC
      """;

  /// 浏览检索总数，条件与 [#BROWSE_WHERE_ORDER] 相同。
  private static final String BROWSE_COUNT =
      """
      SELECT COUNT(*) FROM cat_venue v
      LEFT JOIN LATERAL (SELECT r.jif_quartile, r.subject
          FROM cat_venue_jcr_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1) jcr ON TRUE
      LEFT JOIN LATERAL (SELECT r.major_category, r.major_quartile, r.is_top_journal
          FROM cat_venue_cas_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC, r.edition ASC LIMIT 1) cas ON TRUE
      WHERE v.venue_type = 'JOURNAL' AND v.deleted_at IS NULL
        AND (:keyword IS NULL OR v.title ILIKE CONCAT(:keyword, '%') ESCAPE '!'
             OR v.abbreviated_title ILIKE CONCAT(:keyword, '%') ESCAPE '!')
        AND ((:subjects)::text[] IS NULL OR jcr.subject = ANY((:subjects)::text[]))
        AND ((:jcrQuartiles)::text[] IS NULL OR jcr.jif_quartile = ANY((:jcrQuartiles)::text[]))
        AND ((:casQuartiles)::text[] IS NULL OR cas.major_quartile = ANY((:casQuartiles)::text[]))
        AND (:casTop IS NULL OR cas.is_top_journal = :casTop)
        AND (:isOpenAccess IS NULL OR (v.open_access ->> 'isOa')::boolean = :isOpenAccess)
        AND (:doaj IS NULL OR (v.open_access ->> 'isInDoaj')::boolean = :doaj)
        AND ((:countryCodes)::text[] IS NULL OR v.country_code = ANY((:countryCodes)::text[]))
      """;

  /// 按 ID 批量读卡片：只返回期刊类型且未软删的，顺序不保证。
  private static final String CARDS_BY_IDS_WHERE =
      """
      WHERE v.venue_type = 'JOURNAL' AND v.deleted_at IS NULL
        AND v.id = ANY((:ids)::bigint[])
      """;

  /// JCR 学科 facet（drill-down：忽略 subjects 维度自身的过滤），按计数降序。
  private static final String FACET_SUBJECTS =
      """
      SELECT b.jcr_subject AS "value", count(*) AS "count"
      FROM (
        SELECT v.id,
               jcr.subject AS jcr_subject
        FROM cat_venue v
        LEFT JOIN LATERAL (SELECT r.jif_quartile, r.subject
            FROM cat_venue_jcr_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1) jcr ON TRUE
        LEFT JOIN LATERAL (SELECT r.major_quartile, r.is_top_journal
            FROM cat_venue_cas_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC, r.edition ASC LIMIT 1) cas ON TRUE
        WHERE v.venue_type = 'JOURNAL' AND v.deleted_at IS NULL
          AND (:keyword IS NULL OR v.title ILIKE CONCAT(:keyword, '%') ESCAPE '!'
               OR v.abbreviated_title ILIKE CONCAT(:keyword, '%') ESCAPE '!')
          AND ((:jcrQuartiles)::text[] IS NULL OR jcr.jif_quartile = ANY((:jcrQuartiles)::text[]))
          AND ((:casQuartiles)::text[] IS NULL OR cas.major_quartile = ANY((:casQuartiles)::text[]))
          AND (:casTop IS NULL OR cas.is_top_journal = :casTop)
          AND (:isOpenAccess IS NULL OR (v.open_access ->> 'isOa')::boolean = :isOpenAccess)
          AND (:doaj IS NULL OR (v.open_access ->> 'isInDoaj')::boolean = :doaj)
          AND ((:countryCodes)::text[] IS NULL OR v.country_code = ANY((:countryCodes)::text[]))
      ) b
      WHERE b.jcr_subject IS NOT NULL
      GROUP BY b.jcr_subject
      ORDER BY count(*) DESC
      """;

  /// JCR 分区 facet（drill-down：忽略 jcrQuartiles 维度自身的过滤），按计数降序。
  private static final String FACET_JCR_QUARTILES =
      """
      SELECT b.jcr_quartile AS "value", count(*) AS "count"
      FROM (
        SELECT v.id,
               jcr.jif_quartile AS jcr_quartile
        FROM cat_venue v
        LEFT JOIN LATERAL (SELECT r.jif_quartile, r.subject
            FROM cat_venue_jcr_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1) jcr ON TRUE
        LEFT JOIN LATERAL (SELECT r.major_quartile, r.is_top_journal
            FROM cat_venue_cas_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC, r.edition ASC LIMIT 1) cas ON TRUE
        WHERE v.venue_type = 'JOURNAL' AND v.deleted_at IS NULL
          AND (:keyword IS NULL OR v.title ILIKE CONCAT(:keyword, '%') ESCAPE '!'
               OR v.abbreviated_title ILIKE CONCAT(:keyword, '%') ESCAPE '!')
          AND ((:subjects)::text[] IS NULL OR jcr.subject = ANY((:subjects)::text[]))
          AND ((:casQuartiles)::text[] IS NULL OR cas.major_quartile = ANY((:casQuartiles)::text[]))
          AND (:casTop IS NULL OR cas.is_top_journal = :casTop)
          AND (:isOpenAccess IS NULL OR (v.open_access ->> 'isOa')::boolean = :isOpenAccess)
          AND (:doaj IS NULL OR (v.open_access ->> 'isInDoaj')::boolean = :doaj)
          AND ((:countryCodes)::text[] IS NULL OR v.country_code = ANY((:countryCodes)::text[]))
      ) b
      WHERE b.jcr_quartile IS NOT NULL
      GROUP BY b.jcr_quartile
      ORDER BY count(*) DESC
      """;

  /// CAS 分区 facet（drill-down：忽略 casQuartiles 维度自身的过滤），按计数降序。
  private static final String FACET_CAS_QUARTILES =
      """
      SELECT b.cas_quartile AS "value", count(*) AS "count"
      FROM (
        SELECT v.id,
               cas.major_quartile AS cas_quartile
        FROM cat_venue v
        LEFT JOIN LATERAL (SELECT r.jif_quartile, r.subject
            FROM cat_venue_jcr_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1) jcr ON TRUE
        LEFT JOIN LATERAL (SELECT r.major_quartile, r.is_top_journal
            FROM cat_venue_cas_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC, r.edition ASC LIMIT 1) cas ON TRUE
        WHERE v.venue_type = 'JOURNAL' AND v.deleted_at IS NULL
          AND (:keyword IS NULL OR v.title ILIKE CONCAT(:keyword, '%') ESCAPE '!'
               OR v.abbreviated_title ILIKE CONCAT(:keyword, '%') ESCAPE '!')
          AND ((:subjects)::text[] IS NULL OR jcr.subject = ANY((:subjects)::text[]))
          AND ((:jcrQuartiles)::text[] IS NULL OR jcr.jif_quartile = ANY((:jcrQuartiles)::text[]))
          AND (:casTop IS NULL OR cas.is_top_journal = :casTop)
          AND (:isOpenAccess IS NULL OR (v.open_access ->> 'isOa')::boolean = :isOpenAccess)
          AND (:doaj IS NULL OR (v.open_access ->> 'isInDoaj')::boolean = :doaj)
          AND ((:countryCodes)::text[] IS NULL OR v.country_code = ANY((:countryCodes)::text[]))
      ) b
      WHERE b.cas_quartile IS NOT NULL
      GROUP BY b.cas_quartile
      ORDER BY count(*) DESC
      """;

  /// 国家 / 地区 facet（drill-down：忽略 countryCodes 维度自身的过滤），按计数降序。
  private static final String FACET_COUNTRIES =
      """
      SELECT b.country_code AS "value", count(*) AS "count"
      FROM (
        SELECT v.id, v.country_code
        FROM cat_venue v
        LEFT JOIN LATERAL (SELECT r.jif_quartile, r.subject
            FROM cat_venue_jcr_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1) jcr ON TRUE
        LEFT JOIN LATERAL (SELECT r.major_quartile, r.is_top_journal
            FROM cat_venue_cas_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC, r.edition ASC LIMIT 1) cas ON TRUE
        WHERE v.venue_type = 'JOURNAL' AND v.deleted_at IS NULL
          AND (:keyword IS NULL OR v.title ILIKE CONCAT(:keyword, '%') ESCAPE '!'
               OR v.abbreviated_title ILIKE CONCAT(:keyword, '%') ESCAPE '!')
          AND ((:subjects)::text[] IS NULL OR jcr.subject = ANY((:subjects)::text[]))
          AND ((:jcrQuartiles)::text[] IS NULL OR jcr.jif_quartile = ANY((:jcrQuartiles)::text[]))
          AND ((:casQuartiles)::text[] IS NULL OR cas.major_quartile = ANY((:casQuartiles)::text[]))
          AND (:casTop IS NULL OR cas.is_top_journal = :casTop)
          AND (:isOpenAccess IS NULL OR (v.open_access ->> 'isOa')::boolean = :isOpenAccess)
          AND (:doaj IS NULL OR (v.open_access ->> 'isInDoaj')::boolean = :doaj)
      ) b
      WHERE b.country_code IS NOT NULL
      GROUP BY b.country_code
      ORDER BY count(*) DESC
      """;

  /// CAS 顶刊计数：满足除 casTop 外全部筛选且是顶刊的期刊数。
  private static final String COUNT_CAS_TOP =
      """
      SELECT count(*) FROM cat_venue v
      LEFT JOIN LATERAL (SELECT r.jif_quartile, r.subject
          FROM cat_venue_jcr_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1) jcr ON TRUE
      LEFT JOIN LATERAL (SELECT r.major_quartile, r.is_top_journal
          FROM cat_venue_cas_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC, r.edition ASC LIMIT 1) cas ON TRUE
      WHERE v.venue_type = 'JOURNAL' AND v.deleted_at IS NULL
        AND (:keyword IS NULL OR v.title ILIKE CONCAT(:keyword, '%') ESCAPE '!'
             OR v.abbreviated_title ILIKE CONCAT(:keyword, '%') ESCAPE '!')
        AND ((:subjects)::text[] IS NULL OR jcr.subject = ANY((:subjects)::text[]))
        AND ((:jcrQuartiles)::text[] IS NULL OR jcr.jif_quartile = ANY((:jcrQuartiles)::text[]))
        AND ((:casQuartiles)::text[] IS NULL OR cas.major_quartile = ANY((:casQuartiles)::text[]))
        AND (:isOpenAccess IS NULL OR (v.open_access ->> 'isOa')::boolean = :isOpenAccess)
        AND (:doaj IS NULL OR (v.open_access ->> 'isInDoaj')::boolean = :doaj)
        AND ((:countryCodes)::text[] IS NULL OR v.country_code = ANY((:countryCodes)::text[]))
        AND cas.is_top_journal = TRUE
      """;

  /// OA 计数：满足除 isOpenAccess 外全部筛选且 OA 的期刊数。
  private static final String COUNT_OPEN_ACCESS =
      """
      SELECT count(*) FROM cat_venue v
      LEFT JOIN LATERAL (SELECT r.jif_quartile, r.subject
          FROM cat_venue_jcr_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1) jcr ON TRUE
      LEFT JOIN LATERAL (SELECT r.major_quartile, r.is_top_journal
          FROM cat_venue_cas_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC, r.edition ASC LIMIT 1) cas ON TRUE
      WHERE v.venue_type = 'JOURNAL' AND v.deleted_at IS NULL
        AND (:keyword IS NULL OR v.title ILIKE CONCAT(:keyword, '%') ESCAPE '!'
             OR v.abbreviated_title ILIKE CONCAT(:keyword, '%') ESCAPE '!')
        AND ((:subjects)::text[] IS NULL OR jcr.subject = ANY((:subjects)::text[]))
        AND ((:jcrQuartiles)::text[] IS NULL OR jcr.jif_quartile = ANY((:jcrQuartiles)::text[]))
        AND ((:casQuartiles)::text[] IS NULL OR cas.major_quartile = ANY((:casQuartiles)::text[]))
        AND (:casTop IS NULL OR cas.is_top_journal = :casTop)
        AND (:doaj IS NULL OR (v.open_access ->> 'isInDoaj')::boolean = :doaj)
        AND ((:countryCodes)::text[] IS NULL OR v.country_code = ANY((:countryCodes)::text[]))
        AND (v.open_access ->> 'isOa')::boolean = TRUE
      """;

  /// DOAJ 计数：满足除 doaj 外全部筛选且收录于 DOAJ 的期刊数。
  private static final String COUNT_DOAJ =
      """
      SELECT count(*) FROM cat_venue v
      LEFT JOIN LATERAL (SELECT r.jif_quartile, r.subject
          FROM cat_venue_jcr_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1) jcr ON TRUE
      LEFT JOIN LATERAL (SELECT r.major_quartile, r.is_top_journal
          FROM cat_venue_cas_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC, r.edition ASC LIMIT 1) cas ON TRUE
      WHERE v.venue_type = 'JOURNAL' AND v.deleted_at IS NULL
        AND (:keyword IS NULL OR v.title ILIKE CONCAT(:keyword, '%') ESCAPE '!'
             OR v.abbreviated_title ILIKE CONCAT(:keyword, '%') ESCAPE '!')
        AND ((:subjects)::text[] IS NULL OR jcr.subject = ANY((:subjects)::text[]))
        AND ((:jcrQuartiles)::text[] IS NULL OR jcr.jif_quartile = ANY((:jcrQuartiles)::text[]))
        AND ((:casQuartiles)::text[] IS NULL OR cas.major_quartile = ANY((:casQuartiles)::text[]))
        AND (:casTop IS NULL OR cas.is_top_journal = :casTop)
        AND (:isOpenAccess IS NULL OR (v.open_access ->> 'isOa')::boolean = :isOpenAccess)
        AND ((:countryCodes)::text[] IS NULL OR v.country_code = ANY((:countryCodes)::text[]))
        AND (v.open_access ->> 'isInDoaj')::boolean = TRUE
      """;

  private final JdbcClient jdbc;

  /// 一次检索的已绑定参数（LIKE 已转义、列表已转数组）。facets 的 drill-down 靠 SQL 不引用本维度参数实现。
  ///
  /// @param keyword 已转义的标题前缀，null 不过滤
  /// @param subjects JCR 学科，null 不过滤
  /// @param jcrQuartiles JCR 分区，null 不过滤
  /// @param casQuartiles CAS 大类分区，null 不过滤
  /// @param casTop 是否 CAS 顶刊，null 不过滤
  /// @param isOpenAccess 是否 OA，null 不过滤
  /// @param doaj 是否收录于 DOAJ，null 不过滤
  /// @param countryCodes 国家 / 地区码，null 不过滤
  private record Bound(
      String keyword,
      String[] subjects,
      String[] jcrQuartiles,
      String[] casQuartiles,
      Boolean casTop,
      Boolean isOpenAccess,
      Boolean doaj,
      String[] countryCodes) {

    /// 由领域筛选条件构建。
    ///
    /// @param f 筛选条件
    /// @return 已绑定参数
    static Bound of(VenueBrowseFilter f) {
      return new Bound(
          escapeLike(f.keyword()),
          toArray(f.subjects()),
          toArray(f.jcrQuartiles()),
          toArray(f.casQuartiles()),
          f.casTop(),
          f.isOpenAccess(),
          f.doaj(),
          toArray(f.countryCodes()));
    }
  }

  /// 分页检索期刊浏览列表。
  ///
  /// 支持关键词全文搜索与多值筛选（学科、JCR 分区、CAS 分区、国家等），
  /// 多值条件在同一维度内为 OR 语义，跨维度之间为 AND 语义。
  /// 结果按 `filter.sort()` 排序，页码从 1 开始。
  ///
  /// @param filter 筛选条件，包含关键词、分区、布尔标志等
  /// @param paging 分页参数（1-based page + pageSize）
  /// @return 当前页数据及总条数

  @Override
  public PageResult<VenueBrowseReadModel> search(VenueBrowseFilter filter, PagingParams paging) {
    Bound b = Bound.of(filter);
    PageRequest pageable = PageRequest.of(paging.page() - 1, paging.pageSize());
    List<VenueBrowseRow> content =
        bind(jdbc.sql(CARD_SELECT + BROWSE_WHERE_ORDER + "LIMIT :limit OFFSET :offset"), b)
            .param("sort", filter.sort().name())
            .param("limit", pageable.getPageSize())
            .param("offset", pageable.getOffset())
            .query(VenueBrowseRow.class)
            .list();
    Page<VenueBrowseRow> page =
        PageableExecutionUtils.getPage(content, pageable, () -> count(BROWSE_COUNT, b));

    List<VenueBrowseReadModel> items = page.getContent().stream().map(this::toReadModel).toList();
    return PageResult.of(items, paging.page(), paging.pageSize(), page.getTotalElements());
  }

  @Override
  public List<VenueBrowseReadModel> findCardsByIds(Collection<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return jdbc
        .sql(CARD_SELECT + CARDS_BY_IDS_WHERE)
        .param("ids", ids.toArray(Long[]::new))
        .query(VenueBrowseRow.class)
        .list()
        .stream()
        .map(this::toReadModel)
        .toList();
  }

  /// 计算各筛选维度的 drill-down 计数。
  ///
  /// 每个维度的计数采用「当前 query + 除本维度外其它已选维度」的聚合策略：
  /// 即计算某维度候选值的命中数时，暂时忽略该维度自身已选值，
  /// 从而保证组内多选（同一维度勾选多个值）不会使其余候选项计数归零。
  ///
  /// 示例：用户已选 JCR=Q1，计算 JCR 各值计数时不带入 Q1 约束，
  /// 使 Q2/Q3/Q4 仍然显示真实命中数，支持继续追加选择。
  ///
  /// @param filter 当前筛选条件（含已选维度值）
  /// @return 各维度候选值及其 drill-down 命中计数

  @Override
  public VenueBrowseFacets facets(VenueBrowseFilter filter) {
    Bound b = Bound.of(filter);
    return VenueBrowseFacets.builder()
        .subjects(facet(FACET_SUBJECTS, b))
        .jcrQuartiles(facet(FACET_JCR_QUARTILES, b))
        .casQuartiles(facet(FACET_CAS_QUARTILES, b))
        .countries(facet(FACET_COUNTRIES, b))
        .casTop(count(COUNT_CAS_TOP, b))
        .openAccess(count(COUNT_OPEN_ACCESS, b))
        .doaj(count(COUNT_DOAJ, b))
        .build();
  }

  /// 分组计数（保持 SQL 排序）。
  ///
  /// @param sql 分组查询
  /// @param b 已绑定参数
  /// @return facet 列表
  private List<FacetCount> facet(String sql, Bound b) {
    return bind(jdbc.sql(sql), b).query(VenueFacetCountRow.class).list().stream()
        .map(r -> FacetCount.of(r.value(), r.count()))
        .toList();
  }

  /// 计数。
  ///
  /// @param sql 计数查询
  /// @param b 已绑定参数
  /// @return 条数
  private long count(String sql, Bound b) {
    return bind(jdbc.sql(sql), b).query(Long.class).single();
  }

  /// 绑定筛选参数；SQL 没引用的参数被忽略。可空标量带上 SQL 类型，否则 PostgreSQL 推断不出
  /// `:x IS NULL` 里参数的类型；数组在 SQL 里自带类型转换。
  ///
  /// @param statement 语句
  /// @param b 已绑定参数
  /// @return 绑定后的语句
  private static StatementSpec bind(StatementSpec statement, Bound b) {
    return statement
        .param("keyword", b.keyword(), Types.VARCHAR)
        .param("subjects", b.subjects())
        .param("jcrQuartiles", b.jcrQuartiles())
        .param("casQuartiles", b.casQuartiles())
        .param("casTop", b.casTop(), Types.BOOLEAN)
        .param("isOpenAccess", b.isOpenAccess(), Types.BOOLEAN)
        .param("doaj", b.doaj(), Types.BOOLEAN)
        .param("countryCodes", b.countryCodes());
  }

  /// List → `String[]` 转换：空列表转 null（表示"不过滤"），非空则转数组。
  ///
  /// @param list 输入列表，null 或空均表示无过滤
  /// @return String 数组，或 null
  private static String[] toArray(List<String> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    return list.toArray(String[]::new);
  }

  /// 对 LIKE 前缀关键词转义特殊字符，转义符为 `!`，与 SQL `ESCAPE '!'` 子句配套。
  ///
  /// @param keyword 原始关键词，null 时直接返回 null
  /// @return 转义后的关键词，或 null
  private static String escapeLike(String keyword) {
    if (keyword == null) {
      return null;
    }
    return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  private VenueBrowseReadModel toReadModel(VenueBrowseRow row) {
    return VenueBrowseReadModel.builder()
        .id(row.id())
        .name(row.name())
        .abbr(row.abbr())
        .coverObjectKey(row.coverObjectKey())
        .impactFactor(row.impactFactor())
        .jcrQuartile(row.jcrQuartile())
        .jcrSubject(row.jcrSubject())
        .casMajorCategory(row.casMajorCategory())
        .casMajorQuartile(row.casMajorQuartile())
        .casIsTop(row.casIsTop())
        .countryCode(row.countryCode())
        .citedByCount(row.citedByCount())
        .foundedYear(row.foundedYear())
        .isOpenAccess(row.isOpenAccess())
        .isInDoaj(row.isInDoaj())
        .issnL(row.issnL())
        .nlmId(row.nlmId())
        .build();
  }
}
