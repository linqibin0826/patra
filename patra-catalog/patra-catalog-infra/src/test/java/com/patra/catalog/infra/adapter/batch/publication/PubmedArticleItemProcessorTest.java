package com.patra.catalog.infra.adapter.batch.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.aggregate.VenueInstanceAggregate;
import com.patra.catalog.domain.model.enums.PublicationMedium;
import com.patra.catalog.domain.model.vo.publication.PublicationAbstract;
import com.patra.catalog.domain.model.vo.publication.PublicationIdentifier;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueInstanceId;
import com.patra.catalog.domain.model.vo.venueinstance.JournalInstanceParams;
import com.patra.catalog.domain.port.gateway.VenueInstanceGateway;
import com.patra.catalog.domain.port.lookup.FunderLookupPort;
import com.patra.catalog.domain.port.lookup.LanguageLookupPort;
import com.patra.catalog.domain.port.lookup.VenueLookupPort;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.common.model.CanonicalPublication;
import com.patra.common.model.CanonicalPublication.Abstract;
import com.patra.common.model.CanonicalPublication.AbstractSection;
import com.patra.common.model.CanonicalPublication.AlternativeAbstract;
import com.patra.common.model.CanonicalPublication.DescriptorName;
import com.patra.common.model.CanonicalPublication.FundingInfo;
import com.patra.common.model.CanonicalPublication.Identifier;
import com.patra.common.model.CanonicalPublication.Investigator;
import com.patra.common.model.CanonicalPublication.Journal;
import com.patra.common.model.CanonicalPublication.Keyword;
import com.patra.common.model.CanonicalPublication.KeywordSet;
import com.patra.common.model.CanonicalPublication.MeshHeading;
import com.patra.common.model.CanonicalPublication.PersonalNameSubject;
import com.patra.common.model.CanonicalPublication.PublicationDates;
import com.patra.common.model.CanonicalPublication.PublicationType;
import com.patra.common.model.CanonicalPublication.QualifierName;
import com.patra.common.model.CanonicalPublication.SupplMeshName;
import com.patra.common.model.enums.PublicationIdentifierType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// PubmedArticleItemProcessor 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedArticleItemProcessor")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class PubmedArticleItemProcessorTest {

  @Mock private PublicationRepository publicationRepository;

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
            publicationRepository,
            venueLookupPort,
            venueInstanceGateway,
            languageLookupPort,
            funderLookupPort);
  }

  @Nested
  @DisplayName("process()")
  class ProcessTest {

    @Test
    @DisplayName("已存在的 PMID 应该返回 null（跳过）")
    void should_return_null_when_pmid_already_exists() throws Exception {
      // given
      CanonicalPublication publication = createPublication(PMID);
      when(publicationRepository.existsByPmid(PMID)).thenReturn(true);

      // when
      PublicationImportResult result = processor.process(publication);

      // then
      assertThat(result).isNull();
      verify(venueLookupPort, never()).findByPriority(any(), any());
    }

    @Test
    @DisplayName("无法匹配 Venue 时应该返回 null（跳过）")
    void should_return_null_when_venue_not_matched() throws Exception {
      // given
      CanonicalPublication publication = createPublication(PMID);
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
              .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
              .build();

      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      assertThat(abstractContent.getSection("BACKGROUND")).isPresent();
      assertThat(abstractContent.getSection("METHODS")).isPresent();
      assertThat(abstractContent.getSection("RESULTS")).isPresent();
      assertThat(abstractContent.getSection("CONCLUSIONS")).isPresent();
    }

    @Test
    @DisplayName("应该提取扩展标识符 (PMC, PII)")
    void should_extract_extended_identifiers() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithExtendedIdentifiers();
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
              .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
              .publicationStatus("epublish")
              .build();

      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
    @DisplayName("无 MeSH 数据时 meshHeadings 应该为空列表")
    void should_return_empty_mesh_headings_when_no_mesh_data() throws Exception {
      // given
      CanonicalPublication publication = createFullPublication();
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
      when(venueLookupPort.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(createVenueInstance());

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
    @DisplayName("无翻译摘要数据时 alternativeAbstracts 应该为空列表")
    void should_return_empty_alternative_abstracts_when_no_data() throws Exception {
      // given
      CanonicalPublication publication = createFullPublication();
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
    @DisplayName("应该正确处理文献日期数据")
    void should_process_publication_dates() throws Exception {
      // given
      CanonicalPublication publication = createPublicationWithDates();
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
              .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
              .build();

      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
              .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
              .investigators(
                  List.of(
                      // 有效研究者
                      Investigator.builder().lastName("Smith").foreName("John").build(),
                      // 无效：姓名都为空
                      Investigator.builder().lastName(null).foreName(null).build(),
                      // 无效：姓名都为空白字符
                      Investigator.builder().lastName("  ").foreName("").build()))
              .build();

      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
              .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
              .personalNameSubjects(
                  List.of(
                      // 有效
                      PersonalNameSubject.builder().lastName("Darwin").foreName("Charles").build(),
                      // 无效：姓名都为空
                      PersonalNameSubject.builder().lastName(null).foreName(null).build(),
                      // 无效：姓名都为空白
                      PersonalNameSubject.builder().lastName("").foreName("  ").build()))
              .build();

      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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
              .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
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

      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
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

  /// 创建简单的测试文献。
  private CanonicalPublication createPublication(String pmid) {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(pmid).build()))
        .title("Test Article")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
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
        .dates(PublicationDates.builder().published(LocalDate.of(2024, 6, 15)).build())
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
        .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
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
        .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
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
        .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
        .build();
  }

  /// 创建包含 PublicationMedium 的测试文献。
  private CanonicalPublication createPublicationWithPublicationMedium() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
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
        .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
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
        .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
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

  /// 创建包含关键词的测试文献。
  private CanonicalPublication createPublicationWithKeywords() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("Test Article with Keywords")
        .journal(Journal.builder().nlmUniqueId(NLM_ID).build())
        .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
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
        .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
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
        .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
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
        .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
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
        .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
        .alternativeAbstracts(
            List.of(
                AlternativeAbstract.builder()
                    .type("Publisher")
                    .language("zh")
                    .text("这是中文翻译摘要。")
                    .copyright("版权所有 2024")
                    .build(),
                AlternativeAbstract.builder()
                    .type("AIMSHP")
                    .language("ja")
                    .text("これは日本語の翻訳要約です。")
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
                .published(LocalDate.of(2024, 6, 15))
                .received(LocalDate.of(2024, 1, 10))
                .accepted(LocalDate.of(2024, 5, 20))
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
        .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
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
        .dates(PublicationDates.builder().published(LocalDate.of(2024, 1, 1)).build())
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
