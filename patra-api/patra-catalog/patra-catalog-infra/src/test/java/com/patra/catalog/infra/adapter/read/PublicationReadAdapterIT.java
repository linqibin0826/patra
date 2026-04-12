package com.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.read.publication.PublicationFilter;
import com.patra.catalog.domain.model.read.publication.PublicationSummaryReadModel;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.dao.PublicationDao;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import com.patra.catalog.infra.persistence.entity.PublicationEntity;
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

/// PublicationReadAdapter 列表查询集成测试。
///
/// **测试目标**：
///
/// - 无筛选条件时返回所有文献
/// - 各筛选条件独立生效（keyword 包含匹配、yearFrom/yearTo 范围、精确匹配）
/// - 分页元信息与排序规则
/// - venueName 正确关联
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
@DisplayName("PublicationReadAdapter 列表查询集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PublicationReadAdapterIT {

  private static final PublicationFilter EMPTY_FILTER = PublicationFilter.builder().build();

  @Autowired private PublicationReadAdapter publicationReadAdapter;
  @Autowired private PublicationDao publicationDao;
  @Autowired private VenueDao venueDao;

  /// 无筛选条件时应返回所有文献。
  @Test
  @DisplayName("无筛选条件应返回所有文献")
  void shouldReturnAllPublicationsWhenNoFilter() {
    // Given
    VenueEntity venue = saveVenue("Nature");
    savePublication("Article One", "11111111", "10.1234/one", 2024, "en", venue.getId());
    savePublication("Article Two", "22222222", "10.1234/two", 2023, "zh", venue.getId());

    // When
    PageResult<PublicationSummaryReadModel> page =
        publicationReadAdapter.findPublicationPage(PagingParams.of(1, 20), EMPTY_FILTER);

    // Then
    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items()).hasSize(2);
  }

  /// venueName 应通过 venueId JOIN 获取。
  @Test
  @DisplayName("venueName 应正确关联到 SummaryReadModel")
  void shouldMapVenueNameToSummaryReadModel() {
    // Given
    VenueEntity venue = saveVenue("The Lancet");
    savePublication("Lancet Article", "33333333", "10.1234/lancet", 2024, "en", venue.getId());

    // When
    PageResult<PublicationSummaryReadModel> page =
        publicationReadAdapter.findPublicationPage(PagingParams.of(1, 20), EMPTY_FILTER);

    // Then
    assertThat(page.items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.title()).isEqualTo("Lancet Article");
              assertThat(item.venueId()).isEqualTo(venue.getId());
              assertThat(item.venueName()).isEqualTo("The Lancet");
            });
  }

  /// venueId 为 null 时 venueName 应为 null。
  @Test
  @DisplayName("venueId 为 null 时 venueName 应为 null")
  void shouldReturnNullVenueNameWhenVenueIdIsNull() {
    // Given
    savePublication("No Venue Article", "44444444", "10.1234/novenue", 2024, "en", null);

    // When
    PageResult<PublicationSummaryReadModel> page =
        publicationReadAdapter.findPublicationPage(PagingParams.of(1, 20), EMPTY_FILTER);

    // Then
    assertThat(page.items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.venueId()).isNull();
              assertThat(item.venueName()).isNull();
            });
  }

  @Nested
  @DisplayName("关键词检索策略")
  class KeywordSearchTests {

    /// 标题包含匹配应命中 title LIKE %keyword%。
    @Test
    @DisplayName("标题包含匹配")
    void shouldMatchByTitleContains() {
      // Given
      VenueEntity venue = saveVenue("Nature");
      savePublication("Cancer treatment study", "55555555", null, 2024, "en", venue.getId());
      savePublication("Heart disease review", "66666666", null, 2023, "en", venue.getId());

      // When
      PageResult<PublicationSummaryReadModel> page =
          publicationReadAdapter.findPublicationPage(
              PagingParams.of(1, 20), PublicationFilter.builder().keyword("cancer").build());

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items())
          .singleElement()
          .extracting("title")
          .isEqualTo("Cancer treatment study");
    }
  }

  @Nested
  @DisplayName("精确筛选条件")
  class ExactFilterTests {

    /// yearFrom 范围筛选应生效。
    @Test
    @DisplayName("yearFrom 范围筛选")
    void shouldFilterByYearFrom() {
      // Given
      VenueEntity venue = saveVenue("Nature");
      savePublication("Old Article", "77777777", null, 2019, "en", venue.getId());
      savePublication("New Article", "88888888", null, 2024, "en", venue.getId());

      // When
      PageResult<PublicationSummaryReadModel> page =
          publicationReadAdapter.findPublicationPage(
              PagingParams.of(1, 20), PublicationFilter.builder().yearFrom(2020).build());

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("title").isEqualTo("New Article");
    }

    /// yearTo 范围筛选应生效。
    @Test
    @DisplayName("yearTo 范围筛选")
    void shouldFilterByYearTo() {
      // Given
      VenueEntity venue = saveVenue("Nature");
      savePublication("Old Article", "99999991", null, 2019, "en", venue.getId());
      savePublication("New Article", "99999992", null, 2024, "en", venue.getId());

      // When
      PageResult<PublicationSummaryReadModel> page =
          publicationReadAdapter.findPublicationPage(
              PagingParams.of(1, 20), PublicationFilter.builder().yearTo(2020).build());

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("title").isEqualTo("Old Article");
    }

    /// venueId 精确匹配应生效。
    @Test
    @DisplayName("venueId 精确匹配")
    void shouldFilterByVenueId() {
      // Given
      VenueEntity nature = saveVenue("Nature");
      VenueEntity lancet = saveVenue("The Lancet");
      savePublication("Nature Article", "99999993", null, 2024, "en", nature.getId());
      savePublication("Lancet Article", "99999994", null, 2024, "en", lancet.getId());

      // When
      PageResult<PublicationSummaryReadModel> page =
          publicationReadAdapter.findPublicationPage(
              PagingParams.of(1, 20), PublicationFilter.builder().venueId(lancet.getId()).build());

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("title").isEqualTo("Lancet Article");
    }

    /// isOa 布尔筛选应生效。
    @Test
    @DisplayName("isOa 布尔筛选")
    void shouldFilterByIsOa() {
      // Given
      VenueEntity venue = saveVenue("Nature");
      PublicationEntity oaArticle =
          savePublication("OA Article", "99999995", null, 2024, "en", venue.getId());
      oaArticle.setIsOa(true);
      oaArticle.setOaStatus("gold");
      publicationDao.save(oaArticle);

      PublicationEntity closedArticle =
          savePublication("Closed Article", "99999996", null, 2024, "en", venue.getId());
      closedArticle.setIsOa(false);
      closedArticle.setOaStatus("closed");
      publicationDao.save(closedArticle);

      // When
      PageResult<PublicationSummaryReadModel> page =
          publicationReadAdapter.findPublicationPage(
              PagingParams.of(1, 20), PublicationFilter.builder().isOa(true).build());

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("title").isEqualTo("OA Article");
    }

    /// pmid 精确匹配应生效。
    @Test
    @DisplayName("pmid 精确匹配")
    void shouldFilterByPmid() {
      // Given
      VenueEntity venue = saveVenue("Nature");
      savePublication("Target Article", "99999997", null, 2024, "en", venue.getId());
      savePublication("Other Article", "99999998", null, 2024, "en", venue.getId());

      // When
      PageResult<PublicationSummaryReadModel> page =
          publicationReadAdapter.findPublicationPage(
              PagingParams.of(1, 20), PublicationFilter.builder().pmid("99999997").build());

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("title").isEqualTo("Target Article");
    }

    /// venueInstanceId 精确匹配应生效。
    @Test
    @DisplayName("venueInstanceId 精确匹配")
    void shouldFilterByVenueInstanceId() {
      // Given
      VenueEntity venue = saveVenue("Nature");
      Long instanceA = SnowflakeIdGenerator.getId();
      Long instanceB = SnowflakeIdGenerator.getId();
      savePublicationWithInstance(
          "Instance A Article", "99999881", null, 2024, "en", venue.getId(), instanceA);
      savePublicationWithInstance(
          "Instance B Article", "99999882", null, 2024, "en", venue.getId(), instanceB);

      // When
      PageResult<PublicationSummaryReadModel> page =
          publicationReadAdapter.findPublicationPage(
              PagingParams.of(1, 20),
              PublicationFilter.builder().venueInstanceId(instanceA).build());

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("title").isEqualTo("Instance A Article");
    }
  }

  /// 分页应返回正确元信息，并按更新时间/ID 倒序稳定输出。
  @Test
  @DisplayName("分页元信息与排序应正确")
  void shouldReturnCorrectPagingMetaAndSort() {
    // Given
    VenueEntity venue = saveVenue("Nature");
    PublicationEntity p1 =
        savePublication("Article-1", "99999801", null, 2024, "en", venue.getId());
    PublicationEntity p2 =
        savePublication("Article-2", "99999802", null, 2024, "en", venue.getId());
    PublicationEntity p3 =
        savePublication("Article-3", "99999803", null, 2024, "en", venue.getId());

    Long expectedSecondPageId =
        List.of(p1.getId(), p2.getId(), p3.getId()).stream()
            .sorted((left, right) -> Long.compare(right, left))
            .toList()
            .get(1);

    // When
    PageResult<PublicationSummaryReadModel> page =
        publicationReadAdapter.findPublicationPage(PagingParams.of(2, 1), EMPTY_FILTER);

    // Then
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.pageSize()).isEqualTo(1);
    assertThat(page.total()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(3);
    assertThat(page.items()).singleElement().extracting("id").isEqualTo(expectedSecondPageId);
  }

  /// sortBy=citedByCount 时应按被引次数降序排列。
  @Test
  @DisplayName("sortBy=citedByCount 应按被引次数降序排列")
  void shouldSortByCitedByCountDescending() {
    // Given
    VenueEntity venue = saveVenue("Nature");
    PublicationEntity lowCited =
        savePublication("Low Cited", "99999871", null, 2024, "en", venue.getId());
    lowCited.setCitationCount(5);
    publicationDao.save(lowCited);

    PublicationEntity highCited =
        savePublication("High Cited", "99999872", null, 2024, "en", venue.getId());
    highCited.setCitationCount(100);
    publicationDao.save(highCited);

    PublicationEntity midCited =
        savePublication("Mid Cited", "99999873", null, 2024, "en", venue.getId());
    midCited.setCitationCount(50);
    publicationDao.save(midCited);

    // When
    PageResult<PublicationSummaryReadModel> page =
        publicationReadAdapter.findPublicationPage(
            PagingParams.of(1, 20), PublicationFilter.builder().sortBy("citedByCount").build());

    // Then
    assertThat(page.items())
        .extracting("title")
        .containsExactly("High Cited", "Mid Cited", "Low Cited");
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
      String title, String pmid, String doi, Integer year, String langCode, Long venueId) {
    return savePublicationWithInstance(
        title,
        pmid,
        doi,
        year,
        langCode,
        venueId,
        venueId != null ? venueId : SnowflakeIdGenerator.getId());
  }

  /// 保存测试用 Publication 实体（指定 venueInstanceId）。
  private PublicationEntity savePublicationWithInstance(
      String title,
      String pmid,
      String doi,
      Integer year,
      String langCode,
      Long venueId,
      Long venueInstanceId) {
    PublicationEntity entity =
        PublicationEntity.builder()
            .id(SnowflakeIdGenerator.getId())
            .provenanceCode("PUBMED")
            .title(title)
            .pmid(pmid)
            .doi(doi)
            .publicationYear(year)
            .languageCode(langCode)
            .venueId(venueId)
            .venueInstanceId(venueInstanceId)
            .isOa(false)
            .authorsComplete(true)
            .lastSyncedAt(Instant.parse("2026-02-13T00:00:00Z"))
            .build();
    return publicationDao.save(entity);
  }
}
