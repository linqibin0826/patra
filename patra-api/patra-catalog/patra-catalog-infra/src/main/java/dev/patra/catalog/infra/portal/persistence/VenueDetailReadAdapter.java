package dev.patra.catalog.infra.portal.persistence;

import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel;
import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel.CasRatingView;
import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel.IdentifierView;
import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel.JcrRatingView;
import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel.ScopusRatingView;
import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel.YearlyStatView;
import dev.patra.catalog.domain.portal.port.read.VenueDetailReadPort;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/// 期刊详情 CQRS 读适配器。
///
/// 主行通过 LATERAL JOIN 一次性获取最新年 JCR/CAS/Scopus 快照；
/// 子表数据通过 5 条独立查询填充，无 N+1（每次请求仅查询单条 venue）。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class VenueDetailReadAdapter implements VenueDetailReadPort {

  /// 按 ID 查询期刊详情主行（含最新年 JCR / CAS / Scopus），排除软删与非期刊类型。
  private static final String FIND_DETAIL =
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
      """;

  /// 全部 JCR 评级，按年份降序。
  private static final String FIND_JCR_RATINGS =
      """
      SELECT year AS "year", impact_factor AS "impactFactor", jif_quartile AS "quartile",
        subject AS "subject", jif_rank AS "jifRank", jif_percentile AS "jifPercentile"
      FROM cat_venue_jcr_rating
      WHERE venue_id = :id
      ORDER BY year DESC
      """;

  /// 全部 CAS 评级，按年份降序、版本升序。
  private static final String FIND_CAS_RATINGS =
      """
      SELECT year AS "year", edition AS "edition", major_category AS "majorCategory",
        major_quartile AS "majorQuartile", minor_subject AS "minorSubject",
        minor_quartile AS "minorQuartile", is_top_journal AS "isTop",
        is_review_journal AS "isReview"
      FROM cat_venue_cas_rating
      WHERE venue_id = :id
      ORDER BY year DESC, edition ASC
      """;

  /// 全部 Scopus 评级，按年份降序。
  private static final String FIND_SCOPUS_RATINGS =
      """
      SELECT year AS "year", cite_score AS "citeScore", sjr AS "sjr", snip AS "snip",
        quartile AS "quartile", percentile AS "percentile"
      FROM cat_venue_scopus_rating
      WHERE venue_id = :id
      ORDER BY year DESC
      """;

  /// 年度发文量统计，按年份升序。
  private static final String FIND_YEARLY_STATS =
      """
      SELECT year AS "year", works_count AS "worksCount", cited_by_count AS "citedByCount",
        oa_works_count AS "oaWorksCount"
      FROM cat_venue_publication_stats
      WHERE venue_id = :id
      ORDER BY year ASC
      """;

  /// 标识符，主标识符优先，按 id 升序兜底。
  private static final String FIND_IDENTIFIERS =
      """
      SELECT identifier_type AS "type", identifier_value AS "value", is_primary AS "primary"
      FROM cat_venue_identifier
      WHERE venue_id = :id
      ORDER BY is_primary DESC, id ASC
      """;

  private final JdbcClient jdbc;

  @Override
  public Optional<VenueDetailReadModel> findById(long venueId) {
    return jdbc.sql(FIND_DETAIL)
        .param("id", venueId)
        .query(VenueDetailRow.class)
        .optional()
        .map(
            main ->
                VenueDetailReadModel.builder()
                    .id(main.id())
                    .title(main.title())
                    .abbreviatedTitle(main.abbreviatedTitle())
                    .venueType(main.venueType())
                    .issnL(main.issnL())
                    .countryCode(main.countryCode())
                    .primaryLanguage(main.primaryLanguage())
                    .foundedYear(main.foundedYear())
                    .coverObjectKey(main.coverObjectKey())
                    .isOpenAccess(main.isOpenAccess())
                    .impactFactor(main.impactFactor())
                    .jcrQuartile(main.jcrQuartile())
                    .jcrSubject(main.jcrSubject())
                    .casMajorCategory(main.casMajorCategory())
                    .casMajorQuartile(main.casMajorQuartile())
                    .casIsTop(main.casIsTop())
                    .citeScore(main.citeScore())
                    .hIndex(main.hIndex())
                    .citedByCount(main.citedByCount())
                    .worksCount(main.worksCount())
                    .frequency(main.frequency())
                    .medlineIndexed(main.medlineIndexed())
                    .oaType(main.oaType())
                    .apcUsd(main.apcUsd())
                    .isInDoaj(main.isInDoaj())
                    .jcrRatings(
                        mapJcrRatings(children(FIND_JCR_RATINGS, venueId, JcrRatingRow.class)))
                    .casRatings(
                        mapCasRatings(children(FIND_CAS_RATINGS, venueId, CasRatingRow.class)))
                    .scopusRatings(
                        mapScopusRatings(
                            children(FIND_SCOPUS_RATINGS, venueId, ScopusRatingRow.class)))
                    .yearlyStats(
                        mapYearlyStats(children(FIND_YEARLY_STATS, venueId, VenueStatRow.class)))
                    .identifiers(
                        mapIdentifiers(
                            children(FIND_IDENTIFIERS, venueId, VenueIdentifierRow.class)))
                    .build());
  }

  /// 按期刊 ID 查一张子表。
  ///
  /// @param sql 查询
  /// @param venueId 期刊 ID
  /// @param rowType 行类型
  /// @param <T> 行类型
  /// @return 行
  private <T> List<T> children(String sql, long venueId, Class<T> rowType) {
    return jdbc.sql(sql).param("id", venueId).query(rowType).list();
  }

  private List<JcrRatingView> mapJcrRatings(List<JcrRatingRow> rows) {
    return rows.stream()
        .map(
            r ->
                JcrRatingView.builder()
                    .year(r.year())
                    .impactFactor(r.impactFactor())
                    .quartile(r.quartile())
                    .subject(r.subject())
                    .jifRank(r.jifRank())
                    .jifPercentile(r.jifPercentile())
                    .build())
        .toList();
  }

  private List<CasRatingView> mapCasRatings(List<CasRatingRow> rows) {
    return rows.stream()
        .map(
            r ->
                CasRatingView.builder()
                    .year(r.year())
                    .edition(r.edition())
                    .majorCategory(r.majorCategory())
                    .majorQuartile(r.majorQuartile())
                    .minorSubject(r.minorSubject())
                    .minorQuartile(r.minorQuartile())
                    .isTop(r.isTop())
                    .isReview(r.isReview())
                    .build())
        .toList();
  }

  private List<ScopusRatingView> mapScopusRatings(List<ScopusRatingRow> rows) {
    return rows.stream()
        .map(
            r ->
                ScopusRatingView.builder()
                    .year(r.year())
                    .citeScore(r.citeScore())
                    .sjr(r.sjr())
                    .snip(r.snip())
                    .quartile(r.quartile())
                    .percentile(r.percentile())
                    .build())
        .toList();
  }

  private List<YearlyStatView> mapYearlyStats(List<VenueStatRow> rows) {
    return rows.stream()
        .map(r -> YearlyStatView.of(r.year(), r.worksCount(), r.citedByCount(), r.oaWorksCount()))
        .toList();
  }

  private List<IdentifierView> mapIdentifiers(List<VenueIdentifierRow> rows) {
    return rows.stream()
        .map(r -> IdentifierView.of(r.type(), r.value(), Boolean.TRUE.equals(r.primary())))
        .toList();
  }
}
