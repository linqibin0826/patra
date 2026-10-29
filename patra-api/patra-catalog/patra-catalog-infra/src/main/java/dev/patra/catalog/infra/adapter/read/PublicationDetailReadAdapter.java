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
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
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

  /// 按 ID 查询文献详情主行（含期刊名、摘要 LEFT JOIN），排除软删。
  private static final String FIND_DETAIL =
      """
      SELECT p.id AS "id", p.title AS "title", p.original_title AS "originalTitle",
        p.venue_id AS "venueId", v.title AS "venueName", p.publication_year AS "publicationYear",
        p.publication_month AS "publicationMonth", p.publication_day AS "publicationDay",
        a.abstract_type AS "abstractType", a.structured_sections::text AS "structuredSectionsJson",
        a.plain_text AS "abstractPlainText", p.doi AS "doi", p.pmid AS "pmid",
        (SELECT i.identifier_value FROM cat_publication_identifier i
           WHERE i.publication_id = p.id AND i.type = 'pmc' ORDER BY i.id ASC LIMIT 1) AS "pmcid",
        (SELECT i.identifier_value FROM cat_publication_identifier i
           WHERE i.publication_id = p.id AND i.type = 'pii' ORDER BY i.id ASC LIMIT 1) AS "pii",
        p.citation_count AS "citationCount", p.number_of_references AS "numberOfReferences",
        p.conflict_of_interest AS "conflictOfInterest", p.is_oa AS "isOa", p.oa_status AS "oaStatus",
        p.provenance_code AS "provenanceCode", m.full_text_url AS "fullTextUrl",
        (SELECT string_agg(pt.type_value, E'\\x1f' ORDER BY pt.type_order ASC NULLS LAST, pt.id ASC)
           FROM cat_publication_type pt WHERE pt.publication_id = p.id) AS "publicationTypesAgg",
        f.status AS "fullTextStatus", (f.pdf_object_key IS NOT NULL) AS "hasPdfObject",
        f.pdf_size AS "pdfSize", f.word_count AS "wordCount",
        (EXISTS (SELECT 1 FROM cat_publication_type rt
                  WHERE rt.publication_id = p.id AND rt.type_id = 'D016441')
         OR f.unavailable_reason IS NOT DISTINCT FROM 'RETRACTED') AS "retracted"
      FROM cat_publication p
      LEFT JOIN cat_venue v ON v.id = p.venue_id
      LEFT JOIN cat_publication_abstract a ON a.publication_id = p.id
      LEFT JOIN cat_publication_metadata m ON m.publication_id = p.id
      LEFT JOIN cat_publication_fulltext f ON f.publication_id = p.id
      WHERE p.id = :id AND p.deleted_at IS NULL
      """;

  /// 作者列表，按 author_order 升序；作者为文献快照（行内 `display_name`），不受 cat_author 软删 / 合并影响。
  private static final String FIND_AUTHORS =
      """
      SELECT pa.author_order AS "order", pa.is_first_author AS "first",
        pa.is_corresponding_author AS "corresponding", pa.display_name AS "name",
        (SELECT aff.affiliation_string FROM cat_publication_author_affiliation aff
           WHERE aff.pub_author_id = pa.id ORDER BY aff.affiliation_order ASC, aff.id ASC LIMIT 1) AS "affiliation"
      FROM cat_publication_author pa
      WHERE pa.publication_id = :id
      ORDER BY pa.author_order ASC, pa.id ASC
      """;

  /// MeSH 标引，按 heading_order、id 升序。
  private static final String FIND_MESH_HEADINGS =
      """
      SELECT mh.descriptor_ui AS "descriptorUi", d.name AS "term", mh.is_major_topic AS "major"
      FROM cat_publication_mesh_heading mh
      LEFT JOIN cat_mesh_descriptor d ON d.ui = mh.descriptor_ui
      WHERE mh.publication_id = :id
      ORDER BY mh.heading_order ASC NULLS LAST, mh.id ASC
      """;

  /// 关键词，按 order_num、id 升序。
  private static final String FIND_KEYWORDS =
      """
      SELECT k.term AS "term"
      FROM cat_publication_keyword pk
      JOIN cat_keyword k ON k.id = pk.keyword_id
      WHERE pk.publication_id = :id
      ORDER BY pk.order_num ASC NULLS LAST, pk.id ASC
      """;

  /// 资助信息，按 funding_order、id 升序。
  private static final String FIND_FUNDING =
      """
      SELECT f.funder_name_raw AS "funder", f.grant_id AS "grantId", f.country_raw AS "country"
      FROM cat_publication_funding f
      WHERE f.publication_id = :id
      ORDER BY f.funding_order ASC, f.id ASC
      """;

  /// 出版日期，按 order_num、id 升序。
  private static final String FIND_DATES =
      """
      SELECT d.date_type AS "type", d.year AS "year", d.month AS "month", d.day AS "day",
        d.date_precision AS "precision"
      FROM cat_publication_date d
      WHERE d.publication_id = :id
      ORDER BY d.order_num ASC NULLS LAST, d.id ASC
      """;

  private final JdbcClient jdbc;

  /// Spring 托管的 Jackson 3 JsonMapper。
  ///
  /// 写端 FormatMapper 是 starter-jpa 内的独立 Jackson 3 实例，二者配置不共享——
  /// 段落 wire format 由 `PublicationJpaMapperTest` 的 key 集合断言钉死。
  private final JsonMapper objectMapper;

  @Override
  public Optional<PublicationDetailReadModel> findById(long id) {
    return jdbc.sql(FIND_DETAIL)
        .param("id", id)
        .query(PublicationDetailRow.class)
        .optional()
        .map(
            main -> {
              List<String> types = splitTypes(main.publicationTypesAgg());
              EvidenceLevel evidenceLevel = EvidenceLevel.classify(types);
              String primaryType = types.isEmpty() ? null : types.get(0);
              boolean retracted = Boolean.TRUE.equals(main.retracted());
              FullTextAvailability availability =
                  FullTextAvailability.of(
                      main.fullTextStatus() == null
                          ? null
                          : FullTextStatus.valueOf(main.fullTextStatus()),
                      Boolean.TRUE.equals(main.hasPdfObject()),
                      retracted);

              return PublicationDetailReadModel.builder()
                  .id(main.id())
                  .title(main.title())
                  .originalTitle(main.originalTitle())
                  .venueId(main.venueId())
                  .venueName(main.venueName())
                  .publicationYear(main.publicationYear())
                  .publishedDate(
                      PublicationDate.isoString(
                          main.publicationYear(), main.publicationMonth(), main.publicationDay()))
                  .evidenceLevel(evidenceLevel)
                  .abstractType(main.abstractType())
                  .abstractSections(
                      parseSections(id, main.abstractType(), main.structuredSectionsJson()))
                  .abstractPlainText(main.abstractPlainText())
                  .doi(main.doi())
                  .pmid(main.pmid())
                  .pmcid(main.pmcid())
                  .pii(main.pii())
                  .primaryType(primaryType)
                  .publicationTypes(types)
                  .citationCount(main.citationCount())
                  .numberOfReferences(main.numberOfReferences())
                  .conflictOfInterest(main.conflictOfInterest())
                  .isOa(main.isOa())
                  .oaStatus(main.oaStatus())
                  .provenanceCode(main.provenanceCode())
                  .fullTextUrl(main.fullTextUrl())
                  .retracted(retracted)
                  .hasFullText(availability.readable())
                  .hasPdf(availability.pdfDownloadable())
                  .pdfSizeBytes(availability.pdfDownloadable() ? main.pdfSize() : null)
                  .wordCount(main.wordCount())
                  .authors(mapAuthors(children(FIND_AUTHORS, id, PublicationAuthorRow.class)))
                  .meshHeadings(
                      mapMeshHeadings(children(FIND_MESH_HEADINGS, id, PublicationMeshRow.class)))
                  .keywords(mapKeywords(children(FIND_KEYWORDS, id, PublicationKeywordRow.class)))
                  .funding(mapFunding(children(FIND_FUNDING, id, PublicationFundingRow.class)))
                  .dates(mapDates(children(FIND_DATES, id, PublicationDateRow.class)))
                  .build();
            });
  }

  /// 按文献 ID 查一张子表。
  ///
  /// @param sql 查询
  /// @param publicationId 文献 ID
  /// @param rowType 行类型
  /// @param <T> 行类型
  /// @return 行
  private <T> List<T> children(String sql, long publicationId, Class<T> rowType) {
    return jdbc.sql(sql).param("id", publicationId).query(rowType).list();
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
                    .order(r.order() != null ? r.order() : 0)
                    .first(Boolean.TRUE.equals(r.first()))
                    .corresponding(Boolean.TRUE.equals(r.corresponding()))
                    .name(r.name())
                    .affiliation(r.affiliation())
                    .build())
        .toList();
  }

  private List<MeshHeadingView> mapMeshHeadings(List<PublicationMeshRow> rows) {
    return rows.stream()
        .map(r -> MeshHeadingView.of(r.descriptorUi(), r.term(), Boolean.TRUE.equals(r.major())))
        .toList();
  }

  private List<String> mapKeywords(List<PublicationKeywordRow> rows) {
    return rows.stream().map(PublicationKeywordRow::term).toList();
  }

  private List<FundingView> mapFunding(List<PublicationFundingRow> rows) {
    return rows.stream().map(r -> FundingView.of(r.funder(), r.grantId(), r.country())).toList();
  }

  /// 日期行按存储的精度输出降精度 ISO 字符串，不补出原文没有的月日。
  ///
  /// @param rows 日期行
  /// @return 日期视图
  private List<PublicationDateView> mapDates(List<PublicationDateRow> rows) {
    return rows.stream()
        .map(
            r -> {
              boolean hasMonth = !"year".equals(r.precision());
              boolean hasDay = "day".equals(r.precision());
              return PublicationDateView.of(
                  r.type(),
                  PublicationDate.isoString(
                      r.year(), hasMonth ? r.month() : null, hasDay ? r.day() : null));
            })
        .toList();
  }
}
