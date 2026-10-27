package dev.patra.catalog.infra.persistence.dao;

import dev.patra.catalog.infra.adapter.read.CasRatingRow;
import dev.patra.catalog.infra.adapter.read.JcrRatingRow;
import dev.patra.catalog.infra.adapter.read.ScopusRatingRow;
import dev.patra.catalog.infra.adapter.read.VenueBrowseRow;
import dev.patra.catalog.infra.adapter.read.VenueDetailRow;
import dev.patra.catalog.infra.adapter.read.VenueFacetCountRow;
import dev.patra.catalog.infra.adapter.read.VenueIdentifierRow;
import dev.patra.catalog.infra.adapter.read.VenueStatRow;
import dev.patra.catalog.infra.persistence.entity.VenueEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 出版载体 JPA Repository。
///
/// **职责**：
///
/// - 提供 VenueEntity 的 CRUD 操作
/// - 支持各种查询方式（ISSN-L、NLM ID 等）
///
/// @author linqibin
/// @since 0.1.0
public interface VenueDao extends JpaRepository<VenueEntity, Long> {

  /// 门户期刊卡片的投影与 FROM：浏览检索与按 ID 批量读卡片共用（以换行结束，后面直接拼 WHERE）。
  ///
  /// 最新年 JCR / CAS 各用 LATERAL 取一行；列别名与 [VenueBrowseRow] 的 getter 一一对应。
  String PORTAL_VENUE_CARD_SELECT =
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

  /// 检查是否存在任何数据。
  ///
  /// @return 如果存在数据则返回 true
  @Query("SELECT COUNT(v) > 0 FROM VenueEntity v")
  boolean hasAnyData();

  /// 根据 ISSN-L 查找载体。
  ///
  /// @param issnL Linking ISSN
  /// @return 载体实体
  Optional<VenueEntity> findByIssnL(String issnL);

  /// 根据 ISSN-L 列表批量查找载体。
  ///
  /// @param issnLs ISSN-L 列表
  /// @return 载体实体列表
  List<VenueEntity> findByIssnLIn(Collection<String> issnLs);

  /// 根据 NLM ID 查找载体。
  ///
  /// @param nlmId NLM 唯一标识符
  /// @return 载体实体
  Optional<VenueEntity> findByNlmId(String nlmId);

  /// 根据 NLM ID 列表批量查找载体。
  ///
  /// @param nlmIds NLM ID 列表
  /// @return 载体实体列表
  List<VenueEntity> findByNlmIdIn(Collection<String> nlmIds);

  /// 根据 OpenAlex ID 查找载体。
  ///
  /// @param openalexId OpenAlex Source ID
  /// @return 载体实体
  Optional<VenueEntity> findByOpenalexId(String openalexId);

  /// 批量查询已存在的 ISSN-L。
  ///
  /// @param issnLs ISSN-L 列表
  /// @return 数据库中已存在的 ISSN-L 列表
  @Query("SELECT v.issnL FROM VenueEntity v WHERE v.issnL IN :issnLs")
  List<String> findExistingIssnLs(@Param("issnLs") Collection<String> issnLs);

  /// 根据 ID 列表批量查找载体。
  ///
  /// @param ids 载体 ID 列表
  /// @return 载体实体列表
  List<VenueEntity> findByIdIn(Collection<Long> ids);

  /// 仅更新 `cat_venue.image_object_key` 列。
  ///
  /// **使用场景**：LetPub 富化在 Persister 的 `REQUIRES_NEW` 事务里写入封面对象键时，
  /// 并未加载 `VenueEntity` 聚合（只拿到 `VenueSnapshot` 投影），因此不能依赖
  /// dirty check——必须通过此显式 UPDATE 把封面键写回 `cat_venue`，避免为了改
  /// 一个字段而加载整张表行。
  ///
  /// @param id 载体 ID
  /// @param key 封面对象键（非空）
  /// @return 受影响行数
  @Modifying
  @Query("UPDATE VenueEntity v SET v.imageObjectKey = :key WHERE v.id = :id")
  int updateImageObjectKey(@Param("id") Long id, @Param("key") String key);

