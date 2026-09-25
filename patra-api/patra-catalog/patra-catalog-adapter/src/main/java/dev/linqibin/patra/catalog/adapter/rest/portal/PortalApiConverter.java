package dev.linqibin.patra.catalog.adapter.rest.portal;

import dev.linqibin.patra.catalog.adapter.rest.portal.response.EvidenceLevelView;
import dev.linqibin.patra.catalog.adapter.rest.portal.response.FacetCountResponse;
import dev.linqibin.patra.catalog.adapter.rest.portal.response.PortalPaperResponse;
import dev.linqibin.patra.catalog.adapter.rest.portal.response.PortalPublicationDetailResponse;
import dev.linqibin.patra.catalog.adapter.rest.portal.response.PortalPublicationFacetsResponse;
import dev.linqibin.patra.catalog.adapter.rest.portal.response.PortalVenueBrowseResponse;
import dev.linqibin.patra.catalog.adapter.rest.portal.response.PortalVenueDetailResponse;
import dev.linqibin.patra.catalog.adapter.rest.portal.response.PortalVenueFacetsResponse;
import dev.linqibin.patra.catalog.domain.model.read.portal.FacetCount;
import dev.linqibin.patra.catalog.domain.model.read.portal.PortalPaperReadModel;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationDetailReadModel;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchFacets;
import dev.linqibin.patra.catalog.domain.model.read.portal.VenueBrowseFacets;
import dev.linqibin.patra.catalog.domain.model.read.portal.VenueBrowseReadModel;
import dev.linqibin.patra.catalog.domain.model.read.portal.VenueDetailReadModel;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/// Portal 读模型 → 响应 DTO 转换器。
///
/// @author linqibin
/// @since 0.1.0
@Component
public class PortalApiConverter {

  /// 将读模型转为响应 DTO。
  ///
  /// @param model 读模型
  /// @return 响应 DTO
  public PortalPaperResponse toResponse(PortalPaperReadModel model) {
    return new PortalPaperResponse(
        Long.toString(model.id()),
        model.title(),
        model.venueName(),
        model.publicationYear(),
        model.authors(),
        model.citationCount(),
        0, // bookmarks：无用户系统，恒为 0
        model.doi(),
        model.pmid(),
        toSource(model.provenanceCode()),
        // TODO(LLM-summary)：接入 LLM 摘要生成后填充
        null,
        // TODO(read-time)：接入原文采集/字数后估算原文阅读时长
        null,
        model.studyType(),
        toMinutesAgo(model.lastSyncedAt()),
        model.venueId() != null ? Long.toString(model.venueId()) : null,
        EvidenceLevelView.of(model.evidenceLevel()),
        model.abstractSnippet());
  }

  /// 将期刊浏览读模型转为响应 DTO。
  ///
  /// @param model 期刊浏览读模型
  /// @return 响应 DTO
  public PortalVenueBrowseResponse toVenueBrowseResponse(VenueBrowseReadModel model) {
    return PortalVenueBrowseResponse.builder()
        .id(Long.toString(model.id()))
        .name(model.name())
        .abbr(model.abbr())
        .coverObjectKey(model.coverObjectKey())
        .impactFactor(model.impactFactor())
        .jcrQuartile(model.jcrQuartile())
        .jcrSubject(model.jcrSubject())
        .casMajorCategory(model.casMajorCategory())
        .casMajorQuartile(model.casMajorQuartile())
        .casIsTop(model.casIsTop())
        .countryCode(model.countryCode())
        .citedByCount(model.citedByCount())
        .foundedYear(model.foundedYear())
        .isOpenAccess(model.isOpenAccess())
        .isInDoaj(model.isInDoaj())
        .issnL(model.issnL())
        .build();
  }

