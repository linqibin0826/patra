package com.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.read.venue.VenueFilter;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import com.patra.common.query.PageResult;
import com.patra.common.query.PagingParams;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// VenueReadAdapter 集成测试。
///
/// **测试目标**：
///
/// - 仅查询 `venue_type=JOURNAL`
/// - 各筛选条件独立生效（keyword 前缀匹配、provenanceCode/countryCode/issnL/nlmId 精确匹配）
/// - 分页元信息与排序规则
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({VenueReadAdapter.class, VenueReadModelMapperImpl.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("VenueReadAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueReadAdapterIT {

  private static final VenueFilter EMPTY_FILTER = VenueFilter.builder().build();

  @Autowired private VenueReadAdapter venueReadAdapter;

  @Autowired private VenueDao venueDao;

  /// 无筛选条件时应返回所有期刊并排除非期刊类型。
  @Test
  @DisplayName("无筛选条件应只返回 JOURNAL 类型")
  void shouldReturnOnlyJournalsWhenNoFilter() {
    // Given
    saveVenue("JOURNAL", "Nature", "0028-0836", "0410462", "OPENALEX", "US");
    saveVenue("JOURNAL", "The Lancet", "0140-6736", "2985213", "OPENALEX", "GB");
    saveVenue("REPOSITORY", "PubMed Central", null, null, "PUBMED", "US");

    // When
    PageResult<VenueSummaryReadModel> page =
        venueReadAdapter.findVenuePage(PagingParams.of(1, 20), EMPTY_FILTER);

    // Then
    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items()).hasSize(2);
    assertThat(page.items()).extracting("displayName").containsExactly("The Lancet", "Nature");
  }

  @Nested
  @DisplayName("关键词检索策略")
  class KeywordSearchTests {

    /// 名称前缀匹配应命中 displayName LIKE q%。
    @Test
    @DisplayName("名称前缀匹配")
    void shouldMatchByDisplayNamePrefix() {
      // Given
      saveVenue("JOURNAL", "Nature", "0028-0836", "0410462", "OPENALEX", "US");
      saveVenue("JOURNAL", "Science", "0036-8075", "0404511", "OPENALEX", "US");

      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20), VenueFilter.builder().keyword("Nat").build());

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("displayName").isEqualTo("Nature");
    }

    /// ISSN-L 精确匹配应命中对应期刊。
    @Test
    @DisplayName("ISSN-L 精确匹配")
    void shouldMatchByExactIssnL() {
      // Given
      saveVenue("JOURNAL", "Nature", "0028-0836", "0410462", "OPENALEX", "US");
      saveVenue("JOURNAL", "Science", "0036-8075", "0404511", "OPENALEX", "US");

      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20), VenueFilter.builder().issnL("0036-8075").build());

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("displayName").isEqualTo("Science");
    }

    /// NLM ID 精确匹配应命中对应期刊。
    @Test
    @DisplayName("NLM ID 精确匹配")
    void shouldMatchByExactNlmId() {
      // Given
      saveVenue("JOURNAL", "Nature", "0028-0836", "0410462", "OPENALEX", "US");
      saveVenue("JOURNAL", "Science", "0036-8075", "0404511", "OPENALEX", "US");

      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20), VenueFilter.builder().nlmId("0410462").build());

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("displayName").isEqualTo("Nature");
    }

    /// provenanceCode 精确匹配应命中对应数据来源的期刊。
    @Test
    @DisplayName("provenanceCode 精确匹配")
    void shouldMatchByExactProvenanceCode() {
      // Given
      saveVenue("JOURNAL", "Nature", "0028-0836", "0410462", "OPENALEX", "US");
      saveVenue("JOURNAL", "BMJ", "0959-8138", "0372351", "PUBMED", "GB");

      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20), VenueFilter.builder().provenanceCode("PUBMED").build());

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("displayName").isEqualTo("BMJ");
    }

    /// countryCode 精确匹配应命中对应国家的期刊。
    @Test
    @DisplayName("countryCode 精确匹配")
    void shouldMatchByExactCountryCode() {
      // Given
      saveVenue("JOURNAL", "Nature", "0028-0836", "0410462", "OPENALEX", "US");
      saveVenue("JOURNAL", "The Lancet", "0140-6736", "2985213", "OPENALEX", "GB");
      saveVenue("JOURNAL", "Science", "0036-8075", "0404511", "OPENALEX", "US");

      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20), VenueFilter.builder().countryCode("GB").build());

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("displayName").isEqualTo("The Lancet");
    }
  }

  /// 分页应返回正确元信息，并按更新时间/ID 倒序稳定输出。
  @Test
  @DisplayName("分页元信息与排序应正确")
  void shouldReturnCorrectPagingMetaAndSort() {
    // Given
    VenueEntity v1 = saveVenue("JOURNAL", "Journal-1", "1111-1111", "NLM1", "OPENALEX", "US");
    VenueEntity v2 = saveVenue("JOURNAL", "Journal-2", "2222-2222", "NLM2", "OPENALEX", "US");
    VenueEntity v3 = saveVenue("JOURNAL", "Journal-3", "3333-3333", "NLM3", "OPENALEX", "US");

    Long expectedSecondPageId =
        List.of(v1.getId(), v2.getId(), v3.getId()).stream()
            .sorted((left, right) -> Long.compare(right, left))
            .toList()
            .get(1);

    // When
    PageResult<VenueSummaryReadModel> page =
        venueReadAdapter.findVenuePage(PagingParams.of(2, 1), EMPTY_FILTER);

    // Then
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.pageSize()).isEqualTo(1);
    assertThat(page.total()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(3);
    assertThat(page.items()).singleElement().extracting("id").isEqualTo(expectedSecondPageId);
  }

  /// 保存测试用 Venue 实体。
  ///
  /// @param venueType 载体类型
  /// @param displayName 展示名称
  /// @param issnL ISSN-L
  /// @param nlmId NLM ID
  /// @param provenanceCode 数据来源编码
  /// @param countryCode 国家编码
  /// @return 持久化后的实体
  private VenueEntity saveVenue(
      String venueType,
      String displayName,
      String issnL,
      String nlmId,
      String provenanceCode,
      String countryCode) {
    VenueEntity entity = new VenueEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setVenueType(venueType);
    entity.setDisplayName(displayName);
    entity.setIssnL(issnL);
    entity.setNlmId(nlmId);
    entity.setProvenanceCode(provenanceCode);
    entity.setCountryCode(countryCode);
    entity.setLastSyncedAt(Instant.parse("2026-02-13T00:00:00Z"));
    return venueDao.save(entity);
  }
}