  /// 分页查询期刊列表，按被引总次数降序排列。
  ///
  /// 查询条件（AND 关系，空值忽略）：
  ///
  /// - 固定 `venueType=JOURNAL`
  /// - `keyword`：title 前缀模糊匹配
  ///   - 显式 ILIKE 实现大小写不敏感（与 DB collation 解耦）
  ///   - **调用方契约**：`:keyword` 必须是已通过 `StringUtils.escapeLike()`
  ///     转义的字符串，查询内置 `ESCAPE '!'` 子句与之配套，防止用户输入的
  ///     `%` / `_` 被当作通配符
  /// - `countryCode`：国家编码精确匹配
  /// - `issnL`：ISSN-L 精确匹配
  /// - `nlmId`：NLM ID 精确匹配
  ///
  /// @param keyword title 前缀搜索关键词（可空）
  /// @param countryCode 国家编码（可空）
  /// @param issnL ISSN-L（可空）
  /// @param nlmId NLM ID（可空）
  /// @param pageable 分页参数（仅使用分页，排序由查询内置）
  /// @return 期刊分页结果
  @Query(
      value =
          """
      SELECT * FROM cat_venue v
      WHERE v.venue_type = 'JOURNAL'
        AND (:keyword IS NULL OR v.title ILIKE CONCAT(:keyword, '%') ESCAPE '!')
        AND (:countryCode IS NULL OR v.country_code = :countryCode)
        AND (:issnL IS NULL OR v.issn_l = :issnL)
        AND (:nlmId IS NULL OR v.nlm_id = :nlmId)
      ORDER BY COALESCE(v.cited_by_count, 0) DESC,
               v.id DESC
      """,
      countQuery =
          """
      SELECT COUNT(*) FROM cat_venue v
      WHERE v.venue_type = 'JOURNAL'
        AND (:keyword IS NULL OR v.title ILIKE CONCAT(:keyword, '%') ESCAPE '!')
        AND (:countryCode IS NULL OR v.country_code = :countryCode)
        AND (:issnL IS NULL OR v.issn_l = :issnL)
        AND (:nlmId IS NULL OR v.nlm_id = :nlmId)
      """,
      nativeQuery = true)
  Page<VenueEntity> findJournalPage(
      @Param("keyword") String keyword,
      @Param("countryCode") String countryCode,
      @Param("issnL") String issnL,
      @Param("nlmId") String nlmId,
      Pageable pageable);

