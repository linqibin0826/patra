package dev.patra.catalog.infra.adapter.read;

import dev.patra.catalog.domain.model.enums.FullTextStatus;
import dev.patra.catalog.domain.model.read.portal.FullTextAvailability;
import dev.patra.catalog.domain.model.read.portal.PublicationDetailReadModel;
import dev.patra.catalog.domain.model.read.portal.PublicationDetailReadModel.AbstractSectionView;
import dev.patra.catalog.domain.model.read.portal.PublicationDetailReadModel.AuthorView;
import dev.patra.catalog.domain.model.read.portal.PublicationDetailReadModel.FundingView;
import dev.patra.catalog.domain.model.read.portal.PublicationDetailReadModel.MeshHeadingView;
import dev.patra.catalog.domain.model.read.portal.PublicationDetailReadModel.PublicationDateView;
import dev.patra.catalog.domain.model.vo.publication.EvidenceLevel;
import dev.patra.catalog.domain.model.vo.publication.PublicationDate;
import dev.patra.catalog.domain.port.read.PublicationDetailReadPort;
import dev.patra.catalog.infra.persistence.dao.PublicationDetailDao;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/// 文献详情 CQRS 读适配器。
///
/// 主行通过 native query 一次性获取文献基本信息、期刊名、摘要；
/// 子表数据通过 5 条独立查询填充，无 N+1（每次请求仅查询单条文献）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class PublicationDetailReadAdapter implements PublicationDetailReadPort {

  /// 出版类型拼接分隔符，与 SQL 中 `string_agg(..., E'\x1f', ...)` 一致（U+001F）。
  private static final String DELIMITER = "";

  private final PublicationDetailDao publicationDetailDao;

  /// Spring 托管的 Jackson 3 JsonMapper。
  ///
  /// 写端 FormatMapper 是 starter-jpa 内的独立 Jackson 3 实例，二者配置不共享——
  /// 段落 wire format 由 `PublicationJpaMapperTest` 的 key 集合断言钉死。
  private final JsonMapper objectMapper;

  @Override
  public Optional<PublicationDetailReadModel> findById(long id) {
    return publicationDetailDao
        .findDetailById(id)
        .map(
            main -> {
              List<String> types = splitTypes(main.getPublicationTypesAgg());
              EvidenceLevel evidenceLevel = EvidenceLevel.classify(types);
              String primaryType = types.isEmpty() ? null : types.get(0);
              boolean retracted = Boolean.TRUE.equals(main.getRetracted());
              FullTextAvailability availability =
                  FullTextAvailability.of(
                      main.getFullTextStatus() == null
                          ? null
                          : FullTextStatus.valueOf(main.getFullTextStatus()),
                      Boolean.TRUE.equals(main.getHasPdfObject()),
                      retracted);

              return PublicationDetailReadModel.builder()
                  .id(main.getId())
                  .title(main.getTitle())
                  .originalTitle(main.getOriginalTitle())
                  .venueId(main.getVenueId())
                  .venueName(main.getVenueName())
                  .publicationYear(main.getPublicationYear())
                  .publishedDate(
                      PublicationDate.isoString(
                          main.getPublicationYear(),
                          main.getPublicationMonth(),
                          main.getPublicationDay()))
                  .evidenceLevel(evidenceLevel)
                  .abstractType(main.getAbstractType())
                  .abstractSections(
                      parseSections(id, main.getAbstractType(), main.getStructuredSectionsJson()))
                  .abstractPlainText(main.getAbstractPlainText())
                  .doi(main.getDoi())
                  .pmid(main.getPmid())
                  .pmcid(main.getPmcid())
                  .pii(main.getPii())
                  .primaryType(primaryType)
                  .publicationTypes(types)
                  .citationCount(main.getCitationCount())
                  .numberOfReferences(main.getNumberOfReferences())
                  .conflictOfInterest(main.getConflictOfInterest())
                  .isOa(main.getIsOa())
                  .oaStatus(main.getOaStatus())
                  .provenanceCode(main.getProvenanceCode())
                  .fullTextUrl(main.getFullTextUrl())
                  .retracted(retracted)
                  .hasFullText(availability.readable())
                  .hasPdf(availability.pdfDownloadable())
                  .pdfSizeBytes(availability.pdfDownloadable() ? main.getPdfSize() : null)
                  .wordCount(main.getWordCount())
                  .authors(mapAuthors(publicationDetailDao.findAuthorsByPublicationId(id)))
                  .meshHeadings(
                      mapMeshHeadings(publicationDetailDao.findMeshHeadingsByPublicationId(id)))
                  .keywords(mapKeywords(publicationDetailDao.findKeywordsByPublicationId(id)))
                  .funding(mapFunding(publicationDetailDao.findFundingByPublicationId(id)))
                  .dates(mapDates(publicationDetailDao.findDatesByPublicationId(id)))
                  .build();
            });
  }

  private List<String> splitTypes(String agg) {
    if (agg == null || agg.isBlank()) {
      return List.of();
    }
    return List.of(agg.split(DELIMITER, -1));
  }

  /// 解析结构化摘要 JSON（有序数组形态）。
  ///
  /// 解析失败降级为空列表并 error 记录（重建后数据格式可信，失败即 bug 必须响）；
  /// 不打印摘要正文，仅记 id / 类型 / 异常。
  ///
  /// @param publicationId 文献 ID
  /// @param abstractType 摘要类型
  /// @param json 结构化摘要 JSON 文本
  /// @return 摘要段落视图列表，解析失败返回空列表
  private List<AbstractSectionView> parseSections(
      long publicationId, String abstractType, String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<List<AbstractSectionView>>() {});
    } catch (Exception e) {
      log.error(
          "structured_sections 解析失败，降级空列表：publicationId={}, abstractType={}",
          publicationId,
          abstractType,
          e);
      return List.of();
    }
  }

  private List<AuthorView> mapAuthors(List<PublicationAuthorRow> rows) {
    return rows.stream()
        .map(
            r ->
                AuthorView.builder()
                    .order(r.getOrder() != null ? r.getOrder() : 0)
                    .first(Boolean.TRUE.equals(r.getFirst()))
                    .corresponding(Boolean.TRUE.equals(r.getCorresponding()))
                    .name(r.getName())
                    .affiliation(r.getAffiliation())
                    .build())
        .toList();
  }

  private List<MeshHeadingView> mapMeshHeadings(List<PublicationMeshRow> rows) {
    return rows.stream()
        .map(
            r ->
                MeshHeadingView.of(
                    r.getDescriptorUi(), r.getTerm(), Boolean.TRUE.equals(r.getMajor())))
        .toList();
  }

  private List<String> mapKeywords(List<PublicationKeywordRow> rows) {
    return rows.stream().map(PublicationKeywordRow::getTerm).toList();
  }

  private List<FundingView> mapFunding(List<PublicationFundingRow> rows) {
    return rows.stream()
        .map(r -> FundingView.of(r.getFunder(), r.getGrantId(), r.getCountry()))
        .toList();
  }

  /// 日期行按存储的精度输出降精度 ISO 字符串，不补出原文没有的月日。
  ///
  /// @param rows 日期行
  /// @return 日期视图
  private List<PublicationDateView> mapDates(List<PublicationDateRow> rows) {
    return rows.stream()
        .map(
            r -> {
              boolean hasMonth = !"year".equals(r.getPrecision());
              boolean hasDay = "day".equals(r.getPrecision());
              return PublicationDateView.of(
                  r.getType(),
                  PublicationDate.isoString(
                      r.getYear(), hasMonth ? r.getMonth() : null, hasDay ? r.getDay() : null));
            })
        .toList();
  }
}
