package com.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import com.patra.catalog.domain.model.vo.venue.CitationMetrics;
import com.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
import com.patra.catalog.domain.model.vo.venue.PublicationProfile;
import com.patra.catalog.domain.model.vo.venue.Society;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

/// VenueReadAdapter 详情查询集成测试。
///
/// **测试目标**：
///
/// - 正常查询：插入测试数据后查询，验证所有字段映射正确
/// - 不存在：查询不存在的 ID，返回 Optional.empty()
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({VenueReadAdapter.class, VenueReadModelMapperImpl.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("VenueReadAdapter 详情查询集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueReadAdapterDetailIT {

  @Autowired private VenueReadAdapter venueReadAdapter;

  @Autowired private VenueDao venueDao;

  /// 正常查询应返回完整详情。
  @Test
  @DisplayName("正常查询应返回完整详情并验证所有字段映射")
  void shouldReturnCompleteDetailWithAllFieldsMapped() {
    // Given: 插入测试 Venue 数据
    VenueEntity entity = new VenueEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setVenueType("JOURNAL");
    entity.setDisplayName("Nature");
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
    assertThat(detail.displayName()).isEqualTo("Nature");
    assertThat(detail.issnL()).isEqualTo("0028-0836");
    assertThat(detail.nlmId()).isEqualTo("0410462");
    assertThat(detail.openalexId()).isEqualTo("S12345");
    assertThat(detail.abbreviatedTitle()).isEqualTo("Nature");
    assertThat(detail.primaryLanguage()).isEqualTo("eng");
    assertThat(detail.countryCode()).isEqualTo("US");
    assertThat(detail.provenanceCode()).isEqualTo("OPENALEX");
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
    entity.setDisplayName("The Lancet");
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
            .homepageUrl("https://www.thelancet.com")
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
    assertThat(detail.publicationProfile().homepageUrl()).isEqualTo("https://www.thelancet.com");
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
}