  /// 分页查询期刊列表（带评级 LATERAL JOIN），支持多维筛选和多字段排序。
  ///
  /// 当存在高级筛选条件（JCR 分区、CAS 分区、Top、OA 类型、收录集、研究方向、预警）
  /// 或非默认排序时使用此查询，通过 LATERAL JOIN 在单次查询中完成评级关联，
  /// 避免 N+1 查询。
  ///
  /// **LATERAL JOIN 说明**：
  ///
  /// - JCR：取最新年份一条记录
  /// - CAS：取最新年份 + 版本排序第一条记录
  /// - Scopus：取最新年份一条记录
  ///
  /// **排序策略**：使用多分支 CASE 表达式避免混合数值类型问题。
  ///
  /// @param keyword title 前缀搜索关键词（可空）
  /// @param countryCode 国家编码（可空）
  /// @param issnL ISSN-L（可空）
  /// @param nlmId NLM ID（可空）
  /// @param jifQuartile JCR JIF 分区精确匹配（可空）
  /// @param casMajorQuartile CAS 大类分区精确匹配（可空）
  /// @param casTopJournal 是否仅 Top 期刊（可空）
  /// @param oaType OA 类型精确匹配（可空）
  /// @param collection JCR 收录集精确匹配（可空）
  /// @param researchDirection 研究方向模糊匹配（可空）
  /// @param warningOnly 是否仅预警期刊（可空）
  /// @param sortBy 排序字段（可空，默认 citedByCount）
  /// @param pageable 分页参数（仅使用分页，排序由查询内置）
  /// @return 包含评级数据的 Object[] 分页结果
  @Query(
      value =
          """
      SELECT v.id, v.title, v.country_code, v.image_object_key,
             v.citation_metrics, v.open_access, v.cited_by_count,
             jcr.impact_factor, jcr.jif_quartile, jcr.collection, jcr.research_direction,
             cas.major_quartile, cas.is_top_journal,
             scopus.cite_score, scopus.quartile AS cite_score_quartile
      FROM cat_venue v
      LEFT JOIN LATERAL (
          SELECT r.impact_factor, r.jif_quartile, r.collection, r.research_direction
          FROM cat_venue_jcr_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1
      ) jcr ON TRUE
      LEFT JOIN LATERAL (
          SELECT r.major_quartile, r.is_top_journal
          FROM cat_venue_cas_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC, r.edition ASC LIMIT 1
      ) cas ON TRUE
      LEFT JOIN LATERAL (
          SELECT r.cite_score, r.quartile
          FROM cat_venue_scopus_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1
      ) scopus ON TRUE
      WHERE v.venue_type = 'JOURNAL'
        AND (:keyword IS NULL OR v.title ILIKE CONCAT(:keyword, '%') ESCAPE '!')
        AND (:countryCode IS NULL OR v.country_code = :countryCode)
        AND (:issnL IS NULL OR v.issn_l = :issnL)
        AND (:nlmId IS NULL OR v.nlm_id = :nlmId)
        AND (:jifQuartile IS NULL OR jcr.jif_quartile = :jifQuartile)
        AND (:casMajorQuartile IS NULL OR cas.major_quartile = :casMajorQuartile)
        AND (:casTopJournal IS NULL OR cas.is_top_journal = :casTopJournal)
        AND (:oaType IS NULL OR v.open_access ->> 'oaType' = :oaType)
        AND (:collection IS NULL OR jcr.collection = :collection)
        AND (:researchDirection IS NULL OR jcr.research_direction ILIKE CONCAT('%', :researchDirection, '%') ESCAPE '!')
        AND (:warningOnly IS NULL OR EXISTS (
            SELECT 1 FROM cat_venue_cas_warning w WHERE w.venue_id = v.id AND w.in_warning_list = TRUE
        ))
      ORDER BY
        CASE WHEN :sortBy = 'impactFactor' THEN COALESCE(jcr.impact_factor, 0) ELSE NULL END DESC,
        CASE WHEN :sortBy = 'citeScore' THEN COALESCE(scopus.cite_score, 0) ELSE NULL END DESC,
        CASE WHEN :sortBy = 'hIndex' THEN COALESCE((v.citation_metrics ->> 'hIndex')::numeric, 0) ELSE NULL END DESC,
        CASE WHEN :sortBy IS NULL OR :sortBy = 'citedByCount' THEN COALESCE(v.cited_by_count, 0) ELSE NULL END DESC,
        v.id DESC
      """,
      countQuery =
          """
      SELECT COUNT(*) FROM cat_venue v
      LEFT JOIN LATERAL (
          SELECT r.jif_quartile, r.collection, r.research_direction
          FROM cat_venue_jcr_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1
      ) jcr ON TRUE
      LEFT JOIN LATERAL (
          SELECT r.major_quartile, r.is_top_journal
          FROM cat_venue_cas_rating r WHERE r.venue_id = v.id ORDER BY r.year DESC, r.edition ASC LIMIT 1
      ) cas ON TRUE
      WHERE v.venue_type = 'JOURNAL'
        AND (:keyword IS NULL OR v.title ILIKE CONCAT(:keyword, '%') ESCAPE '!')
        AND (:countryCode IS NULL OR v.country_code = :countryCode)
        AND (:issnL IS NULL OR v.issn_l = :issnL)
        AND (:nlmId IS NULL OR v.nlm_id = :nlmId)
        AND (:jifQuartile IS NULL OR jcr.jif_quartile = :jifQuartile)
        AND (:casMajorQuartile IS NULL OR cas.major_quartile = :casMajorQuartile)
        AND (:casTopJournal IS NULL OR cas.is_top_journal = :casTopJournal)
        AND (:oaType IS NULL OR v.open_access ->> 'oaType' = :oaType)
        AND (:collection IS NULL OR jcr.collection = :collection)
        AND (:researchDirection IS NULL OR jcr.research_direction ILIKE CONCAT('%', :researchDirection, '%') ESCAPE '!')
        AND (:warningOnly IS NULL OR EXISTS (
            SELECT 1 FROM cat_venue_cas_warning w WHERE w.venue_id = v.id AND w.in_warning_list = TRUE
        ))
      """,
      nativeQuery = true)
  Page<Object[]> findFilteredJournalPage(
      @Param("keyword") String keyword,
      @Param("countryCode") String countryCode,
      @Param("issnL") String issnL,
      @Param("nlmId") String nlmId,
      @Param("jifQuartile") String jifQuartile,
      @Param("casMajorQuartile") String casMajorQuartile,
      @Param("casTopJournal") Boolean casTopJournal,
      @Param("oaType") String oaType,
      @Param("collection") String collection,
      @Param("researchDirection") String researchDirection,
      @Param("warningOnly") Boolean warningOnly,
      @Param("sortBy") String sortBy,
      Pageable pageable);

