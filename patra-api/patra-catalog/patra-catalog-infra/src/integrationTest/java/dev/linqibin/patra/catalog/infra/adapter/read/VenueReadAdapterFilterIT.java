package dev.linqibin.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
import dev.linqibin.patra.catalog.domain.model.read.venue.VenueFilter;
import dev.linqibin.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import dev.linqibin.patra.catalog.domain.model.vo.venue.CitationMetrics;
import dev.linqibin.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
import dev.linqibin.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.linqibin.patra.catalog.infra.persistence.dao.CasRatingDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.CasWarningDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.JcrRatingDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.ScopusRatingDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.VenueDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.CasRatingEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.CasWarningEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.ScopusRatingEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.VenueEntity;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// VenueReadAdapter 高级筛选集成测试。
///
/// **测试目标**：
///
/// - JIF 分区筛选仅返回匹配期刊
/// - CAS Top 期刊筛选生效
/// - 影响因子排序正确
/// - 多条件组合筛选收窄结果
/// - 预警筛选生效
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  VenueReadAdapter.class,
  VenueReadModelMapperImpl.class,
  JpaAuditingConfig.class,
  JacksonAutoConfiguration.class
})
@ActiveProfiles("test")
@DisplayName("VenueReadAdapter 高级筛选集成测试")
class VenueReadAdapterFilterIT {

  @Autowired private VenueReadAdapter venueReadAdapter;
  @Autowired private VenueDao venueDao;
  @Autowired private JcrRatingDao jcrRatingDao;
  @Autowired private CasRatingDao casRatingDao;
  @Autowired private ScopusRatingDao scopusRatingDao;
  @Autowired private CasWarningDao casWarningDao;

  private VenueEntity nature;
  private VenueEntity lancet;
  private VenueEntity plosOne;

  @BeforeEach
  void setUp() {
    // Nature: Q1, SCIE, Top, IF=64.8, citeScore=30.5, hIndex=400
    nature = saveVenue("Nature", "0028-0836", "US", 400, true, "gold");
    saveJcrRating(
        nature.getId(),
        (short) 2025,
        new BigDecimal("64.8000"),
        "Q1",
        "SCIE",
        "MULTIDISCIPLINARY SCIENCES");
    saveCasRating(nature.getId(), (short) 2025, "1区", true);
    saveScopusRating(nature.getId(), (short) 2025, new BigDecimal("30.5000"), "Q1");

    // The Lancet: Q1, SSCI, Top, IF=98.4, citeScore=45.2, hIndex=350
    lancet = saveVenue("The Lancet", "0140-6736", "GB", 350, true, "hybrid");
    saveJcrRating(
        lancet.getId(),
        (short) 2025,
        new BigDecimal("98.4000"),
        "Q1",
        "SSCI",
        "MEDICINE, GENERAL & INTERNAL");
    saveCasRating(lancet.getId(), (short) 2025, "1区", true);
    saveScopusRating(lancet.getId(), (short) 2025, new BigDecimal("45.2000"), "Q1");

    // PLOS ONE: Q2, SCIE, not Top, IF=3.7, citeScore=5.1, hIndex=200
    plosOne = saveVenue("PLOS ONE", "1932-6203", "US", 200, true, "gold");
    saveJcrRating(
        plosOne.getId(),
        (short) 2025,
        new BigDecimal("3.7000"),
        "Q2",
        "SCIE",
        "MULTIDISCIPLINARY SCIENCES");
    saveCasRating(plosOne.getId(), (short) 2025, "3区", false);
    saveScopusRating(plosOne.getId(), (short) 2025, new BigDecimal("5.1000"), "Q2");
  }

  @Nested
  @DisplayName("JIF 分区筛选")
  class JifQuartileFilterTests {

    /// Q1 筛选应仅返回 Q1 期刊。
    @Test
    @DisplayName("筛选 Q1 应仅返回 Q1 期刊")
    void shouldReturnOnlyQ1Journals() {
      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20), VenueFilter.builder().jifQuartile("Q1").build());

