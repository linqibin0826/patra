package dev.patra.catalog.infra.organization.persistence.dao;

/// 机构建议与按 ROR ID 查询的 native SQL 片段（`@Query` 要求编译期常量，所以拼在常量里）。
///
/// 设计见 `docs/patra/specs/2026-10-21-catalog-org-mesh-suggest-design.md` 第 10 节。
/// jsonb 的 `?` 操作符在 Spring Data 里会被当成位置参数，一律用 `jsonb_exists()`。
///
/// @author linqibin
/// @since 0.1.0
final class OrganizationSuggestSql {

  /// 候选集上限：泛词（University、医院）有几万条命中，先按打分截断再做机构级关联与排序。
  static final int CANDIDATE_CAP = 500;

  /// 名称表候选：打分列 `match_rank`（精确 0、前缀 1、词首 2、子串 3）与
  /// `type_rank`（ror_display / label 0、alias 1、acronym 2）。
  static final String NAME_CANDIDATES =
      """
      SELECT n.org_id, n.name_value,
             CASE WHEN lower(n.name_value) = lower(:q) THEN 0
                  WHEN n.name_value ILIKE :prefix ESCAPE '!' THEN 1
                  WHEN n.name_value ILIKE :wordStart ESCAPE '!' THEN 2 ELSE 3 END AS match_rank,
             CASE WHEN jsonb_exists(n.types, 'ror_display') OR jsonb_exists(n.types, 'label') THEN 0
                  WHEN jsonb_exists(n.types, 'alias') THEN 1 ELSE 2 END AS type_rank
      FROM cat_organization_name n
      WHERE
      """;

  /// 别名表候选：LABEL 视同官方名（0），ALIAS 视同别名（1）。
  static final String ALIAS_CANDIDATES =
      """
      SELECT o2.id AS org_id, a.alias AS name_value,
             CASE WHEN lower(a.alias) = lower(:q) THEN 0
                  WHEN a.alias ILIKE :prefix ESCAPE '!' THEN 1
                  WHEN a.alias ILIKE :wordStart ESCAPE '!' THEN 2 ELSE 3 END AS match_rank,
             CASE WHEN a.alias_type = 'LABEL' THEN 0 ELSE 1 END AS type_rank
      FROM cat_organization_alias a
      JOIN cat_organization o2 ON o2.ror_id = a.ror_id
      WHERE
      """;

  /// 子串模式（含中日韩文字）的候选条件。
  static final String NAME_SUBSTRING = "n.name_value ILIKE :contains ESCAPE '!'";

  static final String ALIAS_SUBSTRING = "a.alias ILIKE :contains ESCAPE '!'";

  /// 只有 2 个码点的子串查询（「协和」「华西」）：`%协和%` 凑不出三元组，预编译语句的通用计划却会选
  /// trigram 索引，变成扫完整个索引再对全表 recheck（mini 实测 220 ~ 380 ms）。列上套 `lower()` 后
  /// 规划器用不了索引，稳定走顺序扫描（30 ms）。
  static final String NAME_SHORT_SUBSTRING = "lower(n.name_value) LIKE lower(:contains) ESCAPE '!'";

  static final String ALIAS_SHORT_SUBSTRING = "lower(a.alias) LIKE lower(:contains) ESCAPE '!'";

  /// 词首模式（拉丁文字）的候选条件：整串前缀或某个词的开头。
  static final String NAME_WORD_PREFIX =
      "(n.name_value ILIKE :prefix ESCAPE '!' OR n.name_value ILIKE :wordStart ESCAPE '!')";

  static final String ALIAS_WORD_PREFIX =
      "(a.alias ILIKE :prefix ESCAPE '!' OR a.alias ILIKE :wordStart ESCAPE '!')";

  /// 中文名：别名表的 zh LABEL（0）> ROR 的 zh label（1）> ROR 的 zh alias（2），同级取最短。
  static final String ZH_NAME_LATERAL =
      """
      LEFT JOIN LATERAL (
        SELECT x.name_value FROM (
          SELECT a.alias AS name_value, 0 AS pri
          FROM cat_organization_alias a
          WHERE a.ror_id = o.ror_id AND a.alias_type = 'LABEL' AND a.lang = 'zh'
          UNION ALL
          SELECT z.name_value, CASE WHEN jsonb_exists(z.types, 'label') THEN 1 ELSE 2 END
          FROM cat_organization_name z
          WHERE z.org_id = o.id AND z.lang = 'zh') x
        ORDER BY x.pri, length(x.name_value), x.name_value
        LIMIT 1) zh ON TRUE
      """;