  /// 将期刊 facet 读模型转为响应 DTO。
  ///
  /// @param model 期刊 facet 读模型
  /// @return 响应 DTO
  public PortalVenueFacetsResponse toVenueFacetsResponse(VenueBrowseFacets model) {
    return PortalVenueFacetsResponse.builder()
        .subjects(toFacetCountResponses(model.subjects()))
        .jcrQuartiles(toFacetCountResponses(model.jcrQuartiles()))
        .casQuartiles(toFacetCountResponses(model.casQuartiles()))
        .countries(toFacetCountResponses(model.countries()))
        .casTop(model.casTop())
        .openAccess(model.openAccess())
        .doaj(model.doaj())
        .build();
  }

  private List<FacetCountResponse> toFacetCountResponses(List<FacetCount> facets) {
    return facets.stream().map(f -> FacetCountResponse.of(f.value(), f.count())).toList();
  }

  /// 将文献 facets 读模型转为响应 DTO。
  ///
  /// @param model facets 读模型
  /// @return 响应 DTO
  public PortalPublicationFacetsResponse toPublicationFacetsResponse(
      PublicationSearchFacets model) {
    return PortalPublicationFacetsResponse.builder()
        .years(toFacetCountResponses(model.years()))
        .types(toFacetCountResponses(model.types()))
        .evidence(toFacetCountResponses(model.evidence()))
        .languages(toFacetCountResponses(model.languages()))
        .openAccess(model.openAccess())
        .total(model.total())
        .lastSyncedAt(model.lastSyncedAt())
        .build();
  }

  /// 将期刊详情读模型转为响应 DTO。
  ///
  /// @param model 期刊详情读模型
  /// @return 响应 DTO
  public PortalVenueDetailResponse toVenueDetailResponse(VenueDetailReadModel model) {
    return PortalVenueDetailResponse.builder()
        .id(Long.toString(model.id()))
        .title(model.title())
        .abbreviatedTitle(model.abbreviatedTitle())
        .venueType(model.venueType())
        .issnL(model.issnL())
        .countryCode(model.countryCode())
        .primaryLanguage(model.primaryLanguage())
        .foundedYear(model.foundedYear())
        .coverObjectKey(model.coverObjectKey())
        .homepageUrl(null) // 当前无数据源，恒为 null
        .isOpenAccess(model.isOpenAccess())
        .impactFactor(model.impactFactor())
        .jcrQuartile(model.jcrQuartile())
        .jcrSubject(model.jcrSubject())
        .casMajorCategory(model.casMajorCategory())
        .casMajorQuartile(model.casMajorQuartile())
        .casIsTop(model.casIsTop())
        .citeScore(model.citeScore())
        .hIndex(model.hIndex())
        .citedByCount(model.citedByCount())
        .worksCount(model.worksCount())
        .frequency(model.frequency())
        .medlineIndexed(model.medlineIndexed())
        .oaType(model.oaType())
        .apcUsd(model.apcUsd())
        .isInDoaj(model.isInDoaj())
        .jcrRatings(
            model.jcrRatings().stream()
                .map(
                    v ->
                        PortalVenueDetailResponse.JcrRating.builder()
                            .year(v.year())
                            .impactFactor(v.impactFactor())
                            .quartile(v.quartile())
                            .subject(v.subject())
                            .jifRank(v.jifRank())
                            .jifPercentile(v.jifPercentile())
                            .build())
                .toList())
        .casRatings(
            model.casRatings().stream()
                .map(
                    v ->
                        PortalVenueDetailResponse.CasRating.builder()
                            .year(v.year())
                            .edition(v.edition())
                            .majorCategory(v.majorCategory())
                            .majorQuartile(v.majorQuartile())
                            .minorSubject(v.minorSubject())
                            .minorQuartile(v.minorQuartile())
                            .isTop(v.isTop())
                            .isReview(v.isReview())
                            .build())
                .toList())
        .scopusRatings(
            model.scopusRatings().stream()
                .map(
                    v ->
                        PortalVenueDetailResponse.ScopusRating.builder()
                            .year(v.year())
                            .citeScore(v.citeScore())
                            .sjr(v.sjr())
                            .snip(v.snip())
                            .quartile(v.quartile())
                            .percentile(v.percentile())
                            .build())
                .toList())
        .yearlyStats(
            model.yearlyStats().stream()
                .map(
                    v ->
                        PortalVenueDetailResponse.YearlyStat.of(
                            v.year(), v.worksCount(), v.citedByCount(), v.oaWorksCount()))
                .toList())
        .identifiers(
            model.identifiers().stream()
                .map(v -> PortalVenueDetailResponse.Identifier.of(v.type(), v.value(), v.primary()))
                .toList())
        .build();
  }

