package dev.patra.catalog.infra.adapter.read;

import dev.patra.catalog.domain.model.read.portal.VenueDetailReadModel;
import dev.patra.catalog.domain.model.read.portal.VenueDetailReadModel.CasRatingView;
import dev.patra.catalog.domain.model.read.portal.VenueDetailReadModel.IdentifierView;
import dev.patra.catalog.domain.model.read.portal.VenueDetailReadModel.JcrRatingView;
import dev.patra.catalog.domain.model.read.portal.VenueDetailReadModel.ScopusRatingView;
import dev.patra.catalog.domain.model.read.portal.VenueDetailReadModel.YearlyStatView;
import dev.patra.catalog.domain.port.read.VenueDetailReadPort;
import dev.patra.catalog.infra.persistence.dao.VenueDao;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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

  private final VenueDao venueDao;

  @Override
  public Optional<VenueDetailReadModel> findById(long venueId) {
    return venueDao
        .findVenueDetailById(venueId)
        .map(
            main ->
                VenueDetailReadModel.builder()
                    .id(main.getId())
                    .title(main.getTitle())
                    .abbreviatedTitle(main.getAbbreviatedTitle())
                    .venueType(main.getVenueType())
                    .issnL(main.getIssnL())
                    .countryCode(main.getCountryCode())
                    .primaryLanguage(main.getPrimaryLanguage())
                    .foundedYear(main.getFoundedYear())
                    .coverObjectKey(main.getCoverObjectKey())
                    .isOpenAccess(main.getIsOpenAccess())
                    .impactFactor(main.getImpactFactor())
                    .jcrQuartile(main.getJcrQuartile())
                    .jcrSubject(main.getJcrSubject())
                    .casMajorCategory(main.getCasMajorCategory())
                    .casMajorQuartile(main.getCasMajorQuartile())
                    .casIsTop(main.getCasIsTop())
                    .citeScore(main.getCiteScore())
                    .hIndex(main.getHIndex())
                    .citedByCount(main.getCitedByCount())
                    .worksCount(main.getWorksCount())
                    .frequency(main.getFrequency())
                    .medlineIndexed(main.getMedlineIndexed())
                    .oaType(main.getOaType())
                    .apcUsd(main.getApcUsd())
                    .isInDoaj(main.getIsInDoaj())
                    .jcrRatings(mapJcrRatings(venueDao.findJcrRatingsByVenueId(venueId)))
                    .casRatings(mapCasRatings(venueDao.findCasRatingsByVenueId(venueId)))
                    .scopusRatings(mapScopusRatings(venueDao.findScopusRatingsByVenueId(venueId)))
                    .yearlyStats(mapYearlyStats(venueDao.findPublicationStatsByVenueId(venueId)))
                    .identifiers(mapIdentifiers(venueDao.findIdentifiersByVenueId(venueId)))
                    .build());
  }

  private List<JcrRatingView> mapJcrRatings(List<JcrRatingRow> rows) {
    return rows.stream()
        .map(
            r ->
                JcrRatingView.builder()
                    .year(r.getYear())
                    .impactFactor(r.getImpactFactor())
                    .quartile(r.getQuartile())
                    .subject(r.getSubject())
                    .jifRank(r.getJifRank())
                    .jifPercentile(r.getJifPercentile())
                    .build())
        .toList();
  }

  private List<CasRatingView> mapCasRatings(List<CasRatingRow> rows) {
    return rows.stream()
        .map(
            r ->
                CasRatingView.builder()
                    .year(r.getYear())
                    .edition(r.getEdition())
                    .majorCategory(r.getMajorCategory())
                    .majorQuartile(r.getMajorQuartile())
                    .minorSubject(r.getMinorSubject())
                    .minorQuartile(r.getMinorQuartile())
                    .isTop(r.getIsTop())
                    .isReview(r.getIsReview())
                    .build())
        .toList();
  }

  private List<ScopusRatingView> mapScopusRatings(List<ScopusRatingRow> rows) {
    return rows.stream()
        .map(
            r ->
                ScopusRatingView.builder()
                    .year(r.getYear())
                    .citeScore(r.getCiteScore())
                    .sjr(r.getSjr())
                    .snip(r.getSnip())
                    .quartile(r.getQuartile())
                    .percentile(r.getPercentile())
                    .build())
        .toList();
  }

  private List<YearlyStatView> mapYearlyStats(List<VenueStatRow> rows) {
    return rows.stream()
        .map(
            r ->
                YearlyStatView.of(
                    r.getYear(), r.getWorksCount(), r.getCitedByCount(), r.getOaWorksCount()))
        .toList();
  }

  private List<IdentifierView> mapIdentifiers(List<VenueIdentifierRow> rows) {
    return rows.stream()
        .map(r -> IdentifierView.of(r.getType(), r.getValue(), Boolean.TRUE.equals(r.getPrimary())))
        .toList();
  }
}
