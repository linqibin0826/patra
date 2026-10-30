package dev.patra.catalog.infra.publication.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.kernel.model.enums.PublicationIdentifierType;
import dev.patra.catalog.domain.kernel.model.vo.Pagination;
import dev.patra.catalog.domain.kernel.model.vo.VenueId;
import dev.patra.catalog.domain.organization.port.read.FunderLookupPort;
import dev.patra.catalog.domain.publication.model.aggregate.PublicationAggregate;
import dev.patra.catalog.domain.publication.model.aggregate.VenueInstanceAggregate;
import dev.patra.catalog.domain.publication.model.enums.AbstractType;
import dev.patra.catalog.domain.publication.model.enums.PublicationMedium;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Abstract;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.AbstractSection;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.AlternativeAbstract;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.DescriptorName;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.FundingInfo;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Identifier;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Investigator;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Journal;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Keyword;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.KeywordSet;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.MeshHeading;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.PersonalNameSubject;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.PublicationDates;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.PublicationType;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.QualifierName;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.SupplMeshName;
import dev.patra.catalog.domain.publication.model.vo.JournalInstanceParams;
import dev.patra.catalog.domain.publication.model.vo.PartialDate;
import dev.patra.catalog.domain.publication.model.vo.PublicationAbstract;
import dev.patra.catalog.domain.publication.model.vo.PublicationAuthorSnapshot;
import dev.patra.catalog.domain.publication.model.vo.PublicationIdentifier;
import dev.patra.catalog.domain.publication.model.vo.VenueInstanceId;
import dev.patra.catalog.domain.publication.port.gateway.VenueInstanceGateway;
import dev.patra.catalog.domain.publication.port.lookup.LanguageLookupPort;
import dev.patra.catalog.domain.venue.port.read.VenueLookupPort;
import dev.patra.catalog.infra.publication.batch.PublicationImportResult.PublicationDateData;
import dev.patra.catalog.infra.publication.batch.PublicationImportResult.QualifierData;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// PubmedArticleItemProcessor 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedArticleItemProcessor")
@ExtendWith(MockitoExtension.class)
class PubmedArticleItemProcessorTest {

  @Mock private VenueLookupPort venueLookupPort;

  @Mock private VenueInstanceGateway venueInstanceGateway;

  @Mock private LanguageLookupPort languageLookupPort;

  @Mock private FunderLookupPort funderLookupPort;

  private PubmedArticleItemProcessor processor;

  private static final String PMID = "12345678";
  private static final String DOI = "10.1234/example";
  private static final String NLM_ID = "101234567";
  private static final String ISSN = "1234-5678";
  private static final Long VENUE_ID = 1L;
  private static final Long VENUE_INSTANCE_ID = 100L;
  private static final Long ORG_ID_NIH = 1001L;
  private static final Long ORG_ID_NSFC = 1002L;

  @BeforeEach
  void setUp() {
    // 配置默认的语言查找行为（使用 lenient 避免 UnnecessaryStubbingException）
    lenient().when(languageLookupPort.resolve(anyString())).thenReturn("en");

    // 配置默认的资助机构查找行为
    lenient()
        .when(funderLookupPort.findByPriority(eq("100000002"), any()))
        .thenReturn(Optional.of(ORG_ID_NIH));
    lenient()
        .when(funderLookupPort.findByPriority(eq("501100001809"), any()))
        .thenReturn(Optional.of(ORG_ID_NSFC));
    lenient().when(funderLookupPort.findByPriority(eq(null), any())).thenReturn(Optional.empty());

    processor =
        new PubmedArticleItemProcessor(
            venueLookupPort,
            venueInstanceGateway,
            languageLookupPort,
            funderLookupPort,
            "test-batch-001");
  }

  @Nested
  @DisplayName("引用数据：日期精度、页码、ISO 缩写、PII")
  class CitationDataTest {

    private CanonicalPublication citationPublication(PartialDate published) {
      return CanonicalPublication.builder()
          .identifiers(
              List.of(
                  Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build(),
                  Identifier.builder()
                      .type(PublicationIdentifierType.PII)
                      .value("e0123456")
                      .build()))
          .title("Citation Article")
          .journal(
              Journal.builder()
                  .nlmUniqueId(NLM_ID)
                  .isoAbbreviation("PLoS One")
                  .medlineAbbreviation("PLoS One")
                  .build())
          .pagination(
              CanonicalPublication.Pagination.builder()
                  .medlinePgn("1451-61")
                  .startPage("1451")
                  .endPage("1461")
                  .build())
          .dates(PublicationDates.builder().published(published).build())
          .build();
    }

    private PublicationImportResult processWithVenue(CanonicalPublication publication)
        throws Exception {
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());
      return processor.process(publication);
    }

    @Test
    @DisplayName("只到年：聚合不带月日，主出版日期行为年精度")
    void should_carry_year_only_date_without_fabricated_month_day() throws Exception {
      PublicationImportResult result =
          processWithVenue(citationPublication(PartialDate.ofYear(2023)));

      assertThat(result.publication().getPublicationYear()).isEqualTo(2023);
      assertThat(result.publication().getPublicationMonth()).isNull();
      assertThat(result.publication().getPublicationDay()).isNull();
      PublicationDateData published = result.dates().getFirst();
      assertThat(published.datePrecision()).isEqualTo("year");
      assertThat(published.month()).isNull();
      assertThat(published.day()).isNull();
    }

    @Test
    @DisplayName("缺月日时期刊实例参数的月日为 null，不补 1")
    void should_pass_null_month_day_to_journal_instance_when_absent() throws Exception {
      processWithVenue(citationPublication(PartialDate.ofYear(2023)));

      ArgumentCaptor<JournalInstanceParams> captor =
          ArgumentCaptor.forClass(JournalInstanceParams.class);
      verify(venueInstanceGateway).findOrCreateJournalInstance(captor.capture());
      assertThat(captor.getValue().getPublicationMonth()).isNull();
      assertThat(captor.getValue().getPublicationDay()).isNull();
    }