  private String toSource(String provenanceCode) {
    if (provenanceCode == null) {
      return null;
    }
    try {
      return ProvenanceCode.parse(provenanceCode).getDescription();
    } catch (IllegalArgumentException e) {
      return provenanceCode;
    }
  }

  /// 将文献详情读模型转为响应 DTO。
  ///
  /// @param model 文献详情读模型
  /// @return 响应 DTO
  public PortalPublicationDetailResponse toPublicationDetailResponse(
      PublicationDetailReadModel model) {
    return PortalPublicationDetailResponse.builder()
        .id(Long.toString(model.id()))
        .title(model.title())
        .originalTitle(model.originalTitle())
        .venueId(model.venueId() != null ? Long.toString(model.venueId()) : null)
        .venueName(model.venueName())
        .publicationYear(model.publicationYear())
        .evidenceLevel(EvidenceLevelView.of(model.evidenceLevel()))
        .abstractType(model.abstractType())
        .abstractSections(
            model.abstractSections().stream()
                .map(s -> PortalPublicationDetailResponse.AbstractSection.of(s.label(), s.text()))
                .toList())
        .abstractPlainText(model.abstractPlainText())
        .doi(model.doi())
        .pmid(model.pmid())
        .pmcid(model.pmcid())
        .pii(model.pii())
        .primaryType(model.primaryType())
        .publicationTypes(model.publicationTypes())
        .citationCount(model.citationCount())
        .numberOfReferences(model.numberOfReferences())
        .conflictOfInterest(model.conflictOfInterest())
        .isOa(model.isOa())
        .oaStatus(model.oaStatus())
        .authors(
            model.authors().stream()
                .map(
                    a ->
                        PortalPublicationDetailResponse.Author.builder()
                            .order(a.order())
                            .first(a.first())
                            .corresponding(a.corresponding())
                            .name(a.name())
                            .affiliation(a.affiliation())
                            .build())
                .toList())
        .meshHeadings(
            model.meshHeadings().stream()
                .map(
                    m ->
                        PortalPublicationDetailResponse.MeshHeading.of(
                            m.descriptorUi(), m.term(), m.major()))
                .toList())
        .keywords(model.keywords())
        .funding(
            model.funding().stream()
                .map(
                    f ->
                        PortalPublicationDetailResponse.Funding.of(
                            f.funder(), f.grantId(), f.country()))
                .toList())
        .dates(
            model.dates().stream()
                .map(d -> PortalPublicationDetailResponse.PublicationDate.of(d.type(), d.date()))
                .toList())
        .aiSummary(null) // 当前无 LLM 摘要生成，恒为 null
        .source(toSource(model.provenanceCode()))
        .fullTextUrl(model.fullTextUrl())
        .bookmarks(0) // 无用户系统，恒为 0
        .estimatedReadMin(null) // TODO(read-time)：接入原文采集/字数后估算阅读时长
        .build();
  }

  private Integer toMinutesAgo(Instant lastSyncedAt) {
    if (lastSyncedAt == null) {
      return null;
    }
    // 钳到 0：lastSyncedAt 若晚于当前时间（时钟偏移等），避免负分钟透传到响应
    long minutes = Duration.between(lastSyncedAt, Instant.now()).toMinutes();
    return (int) Math.max(0L, minutes);
  }
}