  /// 分页检索期刊（portal 浏览/搜索），支持多值 OR 筛选。
  ///
  /// 支持多维度过滤（关键词前缀/JCR 学科多值 OR/JCR 分区多值 OR/CAS 分区多值 OR/
  /// CAS 顶刊/OA/DOAJ/国家码多值 OR）和四种排序。
  ///
  /// **参数契约**：列表类参数传 `String[]`，空列表对应传 `null`（跳过过滤）。
  ///
  /// @param keyword 标题前缀（已转义 LIKE 特殊字符），null 时不过滤
  /// @param sort 排序键（[VenueBrowseSort] 枚举名称字符串）
  /// @param subjects JCR 学科数组，null 时不过滤
  /// @param jcrQuartiles JCR 分区数组，null 时不过滤
  /// @param casQuartiles CAS 大类分区数组，null 时不过滤
  /// @param casTop 是否 CAS 顶刊，null 时不过滤
  /// @param isOpenAccess 是否 OA，null 时不过滤
  /// @param doaj 是否收录于 DOAJ，null 时不过滤
  /// @param countryCodes 国家/地区码数组，null 时不过滤
  /// @param pageable 分页参数
  /// @return 期刊分页投影
  @Query(
      value =
          PORTAL_VENUE_CARD_SELECT
              + """
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
      """,
      countQuery =
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
      """,
      nativeQuery = true)
  Page<VenueBrowseRow> findPortalVenueBrowsePage(
      @Param("keyword") String keyword,
      @Param("sort") String sort,
      @Param("subjects") String[] subjects,
      @Param("jcrQuartiles") String[] jcrQuartiles,
      @Param("casQuartiles") String[] casQuartiles,
      @Param("casTop") Boolean casTop,
      @Param("isOpenAccess") Boolean isOpenAccess,
      @Param("doaj") Boolean doaj,
      @Param("countryCodes") String[] countryCodes,
      Pageable pageable);

  /// 按 ID 批量读门户期刊卡片（PAP-117）：只返回期刊类型且未软删的，整批一条 SQL，顺序不保证。
  ///
  /// @param ids 期刊 ID 数组
  /// @return 卡片投影
  @Query(
      value =
          PORTAL_VENUE_CARD_SELECT
              + """
      WHERE v.venue_type = 'JOURNAL' AND v.deleted_at IS NULL
        AND v.id = ANY((:ids)::bigint[])
      """,
      nativeQuery = true)
  List<VenueBrowseRow> findPortalVenueCardsByIds(@Param("ids") Long[] ids);

  /// JCR 学科 facet 聚合（drill-down：忽略 subjects 维度自身的过滤）。
  ///
  /// @param keyword 标题前缀，null 时不过滤
  /// @param jcrQuartiles JCR 分区数组，null 时不过滤
  /// @param casQuartiles CAS 分区数组，null 时不过滤
  /// @param casTop CAS 顶刊，null 时不过滤
  /// @param isOpenAccess OA，null 时不过滤
  /// @param doaj DOAJ，null 时不过滤
  /// @param countryCodes 国家/地区码数组，null 时不过滤
  /// @return 学科分组计数列表，按计数降序
  @Query(
      value =
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
      """,
      nativeQuery = true)
  List<VenueFacetCountRow> facetSubjects(
      @Param("keyword") String keyword,
      @Param("jcrQuartiles") String[] jcrQuartiles,
      @Param("casQuartiles") String[] casQuartiles,
      @Param("casTop") Boolean casTop,
      @Param("isOpenAccess") Boolean isOpenAccess,
      @Param("doaj") Boolean doaj,
      @Param("countryCodes") String[] countryCodes);

