package com.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.read.venue.VenueFilter;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.domain.model.vo.venue.CitationMetrics;
import com.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
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
/// - 各筛选条件独立生效（keyword 前缀匹配、countryCode/issnL/nlmId 精确匹配）
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
    assertThat(page.items()).extracting("title").containsExactly("The Lancet", "Nature");
  }

  @Nested
  @DisplayName("关键词检索策略")
  class KeywordSearchTests {

    /// 名称前缀匹配应命中 title LIKE q%。
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
      assertThat(page.items()).singleElement().extracting("title").isEqualTo("Nature");
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
      assertThat(page.items()).singleElement().extracting("title").isEqualTo("Science");
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
      assertThat(page.items()).singleElement().extracting("title").isEqualTo("Nature");
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
      assertThat(page.items()).singleElement().extracting("title").isEqualTo("The Lancet");
    }
  }

  /// 分页应返回正确元信息，按 h-index DESC, id DESC 排序。
  @Test
  @DisplayName("分页元信息与排序应正确")
  void shouldReturnCorrectPagingMetaAndSort() {
    // Given — 三条期刊均无 h-index，退化为 id DESC 排序
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

  @Nested
  @DisplayName("h-index 排序规则")
  class HIndexSortingTests {

    /// 不同 h-index 值的期刊应按 h-index 降序排列。
    @Test
    @DisplayName("应按 h-index 降序排列")
    void shouldSortByHIndexDescending() {
      // Given
      saveVenueWithHIndex("Low Impact", "1111-1111", 50);
      saveVenueWithHIndex("High Impact", "2222-2222", 400);
      saveVenueWithHIndex("Mid Impact", "3333-3333", 150);

      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(PagingParams.of(1, 10), EMPTY_FILTER);

      // Then
      assertThat(page.items())
          .extracting("title")
          .containsExactly("High Impact", "Mid Impact", "Low Impact");
      assertThat(page.items()).extracting("hIndex").containsExactly(400, 150, 50);
    }

    /// h-index 相同时应按 id 降序排列（稳定排序）。
    @Test
    @DisplayName("h-index 相同时应按 id 降序排列")
    void shouldFallbackToIdDescWhenHIndexEqual() {
      // Given
      saveVenueWithHIndex("First Saved", "1111-1111", 100);
      saveVenueWithHIndex("Second Saved", "2222-2222", 100);

      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(PagingParams.of(1, 10), EMPTY_FILTER);

      // Then — id 递增，DESC 排序后 Second Saved 在前
      assertThat(page.items()).extracting("title").containsExactly("Second Saved", "First Saved");
    }

    /// 无 h-index（citation_metrics 为 null）的期刊应排在有 h-index 的期刊之后。
    @Test
    @DisplayName("无 h-index 的期刊应排在最后")
    void shouldRankNullHIndexLast() {
      // Given
      saveVenue("JOURNAL", "No Metrics", "1111-1111", "NLM1", "OPENALEX", "US");
      saveVenueWithHIndex("Has Metrics", "2222-2222", 200);

      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(PagingParams.of(1, 10), EMPTY_FILTER);

      // Then
      assertThat(page.items()).extracting("title").containsExactly("Has Metrics", "No Metrics");
      assertThat(page.items()).extracting("hIndex").containsExactly(200, null);
    }
  }

  @Nested
  @DisplayName("JSON 嵌套字段映射")
  class JsonNestedFieldMappingTests {

    /// MapStruct 应正确提取 citationMetrics/letPubData/openAccess 中的嵌套字段。
    @Test
    @DisplayName("应正确映射 citationMetrics.hIndex 到 VenueSummaryReadModel")
    void shouldMapCitationMetricsHIndex() {
      // Given
      VenueEntity entity = buildBaseVenue("Nature", "0028-0836");
      entity.setCitationMetrics(CitationMetrics.of(50000, 2000000, 412, 8000, null));
      venueDao.save(entity);

      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(PagingParams.of(1, 10), EMPTY_FILTER);

      // Then
      assertThat(page.items())
          .singleElement()
          .satisfies(
              item -> {
                assertThat(item.hIndex()).isEqualTo(412);
              });
    }

    /// MapStruct 应正确提取 openAccess.isOa 字段。
    @Test
    @DisplayName("应正确映射 openAccess.isOa")
    void shouldMapOpenAccessIsOa() {
      // Given
      VenueEntity entity = buildBaseVenue("PLOS ONE", "1932-6203");
      entity.setOpenAccess(OpenAccessInfo.ofOaStatus(true, true, "gold"));
      venueDao.save(entity);

      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(PagingParams.of(1, 10), EMPTY_FILTER);

      // Then
      assertThat(page.items())
          .singleElement()
          .satisfies(
              item -> {
                assertThat(item.isOa()).isTrue();
              });
    }

    /// 所有 JSON 列均为 null 时，嵌套字段应安全映射为 null。
    @Test
    @DisplayName("JSON 列为 null 时嵌套字段应为 null")
    void shouldMapNullJsonColumnsToNullFields() {
      // Given — 不设置任何 JSON 列
      saveVenue("JOURNAL", "Plain Journal", "9999-9999", "NLM999", "OPENALEX", "US");

      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(PagingParams.of(1, 10), EMPTY_FILTER);

      // Then
      assertThat(page.items())
          .singleElement()
          .satisfies(
              item -> {
                assertThat(item.hIndex()).isNull();
                assertThat(item.jifQuartile()).isNull();
                assertThat(item.casMajorQuartile()).isNull();
                assertThat(item.casTopJournal()).isNull();
                assertThat(item.warningListStatus()).isNull();
                assertThat(item.isOa()).isNull();
                assertThat(item.researchDirection()).isNull();
              });
    }
  }

  /// 构建基础 Venue 实体（JOURNAL 类型），用于 JSON 列映射测试。
  private VenueEntity buildBaseVenue(String title, String issnL) {
    VenueEntity entity = new VenueEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setVenueType("JOURNAL");
    entity.setTitle(title);
    entity.setIssnL(issnL);
    entity.setProvenanceCode("OPENALEX");
    entity.setCountryCode("US");
    entity.setLastSyncedAt(Instant.parse("2026-02-13T00:00:00Z"));
    return entity;
  }

  /// 保存带 h-index 的测试用 Venue 实体。
  private VenueEntity saveVenueWithHIndex(String title, String issnL, int hIndex) {
    VenueEntity entity = buildBaseVenue(title, issnL);
    entity.setCitationMetrics(CitationMetrics.of(null, null, hIndex, null, null));
    return venueDao.save(entity);
  }

  /// 保存测试用 Venue 实体。
  ///
  /// @param venueType 载体类型
  /// @param title 期刊标题
  /// @param issnL ISSN-L
  /// @param nlmId NLM ID
  /// @param provenanceCode 数据来源编码
  /// @param countryCode 国家编码
  /// @return 持久化后的实体
  private VenueEntity saveVenue(
      String venueType,
      String title,
      String issnL,
      String nlmId,
      String provenanceCode,
      String countryCode) {
    VenueEntity entity = new VenueEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setVenueType(venueType);
    entity.setTitle(title);
    entity.setIssnL(issnL);
    entity.setNlmId(nlmId);
    entity.setProvenanceCode(provenanceCode);
    entity.setCountryCode(countryCode);
    entity.setLastSyncedAt(Instant.parse("2026-02-13T00:00:00Z"));
    return venueDao.save(entity);
  }
}
