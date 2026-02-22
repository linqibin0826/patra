package com.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.dao.KeywordDao;
import com.patra.catalog.infra.persistence.dao.PublicationAbstractDao;
import com.patra.catalog.infra.persistence.dao.PublicationDao;
import com.patra.catalog.infra.persistence.dao.PublicationIdentifierDao;
import com.patra.catalog.infra.persistence.dao.PublicationKeywordDao;
import com.patra.catalog.infra.persistence.dao.PublicationMeshHeadingDao;
import com.patra.catalog.infra.persistence.dao.PublicationMeshQualifierDao;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import com.patra.catalog.infra.persistence.entity.KeywordEntity;
import com.patra.catalog.infra.persistence.entity.PublicationAbstractEntity;
import com.patra.catalog.infra.persistence.entity.PublicationEntity;
import com.patra.catalog.infra.persistence.entity.PublicationIdentifierEntity;
import com.patra.catalog.infra.persistence.entity.PublicationKeywordEntity;
import com.patra.catalog.infra.persistence.entity.PublicationMeshHeadingEntity;
import com.patra.catalog.infra.persistence.entity.PublicationMeshQualifierEntity;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import com.patra.common.model.enums.PublicationIdentifierType;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// PublicationReadAdapter 详情查询集成测试。
///
/// **测试目标**：
///
/// - 主表字段映射正确
/// - 摘要、标识符、关键词、MeSH 标引子表数据正确关联
/// - venueName 正确关联
/// - 不存在的 ID 返回 Optional.empty()
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  PublicationReadAdapter.class,
  PublicationReadModelMapperImpl.class,
  JpaAuditingConfig.class
})
@ActiveProfiles("test")
@DisplayName("PublicationReadAdapter 详情查询集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PublicationReadAdapterDetailIT {

  @Autowired private PublicationReadAdapter publicationReadAdapter;
  @Autowired private PublicationDao publicationDao;
  @Autowired private VenueDao venueDao;
  @Autowired private PublicationAbstractDao publicationAbstractDao;
  @Autowired private PublicationIdentifierDao publicationIdentifierDao;
  @Autowired private PublicationKeywordDao publicationKeywordDao;
  @Autowired private KeywordDao keywordDao;
  @Autowired private PublicationMeshHeadingDao publicationMeshHeadingDao;
  @Autowired private PublicationMeshQualifierDao publicationMeshQualifierDao;

  /// 正常查询应返回主表字段和 venueName。
  @Test
  @DisplayName("主表字段和 venueName 应正确映射")
  void shouldReturnMainFieldsAndVenueName() {
    // Given
    VenueEntity venue = saveVenue("Nature");
    PublicationEntity pub =
        savePublication("Test Article", "12345678", "10.1234/test", 2024, venue.getId());

    // When
    Optional<PublicationDetailReadModel> result =
        publicationReadAdapter.findPublicationDetail(pub.getId());

    // Then
    assertThat(result).isPresent();
    PublicationDetailReadModel detail = result.get();
    assertThat(detail.id()).isEqualTo(pub.getId());
    assertThat(detail.provenanceCode()).isEqualTo("PUBMED");
    assertThat(detail.title()).isEqualTo("Test Article");
    assertThat(detail.pmid()).isEqualTo("12345678");
    assertThat(detail.doi()).isEqualTo("10.1234/test");
    assertThat(detail.publicationYear()).isEqualTo(2024);
    assertThat(detail.venueId()).isEqualTo(venue.getId());
    assertThat(detail.venueName()).isEqualTo("Nature");
    assertThat(detail.createdAt()).isNotNull();
    assertThat(detail.updatedAt()).isNotNull();
    assertThat(detail.abstracts()).isEmpty();
    assertThat(detail.identifiers()).isEmpty();
    assertThat(detail.keywords()).isEmpty();
    assertThat(detail.meshHeadings()).isEmpty();
  }

  /// 摘要子表数据应正确关联。
  @Test
  @DisplayName("摘要数据应正确关联到详情")
  void shouldMapAbstracts() {
    // Given
    PublicationEntity pub = savePublication("Abstract Test", "23456789", null, 2024, null);

    PublicationAbstractEntity abstractEntity = new PublicationAbstractEntity();
    abstractEntity.setId(SnowflakeIdGenerator.getId());
    abstractEntity.setPublicationId(pub.getId());
    abstractEntity.setPlainText("This is the abstract text.");
    abstractEntity.setCopyright("Copyright 2024");
    abstractEntity.setAbstractType("MAIN");
    publicationAbstractDao.save(abstractEntity);

    // When
    Optional<PublicationDetailReadModel> result =
        publicationReadAdapter.findPublicationDetail(pub.getId());

    // Then
    assertThat(result).isPresent();
    assertThat(result.get().abstracts()).hasSize(1);
    assertThat(result.get().abstracts().getFirst().plainText())
        .isEqualTo("This is the abstract text.");
    assertThat(result.get().abstracts().getFirst().copyright()).isEqualTo("Copyright 2024");
    assertThat(result.get().abstracts().getFirst().abstractType()).isEqualTo("MAIN");
  }

  /// 标识符子表数据应正确关联。
  @Test
  @DisplayName("标识符数据应正确关联到详情")
  void shouldMapIdentifiers() {
    // Given
    PublicationEntity pub = savePublication("Identifier Test", "34567890", null, 2024, null);

    PublicationIdentifierEntity identifier = new PublicationIdentifierEntity();
    identifier.setId(SnowflakeIdGenerator.getId());
    identifier.setPublicationId(pub.getId());
    identifier.setType(PublicationIdentifierType.DOI);
    identifier.setValue("10.1234/test");
    identifier.setSource("crossref");
    publicationIdentifierDao.save(identifier);

    // When
    Optional<PublicationDetailReadModel> result =
        publicationReadAdapter.findPublicationDetail(pub.getId());

    // Then
    assertThat(result).isPresent();
    assertThat(result.get().identifiers()).hasSize(1);
    assertThat(result.get().identifiers().getFirst().type()).isEqualTo("DOI");
    assertThat(result.get().identifiers().getFirst().value()).isEqualTo("10.1234/test");
    assertThat(result.get().identifiers().getFirst().source()).isEqualTo("crossref");
  }

  /// 关键词子表数据应正确关联（联合 PublicationKeyword + Keyword）。
  @Test
  @DisplayName("关键词数据应正确关联到详情")
  void shouldMapKeywords() {
    // Given
    PublicationEntity pub = savePublication("Keyword Test", "45678901", null, 2024, null);

    KeywordEntity keyword = KeywordEntity.of("apoptosis", "pubmed", "en");
    keyword.setId(SnowflakeIdGenerator.getId());
    keywordDao.save(keyword);

    PublicationKeywordEntity pubKeyword =
        PublicationKeywordEntity.of(pub.getId(), keyword.getId(), true, 1, "MeSH");
    pubKeyword.setId(SnowflakeIdGenerator.getId());
    publicationKeywordDao.save(pubKeyword);

    // When
    Optional<PublicationDetailReadModel> result =
        publicationReadAdapter.findPublicationDetail(pub.getId());

    // Then
    assertThat(result).isPresent();
    assertThat(result.get().keywords()).hasSize(1);
    assertThat(result.get().keywords().getFirst().term()).isEqualTo("apoptosis");
    assertThat(result.get().keywords().getFirst().major()).isTrue();
    assertThat(result.get().keywords().getFirst().keywordSet()).isEqualTo("MeSH");
  }

  /// MeSH 标引 + 限定词子表数据应正确关联。
  @Test
  @DisplayName("MeSH 标引和限定词数据应正确关联到详情")
  void shouldMapMeshHeadingsWithQualifiers() {
    // Given
    PublicationEntity pub = savePublication("MeSH Test", "56789012", null, 2024, null);

    PublicationMeshHeadingEntity heading =
        PublicationMeshHeadingEntity.of(pub.getId(), "D001234", true, 1);
    heading.setId(SnowflakeIdGenerator.getId());
    publicationMeshHeadingDao.save(heading);

    PublicationMeshQualifierEntity qualifier =
        PublicationMeshQualifierEntity.of(heading.getId(), "Q000235", true, 1);
    qualifier.setId(SnowflakeIdGenerator.getId());
    publicationMeshQualifierDao.save(qualifier);

    // When
    Optional<PublicationDetailReadModel> result =
        publicationReadAdapter.findPublicationDetail(pub.getId());

    // Then
    assertThat(result).isPresent();
    assertThat(result.get().meshHeadings()).hasSize(1);
    var meshHeading = result.get().meshHeadings().getFirst();
    assertThat(meshHeading.descriptorUi()).isEqualTo("D001234");
    assertThat(meshHeading.majorTopic()).isTrue();
    assertThat(meshHeading.qualifiers()).hasSize(1);
    assertThat(meshHeading.qualifiers().getFirst().qualifierUi()).isEqualTo("Q000235");
  }

  /// 查询不存在的 ID 应返回 Optional.empty()。
  @Test
  @DisplayName("查询不存在的 ID 应返回 Optional.empty()")
  void shouldReturnEmptyWhenIdNotExists() {
    // When
    Optional<PublicationDetailReadModel> result =
        publicationReadAdapter.findPublicationDetail(999999L);

    // Then
    assertThat(result).isEmpty();
  }

  /// 保存测试用 Venue 实体。
  private VenueEntity saveVenue(String title) {
    VenueEntity entity = new VenueEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setVenueType("JOURNAL");
    entity.setTitle(title);
    entity.setProvenanceCode("OPENALEX");
    entity.setCountryCode("US");
    return venueDao.save(entity);
  }

  /// 保存测试用 Publication 实体。
  private PublicationEntity savePublication(
      String title, String pmid, String doi, Integer year, Long venueId) {
    PublicationEntity entity =
        PublicationEntity.builder()
            .id(SnowflakeIdGenerator.getId())
            .provenanceCode("PUBMED")
            .title(title)
            .pmid(pmid)
            .doi(doi)
            .publicationYear(year)
            .languageCode("en")
            .venueId(venueId)
            .venueInstanceId(venueId != null ? venueId : SnowflakeIdGenerator.getId())
            .isOa(false)
            .authorsComplete(true)
            .lastSyncedAt(Instant.parse("2026-02-13T00:00:00Z"))
            .build();
    return publicationDao.save(entity);
  }
}
