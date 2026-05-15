package dev.linqibin.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.catalog.domain.model.enums.CasWarningLevel;
import dev.linqibin.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import dev.linqibin.patra.catalog.domain.model.read.venue.VenueDetailReadModel.IndexingHistoryItem;
import dev.linqibin.patra.catalog.domain.model.read.venue.VenueDetailReadModel.MeshHeading;
import dev.linqibin.patra.catalog.domain.model.read.venue.VenueDetailReadModel.VenueRelationItem;
import dev.linqibin.patra.catalog.domain.model.read.venue.VenueLatestRating;
import dev.linqibin.patra.catalog.domain.model.vo.venue.CitationMetrics;
import dev.linqibin.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
import dev.linqibin.patra.catalog.domain.model.vo.venue.PublicationProfile;
import dev.linqibin.patra.catalog.domain.model.vo.venue.Society;
import dev.linqibin.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import dev.linqibin.patra.catalog.infra.persistence.dao.CasRatingDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.CasWarningDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.JcrRatingDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.ScopusRatingDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.VenueDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.VenueIndexingHistoryDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.VenueMeshDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.VenueRelationDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.CasRatingEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.CasWarningEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.ScopusRatingEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.VenueEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.VenueIndexingHistoryEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.VenueMeshEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.VenueRelationEntity;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// VenueReadAdapter 详情查询集成测试。
///
/// **测试目标**：
///
/// - 正常查询：插入测试数据后查询，验证所有字段映射正确
/// - 不存在：查询不存在的 ID，返回 Optional.empty()
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  VenueReadAdapter.class,
  VenueReadModelMapperImpl.class,
  JpaAuditingConfig.class,
  JacksonAutoConfiguration.class
})
@ActiveProfiles("test")
@DisplayName("VenueReadAdapter 详情查询集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueReadAdapterDetailIT {

  @Autowired private VenueReadAdapter venueReadAdapter;

  @Autowired private VenueDao venueDao;
  @Autowired private JcrRatingDao jcrRatingDao;
  @Autowired private CasRatingDao casRatingDao;
  @Autowired private ScopusRatingDao scopusRatingDao;
  @Autowired private CasWarningDao casWarningDao;
  @Autowired private VenueMeshDao venueMeshDao;
  @Autowired private VenueRelationDao venueRelationDao;
  @Autowired private VenueIndexingHistoryDao venueIndexingHistoryDao;

  /// 正常查询应返回完整详情。
  @Test
  @DisplayName("正常查询应返回完整详情并验证所有字段映射")
  void shouldReturnCompleteDetailWithAllFieldsMapped() {
    // Given: 插入测试 Venue 数据
    VenueEntity entity = new VenueEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setVenueType("JOURNAL");
    entity.setTitle("Nature");
    entity.setIssnL("0028-0836");
    entity.setNlmId("0410462");
    entity.setOpenalexId("S12345");
    entity.setAbbreviatedTitle("Nature");
    entity.setPrimaryLanguage("eng");
    entity.setCountryCode("US");
    entity.setProvenanceCode("OPENALEX");
    entity.setLastSyncedAt(Instant.parse("2026-02-13T00:00:00Z"));

    // 设置嵌入式值对象（使用空对象而非 null 进行测试）
    entity.setPublicationProfile(null);
    entity.setCitationMetrics(null);
    entity.setOpenAccess(null);
    entity.setAffiliatedSocieties(List.of());

    venueDao.save(entity);

    // When: 查询详情
    Optional<VenueDetailReadModel> result = venueReadAdapter.findVenueDetail(entity.getId());

    // Then: 验证字段映射正确
    assertThat(result).isPresent();
    VenueDetailReadModel detail = result.get();
    assertThat(detail.id()).isEqualTo(entity.getId());
    assertThat(detail.venueType()).isEqualTo("JOURNAL");
    assertThat(detail.title()).isEqualTo("Nature");
    assertThat(detail.issnL()).isEqualTo("0028-0836");
    assertThat(detail.nlmId()).isEqualTo("0410462");
    assertThat(detail.openalexId()).isEqualTo("S12345");
    assertThat(detail.abbreviatedTitle()).isEqualTo("Nature");
    assertThat(detail.primaryLanguage()).isEqualTo("eng");
    assertThat(detail.countryCode()).isEqualTo("US");
    assertThat(detail.lastSyncedAt()).isEqualTo(Instant.parse("2026-02-13T00:00:00Z"));
    assertThat(detail.publicationProfile()).isNull();
    assertThat(detail.citationMetrics()).isNull();
    assertThat(detail.openAccess()).isNull();
    assertThat(detail.affiliatedSocieties()).isEmpty();
    assertThat(detail.createdAt()).isNotNull();
    assertThat(detail.updatedAt()).isNotNull();
  }

  /// 包含完整嵌套值对象的查询应正确映射所有嵌套字段。
  @Test
  @DisplayName("包含嵌套值对象的查询应正确映射 PublicationProfile、CitationMetrics、OpenAccess、Society")
  void shouldMapNestedValueObjectsCorrectly() {
    // Given: 插入包含完整嵌套值对象的 Venue 数据
    VenueEntity entity = new VenueEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setVenueType("JOURNAL");
    entity.setTitle("The Lancet");
    entity.setIssnL("0140-6736");
    entity.setNlmId("0255562");
    entity.setOpenalexId("S49861241");
    entity.setAbbreviatedTitle("Lancet");
    entity.setPrimaryLanguage("eng");
    entity.setCountryCode("GB");
    entity.setProvenanceCode("OPENALEX");
    entity.setLastSyncedAt(Instant.parse("2026-02-14T00:00:00Z"));

    entity.setPublicationProfile(
        PublicationProfile.builder()
            .abbreviatedTitle("Lancet")
            .alternateTitles(List.of("The Lancet", "Lancet (London, England)"))
            .frequency("Weekly")
            .countryCode("GB")
            .build());

    entity.setCitationMetrics(
        new CitationMetrics(380000, 5200000, 450, 3200, new BigDecimal("65.3")));

    entity.setOpenAccess(new OpenAccessInfo(false, false, "hybrid", 5900, List.of()));

    entity.setAffiliatedSocieties(List.of(new Society("https://www.elsevier.com", "Elsevier")));

    venueDao.save(entity);

    // When
    Optional<VenueDetailReadModel> result = venueReadAdapter.findVenueDetail(entity.getId());

    // Then: 验证嵌套值对象映射正确
    assertThat(result).isPresent();
    VenueDetailReadModel detail = result.get();

    // PublicationProfile
    assertThat(detail.publicationProfile()).isNotNull();
    assertThat(detail.publicationProfile().abbreviatedTitle()).isEqualTo("Lancet");
    assertThat(detail.publicationProfile().alternateTitles())
        .containsExactly("The Lancet", "Lancet (London, England)");
    assertThat(detail.publicationProfile().frequency()).isEqualTo("Weekly");

    // CitationMetrics
    assertThat(detail.citationMetrics()).isNotNull();
    assertThat(detail.citationMetrics().worksCount()).isEqualTo(380000);
    assertThat(detail.citationMetrics().citedByCount()).isEqualTo(5200000);
    assertThat(detail.citationMetrics().hIndex()).isEqualTo(450);
    assertThat(detail.citationMetrics().i10Index()).isEqualTo(3200);
    assertThat(detail.citationMetrics().twoYearMeanCitedness())
        .isEqualByComparingTo(new BigDecimal("65.3"));

    // OpenAccess
    assertThat(detail.openAccess()).isNotNull();
    assertThat(detail.openAccess().isOa()).isFalse();
    assertThat(detail.openAccess().oaType()).isEqualTo("hybrid");
    assertThat(detail.openAccess().apcUsd()).isEqualTo(5900);

    // AffiliatedSocieties
    assertThat(detail.affiliatedSocieties()).hasSize(1);
    assertThat(detail.affiliatedSocieties().getFirst().organization()).isEqualTo("Elsevier");
    assertThat(detail.affiliatedSocieties().getFirst().url()).isEqualTo("https://www.elsevier.com");
  }

  /// 查询不存在的 ID 应返回 Optional.empty()。
  @Test
  @DisplayName("查询不存在的 ID 应返回 Optional.empty()")
  void shouldReturnEmptyWhenIdNotExists() {
    // Given: 不存在的 ID
    Long nonExistentId = 999999L;

    // When: 查询不存在的 ID
    Optional<VenueDetailReadModel> result = venueReadAdapter.findVenueDetail(nonExistentId);

    // Then: 返回空
    assertThat(result).isEmpty();
  }

  /// 详情查询应包含最新 JCR/CAS/Scopus 评级和 CAS 预警数据。
  @Test
  @DisplayName("详情查询应包含最新 JCR/CAS/Scopus 评级和 CAS 预警数据")
  void shouldReturnDetailWithLatestRatingData() {
    // Given: 插入 Venue
    VenueEntity venue = new VenueEntity();
    venue.setId(SnowflakeIdGenerator.getId());
    venue.setVenueType("JOURNAL");
    venue.setTitle("Test Journal With Ratings");
    venue.setProvenanceCode("OPENALEX");
    venue.setAffiliatedSocieties(List.of());
    venueDao.save(venue);

    Long venueId = venue.getId();

    // 插入 JCR 评级（两条，应取最新年份 2025）
    JcrRatingEntity jcr2024 = new JcrRatingEntity();
    jcr2024.setId(SnowflakeIdGenerator.getId());
    jcr2024.setVenueId(venueId);
    jcr2024.setYear((short) 2024);
    jcr2024.setImpactFactor(new BigDecimal("10.5000"));
    jcr2024.setJifQuartile("Q1");
    jcrRatingDao.save(jcr2024);

    JcrRatingEntity jcr2025 = new JcrRatingEntity();
    jcr2025.setId(SnowflakeIdGenerator.getId());
    jcr2025.setVenueId(venueId);
    jcr2025.setYear((short) 2025);
    jcr2025.setImpactFactor(new BigDecimal("12.3400"));
    jcr2025.setJifQuartile("Q1");
    jcr2025.setJifRank("5/200");
    jcr2025.setJifPercentile(new BigDecimal("97.50"));
    jcr2025.setWosOverallQuartile("1区");
    jcr2025.setCollection("SCIE");
    jcr2025.setSelfCitationRate(new BigDecimal("3.20"));
    jcr2025.setResearchDirection("MULTIDISCIPLINARY SCIENCES");
    jcr2025.setJciValue(new BigDecimal("8.5000"));
    jcr2025.setJciQuartile("Q1");
    jcrRatingDao.save(jcr2025);

    // 插入 CAS 评级
    CasRatingEntity cas = new CasRatingEntity();
    cas.setId(SnowflakeIdGenerator.getId());
    cas.setVenueId(venueId);
    cas.setYear((short) 2025);
    cas.setEdition("升级版");
    cas.setMajorCategory("医学");
    cas.setMajorQuartile("1区");
    cas.setMinorSubject("肿瘤学");
    cas.setMinorQuartile("1区");
    cas.setIsTopJournal(true);
    cas.setIsReviewJournal(false);
    casRatingDao.save(cas);

    // 插入 Scopus 评级
    ScopusRatingEntity scopus = new ScopusRatingEntity();
    scopus.setId(SnowflakeIdGenerator.getId());
    scopus.setVenueId(venueId);
    scopus.setYear((short) 2025);
    scopus.setCiteScore(new BigDecimal("15.6000"));
    scopus.setSjr(new BigDecimal("5.2300"));
    scopus.setSnip(new BigDecimal("4.1200"));
    scopus.setQuartile("Q1");
    scopus.setPercentile(new BigDecimal("98.00"));
    scopusRatingDao.save(scopus);

    // 插入 CAS 预警（两条，应取最新年份 2025）
    CasWarningEntity warning2024 = new CasWarningEntity();
    warning2024.setId(SnowflakeIdGenerator.getId());
    warning2024.setVenueId(venueId);
    warning2024.setPublishedYear((short) 2024);
    warning2024.setEditionLabel("2024版");
    warning2024.setInWarningList(false);
    casWarningDao.save(warning2024);

    CasWarningEntity warning2025 = new CasWarningEntity();
    warning2025.setId(SnowflakeIdGenerator.getId());
    warning2025.setVenueId(venueId);
    warning2025.setPublishedYear((short) 2025);
    warning2025.setEditionLabel("2025版");
    warning2025.setInWarningList(true);
    warning2025.setWarningLevel(CasWarningLevel.MEDIUM);
    casWarningDao.save(warning2025);

    // When
    Optional<VenueDetailReadModel> result = venueReadAdapter.findVenueDetail(venueId);

    // Then
    assertThat(result).isPresent();
    VenueDetailReadModel detail = result.get();
    VenueLatestRating rating = detail.latestRating();
    assertThat(rating).isNotNull();

    // JCR（应为 2025 年数据）
    assertThat(rating.jcrYear()).isEqualTo((short) 2025);
    assertThat(rating.impactFactor()).isEqualByComparingTo(new BigDecimal("12.34"));
    assertThat(rating.jifQuartile()).isEqualTo("Q1");
    assertThat(rating.jifRank()).isEqualTo("5/200");
    assertThat(rating.jifPercentile()).isEqualByComparingTo(new BigDecimal("97.50"));
    assertThat(rating.wosOverallQuartile()).isEqualTo("1区");
    assertThat(rating.collection()).isEqualTo("SCIE");
    assertThat(rating.selfCitationRate()).isEqualByComparingTo(new BigDecimal("3.20"));
    assertThat(rating.researchDirection()).isEqualTo("MULTIDISCIPLINARY SCIENCES");
    assertThat(rating.jciValue()).isEqualByComparingTo(new BigDecimal("8.50"));
    assertThat(rating.jciQuartile()).isEqualTo("Q1");

    // CAS
    assertThat(rating.casYear()).isEqualTo((short) 2025);
    assertThat(rating.casEdition()).isEqualTo("升级版");
    assertThat(rating.majorCategory()).isEqualTo("医学");
    assertThat(rating.majorQuartile()).isEqualTo("1区");
    assertThat(rating.minorSubject()).isEqualTo("肿瘤学");
    assertThat(rating.minorQuartile()).isEqualTo("1区");
    assertThat(rating.isTopJournal()).isTrue();
    assertThat(rating.isReviewJournal()).isFalse();

    // Scopus
    assertThat(rating.scopusYear()).isEqualTo((short) 2025);
    assertThat(rating.citeScore()).isEqualByComparingTo(new BigDecimal("15.60"));
    assertThat(rating.sjr()).isEqualByComparingTo(new BigDecimal("5.23"));
    assertThat(rating.snip()).isEqualByComparingTo(new BigDecimal("4.12"));
    assertThat(rating.citeScoreQuartile()).isEqualTo("Q1");
    assertThat(rating.citeScorePercentile()).isEqualByComparingTo(new BigDecimal("98.00"));

    // Warning（应为 2025 年数据）
    assertThat(rating.inWarningList()).isTrue();
    assertThat(rating.warningLevel()).isEqualTo("medium");
  }

  /// 无评级数据时 latestRating 应为 null。
  @Test
  @DisplayName("无评级数据时 latestRating 应为 null")
  void shouldReturnNullLatestRatingWhenNoRatingData() {
    // Given: 仅插入 Venue，无评级数据
    VenueEntity venue = new VenueEntity();
    venue.setId(SnowflakeIdGenerator.getId());
    venue.setVenueType("JOURNAL");
    venue.setTitle("Journal Without Ratings");
    venue.setProvenanceCode("OPENALEX");
    venue.setAffiliatedSocieties(List.of());
    venueDao.save(venue);

    // When
    Optional<VenueDetailReadModel> result = venueReadAdapter.findVenueDetail(venue.getId());

    // Then
    assertThat(result).isPresent();
    assertThat(result.get().latestRating()).isNull();
  }

  /// 详情查询应包含 MeSH 主题词数据。
  @Test
  @DisplayName("详情查询应包含 MeSH 主题词数据")
  void shouldReturnDetailWithMeshHeadings() {
    // Given: 插入 Venue 和 MeSH 数据
    VenueEntity venue = new VenueEntity();
    venue.setId(SnowflakeIdGenerator.getId());
    venue.setVenueType("JOURNAL");
    venue.setTitle("Journal With MeSH");
    venue.setProvenanceCode("NLM");
    venue.setAffiliatedSocieties(List.of());
    venueDao.save(venue);

    Long venueId = venue.getId();

    VenueMeshEntity mesh1 = new VenueMeshEntity();
    mesh1.setId(SnowflakeIdGenerator.getId());
    mesh1.setVenueId(venueId);
    mesh1.setDescriptorName("Medicine");
    mesh1.setDescriptorUi("D008511");
    mesh1.setIsMajorTopic(true);
    mesh1.setQualifierName(null);
    mesh1.setQualifierUi(null);
    venueMeshDao.save(mesh1);

    VenueMeshEntity mesh2 = new VenueMeshEntity();
    mesh2.setId(SnowflakeIdGenerator.getId());
    mesh2.setVenueId(venueId);
    mesh2.setDescriptorName("Cardiology");
    mesh2.setDescriptorUi("D002309");
    mesh2.setIsMajorTopic(false);
    mesh2.setQualifierName("methods");
    mesh2.setQualifierUi("Q000379");
    venueMeshDao.save(mesh2);

    // When
    Optional<VenueDetailReadModel> result = venueReadAdapter.findVenueDetail(venueId);

    // Then
    assertThat(result).isPresent();
    VenueDetailReadModel detail = result.get();
    assertThat(detail.meshHeadings()).hasSize(2);

    MeshHeading medicineHeading =
        detail.meshHeadings().stream()
            .filter(m -> "Medicine".equals(m.descriptorName()))
            .findFirst()
            .orElseThrow();
    assertThat(medicineHeading.descriptorUi()).isEqualTo("D008511");
    assertThat(medicineHeading.isMajorTopic()).isTrue();
    assertThat(medicineHeading.qualifierName()).isNull();

    MeshHeading cardiologyHeading =
        detail.meshHeadings().stream()
            .filter(m -> "Cardiology".equals(m.descriptorName()))
            .findFirst()
            .orElseThrow();
    assertThat(cardiologyHeading.descriptorUi()).isEqualTo("D002309");
    assertThat(cardiologyHeading.isMajorTopic()).isFalse();
    assertThat(cardiologyHeading.qualifierName()).isEqualTo("methods");
    assertThat(cardiologyHeading.qualifierUi()).isEqualTo("Q000379");
  }

  /// 详情查询应包含期刊关联关系数据。
  @Test
  @DisplayName("详情查询应包含期刊关联关系数据")
  void shouldReturnDetailWithRelations() {
    // Given: 插入 Venue 和关联关系数据
    VenueEntity venue = new VenueEntity();
    venue.setId(SnowflakeIdGenerator.getId());
    venue.setVenueType("JOURNAL");
    venue.setTitle("Journal With Relations");
    venue.setProvenanceCode("NLM");
    venue.setAffiliatedSocieties(List.of());
    venueDao.save(venue);

    Long venueId = venue.getId();

    VenueRelationEntity relation = new VenueRelationEntity();
    relation.setId(SnowflakeIdGenerator.getId());
    relation.setVenueId(venueId);
    relation.setRelatedVenueId(123456L);
    relation.setRelatedTitle("Previous Journal Title");
    relation.setRelationType("PRECEDING");
    relation.setEffectiveDate(LocalDate.of(2000, 1, 1));
    relation.setNotes("Journal was renamed");
    venueRelationDao.save(relation);

    // When
    Optional<VenueDetailReadModel> result = venueReadAdapter.findVenueDetail(venueId);

    // Then
    assertThat(result).isPresent();
    VenueDetailReadModel detail = result.get();
    assertThat(detail.relations()).hasSize(1);

    VenueRelationItem item = detail.relations().getFirst();
    assertThat(item.relatedVenueId()).isEqualTo(123456L);
    assertThat(item.relatedTitle()).isEqualTo("Previous Journal Title");
    assertThat(item.relationType()).isEqualTo("PRECEDING");
    assertThat(item.effectiveDate()).isEqualTo(LocalDate.of(2000, 1, 1));
    assertThat(item.notes()).isEqualTo("Journal was renamed");
  }

  /// 详情查询应包含索引历史数据。
  @Test
  @DisplayName("详情查询应包含索引历史数据")
  void shouldReturnDetailWithIndexingHistory() {
    // Given: 插入 Venue 和索引历史数据
    VenueEntity venue = new VenueEntity();
    venue.setId(SnowflakeIdGenerator.getId());
    venue.setVenueType("JOURNAL");
    venue.setTitle("Journal With Indexing");
    venue.setProvenanceCode("NLM");
    venue.setAffiliatedSocieties(List.of());
    venueDao.save(venue);

    Long venueId = venue.getId();

    VenueIndexingHistoryEntity indexing = new VenueIndexingHistoryEntity();
    indexing.setId(SnowflakeIdGenerator.getId());
    indexing.setVenueId(venueId);
    indexing.setIndexingSource("MEDLINE");
    indexing.setCurrentlyIndexed(true);
    indexing.setIndexingTreatment("FULL");
    indexing.setStartYear(1966);
    indexing.setEndYear(null);
    indexing.setStartVolume("1");
    indexing.setStartIssue("1");
    indexing.setEndVolume(null);
    indexing.setEndIssue(null);
    venueIndexingHistoryDao.save(indexing);

    // When
    Optional<VenueDetailReadModel> result = venueReadAdapter.findVenueDetail(venueId);

    // Then
    assertThat(result).isPresent();
    VenueDetailReadModel detail = result.get();
    assertThat(detail.indexingHistory()).hasSize(1);

    IndexingHistoryItem item = detail.indexingHistory().getFirst();
    assertThat(item.indexingSource()).isEqualTo("MEDLINE");
    assertThat(item.currentlyIndexed()).isTrue();
    assertThat(item.indexingTreatment()).isEqualTo("FULL");
    assertThat(item.startYear()).isEqualTo(1966);
    assertThat(item.endYear()).isNull();
    assertThat(item.startVolume()).isEqualTo("1");
    assertThat(item.startIssue()).isEqualTo("1");
    assertThat(item.endVolume()).isNull();
    assertThat(item.endIssue()).isNull();
  }

  /// 无 MeSH/关系/索引历史时应返回空列表。
  @Test
  @DisplayName("无 MeSH/关系/索引历史时应返回空列表")
  void shouldReturnEmptyListsWhenNoMeshRelationsOrIndexing() {
    // Given: 仅插入 Venue，无关联数据
    VenueEntity venue = new VenueEntity();
    venue.setId(SnowflakeIdGenerator.getId());
    venue.setVenueType("JOURNAL");
    venue.setTitle("Journal Without Extra Data");
    venue.setProvenanceCode("OPENALEX");
    venue.setAffiliatedSocieties(List.of());
    venueDao.save(venue);

    // When
    Optional<VenueDetailReadModel> result = venueReadAdapter.findVenueDetail(venue.getId());

    // Then
    assertThat(result).isPresent();
    VenueDetailReadModel detail = result.get();
    assertThat(detail.meshHeadings()).isEmpty();
    assertThat(detail.relations()).isEmpty();
    assertThat(detail.indexingHistory()).isEmpty();
  }
}