  /// JCR 分区 facet 聚合（drill-down：忽略 jcrQuartiles 维度自身的过滤）。
  ///
  /// @param keyword 标题前缀，null 时不过滤
  /// @param subjects JCR 学科数组，null 时不过滤
  /// @param casQuartiles CAS 分区数组，null 时不过滤
  /// @param casTop CAS 顶刊，null 时不过滤
  /// @param isOpenAccess OA，null 时不过滤
  /// @param doaj DOAJ，null 时不过滤
  /// @param countryCodes 国家/地区码数组，null 时不过滤
  /// @return JCR 分区分组计数列表，按计数降序
  @Query(
      value =
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
      """,
      nativeQuery = true)
  List<VenueFacetCountRow> facetJcrQuartiles(
      @Param("keyword") String keyword,
      @Param("subjects") String[] subjects,
      @Param("casQuartiles") String[] casQuartiles,
      @Param("casTop") Boolean casTop,
      @Param("isOpenAccess") Boolean isOpenAccess,
      @Param("doaj") Boolean doaj,
      @Param("countryCodes") String[] countryCodes);

  /// CAS 分区 facet 聚合（drill-down：忽略 casQuartiles 维度自身的过滤）。
  ///
  /// @param keyword 标题前缀，null 时不过滤
  /// @param subjects JCR 学科数组，null 时不过滤
  /// @param jcrQuartiles JCR 分区数组，null 时不过滤
  /// @param casTop CAS 顶刊，null 时不过滤
  /// @param isOpenAccess OA，null 时不过滤
  /// @param doaj DOAJ，null 时不过滤
  /// @param countryCodes 国家/地区码数组，null 时不过滤
  /// @return CAS 分区分组计数列表，按计数降序
  @Query(
      value =
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
      """,
      nativeQuery = true)
  List<VenueFacetCountRow> facetCasQuartiles(
      @Param("keyword") String keyword,
      @Param("subjects") String[] subjects,
      @Param("jcrQuartiles") String[] jcrQuartiles,
      @Param("casTop") Boolean casTop,
      @Param("isOpenAccess") Boolean isOpenAccess,
      @Param("doaj") Boolean doaj,
      @Param("countryCodes") String[] countryCodes);

  /// 国家/地区 facet 聚合（drill-down：忽略 countryCodes 维度自身的过滤）。
  ///
  /// @param keyword 标题前缀，null 时不过滤
  /// @param subjects JCR 学科数组，null 时不过滤
  /// @param jcrQuartiles JCR 分区数组，null 时不过滤
  /// @param casQuartiles CAS 分区数组，null 时不过滤
  /// @param casTop CAS 顶刊，null 时不过滤
  /// @param isOpenAccess OA，null 时不过滤
  /// @param doaj DOAJ，null 时不过滤
  /// @return 国家/地区分组计数列表，按计数降序
  @Query(
      value =
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
      """,
      nativeQuery = true)
  List<VenueFacetCountRow> facetCountries(
      @Param("keyword") String keyword,
      @Param("subjects") String[] subjects,
      @Param("jcrQuartiles") String[] jcrQuartiles,
      @Param("casQuartiles") String[] casQuartiles,
      @Param("casTop") Boolean casTop,
      @Param("isOpenAccess") Boolean isOpenAccess,
      @Param("doaj") Boolean doaj);

  /// CAS 顶刊布尔计数（满足全量 filter 且 casTop=true 的期刊数）。
  ///
  /// @param keyword 标题前缀，null 时不过滤
  /// @param subjects JCR 学科数组，null 时不过滤
  /// @param jcrQuartiles JCR 分区数组，null 时不过滤
  /// @param casQuartiles CAS 分区数组，null 时不过滤
  /// @param isOpenAccess OA，null 时不过滤
  /// @param doaj DOAJ，null 时不过滤
  /// @param countryCodes 国家/地区码数组，null 时不过滤
  /// @return 满足条件的 CAS 顶刊数
  @Query(
      value =
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
      """,
      nativeQuery = true)
  long countCasTop(
      @Param("keyword") String keyword,
      @Param("subjects") String[] subjects,
      @Param("jcrQuartiles") String[] jcrQuartiles,
      @Param("casQuartiles") String[] casQuartiles,
      @Param("isOpenAccess") Boolean isOpenAccess,
      @Param("doaj") Boolean doaj,
      @Param("countryCodes") String[] countryCodes);

