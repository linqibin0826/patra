package com.patra.catalog.infra.adapter.batch.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.vo.publication.PublicationId;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueInstanceId;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.FundingData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.KeywordData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.MeshHeadingData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.PublicationTypeData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.QualifierData;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationFundingDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationKeywordDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationMeshHeadingDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationMeshQualifierDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationTypeDao;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationFundingEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationKeywordEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationMeshHeadingEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationMeshQualifierEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationTypeEntity;
import com.patra.common.enums.ProvenanceCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

/// PublicationItemWriter 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PublicationItemWriter")
@ExtendWith(MockitoExtension.class)
class PublicationItemWriterTest {

  @Mock private PublicationRepository publicationRepository;

  @Mock private PublicationMeshHeadingDao meshHeadingDao;

  @Mock private PublicationMeshQualifierDao meshQualifierDao;

  @Mock private PublicationKeywordDao keywordDao;

  @Mock private PublicationFundingDao fundingDao;

  @Mock private PublicationTypeDao typeDao;

  @Captor private ArgumentCaptor<List<PublicationMeshHeadingEntity>> headingCaptor;

  @Captor private ArgumentCaptor<List<PublicationMeshQualifierEntity>> qualifierCaptor;

  @Captor private ArgumentCaptor<List<PublicationKeywordEntity>> keywordCaptor;

  @Captor private ArgumentCaptor<List<PublicationFundingEntity>> fundingCaptor;

  @Captor private ArgumentCaptor<List<PublicationTypeEntity>> typeCaptor;

  private PublicationItemWriter writer;

  private static final Long PUBLICATION_ID = 1001L;

  @BeforeEach
  void setUp() {
    writer =
        new PublicationItemWriter(
            publicationRepository,
            meshHeadingDao,
            meshQualifierDao,
            keywordDao,
            fundingDao,
            typeDao);
  }

  @Nested
  @DisplayName("write()")
  class WriteTest {

    @Test
    @DisplayName("应该批量插入 Publication 列表")
    void should_insert_all_publications() throws Exception {
      // given
      PublicationAggregate pub1 = createPublication("12345678");
      PublicationAggregate pub2 = createPublication("87654321");
      PublicationImportResult result1 = PublicationImportResult.ofPublication(pub1);
      PublicationImportResult result2 = PublicationImportResult.ofPublication(pub2);
      Chunk<PublicationImportResult> chunk = new Chunk<>(List.of(result1, result2));

      // when
      writer.write(chunk);

      // then
      verify(publicationRepository).insertAll(List.of(pub1, pub2));
    }

    @Test
    @DisplayName("空 chunk 不应调用 insertAll")
    void should_not_call_insert_all_for_empty_chunk() throws Exception {
      // given
      Chunk<PublicationImportResult> emptyChunk = new Chunk<>();

      // when
      writer.write(emptyChunk);

      // then
      verify(publicationRepository, never()).insertAll(anyList());
    }

    @Test
    @DisplayName("应该写入 MeSH Heading 关联")
    void should_write_mesh_heading_associations() throws Exception {
      // given
      PublicationAggregate pub = createPublication("12345678");
      pub.assignId(PublicationId.of(PUBLICATION_ID));

      MeshHeadingData heading = MeshHeadingData.of("D000001", true, 1, List.of());

      PublicationImportResult result = PublicationImportResult.of(pub, List.of(heading));
      Chunk<PublicationImportResult> chunk = new Chunk<>(List.of(result));

      // when
      writer.write(chunk);

      // then
      verify(meshHeadingDao).saveAll(headingCaptor.capture());
      List<PublicationMeshHeadingEntity> savedHeadings = headingCaptor.getValue();
      assertThat(savedHeadings).hasSize(1);
      assertThat(savedHeadings.getFirst().getPublicationId()).isEqualTo(PUBLICATION_ID);
      assertThat(savedHeadings.getFirst().getDescriptorUi()).isEqualTo("D000001");
      assertThat(savedHeadings.getFirst().getMajorTopic()).isTrue();
    }