    @Test
    @DisplayName("页码、ISO 缩写进聚合，PII 进扩展标识符")
    void should_add_pii_identifier_and_pagination_and_iso_abbreviation() throws Exception {
      PublicationImportResult result =
          processWithVenue(citationPublication(PartialDate.ofYearMonth(2022, 10)));

      PublicationAggregate aggregate = result.publication();
      assertThat(aggregate.getPublicationMonth()).isEqualTo(10);
      assertThat(aggregate.getPagination()).isEqualTo(Pagination.of("1451-61", "1451", "1461"));
      assertThat(aggregate.getJournalIsoAbbreviation()).isEqualTo("PLoS One");
      assertThat(aggregate.getExtendedIdentifiers())
          .anySatisfy(
              id -> {
                assertThat(id.type()).isEqualTo(PublicationIdentifierType.PII);
                assertThat(id.value()).isEqualTo("e0123456");
              });
    }
  }

  @Nested
  @DisplayName("process()")
  class ProcessTest {

    @Test
    @DisplayName("缺少 PMID 时应该返回 null（跳过）")
    void should_return_null_when_pmid_missing() throws Exception {
      // given
      CanonicalPublication publication =
          CanonicalPublication.builder()
              .title("No PMID")
              .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
              .dates(
                  PublicationDates.builder()
                      .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                      .build())
              .build();

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNull();
      verify(venueLookupPort, never()).findByPriority(any(), any());
    }

    @Test
    @DisplayName("Processor 不负责 DOI 去重，同一 DOI 的两条记录都应继续处理")
    void should_not_deduplicate_doi_in_processor() throws Exception {
      // given
      CanonicalPublication first = createPublicationWithPmidAndDoi("92345678", DOI);
      CanonicalPublication second = createPublicationWithPmidAndDoi("92345679", DOI);
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult firstResult = processor.process(first);
      PublicationImportResult secondResult = processor.process(second);

      // then
      assertThat(firstResult).isNotNull();
      assertThat(secondResult).isNotNull();
    }

    @Test
    @DisplayName("被业务条件跳过的记录不会影响后续处理")
    void should_not_affect_following_records_when_record_skipped_by_business_rules()
        throws Exception {
      // given
      CanonicalPublication skipped =
          CanonicalPublication.builder()
              .identifiers(
                  List.of(
                      Identifier.builder()
                          .type(PublicationIdentifierType.PMID)
                          .value("93333331")
                          .build(),
                      Identifier.builder().type(PublicationIdentifierType.DOI).value(DOI).build()))
              .title("Skipped Article")
              .build(); // 无 Journal，命中业务跳过路径
      CanonicalPublication valid = createPublicationWithPmidAndDoi("93333332", DOI);

      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult skippedResult = processor.process(skipped);
      PublicationImportResult validResult = processor.process(valid);

      // then
      assertThat(skippedResult).isNull();
      assertThat(validResult).isNotNull();
    }

    @Test
    @DisplayName("无法匹配 Venue 时应该返回 null（跳过）")
    void should_return_null_when_venue_not_matched() throws Exception {
      // given
      CanonicalPublication publication = createPublication(PMID);
      when(venueLookupPort.findByPriority(any(), any())).thenReturn(Optional.empty());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNull();
      verify(venueInstanceGateway, never()).findOrCreateJournalInstance(any());
    }

    @Test
    @DisplayName("成功匹配 Venue 时应该创建 PublicationImportResult")
    void should_create_publication_aggregate_when_venue_matched() throws Exception {
      // given
      CanonicalPublication publication = createFullPublication();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));

      VenueInstanceAggregate venueInstance = createVenueInstance();
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(venueInstance);

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      PublicationAggregate aggregate = result.publication();
      assertThat(aggregate.getPmid()).isEqualTo(PMID);
      assertThat(aggregate.getDoi()).isEqualTo(DOI);
      assertThat(aggregate.getTitle()).isEqualTo("Test Article Title");
      assertThat(aggregate.getPublicationYear()).isEqualTo(2024);
      assertThat(aggregate.getVenueId().value()).isEqualTo(VENUE_ID);
      assertThat(aggregate.getVenueInstanceId().value()).isEqualTo(VENUE_INSTANCE_ID);
    }

    @Test
    @DisplayName("title 有值时应优先使用 title")
    void should_prefer_title_when_title_present() throws Exception {
      // given
      CanonicalPublication publication =
          createPublicationWithTitles(PMID, "Primary Title", "Original Title");
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.publication().getTitle()).isEqualTo("Primary Title");
    }

    @Test
    @DisplayName("title 为空时应回退使用 originalTitle")
    void should_fallback_to_original_title_when_title_is_blank() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithTitles(PMID, "   ", "  备用标题  ");
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.publication().getTitle()).isEqualTo("备用标题");
    }

    @Test
    @DisplayName("title 和 originalTitle 都为空时应返回 null")
    void should_return_null_when_both_title_and_original_title_blank() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithTitles(PMID, "   ", null);

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("title 和 originalTitle 都为空时应短路，不调用 Venue/Gateway")
    void should_short_circuit_when_both_titles_blank() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithTitles(PMID, "", "  ");

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNull();
      verify(venueLookupPort, never()).findByPriority(any(), any());
      verify(venueInstanceGateway, never()).findOrCreateJournalInstance(any());
    }

    @Test
    @DisplayName("无 DOI 时应该只使用 PMID 创建标识符")
    void should_create_identifiers_with_pmid_only_when_no_doi() throws Exception {
      // given
      CanonicalPublication publication =
          CanonicalPublication.builder()
              .identifiers(
                  List.of(
                      Identifier.builder()
                          .type(PublicationIdentifierType.PMID)
                          .value(PMID)
                          .build()))
              .title("Test Article")
              .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
              .dates(
                  PublicationDates.builder()
                      .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                      .build())
              .build();

      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));

      VenueInstanceAggregate venueInstance = createVenueInstance();
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(venueInstance);

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      PublicationAggregate aggregate = result.publication();
      assertThat(aggregate.getPmid()).isEqualTo(PMID);
      assertThat(aggregate.getDoi()).isNull();
    }

    @Test
    @DisplayName("应该正确映射纯文本摘要")
    void should_map_plain_text_abstract() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithPlainTextAbstract();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      PublicationAggregate aggregate = result.publication();
      assertThat(aggregate.hasAbstract()).isTrue();
      PublicationAbstract abstractContent = aggregate.getPublicationAbstract();
      assertThat(abstractContent.plainText()).isEqualTo("This is a test abstract content.");
      assertThat(abstractContent.copyright()).isEqualTo("Copyright 2024");
      assertThat(abstractContent.isStructured()).isFalse();
    }

    @Test
    @DisplayName("应该正确映射结构化摘要")
    void should_map_structured_abstract() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithStructuredAbstract();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      PublicationAggregate aggregate = result.publication();
      assertThat(aggregate.hasAbstract()).isTrue();
      PublicationAbstract abstractContent = aggregate.getPublicationAbstract();
      assertThat(abstractContent.isStructured()).isTrue();
      assertThat(abstractContent.structuredSections()).hasSize(4);
      assertThat(abstractContent.findSectionsByLabel("BACKGROUND")).hasSize(1);
      assertThat(abstractContent.findSectionsByLabel("METHODS")).hasSize(1);
      assertThat(abstractContent.findSectionsByLabel("RESULTS")).hasSize(1);
      assertThat(abstractContent.findSectionsByLabel("CONCLUSIONS")).hasSize(1);
      assertThat(abstractContent.plainText())
          .isEqualTo(
              "BACKGROUND: Background content.\n"
                  + "METHODS: Methods content.\n"
                  + "RESULTS: Results content.\n"
                  + "CONCLUSIONS: Conclusions content.");
    }

    @Test
    @DisplayName("应该提取扩展标识符 (PMC, PII)")
    void should_extract_extended_identifiers() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithExtendedIdentifiers();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      PublicationAggregate aggregate = result.publication();
      List<PublicationIdentifier> extIds = aggregate.getExtendedIdentifiers();
      assertThat(extIds).hasSize(2);
      assertThat(extIds).anyMatch(id -> id.isPmc() && id.value().equals("PMC1234567"));
      assertThat(extIds).anyMatch(id -> id.type() == PublicationIdentifierType.PII);
    }

    @Test
    @DisplayName("应该正确映射 PublicationMedium")
    void should_map_media_type() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithPublicationMedium();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.publication().getMediaType()).isEqualTo(PublicationMedium.ELECTRONIC);
    }

    @Test
    @DisplayName("应该正确填充 numberOfReferences 和 conflictOfInterest")
    void should_fill_number_of_references_and_conflict_of_interest() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithMetadata();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      PublicationAggregate aggregate = result.publication();
      assertThat(aggregate.getNumberOfReferences()).isEqualTo(42);
      assertThat(aggregate.getConflictOfInterest())
          .isEqualTo("The authors declare no conflict of interest.");
    }

    @Test
    @DisplayName("应该使用出版状态转换枚举值")
    void should_convert_publication_status() throws Exception {
      // given
      CanonicalPublication publication =
          CanonicalPublication.builder()
              .identifiers(
                  List.of(
                      Identifier.builder()
                          .type(PublicationIdentifierType.PMID)
                          .value(PMID)
                          .build()))
              .title("Test Article")
              .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
              .dates(
                  PublicationDates.builder()
                      .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                      .build())
              .publicationStatus("epublish")
              .build();

      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));

      VenueInstanceAggregate venueInstance = createVenueInstance();
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(venueInstance);

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.publication().getPublicationStatus()).isNotNull();
      assertThat(result.publication().getPublicationStatus().getCode()).isEqualTo("epublish");
    }

    @Test
    @DisplayName("应该正确处理 MeSH 标引数据")
    void should_process_mesh_headings() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithMeshHeadings();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasMeshHeadings()).isTrue();
      assertThat(result.meshHeadings()).hasSize(1);

      var heading = result.meshHeadings().getFirst();
      assertThat(heading.descriptorUi()).isEqualTo("D000001");
      assertThat(heading.majorTopic()).isTrue();
      assertThat(heading.hasQualifiers()).isTrue();
      assertThat(heading.qualifiers()).hasSize(1);
      assertThat(heading.qualifiers().getFirst().qualifierUi()).isEqualTo("Q000379");
    }

    @Test
    @DisplayName("同一标引下重复的限定词应该按 UI 去重并保留首次出现的顺序")
    void should_deduplicate_qualifiers_by_ui_when_same_qualifier_listed_twice() throws Exception {
      // given：PMID 41601277 的 Glycine 标引把 Q000031 列了两次（第二次带 AutoHM="Y"）
      CanonicalPublication publication =
          createPublicationWithQualifiers(
              qualifier("Q000031", false),
              qualifier("Q000627", false),
              qualifier("Q000031", false));
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result.meshHeadings().getFirst().qualifiers())
          .containsExactly(
              QualifierData.of("Q000031", false, 1), QualifierData.of("Q000627", false, 2));
    }

    @Test
    @DisplayName("重复限定词任一次标为主要主题时，去重后应该标为主要主题")
    void should_mark_deduplicated_qualifier_major_when_any_occurrence_is_major() throws Exception {
      // given
      CanonicalPublication publication =
          createPublicationWithQualifiers(
              qualifier("Q000031", false), qualifier("Q000627", false), qualifier("Q000031", true));
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result.meshHeadings().getFirst().qualifiers())
          .containsExactly(
              QualifierData.of("Q000031", true, 1), QualifierData.of("Q000627", false, 2));
    }

    @Test
    @DisplayName("缺少 UI 的限定词应该被过滤，其余限定词连续编号")
    void should_skip_qualifier_without_ui_and_number_rest_continuously() throws Exception {
      // given
      CanonicalPublication publication =
          createPublicationWithQualifiers(
              qualifier(null, true), qualifier("Q000031", false), qualifier("Q000627", false));
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result.meshHeadings().getFirst().qualifiers())
          .containsExactly(
              QualifierData.of("Q000031", false, 1), QualifierData.of("Q000627", false, 2));
    }

    @Test
    @DisplayName("全部限定词都缺少 UI 时，标引的限定词应该为空")
    void should_return_empty_qualifiers_when_all_qualifiers_lack_ui() throws Exception {
      // given
      CanonicalPublication publication =
          createPublicationWithQualifiers(qualifier(null, false), qualifier(null, true));
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result.meshHeadings().getFirst().qualifiers()).isEmpty();
    }

    @Test
    @DisplayName("无 MeSH 数据时 meshHeadings 应该为空列表")
    void should_return_empty_mesh_headings_when_no_mesh_data() throws Exception {
      // given
      CanonicalPublication publication = createFullPublication();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasMeshHeadings()).isFalse();
      assertThat(result.meshHeadings()).isEmpty();
    }

    @Test
    @DisplayName("应该正确处理关键词数据")
    void should_process_keywords() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithKeywords();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasKeywords()).isTrue();
      assertThat(result.keywords()).hasSize(3);

      var keyword1 = result.keywords().get(0);
      assertThat(keyword1.source()).isEqualTo("author");
      assertThat(keyword1.term()).isEqualTo("machine learning");
      assertThat(keyword1.majorTopic()).isTrue();
      assertThat(keyword1.keywordOrder()).isEqualTo(1);

      var keyword2 = result.keywords().get(1);
      assertThat(keyword2.source()).isEqualTo("author");
      assertThat(keyword2.term()).isEqualTo("deep learning");
      assertThat(keyword2.majorTopic()).isFalse();

      var keyword3 = result.keywords().get(2);
      assertThat(keyword3.source()).isEqualTo("publisher");
      assertThat(keyword3.term()).isEqualTo("artificial intelligence");
    }

    @Test
    @DisplayName("无关键词数据时 keywords 应该为空列表")
    void should_return_empty_keywords_when_no_keyword_data() throws Exception {
      // given
      CanonicalPublication publication = createFullPublication();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasKeywords()).isFalse();
      assertThat(result.keywords()).isEmpty();
    }

    @Test
    @DisplayName("应该正确处理资助信息数据")
    void should_process_funding() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithFunding();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasFunding()).isTrue();
      assertThat(result.funding()).hasSize(2);

      var funding1 = result.funding().get(0);
      assertThat(funding1.organizationId()).isEqualTo(ORG_ID_NIH);
      assertThat(funding1.grantId()).isEqualTo("R01AI123456");
      assertThat(funding1.funderNameRaw()).isEqualTo("National Institutes of Health");
      assertThat(funding1.funderAcronymRaw()).isEqualTo("NIH");
      assertThat(funding1.funderIdentifierRaw()).isEqualTo("100000002");
      assertThat(funding1.countryRaw()).isEqualTo("United States");
      assertThat(funding1.fundingOrder()).isEqualTo(1);
      assertThat(funding1.provenanceCode()).isEqualTo("PUBMED");

      var funding2 = result.funding().get(1);
      assertThat(funding2.organizationId()).isEqualTo(ORG_ID_NSFC);
      assertThat(funding2.grantId()).isEqualTo("81970001");
      assertThat(funding2.funderNameRaw())
          .isEqualTo("National Natural Science Foundation of China");
      assertThat(funding2.funderAcronymRaw()).isEqualTo("NSFC");
      assertThat(funding2.countryRaw()).isEqualTo("China");
    }

    @Test
    @DisplayName("无资助信息数据时 funding 应该为空列表")
    void should_return_empty_funding_when_no_funding_data() throws Exception {
      // given
      CanonicalPublication publication = createFullPublication();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasFunding()).isFalse();
      assertThat(result.funding()).isEmpty();
    }

    @Test
    @DisplayName("应该正确处理出版类型数据")
    void should_process_publication_types() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithTypes();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasPublicationTypes()).isTrue();
      assertThat(result.publicationTypes()).hasSize(2);

      var type1 = result.publicationTypes().get(0);
      assertThat(type1.typeId()).isEqualTo("D016428");
      assertThat(type1.typeValue()).isEqualTo("Journal Article");
      assertThat(type1.vocabularySource()).isEqualTo("MeSH");
      assertThat(type1.typeOrder()).isEqualTo(1);

      var type2 = result.publicationTypes().get(1);
      assertThat(type2.typeId()).isEqualTo("D016454");
      assertThat(type2.typeValue()).isEqualTo("Review");
      assertThat(type2.vocabularySource()).isEqualTo("MeSH");
    }

    @Test
    @DisplayName("无出版类型数据时 publicationTypes 应该为空列表")
    void should_return_empty_publication_types_when_no_type_data() throws Exception {
      // given
      CanonicalPublication publication = createFullPublication();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasPublicationTypes()).isFalse();
      assertThat(result.publicationTypes()).isEmpty();
    }

    @Test
    @DisplayName("应该正确处理补充 MeSH 概念数据")
    void should_process_suppl_mesh_names() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithSupplMeshNames();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasSupplMeshNames()).isTrue();
      assertThat(result.supplMeshNames()).hasSize(2);

      var suppl1 = result.supplMeshNames().get(0);
      assertThat(suppl1.scrUi()).isEqualTo("C538003");
      assertThat(suppl1.supplOrder()).isEqualTo(1);

      var suppl2 = result.supplMeshNames().get(1);
      assertThat(suppl2.scrUi()).isEqualTo("C095232");
      assertThat(suppl2.supplOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("无补充 MeSH 概念数据时 supplMeshNames 应该为空列表")
    void should_return_empty_suppl_mesh_when_no_suppl_mesh_data() throws Exception {
      // given
      CanonicalPublication publication = createFullPublication();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasSupplMeshNames()).isFalse();
      assertThat(result.supplMeshNames()).isEmpty();
    }

    @Test
    @DisplayName("应该正确处理翻译摘要数据")
    void should_process_alternative_abstracts() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithAlternativeAbstracts();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());
      when(languageLookupPort.resolve("chi")).thenReturn("zh");
      when(languageLookupPort.resolve("jpn")).thenReturn("ja");

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasAlternativeAbstracts()).isTrue();
      assertThat(result.alternativeAbstracts()).hasSize(2);

      var abstract1 = result.alternativeAbstracts().get(0);
      assertThat(abstract1.languageCode()).isEqualTo("zh");
      assertThat(abstract1.abstractType()).isEqualTo("Publisher");
      assertThat(abstract1.plainText()).isEqualTo("这是中文翻译摘要。");
      assertThat(abstract1.copyright()).isEqualTo("版权所有 2024");
      assertThat(abstract1.abstractOrder()).isEqualTo(1);

      var abstract2 = result.alternativeAbstracts().get(1);
      assertThat(abstract2.languageCode()).isEqualTo("ja");
      assertThat(abstract2.abstractType()).isEqualTo("AIMSHP");
      assertThat(abstract2.plainText()).isEqualTo("これは日本語の翻訳要約です。");
      assertThat(abstract2.abstractOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("同语言不同来源类型的翻译摘要应该全部保留")
    void should_keep_same_language_alternative_abstracts_with_different_source_type()
        throws Exception {
      // given
      CanonicalPublication publication =
          createPublicationWithSameLanguageDifferentAlternativeAbstractTypes();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());
      when(languageLookupPort.resolve("fre")).thenReturn("fr");

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasAlternativeAbstracts()).isTrue();
      assertThat(result.alternativeAbstracts()).hasSize(2);

      var first = result.alternativeAbstracts().get(0);
      assertThat(first.languageCode()).isEqualTo("fr");
      assertThat(first.abstractType()).isEqualTo("Publisher");
      assertThat(first.abstractOrder()).isEqualTo(1);

      var second = result.alternativeAbstracts().get(1);
      assertThat(second.languageCode()).isEqualTo("fr");
      assertThat(second.abstractType()).isEqualTo("plain-language-summary");
      assertThat(second.abstractOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("无翻译摘要数据时 alternativeAbstracts 应该为空列表")
    void should_return_empty_alternative_abstracts_when_no_data() throws Exception {
      // given
      CanonicalPublication publication = createFullPublication();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasAlternativeAbstracts()).isFalse();
      assertThat(result.alternativeAbstracts()).isEmpty();
    }

    @Test
    @DisplayName("仅有段落无 text 的翻译摘要应保留并映射段落")
    void should_keep_alternative_abstract_with_sections_only() throws Exception {
      // given
      CanonicalPublication publication =
          CanonicalPublication.builder()
              .identifiers(
                  List.of(
                      Identifier.builder()
                          .type(PublicationIdentifierType.PMID)
                          .value(PMID)
                          .build()))
              .title("Test Article")
              .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
              .dates(
                  PublicationDates.builder()
                      .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                      .build())
              .alternativeAbstracts(
                  List.of(
                      AlternativeAbstract.builder()
                          .type("Publisher")
                          .language("chi")
                          .sections(
                              List.of(
                                  abstractSection("目的", "评估疗效。"),
                                  abstractSection(null, "补充说明。"),
                                  abstractSection("方法", "  ")))
                          .copyright("版权所有 2024")
                          .build()))
              .build();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());
      when(languageLookupPort.resolve("chi")).thenReturn("zh");

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.alternativeAbstracts()).hasSize(1);

      var alt = result.alternativeAbstracts().getFirst();
      assertThat(alt.languageCode()).isEqualTo("zh");
      assertThat(alt.plainText()).isEqualTo("目的: 评估疗效。\n补充说明。");
      assertThat(alt.sections()).hasSize(2);
      assertThat(alt.sections().get(0).label()).isEqualTo("目的");
      assertThat(alt.sections().get(1).label()).isNull();
      assertThat(alt.copyright()).isEqualTo("版权所有 2024");
    }

    @Test
    @DisplayName("text 与段落并存时 plainText 应取段落统一拼接形态")
    void should_regenerate_alternative_abstract_plain_text_from_sections() throws Exception {
      // given：PubMed parser 路径同时给出 text（空格拼接、丢 label）与 sections
      CanonicalPublication publication =
          CanonicalPublication.builder()
              .identifiers(
                  List.of(
                      Identifier.builder()
                          .type(PublicationIdentifierType.PMID)
                          .value(PMID)
                          .build()))
              .title("Test Article")
              .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
              .dates(
                  PublicationDates.builder()
                      .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                      .build())
              .alternativeAbstracts(
                  List.of(
                      AlternativeAbstract.builder()
                          .type("Publisher")
                          .language("chi")
                          .text("评估疗效。 得出结论。")
                          .sections(
                              List.of(
                                  abstractSection("目的", "评估疗效。"), abstractSection("结论", "得出结论。")))
                          .build()))
              .build();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());
      when(languageLookupPort.resolve("chi")).thenReturn("zh");

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.alternativeAbstracts()).hasSize(1);

      var alt = result.alternativeAbstracts().getFirst();
      assertThat(alt.plainText()).isEqualTo("目的: 评估疗效。\n结论: 得出结论。");
      assertThat(alt.sections()).hasSize(2);
    }

    @Test
    @DisplayName("应该正确处理文献日期数据")
    void should_process_publication_dates() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithDates();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasDates()).isTrue();
      assertThat(result.dates()).hasSize(3);

      // published 日期应该标记为 primary
      var publishedDate = result.dates().get(0);
      assertThat(publishedDate.dateType()).isEqualTo("published");
      assertThat(publishedDate.year()).isEqualTo(2024);
      assertThat(publishedDate.month()).isEqualTo(6);
      assertThat(publishedDate.day()).isEqualTo(15);
      assertThat(publishedDate.isPrimary()).isTrue();
      assertThat(publishedDate.orderNum()).isEqualTo(1);

      // received 日期
      var receivedDate = result.dates().get(1);
      assertThat(receivedDate.dateType()).isEqualTo("received");
      assertThat(receivedDate.year()).isEqualTo(2024);
      assertThat(receivedDate.month()).isEqualTo(1);
      assertThat(receivedDate.day()).isEqualTo(10);
      assertThat(receivedDate.isPrimary()).isFalse();
      assertThat(receivedDate.orderNum()).isEqualTo(2);

      // accepted 日期
      var acceptedDate = result.dates().get(2);
      assertThat(acceptedDate.dateType()).isEqualTo("accepted");
      assertThat(acceptedDate.year()).isEqualTo(2024);
      assertThat(acceptedDate.month()).isEqualTo(5);
      assertThat(acceptedDate.day()).isEqualTo(20);
      assertThat(acceptedDate.isPrimary()).isFalse();
    }

    @Test
    @DisplayName("PMC 放开日期应映射为 pmcrelease 类型的非主日期")
    void should_map_pmc_release_to_date_type_pmc_release() throws Exception {
      // given
      CanonicalPublication publication =
          CanonicalPublication.builder()
              .identifiers(
                  List.of(
                      Identifier.builder()
                          .type(PublicationIdentifierType.PMID)
                          .value(PMID)
                          .build()))
              .title("Test Article with PMC release")
              .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
              .dates(
                  PublicationDates.builder()
                      .published(PartialDate.ofDay(LocalDate.of(2026, 1, 1)))
                      .pmcRelease(PartialDate.ofDay(LocalDate.of(2027, 1, 15)))
                      .build())
              .build();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result.dates())
          .filteredOn(d -> "pmcrelease".equals(d.dateType()))
          .singleElement()
          .satisfies(
              d -> {
                assertThat(d.year()).isEqualTo(2027);
                assertThat(d.month()).isEqualTo(1);
                assertThat(d.day()).isEqualTo(15);
                assertThat(d.isPrimary()).isFalse();
              });
    }

    @Test
    @DisplayName("无日期数据时 dates 应该为空列表")
    void should_return_empty_dates_when_no_date_data() throws Exception {
      // given - 创建不带日期的文献
      CanonicalPublication publication =
          CanonicalPublication.builder()
              .identifiers(
                  List.of(
                      Identifier.builder()
                          .type(PublicationIdentifierType.PMID)
                          .value(PMID)
                          .build()))
              .title("Test Article")
              .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
              // 使用最小的 PublicationDates（只有 published）
              .dates(
                  PublicationDates.builder()
                      .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                      .build())
              .build();

      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      // 即使只有 published 日期，也应该有 1 条日期记录
      assertThat(result.hasDates()).isTrue();
      assertThat(result.dates()).hasSize(1);
      assertThat(result.dates().getFirst().dateType()).isEqualTo("published");
    }

    @Test
    @DisplayName("应该正确处理研究者数据")
    void should_process_investigators() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithInvestigators();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasInvestigators()).isTrue();
      assertThat(result.investigators()).hasSize(2);

      // 验证第一个研究者（带 ORCID）
      var inv1 = result.investigators().get(0);
      assertThat(inv1.lastName()).isEqualTo("Smith");
      assertThat(inv1.foreName()).isEqualTo("John");
      assertThat(inv1.initials()).isEqualTo("J.S.");
      assertThat(inv1.orcid()).isEqualTo("0000-0001-2345-6789");
      assertThat(inv1.affiliationName()).isEqualTo("Harvard Medical School");
      assertThat(inv1.dedupKey()).isNotNull();
      assertThat(inv1.orderNum()).isEqualTo(1);

      // 验证第二个研究者（无 ORCID）
      var inv2 = result.investigators().get(1);
      assertThat(inv2.lastName()).isEqualTo("Jones");
      assertThat(inv2.foreName()).isEqualTo("Mary");
      assertThat(inv2.orcid()).isNull();
      assertThat(inv2.affiliationName()).isEqualTo("Stanford University");
      assertThat(inv2.orderNum()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该过滤掉姓名都为空的研究者")
    void should_filter_invalid_investigators() throws Exception {
      // given - 创建包含无效研究者的文献
      CanonicalPublication publication =
          CanonicalPublication.builder()
              .identifiers(
                  List.of(
                      Identifier.builder()
                          .type(PublicationIdentifierType.PMID)
                          .value(PMID)
                          .build()))
              .title("Test Article")
              .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
              .dates(
                  PublicationDates.builder()
                      .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                      .build())
              .investigators(
                  List.of(
                      // 有效研究者
                      Investigator.builder().lastName("Smith").foreName("John").build(),
                      // 无效：姓名都为空
                      Investigator.builder().lastName(null).foreName(null).build(),
                      // 无效：姓名都为空白字符
                      Investigator.builder().lastName("  ").foreName("").build()))
              .build();

      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasInvestigators()).isTrue();
      assertThat(result.investigators()).hasSize(1);
      assertThat(result.investigators().getFirst().lastName()).isEqualTo("Smith");
    }

    @Test
    @DisplayName("无研究者数据时 investigators 应该为空列表")
    void should_return_empty_investigators_when_no_investigator_data() throws Exception {
      // given
      CanonicalPublication publication = createFullPublication();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasInvestigators()).isFalse();
      assertThat(result.investigators()).isEmpty();
    }

    @Test
    @DisplayName("应该正确处理人物主题数据")
    void should_process_personal_name_subjects() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithPersonalNameSubjects();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasPersonalNameSubjects()).isTrue();
      assertThat(result.personalNameSubjects()).hasSize(2);

      // 验证第一个人物主题
      var subject1 = result.personalNameSubjects().get(0);
      assertThat(subject1.lastName()).isEqualTo("Darwin");
      assertThat(subject1.foreName()).isEqualTo("Charles");
      assertThat(subject1.initials()).isEqualTo("C.R.");
      assertThat(subject1.suffix()).isEqualTo("FRS");
      assertThat(subject1.orderNum()).isEqualTo(1);

      // 验证第二个人物主题
      var subject2 = result.personalNameSubjects().get(1);
      assertThat(subject2.lastName()).isEqualTo("Pasteur");
      assertThat(subject2.foreName()).isEqualTo("Louis");
      assertThat(subject2.orderNum()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该过滤掉姓名都为空的人物主题")
    void should_filter_invalid_personal_name_subjects() throws Exception {
      // given - 创建包含无效人物主题的文献
      CanonicalPublication publication =
          CanonicalPublication.builder()
              .identifiers(
                  List.of(
                      Identifier.builder()
                          .type(PublicationIdentifierType.PMID)
                          .value(PMID)
                          .build()))
              .title("Test Article")
              .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
              .dates(
                  PublicationDates.builder()
                      .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                      .build())
              .personalNameSubjects(
                  List.of(
                      // 有效
                      PersonalNameSubject.builder().lastName("Darwin").foreName("Charles").build(),
                      // 无效：姓名都为空
                      PersonalNameSubject.builder().lastName(null).foreName(null).build(),
                      // 无效：姓名都为空白
                      PersonalNameSubject.builder().lastName("").foreName("  ").build()))
              .build();

      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasPersonalNameSubjects()).isTrue();
      assertThat(result.personalNameSubjects()).hasSize(1);
      assertThat(result.personalNameSubjects().getFirst().lastName()).isEqualTo("Darwin");
    }

    @Test
    @DisplayName("无人物主题数据时 personalNameSubjects 应该为空列表")
    void should_return_empty_personal_name_subjects_when_no_data() throws Exception {
      // given
      CanonicalPublication publication = createFullPublication();
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.hasPersonalNameSubjects()).isFalse();
      assertThat(result.personalNameSubjects()).isEmpty();
    }

    @Test
    @DisplayName("应该保留原始语言代码到 languageInfo.raw")
    void should_preserve_raw_language_code_in_language_info() throws Exception {
      // given - 使用 PubMed 的 ISO 639-3 三字母代码
      CanonicalPublication publication =
          CanonicalPublication.builder()
              .identifiers(
                  List.of(
                      Identifier.builder()
                          .type(PublicationIdentifierType.PMID)
                          .value(PMID)
                          .build()))
              .title("Test Article")
              .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
              .dates(
                  PublicationDates.builder()
                      .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                      .build())
              .language("eng")
              .build();

      when(languageLookupPort.resolve("eng")).thenReturn("en");
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      PublicationAggregate aggregate = result.publication();
      assertThat(aggregate.getLanguageInfo()).isNotNull();
      assertThat(aggregate.getLanguageInfo().raw()).isEqualTo("eng");
      assertThat(aggregate.getLanguageInfo().code()).isEqualTo("en");
      assertThat(aggregate.getLanguageInfo().base()).isEqualTo("en");
    }

    @Test
    @DisplayName("语言代码无法解析时应保留原始代码并回退为英语")
    void should_preserve_raw_and_fallback_to_english_when_language_unresolvable() throws Exception {
      // given
      CanonicalPublication publication =
          CanonicalPublication.builder()
              .identifiers(
                  List.of(
                      Identifier.builder()
                          .type(PublicationIdentifierType.PMID)
                          .value(PMID)
                          .build()))
              .title("Test Article")
              .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
              .dates(
                  PublicationDates.builder()
                      .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                      .build())
              .language("zzz")
              .build();

      when(languageLookupPort.resolve("zzz")).thenReturn(LanguageLookupPort.UNKNOWN_LANGUAGE);
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      PublicationAggregate aggregate = result.publication();
      assertThat(aggregate.getLanguageInfo().raw()).isEqualTo("zzz");
      assertThat(aggregate.getLanguageInfo().code()).isEqualTo("en");
    }

    @Test
    @DisplayName("去重键应该包含 ORCID 并转换为小写")
    void should_calculate_dedup_key_with_orcid_lowercase() throws Exception {
      // given - 创建带大写姓名和 ORCID 的研究者
      CanonicalPublication publication =
          CanonicalPublication.builder()
              .identifiers(
                  List.of(
                      Identifier.builder()
                          .type(PublicationIdentifierType.PMID)
                          .value(PMID)
                          .build()))
              .title("Test Article")
              .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
              .dates(
                  PublicationDates.builder()
                      .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                      .build())
              .investigators(
                  List.of(
                      Investigator.builder()
                          .lastName("SMITH")
                          .foreName("JOHN")
                          .identifiers(
                              List.of(
                                  Identifier.builder()
                                      .type(PublicationIdentifierType.ORCID)
                                      .value("0000-0001-2345-6789")
                                      .build()))
                          .build()))
              .build();

      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNotNull();
      assertThat(result.investigators()).hasSize(1);

      var inv = result.investigators().getFirst();
      assertThat(inv.dedupKey()).isNotNull();
      // 去重键应该是 32 位的 MD5 哈希
      assertThat(inv.dedupKey()).hasSize(32);
      // 去重键应该全部是小写十六进制字符
      assertThat(inv.dedupKey()).matches("[a-f0-9]{32}");
    }
  }

  @Nested
  @DisplayName("摘要三态分流")
  class AbstractTypingTest {

    @BeforeEach
    void stubVenueResolution() {
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());
    }

    @Test
    @DisplayName("含带标签段应判为 STRUCTURED，无标签段以 label=null 保位，plainText 按段落拼接")
    void should_type_as_structured_and_join_plain_text() throws Exception {
      // given
      Abstract abstractContent =
          Abstract.builder()
              .sections(
                  List.of(
                      abstractSection("BACKGROUND", "bg."),
                      abstractSection("METHODS", "m."),
                      abstractSection(null, "tail.")))
              .copyright("© X")
              .build();

      // when
      PublicationAbstract abs = processAbstract(abstractContent);

      // then
      assertThat(abs).isNotNull();
      assertThat(abs.abstractType()).isEqualTo(AbstractType.STRUCTURED);
      assertThat(abs.structuredSections()).hasSize(3);
      assertThat(abs.structuredSections().get(2).label()).isNull();
      assertThat(abs.plainText()).isEqualTo("BACKGROUND: bg.\nMETHODS: m.\ntail.");
      assertThat(abs.copyright()).isEqualTo("© X");
    }

    @Test
    @DisplayName("全部无标签段应判为 UNSTRUCTURED 且段落不入库")
    void should_type_as_unstructured_when_all_sections_unlabeled() throws Exception {
      // given
      Abstract abstractContent =
          Abstract.builder()
              .sections(
                  List.of(abstractSection(null, "para one."), abstractSection(null, "para two.")))
              .build();

      // when
      PublicationAbstract abs = processAbstract(abstractContent);

      // then
      assertThat(abs).isNotNull();
      assertThat(abs.abstractType()).isEqualTo(AbstractType.UNSTRUCTURED);
      assertThat(abs.structuredSections()).isEmpty();
      assertThat(abs.plainText()).isEqualTo("para one.\npara two.");
    }

    @Test
    @DisplayName("空白 label 应先归一为 null 再判型")
    void should_normalize_blank_label_before_typing() throws Exception {
      // given
      Abstract abstractContent =
          Abstract.builder().sections(List.of(abstractSection("  ", "only."))).build();

      // when
      PublicationAbstract abs = processAbstract(abstractContent);

      // then
      assertThat(abs).isNotNull();
      assertThat(abs.abstractType()).isEqualTo(AbstractType.UNSTRUCTURED);
      assertThat(abs.plainText()).isEqualTo("only.");
    }

    @Test
    @DisplayName("内容空白的段落应被丢弃")
    void should_drop_sections_with_blank_content() throws Exception {
      // given
      Abstract abstractContent =
          Abstract.builder()
              .sections(
                  List.of(
                      abstractSection("BACKGROUND", "bg."),
                      abstractSection("METHODS", "   "),
                      abstractSection("RESULTS", null)))
              .build();

      // when
      PublicationAbstract abs = processAbstract(abstractContent);

      // then
      assertThat(abs).isNotNull();
      assertThat(abs.structuredSections()).hasSize(1);
      assertThat(abs.plainText()).isEqualTo("BACKGROUND: bg.");
    }

    @Test
    @DisplayName("无段落但有文本时应回退为纯文本摘要")
    void should_fall_back_to_canonical_text_when_no_sections() throws Exception {
      // given
      Abstract abstractContent = Abstract.builder().text("plain only.").copyright("© Y").build();

      // when
      PublicationAbstract abs = processAbstract(abstractContent);

      // then
      assertThat(abs).isNotNull();
      assertThat(abs.abstractType()).isEqualTo(AbstractType.UNSTRUCTURED);
      assertThat(abs.plainText()).isEqualTo("plain only.");
      assertThat(abs.copyright()).isEqualTo("© Y");
    }

    @Test
    @DisplayName("既无段落也无文本应返回 null")
    void should_return_null_when_no_sections_and_no_text() throws Exception {
      // when
      PublicationAbstract abs = processAbstract(Abstract.builder().build());

      // then
      assertThat(abs).isNull();
    }

    @Test
    @DisplayName("段落全被过滤且无文本时应返回 null")
    void should_return_null_when_all_sections_filtered_and_no_text() throws Exception {
      // given
      Abstract abstractContent =
          Abstract.builder()
              .sections(List.of(abstractSection("BACKGROUND", "   "), abstractSection(null, null)))
              .build();

      // when
      PublicationAbstract abs = processAbstract(abstractContent);

      // then
      assertThat(abs).isNull();
    }

    /// 走完整 process() 链路取出主摘要值对象。
    private PublicationAbstract processAbstract(Abstract abstractContent) throws Exception {
      CanonicalPublication publication =
          CanonicalPublication.builder()
              .identifiers(
                  List.of(
                      Identifier.builder()
                          .type(PublicationIdentifierType.PMID)
                          .value(PMID)
                          .build()))
              .title("Test Article")
              .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
              .dates(
                  PublicationDates.builder()
                      .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                      .build())
              .abstractContent(abstractContent)
              .build();
      return processor.process(publication).publication().getPublicationAbstract();
    }
  }

  @Nested
  @DisplayName("作者快照构建")
  class AuthorSnapshotTest {

    @BeforeEach
    void stubVenueResolution() {
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());
    }

    @Test
    @DisplayName("按 XML 顺序 1 起编号，首位标记第一作者并透传同等贡献")
    void should_number_authors_from_one_and_mark_first_author() throws Exception {
      // given
      List<CanonicalPublication.Author> authors =
          List.of(
              personalAuthor("Smith", "John", null, true),
              personalAuthor("Doe", "Jane", null, null));

      // when
      List<PublicationAuthorSnapshot> snapshots = processAuthors(authors);

      // then
      assertThat(snapshots).hasSize(2);
      assertThat(snapshots.get(0).order()).isEqualTo(1);
      assertThat(snapshots.get(0).firstAuthor()).isTrue();
      assertThat(snapshots.get(0).equalContribution()).isTrue();
      assertThat(snapshots.get(1).order()).isEqualTo(2);
      assertThat(snapshots.get(1).firstAuthor()).isFalse();
      assertThat(snapshots.get(1).equalContribution()).isFalse();
      assertThat(snapshots.get(1).displayName()).isEqualTo("Doe Jane");
    }

    @Test
    @DisplayName("集体作者应以组织名成展示名且不落姓名字段")
    void should_map_collective_author() throws Exception {
      // given
      CanonicalPublication.Author collective =
          CanonicalPublication.Author.builder().organizationName("WHO Study Group").build();

      // when
      List<PublicationAuthorSnapshot> snapshots = processAuthors(List.of(collective));

      // then
      assertThat(snapshots).hasSize(1);
      assertThat(snapshots.getFirst().isCollective()).isTrue();
      assertThat(snapshots.getFirst().displayName()).isEqualTo("WHO Study Group");
      assertThat(snapshots.getFirst().lastName()).isNull();
      assertThat(snapshots.getFirst().orcid()).isNull();
      assertThat(snapshots.getFirst().affiliations()).isEmpty();
    }

    @Test
    @DisplayName("姓名全空的作者应跳过且不留顺序空洞")
    void should_skip_blank_name_authors_keeping_order_continuous() throws Exception {
      // given
      List<CanonicalPublication.Author> authors =
          List.of(
              personalAuthor("Smith", null, null, null),
              personalAuthor("  ", "  ", null, null),
              personalAuthor("Wang", null, null, null));

      // when
      List<PublicationAuthorSnapshot> snapshots = processAuthors(authors);

      // then
      assertThat(snapshots).hasSize(2);
      assertThat(snapshots.get(0).order()).isEqualTo(1);
      assertThat(snapshots.get(1).order()).isEqualTo(2);
      assertThat(snapshots.get(1).displayName()).isEqualTo("Wang");
    }

    @Test
    @DisplayName("机构原文应保序并从 1 起连续编号")
    void should_number_affiliations_from_one() throws Exception {
      // given
      CanonicalPublication.Author author =
          CanonicalPublication.Author.builder()
              .lastName("Smith")
              .affiliations(
                  List.of(
                      CanonicalPublication.Affiliation.builder().name("Harvard").build(),
                      CanonicalPublication.Affiliation.builder().name("  ").build(),
                      CanonicalPublication.Affiliation.builder().name("MIT").build()))
              .build();

      // when
      List<PublicationAuthorSnapshot> snapshots = processAuthors(List.of(author));

      // then
      var affiliations = snapshots.getFirst().affiliations();
      assertThat(affiliations).hasSize(2);
      assertThat(affiliations.get(0).order()).isEqualTo(1);
      assertThat(affiliations.get(0).affiliationString()).isEqualTo("Harvard");
      assertThat(affiliations.get(1).order()).isEqualTo(2);
      assertThat(affiliations.get(1).affiliationString()).isEqualTo("MIT");
    }

    @Test
    @DisplayName("ORCID 应剥离 URL 前缀并大写校验位")
    void should_normalize_orcid_url_prefix_and_uppercase_check_digit() throws Exception {
      // given
      List<CanonicalPublication.Author> authors =
          List.of(
              authorWithOrcid("Smith", "https://orcid.org/0000-0002-1825-0097"),
              authorWithOrcid("Doe", "0000-0002-1694-233x"));

      // when
      List<PublicationAuthorSnapshot> snapshots = processAuthors(authors);

      // then
      assertThat(snapshots.get(0).orcid()).isEqualTo("0000-0002-1825-0097");
      assertThat(snapshots.get(1).orcid()).isEqualTo("0000-0002-1694-233X");
    }

    @Test
    @DisplayName("格式或校验位非法的 ORCID 应丢弃为 null")
    void should_drop_invalid_orcid() throws Exception {
      // given
      List<CanonicalPublication.Author> authors =
          List.of(
              authorWithOrcid("Smith", "0000-0002-1825-0098"),
              authorWithOrcid("Doe", "not-an-orcid"));

      // when
      List<PublicationAuthorSnapshot> snapshots = processAuthors(authors);

      // then
      assertThat(snapshots.get(0).orcid()).isNull(); // 校验位错
      assertThat(snapshots.get(1).orcid()).isNull(); // 格式错
    }

    @Test
    @DisplayName("非 ORCID 类型的标识符不应被误取为 ORCID")
    void should_ignore_non_orcid_identifiers() throws Exception {
      // given
      CanonicalPublication.Author author =
          CanonicalPublication.Author.builder()
              .lastName("Smith")
              .identifiers(
                  List.of(
                      Identifier.builder()
                          .type(PublicationIdentifierType.DOI)
                          .value("10.1234/example")
                          .build(),
                      Identifier.builder()
                          .type(PublicationIdentifierType.PMID)
                          .value(PMID)
                          .build()))
              .build();

      // when
      List<PublicationAuthorSnapshot> snapshots = processAuthors(List.of(author));

      // then
      assertThat(snapshots.getFirst().orcid()).isNull();
    }

    @Test
    @DisplayName("缩写与后缀应透传，且后缀不拼入展示名")
    void should_pass_through_initials_and_suffix_without_suffix_in_display_name() throws Exception {
      // given
      CanonicalPublication.Author author =
          CanonicalPublication.Author.builder()
              .lastName("Smith")
              .initials("J")
              .suffix("Jr")
              .build();

      // when
      List<PublicationAuthorSnapshot> snapshots = processAuthors(List.of(author));

      // then
      assertThat(snapshots.getFirst().initials()).isEqualTo("J");
      assertThat(snapshots.getFirst().suffix()).isEqualTo("Jr");
      assertThat(snapshots.getFirst().displayName()).isEqualTo("Smith J");
    }

    /// 构造个人作者。
    private CanonicalPublication.Author personalAuthor(
        String lastName, String foreName, String initials, Boolean equalContribution) {
      return CanonicalPublication.Author.builder()
          .lastName(lastName)
          .foreName(foreName)
          .initials(initials)
          .equalContribution(equalContribution)
          .build();
    }

    /// 构造带 ORCID 标识符的个人作者。
    private CanonicalPublication.Author authorWithOrcid(String lastName, String orcid) {
      return CanonicalPublication.Author.builder()
          .lastName(lastName)
          .identifiers(
              List.of(
                  Identifier.builder().type(PublicationIdentifierType.ORCID).value(orcid).build()))
          .build();
    }

    /// 走完整 process() 链路取出聚合根上的作者快照列表。
    private List<PublicationAuthorSnapshot> processAuthors(
        List<CanonicalPublication.Author> authors) throws Exception {
      CanonicalPublication publication =
          CanonicalPublication.builder()
              .identifiers(
                  List.of(
                      Identifier.builder()
                          .type(PublicationIdentifierType.PMID)
                          .value(PMID)
                          .build()))
              .title("Test Article")
              .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
              .dates(
                  PublicationDates.builder()
                      .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                      .build())
              .authors(authors)
              .build();
      return processor.process(publication).publication().getAuthors();
    }
  }

  /// 创建 Canonical 摘要段落。
  private static AbstractSection abstractSection(String label, String content) {
    return AbstractSection.builder().label(label).content(content).build();
  }

  /// 创建简单的测试文献。
  private CanonicalPublication createPublication(String pmid) {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(pmid).build()))
        .title("Test Article")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .build();
  }

  /// 创建包含 PMID 与 DOI 的测试文献。
  private CanonicalPublication createPublicationWithPmidAndDoi(String pmid, String doi) {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(
                Identifier.builder().type(PublicationIdentifierType.PMID).value(pmid).build(),
                Identifier.builder().type(PublicationIdentifierType.DOI).value(doi).build()))
        .title("Test Article")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .build();
  }

  /// 创建可指定中英文标题的测试文献。
  private CanonicalPublication createPublicationWithTitles(
      String pmid, String title, String originalTitle) {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(pmid).build()))
        .title(title)
        .originalTitle(originalTitle)
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .build();
  }

  /// 创建包含所有字段的测试文献。
  private CanonicalPublication createFullPublication() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(
                Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build(),
                Identifier.builder().type(PublicationIdentifierType.DOI).value(DOI).build()))
        .title("Test Article Title")
        .originalTitle("测试文章标题")
        .journal(
            Journal.builder()
                .nlmUniqueId(NLM_ID)
                .issn(ISSN)
                .issnType("print")
                .title("Test Journal")
                .volume("10")
                .issue("2")
                .build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 6, 15)))
                .build())
        .language("en")
        .publicationStatus("ppublish")
        .authorsComplete(true)
        .build();
  }

  /// 创建包含纯文本摘要的测试文献。
  private CanonicalPublication createPublicationWithPlainTextAbstract() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .abstractContent(
            Abstract.builder()
                .text("This is a test abstract content.")
                .copyright("Copyright 2024")
                .build())
        .build();
  }

  /// 创建包含结构化摘要的测试文献。
  private CanonicalPublication createPublicationWithStructuredAbstract() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .abstractContent(
            Abstract.builder()
                .sections(
                    List.of(
                        AbstractSection.builder()
                            .label("BACKGROUND")
                            .content("Background content.")
                            .build(),
                        AbstractSection.builder()
                            .label("METHODS")
                            .content("Methods content.")
                            .build(),
                        AbstractSection.builder()
                            .label("RESULTS")
                            .content("Results content.")
                            .build(),
                        AbstractSection.builder()
                            .label("CONCLUSIONS")
                            .content("Conclusions content.")
                            .build()))
                .build())
        .build();
  }

  /// 创建包含扩展标识符的测试文献。
  private CanonicalPublication createPublicationWithExtendedIdentifiers() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(
                Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build(),
                Identifier.builder().type(PublicationIdentifierType.DOI).value(DOI).build(),
                Identifier.builder()
                    .type(PublicationIdentifierType.PMC)
                    .value("PMC1234567")
                    .build(),
                Identifier.builder()
                    .type(PublicationIdentifierType.PII)
                    .value("S0140-6736(21)00123-4")
                    .build()))
        .title("Test Article")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .build();
  }

  /// 创建包含 PublicationMedium 的测试文献。
  private CanonicalPublication createPublicationWithPublicationMedium() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .mediaType("electronic")
        .build();
  }

  /// 创建包含元数据的测试文献。
  private CanonicalPublication createPublicationWithMetadata() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .numberOfReferences(42)
        .conflictOfInterestStatement("The authors declare no conflict of interest.")
        .build();
  }

  /// 创建测试用的 VenueInstance。
  private VenueInstanceAggregate createVenueInstance() {
    // 使用 restore 模拟已持久化的实例
    return VenueInstanceAggregate.restore(
        VenueInstanceId.of(VENUE_INSTANCE_ID),
        VenueId.of(VENUE_ID),
        "10",
        "2",
        null,
        2024,
        6,
        15,
        null,
        null,
        null,
        null,
        0L);
  }

  /// 创建包含 MeSH 标引的测试文献。
  private CanonicalPublication createPublicationWithMeshHeadings() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article with MeSH")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .meshHeadings(
            List.of(
                MeshHeading.builder()
                    .descriptorName(
                        DescriptorName.builder()
                            .ui("D000001")
                            .term("Calcimycin")
                            .majorTopic(true)
                            .build())
                    .qualifierNames(
                        List.of(
                            QualifierName.builder()
                                .ui("Q000379")
                                .term("methods")
                                .majorTopic(false)
                                .build()))
                    .build()))
        .build();
  }

  /// 创建只有一个标引、带指定限定词的测试文献。
  private CanonicalPublication createPublicationWithQualifiers(QualifierName... qualifiers) {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article with Qualifiers")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .meshHeadings(
            List.of(
                MeshHeading.builder()
                    .descriptorName(
                        DescriptorName.builder()
                            .ui("D005998")
                            .term("Glycine")
                            .majorTopic(false)
                            .build())
                    .qualifierNames(List.of(qualifiers))
                    .build()))
        .build();
  }

  /// 创建限定词。
  private QualifierName qualifier(String ui, boolean majorTopic) {
    return QualifierName.builder().ui(ui).term(ui).majorTopic(majorTopic).build();
  }

  /// 创建包含关键词的测试文献。
  private CanonicalPublication createPublicationWithKeywords() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article with Keywords")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .keywords(
            List.of(
                KeywordSet.builder()
                    .source("author")
                    .keywords(
                        List.of(
                            Keyword.builder().term("machine learning").majorTopic(true).build(),
                            Keyword.builder().term("deep learning").majorTopic(false).build()))
                    .build(),
                KeywordSet.builder()
                    .source("publisher")
                    .keywords(
                        List.of(
                            Keyword.builder()
                                .term("artificial intelligence")
                                .majorTopic(false)
                                .build()))
                    .build()))
        .build();
  }

  /// 创建包含资助信息的测试文献。
  private CanonicalPublication createPublicationWithFunding() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article with Funding")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .funding(
            List.of(
                FundingInfo.builder()
                    .grantId("R01AI123456")
                    .funderName("National Institutes of Health")
                    .funderAcronym("NIH")
                    .funderIdentifier("100000002")
                    .country("United States")
                    .build(),
                FundingInfo.builder()
                    .grantId("81970001")
                    .funderName("National Natural Science Foundation of China")
                    .funderAcronym("NSFC")
                    .funderIdentifier("501100001809")
                    .country("China")
                    .build()))
        .build();
  }

  /// 创建包含出版类型的测试文献。
  private CanonicalPublication createPublicationWithTypes() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article with Publication Types")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .publicationTypes(
            List.of(
                PublicationType.builder()
                    .id("D016428")
                    .value("Journal Article")
                    .vocabularySource("MeSH")
                    .build(),
                PublicationType.builder()
                    .id("D016454")
                    .value("Review")
                    .vocabularySource("MeSH")
                    .build()))
        .build();
  }

  /// 创建包含补充 MeSH 概念的测试文献。
  private CanonicalPublication createPublicationWithSupplMeshNames() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article with SupplMeshNames")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .supplMeshNames(
            List.of(
                SupplMeshName.builder()
                    .ui("C538003")
                    .name("Aspirin-sensitive asthma")
                    .type("Disease")
                    .build(),
                SupplMeshName.builder()
                    .ui("C095232")
                    .name("FOLFOX protocol")
                    .type("Protocol")
                    .build()))
        .build();
  }

  /// 创建包含翻译摘要的测试文献。
  private CanonicalPublication createPublicationWithAlternativeAbstracts() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article with Alternative Abstracts")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .alternativeAbstracts(
            List.of(
                AlternativeAbstract.builder()
                    .type("Publisher")
                    .language("chi")
                    .text("这是中文翻译摘要。")
                    .copyright("版权所有 2024")
                    .build(),
                AlternativeAbstract.builder()
                    .type("AIMSHP")
                    .language("jpn")
                    .text("これは日本語の翻訳要約です。")
                    .build()))
        .build();
  }

  /// 创建同语言不同来源类型翻译摘要的测试文献。
  private CanonicalPublication
      createPublicationWithSameLanguageDifferentAlternativeAbstractTypes() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article with Same Language Alternative Abstract Types")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .alternativeAbstracts(
            List.of(
                AlternativeAbstract.builder()
                    .type("Publisher")
                    .language("fre")
                    .text("法语官方摘要。")
                    .build(),
                AlternativeAbstract.builder()
                    .type("plain-language-summary")
                    .language("fre")
                    .text("法语通俗语言摘要。")
                    .build()))
        .build();
  }

  /// 创建包含多种日期的测试文献。
  private CanonicalPublication createPublicationWithDates() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article with Dates")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 6, 15)))
                .received(PartialDate.ofDay(LocalDate.of(2024, 1, 10)))
                .accepted(PartialDate.ofDay(LocalDate.of(2024, 5, 20)))
                .build())
        .build();
  }

  /// 创建包含研究者的测试文献。
  private CanonicalPublication createPublicationWithInvestigators() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article with Investigators")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .investigators(
            List.of(
                // 第一个研究者：带 ORCID 和机构
                Investigator.builder()
                    .lastName("Smith")
                    .foreName("John")
                    .initials("J.S.")
                    .suffix("Jr")
                    .identifiers(
                        List.of(
                            Identifier.builder()
                                .type(PublicationIdentifierType.ORCID)
                                .value("0000-0001-2345-6789")
                                .build()))
                    .affiliations(
                        List.of(
                            CanonicalPublication.Affiliation.builder()
                                .name("Harvard Medical School")
                                .build()))
                    .build(),
                // 第二个研究者：无 ORCID
                Investigator.builder()
                    .lastName("Jones")
                    .foreName("Mary")
                    .initials("M.J.")
                    .affiliations(
                        List.of(
                            CanonicalPublication.Affiliation.builder()
                                .name("Stanford University")
                                .build()))
                    .build()))
        .build();
  }

  /// 创建包含人物主题的测试文献。
  private CanonicalPublication createPublicationWithPersonalNameSubjects() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Biography of Famous Scientists")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(
            PublicationDates.builder()
                .published(PartialDate.ofDay(LocalDate.of(2024, 1, 1)))
                .build())
        .personalNameSubjects(
            List.of(
                PersonalNameSubject.builder()
                    .lastName("Darwin")
                    .foreName("Charles")
                    .initials("C.R.")
                    .suffix("FRS")
                    .build(),
                PersonalNameSubject.builder()
                    .lastName("Pasteur")
                    .foreName("Louis")
                    .initials("L.")
                    .build()))
        .build();
  }
}