  /// OA 布尔计数（满足全量 filter 且 isOpenAccess=true 的期刊数）。
  ///
  /// @param keyword 标题前缀，null 时不过滤
  /// @param subjects JCR 学科数组，null 时不过滤
  /// @param jcrQuartiles JCR 分区数组，null 时不过滤
  /// @param casQuartiles CAS 分区数组，null 时不过滤
  /// @param casTop CAS 顶刊，null 时不过滤
  /// @param doaj DOAJ，null 时不过滤
  /// @param countryCodes 国家/地区码数组，null 时不过滤
  /// @return 满足条件的 OA 期刊数
  @Query(
      value =
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
      """,
      nativeQuery = true)
  long countOpenAccess(
      @Param("keyword") String keyword,
      @Param("subjects") String[] subjects,
      @Param("jcrQuartiles") String[] jcrQuartiles,
      @Param("casQuartiles") String[] casQuartiles,
      @Param("casTop") Boolean casTop,
      @Param("doaj") Boolean doaj,
      @Param("countryCodes") String[] countryCodes);

  /// DOAJ 布尔计数（满足全量 filter 且 isInDoaj=true 的期刊数）。
  ///
  /// @param keyword 标题前缀，null 时不过滤
  /// @param subjects JCR 学科数组，null 时不过滤
  /// @param jcrQuartiles JCR 分区数组，null 时不过滤
  /// @param casQuartiles CAS 分区数组，null 时不过滤
  /// @param casTop CAS 顶刊，null 时不过滤
  /// @param isOpenAccess OA，null 时不过滤
  /// @param countryCodes 国家/地区码数组，null 时不过滤
  /// @return 满足条件的 DOAJ 期刊数
  @Query(
      value =
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
      """,
      nativeQuery = true)
  long countDoaj(
      @Param("keyword") String keyword,
      @Param("subjects") String[] subjects,
      @Param("jcrQuartiles") String[] jcrQuartiles,
      @Param("casQuartiles") String[] casQuartiles,
      @Param("casTop") Boolean casTop,
      @Param("isOpenAccess") Boolean isOpenAccess,
      @Param("countryCodes") String[] countryCodes);

  /// 按 ID 查询期刊详情主行（含最新年 JCR/CAS/Scopus LATERAL JOIN）。
  ///
  /// 软删除过滤：`deleted_at IS NULL`；类型过滤：`venue_type = 'JOURNAL'`（非期刊类型返回 empty）。
  ///
  /// @param id 期刊 ID
  /// @return 期刊详情投影
  @Query(
      value =
          """
      SELECT v.id AS "id", v.title AS "title", v.abbreviated_title AS "abbreviatedTitle",
        v.venue_type AS "venueType", v.issn_l AS "issnL", v.country_code AS "countryCode",
        v.primary_language AS "primaryLanguage",
        (v.publication_profile -> 'publicationHistory' ->> 'startYear')::int AS "foundedYear",
        v.image_object_key AS "coverObjectKey",
        (v.open_access ->> 'isOa')::boolean AS "isOpenAccess",
        jcr.impact_factor AS "impactFactor", jcr.jif_quartile AS "jcrQuartile", jcr.subject AS "jcrSubject",
        cas.major_category AS "casMajorCategory", cas.major_quartile AS "casMajorQuartile",
        cas.is_top_journal AS "casIsTop", scopus.cite_score AS "citeScore",
        (v.citation_metrics ->> 'hIndex')::int AS "hIndex", v.cited_by_count AS "citedByCount",
        (v.citation_metrics ->> 'worksCount')::int AS "worksCount",
        v.publication_profile ->> 'frequency' AS "frequency",
        (v.publication_profile -> 'indexingInfo' ->> 'medlineTa') IS NOT NULL AS "medlineIndexed",
        v.open_access ->> 'oaType' AS "oaType", (v.open_access ->> 'apcUsd')::int AS "apcUsd",
        (v.open_access ->> 'isInDoaj')::boolean AS "isInDoaj"
      FROM cat_venue v
      LEFT JOIN LATERAL (SELECT r.impact_factor, r.jif_quartile, r.subject FROM cat_venue_jcr_rating r
          WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1) jcr ON TRUE
      LEFT JOIN LATERAL (SELECT r.major_category, r.major_quartile, r.is_top_journal FROM cat_venue_cas_rating r
          WHERE r.venue_id = v.id ORDER BY r.year DESC, r.edition ASC LIMIT 1) cas ON TRUE
      LEFT JOIN LATERAL (SELECT r.cite_score FROM cat_venue_scopus_rating r
          WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1) scopus ON TRUE
      WHERE v.id = :id AND v.deleted_at IS NULL AND v.venue_type = 'JOURNAL'
      """,
      nativeQuery = true)
  Optional<VenueDetailRow> findVenueDetailById(@Param("id") long id);