      // Then
      assertThat(page.total()).isEqualTo(2);
      assertThat(page.items())
          .extracting("title")
          .containsExactlyInAnyOrder("Nature", "The Lancet");
    }

    /// Q2 筛选应仅返回 PLOS ONE。
    @Test
    @DisplayName("筛选 Q2 应仅返回 Q2 期刊")
    void shouldReturnOnlyQ2Journals() {
      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20), VenueFilter.builder().jifQuartile("Q2").build());

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("title").isEqualTo("PLOS ONE");
    }
  }

  @Nested
  @DisplayName("CAS Top 期刊筛选")
  class CasTopFilterTests {

    /// casTopJournal=true 应仅返回 Top 期刊。
    @Test
    @DisplayName("casTopJournal=true 应仅返回 Top 期刊")
    void shouldReturnOnlyTopJournals() {
      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20), VenueFilter.builder().casTopJournal(true).build());

      // Then
      assertThat(page.total()).isEqualTo(2);
      assertThat(page.items())
          .extracting("title")
          .containsExactlyInAnyOrder("Nature", "The Lancet");
      assertThat(page.items()).allSatisfy(item -> assertThat(item.casTopJournal()).isTrue());
    }
  }

  @Nested
  @DisplayName("排序功能")
  class SortTests {

    /// 按 impactFactor DESC 排序应将最高 IF 排在第一位。
    @Test
    @DisplayName("按 impactFactor 降序排列")
    void shouldSortByImpactFactorDesc() {
      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20), VenueFilter.builder().sortBy("impactFactor").build());

      // Then
      assertThat(page.items())
          .extracting("title")
          .containsExactly("The Lancet", "Nature", "PLOS ONE");
      assertThat(page.items())
          .extracting("impactFactor")
          .containsExactly(
              new BigDecimal("98.4000"), new BigDecimal("64.8000"), new BigDecimal("3.7000"));
    }

    /// 按 citeScore DESC 排序应将最高 CiteScore 排在第一位。
    @Test
    @DisplayName("按 citeScore 降序排列")
    void shouldSortByCiteScoreDesc() {
      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20), VenueFilter.builder().sortBy("citeScore").build());

      // Then
      assertThat(page.items())
          .extracting("title")
          .containsExactly("The Lancet", "Nature", "PLOS ONE");
    }
  }

  @Nested
  @DisplayName("组合筛选")
  class CombinedFilterTests {

    /// Q1 + SCIE 组合筛选应只返回同时满足两个条件的期刊。
    @Test
    @DisplayName("Q1 + SCIE 组合筛选应收窄结果")
    void shouldNarrowResultsWithCombinedFilters() {
      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20),
              VenueFilter.builder().jifQuartile("Q1").collection("SCIE").build());

      // Then - Nature is Q1+SCIE, Lancet is Q1+SSCI, PLOS is Q2+SCIE
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("title").isEqualTo("Nature");
    }

    /// Q1 + Top + 按 impactFactor 排序的组合查询。
    @Test
    @DisplayName("Q1 + Top + impactFactor 排序")
    void shouldCombineFiltersAndSort() {
      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20),
              VenueFilter.builder()
                  .jifQuartile("Q1")
                  .casTopJournal(true)
                  .sortBy("impactFactor")
                  .build());

      // Then
      assertThat(page.total()).isEqualTo(2);
      assertThat(page.items()).extracting("title").containsExactly("The Lancet", "Nature");
    }
  }

  @Nested
  @DisplayName("预警筛选")
  class WarningFilterTests {

    /// warningOnly=true 应仅返回有预警记录的期刊。
    @Test
    @DisplayName("warningOnly=true 应仅返回预警期刊")
    void shouldReturnOnlyWarnedJournals() {
      // Given - 给 PLOS ONE 添加预警记录
      saveCasWarning(plosOne.getId(), (short) 2025, true);

      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20), VenueFilter.builder().warningOnly(true).build());

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("title").isEqualTo("PLOS ONE");
    }

    /// 无预警记录时 warningOnly=true 应返回空结果。
    @Test
    @DisplayName("无预警期刊时 warningOnly 返回空")
    void shouldReturnEmptyWhenNoWarnings() {
      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20), VenueFilter.builder().warningOnly(true).build());

      // Then
      assertThat(page.total()).isZero();
      assertThat(page.items()).isEmpty();
    }
  }

  @Nested
  @DisplayName("新增字段映射")
  class NewFieldMappingTests {

    /// LATERAL JOIN 路径应正确映射 impactFactor 和 collection 字段。
    @Test
    @DisplayName("应正确映射 impactFactor 和 collection")
    void shouldMapImpactFactorAndCollection() {
      // When - 触发高级路径
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20),
              VenueFilter.builder().jifQuartile("Q1").collection("SCIE").build());

      // Then
      assertThat(page.items())
          .singleElement()
          .satisfies(
              item -> {
                assertThat(item.impactFactor()).isEqualByComparingTo(new BigDecimal("64.8"));
                assertThat(item.collection()).isEqualTo("SCIE");
                assertThat(item.jifQuartile()).isEqualTo("Q1");
                assertThat(item.casMajorQuartile()).isEqualTo("1区");
                assertThat(item.casTopJournal()).isTrue();
                assertThat(item.citeScore()).isEqualByComparingTo(new BigDecimal("30.5"));
                assertThat(item.citeScoreQuartile()).isEqualTo("Q1");
              });
    }

    /// 批量加载路径也应正确映射 impactFactor 和 collection 字段。
    @Test
    @DisplayName("批量加载路径也应正确映射新字段")
    void shouldMapNewFieldsViaBatchLoadPath() {
      // When - 无高级筛选，走批量加载路径
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(
              PagingParams.of(1, 20), VenueFilter.builder().keyword("Nat").build());

      // Then
      assertThat(page.items())
          .singleElement()
          .satisfies(
              item -> {
                assertThat(item.impactFactor()).isEqualByComparingTo(new BigDecimal("64.8"));
                assertThat(item.collection()).isEqualTo("SCIE");
              });
    }
  }

  // ========== 测试数据构建辅助方法 ==========

  /// 保存测试用 Venue 实体。
  private VenueEntity saveVenue(
      String title, String issnL, String countryCode, int hIndex, boolean isOa, String oaType) {
    VenueEntity entity = new VenueEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setVenueType("JOURNAL");
    entity.setTitle(title);
    entity.setIssnL(issnL);
    entity.setCountryCode(countryCode);
    entity.setProvenanceCode("OPENALEX");
    entity.setCitationMetrics(CitationMetrics.of(null, null, hIndex, null, null));
    entity.setOpenAccess(OpenAccessInfo.ofOaStatus(isOa, false, oaType));
    entity.setLastSyncedAt(Instant.parse("2026-02-13T00:00:00Z"));
    return venueDao.save(entity);
  }

  /// 保存 JCR 评级记录。
  private JcrRatingEntity saveJcrRating(
      Long venueId,
      short year,
      BigDecimal impactFactor,
      String quartile,
      String collection,
      String researchDirection) {
    JcrRatingEntity entity = new JcrRatingEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setVenueId(venueId);
    entity.setYear(year);
    entity.setImpactFactor(impactFactor);
    entity.setJifQuartile(quartile);
    entity.setCollection(collection);
    entity.setResearchDirection(researchDirection);
    entity.setFetchedAt(Instant.now());
    return jcrRatingDao.save(entity);
  }

  /// 保存 CAS 评级记录。
  private CasRatingEntity saveCasRating(
      Long venueId, short year, String majorQuartile, boolean isTopJournal) {
    CasRatingEntity entity = new CasRatingEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setVenueId(venueId);
    entity.setYear(year);
    entity.setEdition("升级版");
    entity.setMajorQuartile(majorQuartile);
    entity.setIsTopJournal(isTopJournal);
    entity.setFetchedAt(Instant.now());
    return casRatingDao.save(entity);
  }

  /// 保存 Scopus 评级记录。
  private ScopusRatingEntity saveScopusRating(
      Long venueId, short year, BigDecimal citeScore, String quartile) {
    ScopusRatingEntity entity = new ScopusRatingEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setVenueId(venueId);
    entity.setYear(year);
    entity.setCiteScore(citeScore);
    entity.setQuartile(quartile);
    entity.setFetchedAt(Instant.now());
    return scopusRatingDao.save(entity);
  }

  /// 保存 CAS 预警记录。
  private CasWarningEntity saveCasWarning(
      Long venueId, short publishedYear, boolean inWarningList) {
    CasWarningEntity entity = new CasWarningEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setVenueId(venueId);
    entity.setPublishedYear(publishedYear);
    entity.setEditionLabel(publishedYear + "版");
    entity.setInWarningList(inWarningList);
    entity.setFetchedAt(Instant.now());
    return casWarningDao.save(entity);
  }
}