  /// 第一条地点。
  static final String LOCATION_LATERAL =
      """
      LEFT JOIN LATERAL (
        SELECT loc.country_code, loc.city_name
        FROM cat_organization_location loc
        WHERE loc.org_id = o.id
        ORDER BY loc.id
        LIMIT 1) l ON TRUE
      """;

  /// 建议查询的开头：把名称表与别名表的候选合在一起，按打分截断。
  static final String SUGGEST_HEAD =
      "WITH cand AS (SELECT u.org_id, u.name_value, u.match_rank, u.type_rank FROM (";

  /// 建议查询的结尾：每家机构只留最好的一条，关联机构、中文名、地点后排序。
  static final String SUGGEST_TAIL =
      // 文本块会吃掉行尾空格，常量前后的空格写在普通字符串里
      ") u ORDER BY u.match_rank, u.type_rank, length(u.name_value) LIMIT "
          + CANDIDATE_CAP
          + """
      ),
      best AS (
        SELECT DISTINCT ON (c.org_id) c.org_id, c.name_value, c.match_rank, c.type_rank
        FROM cand c
        ORDER BY c.org_id, c.match_rank, c.type_rank, length(c.name_value))
      SELECT o.ror_id AS "rorId", o.display_name AS "displayName", b.name_value AS "matchedName",
             zh.name_value AS "zhName", l.country_code AS "countryCode", l.city_name AS "cityName",
             array_to_string(ARRAY(SELECT jsonb_array_elements_text(o.types)), ' ') AS "types"
      FROM best b
      JOIN cat_organization o ON o.id = b.org_id AND o.status = 'active' AND o.deleted_at IS NULL
      """
          + ZH_NAME_LATERAL
          + LOCATION_LATERAL
          + """
      ORDER BY b.match_rank, (l.country_code = 'CN') DESC NULLS LAST,
               coalesce(jsonb_exists(o.types, 'healthcare'), false) DESC,
               b.type_rank, length(b.name_value), o.display_name
      LIMIT :limit
      """;

  /// 子串模式的完整建议查询。
  static final String SUGGEST_BY_SUBSTRING =
      SUGGEST_HEAD
          + NAME_CANDIDATES
          + NAME_SUBSTRING
          + " UNION ALL "
          + ALIAS_CANDIDATES
          + ALIAS_SUBSTRING
          + SUGGEST_TAIL;

  /// 2 码点子串模式的完整建议查询。
  static final String SUGGEST_BY_SHORT_SUBSTRING =
      SUGGEST_HEAD
          + NAME_CANDIDATES
          + NAME_SHORT_SUBSTRING
          + " UNION ALL "
          + ALIAS_CANDIDATES
          + ALIAS_SHORT_SUBSTRING
          + SUGGEST_TAIL;

  /// 词首模式的完整建议查询。
  static final String SUGGEST_BY_WORD_PREFIX =
      SUGGEST_HEAD
          + NAME_CANDIDATES
          + NAME_WORD_PREFIX
          + " UNION ALL "
          + ALIAS_CANDIDATES
          + ALIAS_WORD_PREFIX
          + SUGGEST_TAIL;

  /// 按 ROR ID 查摘要（只认 active 且未软删）。
  static final String FIND_SUMMARY_BY_ROR_ID =
      """
      SELECT o.ror_id AS "rorId", o.display_name AS "displayName", zh.name_value AS "zhName",
             l.country_code AS "countryCode", l.city_name AS "cityName",
             array_to_string(ARRAY(SELECT jsonb_array_elements_text(o.types)), ' ') AS "types"
      FROM cat_organization o
      """
          + ZH_NAME_LATERAL
          + LOCATION_LATERAL
          + """
      WHERE o.ror_id = :rorId AND o.status = 'active' AND o.deleted_at IS NULL
      """;

  private OrganizationSuggestSql() {}
}