  /// 按 venue_id 查询所有 JCR 评级，按年份降序。
  ///
  /// @param id 期刊 ID
  /// @return JCR 评级投影列表
  @Query(
      value =
          """
      SELECT year AS "year", impact_factor AS "impactFactor", jif_quartile AS "quartile",
        subject AS "subject", jif_rank AS "jifRank", jif_percentile AS "jifPercentile"
      FROM cat_venue_jcr_rating
      WHERE venue_id = :id
      ORDER BY year DESC
      """,
      nativeQuery = true)
  List<JcrRatingRow> findJcrRatingsByVenueId(@Param("id") long id);

  /// 按 venue_id 查询所有 CAS 评级，按年份降序、版本升序。
  ///
  /// @param id 期刊 ID
  /// @return CAS 评级投影列表
  @Query(
      value =
          """
      SELECT year AS "year", edition AS "edition", major_category AS "majorCategory",
        major_quartile AS "majorQuartile", minor_subject AS "minorSubject",
        minor_quartile AS "minorQuartile", is_top_journal AS "isTop",
        is_review_journal AS "isReview"
      FROM cat_venue_cas_rating
      WHERE venue_id = :id
      ORDER BY year DESC, edition ASC
      """,
      nativeQuery = true)
  List<CasRatingRow> findCasRatingsByVenueId(@Param("id") long id);

  /// 按 venue_id 查询所有 Scopus 评级，按年份降序。
  ///
  /// @param id 期刊 ID
  /// @return Scopus 评级投影列表
  @Query(
      value =
          """
      SELECT year AS "year", cite_score AS "citeScore", sjr AS "sjr", snip AS "snip",
        quartile AS "quartile", percentile AS "percentile"
      FROM cat_venue_scopus_rating
      WHERE venue_id = :id
      ORDER BY year DESC
      """,
      nativeQuery = true)
  List<ScopusRatingRow> findScopusRatingsByVenueId(@Param("id") long id);

  /// 按 venue_id 查询所有年度发文量统计，按年份升序。
  ///
  /// @param id 期刊 ID
  /// @return 年度统计投影列表
  @Query(
      value =
          """
      SELECT year AS "year", works_count AS "worksCount", cited_by_count AS "citedByCount",
        oa_works_count AS "oaWorksCount"
      FROM cat_venue_publication_stats
      WHERE venue_id = :id
      ORDER BY year ASC
      """,
      nativeQuery = true)
  List<VenueStatRow> findPublicationStatsByVenueId(@Param("id") long id);

  /// 按 venue_id 查询所有标识符，主标识符优先，按 id 升序兜底。
  ///
  /// @param id 期刊 ID
  /// @return 标识符投影列表
  @Query(
      value =
          """
      SELECT identifier_type AS "type", identifier_value AS "value", is_primary AS "primary"
      FROM cat_venue_identifier
      WHERE venue_id = :id
      ORDER BY is_primary DESC, id ASC
      """,
      nativeQuery = true)
  List<VenueIdentifierRow> findIdentifiersByVenueId(@Param("id") long id);
}