    @Test
    @DisplayName("应该写入 MeSH Qualifier 关联")
    void should_write_mesh_qualifier_associations() throws Exception {
      // given
      PublicationAggregate pub = createPublication("12345678");
      pub.assignId(PublicationId.of(PUBLICATION_ID));

      QualifierData qualifier = QualifierData.of("Q000379", false);
      MeshHeadingData heading = MeshHeadingData.of("D000001", true, 1, List.of(qualifier));

      PublicationImportResult result = PublicationImportResult.of(pub, List.of(heading));
      Chunk<PublicationImportResult> chunk = new Chunk<>(List.of(result));

      // when
      writer.write(chunk);

      // then
      verify(meshQualifierDao).saveAll(qualifierCaptor.capture());
      List<PublicationMeshQualifierEntity> savedQualifiers = qualifierCaptor.getValue();
      assertThat(savedQualifiers).hasSize(1);
      assertThat(savedQualifiers.getFirst().getQualifierUi()).isEqualTo("Q000379");
      assertThat(savedQualifiers.getFirst().getMajorTopic()).isFalse();
    }

    @Test
    @DisplayName("无 MeSH 数据时不应调用 DAO")
    void should_not_call_mesh_dao_when_no_mesh_data() throws Exception {
      // given
      PublicationAggregate pub = createPublication("12345678");
      pub.assignId(PublicationId.of(PUBLICATION_ID));

      PublicationImportResult result = PublicationImportResult.ofPublication(pub);
      Chunk<PublicationImportResult> chunk = new Chunk<>(List.of(result));

      // when
      writer.write(chunk);

      // then
      verify(meshHeadingDao, never()).saveAll(anyList());
      verify(meshQualifierDao, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("应该写入关键词关联")
    void should_write_keyword_associations() throws Exception {
      // given
      PublicationAggregate pub = createPublication("12345678");
      pub.assignId(PublicationId.of(PUBLICATION_ID));

      KeywordData keyword1 = KeywordData.of("author", "machine learning", true, 1);
      KeywordData keyword2 = KeywordData.of("publisher", "AI", false, 2);

      PublicationImportResult result =
          PublicationImportResult.ofAll(
              pub, List.of(), List.of(keyword1, keyword2), List.of(), List.of());
      Chunk<PublicationImportResult> chunk = new Chunk<>(List.of(result));

      // when
      writer.write(chunk);

      // then
      verify(keywordDao).saveAll(keywordCaptor.capture());
      List<PublicationKeywordEntity> savedKeywords = keywordCaptor.getValue();
      assertThat(savedKeywords).hasSize(2);
      assertThat(savedKeywords.get(0).getPublicationId()).isEqualTo(PUBLICATION_ID);
      assertThat(savedKeywords.get(0).getSource()).isEqualTo("author");
      assertThat(savedKeywords.get(0).getTerm()).isEqualTo("machine learning");
      assertThat(savedKeywords.get(0).getMajorTopic()).isTrue();
      assertThat(savedKeywords.get(1).getSource()).isEqualTo("publisher");
    }

    @Test
    @DisplayName("无关键词数据时不应调用 DAO")
    void should_not_call_keyword_dao_when_no_keyword_data() throws Exception {
      // given
      PublicationAggregate pub = createPublication("12345678");
      pub.assignId(PublicationId.of(PUBLICATION_ID));

      PublicationImportResult result = PublicationImportResult.ofPublication(pub);
      Chunk<PublicationImportResult> chunk = new Chunk<>(List.of(result));

      // when
      writer.write(chunk);

      // then
      verify(keywordDao, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("应该写入资助信息关联")
    void should_write_funding_associations() throws Exception {
      // given
      PublicationAggregate pub = createPublication("12345678");
      pub.assignId(PublicationId.of(PUBLICATION_ID));

      FundingData funding =
          FundingData.builder()
              .organizationId(1001L)
              .grantId("R01AI123456")
              .funderNameRaw("NIH")
              .funderAcronymRaw("NIH")
              .funderIdentifierRaw("100000002")
              .countryRaw("US")
              .fundingOrder(1)
              .provenanceCode("PUBMED")
              .build();

      PublicationImportResult result =
          PublicationImportResult.ofAll(pub, List.of(), List.of(), List.of(funding), List.of());
      Chunk<PublicationImportResult> chunk = new Chunk<>(List.of(result));

      // when
      writer.write(chunk);

      // then
      verify(fundingDao).saveAll(fundingCaptor.capture());
      List<PublicationFundingEntity> savedFunding = fundingCaptor.getValue();
      assertThat(savedFunding).hasSize(1);
      assertThat(savedFunding.getFirst().getPublicationId()).isEqualTo(PUBLICATION_ID);
      assertThat(savedFunding.getFirst().getOrganizationId()).isEqualTo(1001L);
      assertThat(savedFunding.getFirst().getGrantId()).isEqualTo("R01AI123456");
      assertThat(savedFunding.getFirst().getFunderNameRaw()).isEqualTo("NIH");
      assertThat(savedFunding.getFirst().getProvenanceCode()).isEqualTo("PUBMED");
    }

    @Test
    @DisplayName("无资助信息数据时不应调用 DAO")
    void should_not_call_funding_dao_when_no_funding_data() throws Exception {
      // given
      PublicationAggregate pub = createPublication("12345678");
      pub.assignId(PublicationId.of(PUBLICATION_ID));

      PublicationImportResult result = PublicationImportResult.ofPublication(pub);
      Chunk<PublicationImportResult> chunk = new Chunk<>(List.of(result));

      // when
      writer.write(chunk);

      // then
      verify(fundingDao, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("应该写入出版类型关联")
    void should_write_publication_type_associations() throws Exception {
      // given
      PublicationAggregate pub = createPublication("12345678");
      pub.assignId(PublicationId.of(PUBLICATION_ID));

      PublicationTypeData type = PublicationTypeData.of("D016428", "Journal Article", "MeSH", 1);

      PublicationImportResult result =
          PublicationImportResult.ofAll(pub, List.of(), List.of(), List.of(), List.of(type));
      Chunk<PublicationImportResult> chunk = new Chunk<>(List.of(result));

      // when
      writer.write(chunk);

      // then
      verify(typeDao).saveAll(typeCaptor.capture());
      List<PublicationTypeEntity> savedTypes = typeCaptor.getValue();
      assertThat(savedTypes).hasSize(1);
      assertThat(savedTypes.getFirst().getPublicationId()).isEqualTo(PUBLICATION_ID);
      assertThat(savedTypes.getFirst().getTypeId()).isEqualTo("D016428");
      assertThat(savedTypes.getFirst().getTypeValue()).isEqualTo("Journal Article");
      assertThat(savedTypes.getFirst().getVocabularySource()).isEqualTo("MeSH");
    }

    @Test
    @DisplayName("无出版类型数据时不应调用 DAO")
    void should_not_call_type_dao_when_no_type_data() throws Exception {
      // given
      PublicationAggregate pub = createPublication("12345678");
      pub.assignId(PublicationId.of(PUBLICATION_ID));

      PublicationImportResult result = PublicationImportResult.ofPublication(pub);
      Chunk<PublicationImportResult> chunk = new Chunk<>(List.of(result));

      // when
      writer.write(chunk);

      // then
      verify(typeDao, never()).saveAll(anyList());
    }
  }

  /// 创建测试用的 PublicationAggregate。
  private PublicationAggregate createPublication(String pmid) {
    return PublicationAggregate.create(
        ProvenanceCode.PUBMED,
        pmid,
        null, // doi
        VenueId.of(1L),
        VenueInstanceId.of(100L),
        "Test Article " + pmid,
        null,
        null,
        null,
        null,
        2024,
        true,
        null,
        null);
  }
}
