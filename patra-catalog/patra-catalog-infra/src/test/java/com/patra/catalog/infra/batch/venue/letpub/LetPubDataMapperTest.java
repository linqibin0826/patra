package com.patra.catalog.infra.batch.venue.letpub;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// LetPubDataMapper 单元测试。
///
/// **测试策略**：
///
/// - JCR 映射：IF 趋势转为多行 JcrRatingEntity，最新年附加分区/排名/学科
/// - CAS 映射：单行 CasRatingEntity，包含大类/小类分区 + 版本名称
/// - 版本提取：从 casVersion 提取 year 和 edition
/// - 边界情况：空数据、缺少字段
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LetPubDataMapper 单元测试")
@Timeout(2)
class LetPubDataMapperTest {

  private static final Long VENUE_ID = 1001L;

  private LetPubDataMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new LetPubDataMapper();
  }

  /// 创建包含完整数据的 LetPubVenueData。
  private LetPubVenueData createFullData() {
    return LetPubVenueData.builder()
        .letPubJournalId("6054")
        .letPubName("NATURE")
        .researchDirection("综合性期刊")
        .country("ENGLAND")
        .language("English")
        .frequency("Weekly")
        .startYear(1869)
        .articlesPerYear(860)
        .researchArticlePercent("52.24%")
        .jcrSubject("MULTIDISCIPLINARY SCIENCES")
        .jcrCollection("SCIE")
        .jifQuartile("Q1")
        .jifRank("2/136")
        .jciQuartile("Q1")
        .jciRank("1/136")
        .casVersion("2025年3月升级版")
        .casMajorCategory("综合性期刊")
        .casMajorQuartile("1区")
        .casMinorSubject("MULTIDISCIPLINARY SCIENCES")
        .casMinorQuartile("1区")
        .casTopJournal(true)
        .casReviewJournal(false)
        .warningListStatus("2024年2月发布的2024版：无预警")
        .reviewSpeedOfficial("较慢，>12周")
        .reviewSpeedUser("平均6.0个月")
        .acceptanceRate("7.69%")
        .apcInfo("US$11390")
        .impactFactorTrend(
            Map.of(
                "2024-2025", 48.5,
                "2023-2024", 50.5,
                "2022-2023", 64.8))
        .fiveYearImpactFactor(55.0)
        .indexedIn(List.of("SCI", "SCIE"))
        .build();
  }

  @Nested
  @DisplayName("JCR 评级映射测试")
  class JcrMappingTests {

    @Test
    @DisplayName("IF 趋势每年应生成一行 JCR rating")
    void shouldCreateOneRatingPerYear() {
      var data = createFullData();

      List<JcrRatingEntity> ratings = mapper.mapToJcrRatings(data, VENUE_ID);

      assertThat(ratings).hasSize(3);
      assertThat(ratings).allMatch(r -> r.getVenueId().equals(VENUE_ID));
    }

    @Test
    @DisplayName("最新年份应包含分区、排名、学科等完整详情")
    void shouldIncludeFullDetailsForLatestYear() {
      var data = createFullData();

      List<JcrRatingEntity> ratings = mapper.mapToJcrRatings(data, VENUE_ID);

      JcrRatingEntity latest =
          ratings.stream().filter(r -> r.getYear() == 2025).findFirst().orElseThrow();

      assertThat(latest.getImpactFactor()).isEqualByComparingTo(new BigDecimal("48.5"));
      assertThat(latest.getFiveYearIf()).isEqualByComparingTo(new BigDecimal("55.0"));
      assertThat(latest.getSubject()).isEqualTo("MULTIDISCIPLINARY SCIENCES");
      assertThat(latest.getCollection()).isEqualTo("SCIE");
      assertThat(latest.getJifQuartile()).isEqualTo("Q1");
      assertThat(latest.getJifRank()).isEqualTo("2/136");
      assertThat(latest.getJciQuartile()).isEqualTo("Q1");
      assertThat(latest.getJciRank()).isEqualTo("1/136");
      assertThat(latest.getResearchDirection()).isEqualTo("综合性期刊");
    }

    @Test
    @DisplayName("历史年份应只有 impactFactor，无分区详情")
    void shouldOnlyHaveImpactFactorForHistoricalYears() {
      var data = createFullData();

      List<JcrRatingEntity> ratings = mapper.mapToJcrRatings(data, VENUE_ID);

      JcrRatingEntity historical =
          ratings.stream().filter(r -> r.getYear() == 2024).findFirst().orElseThrow();

      assertThat(historical.getImpactFactor()).isEqualByComparingTo(new BigDecimal("50.5"));
      assertThat(historical.getJifQuartile()).isNull();
      assertThat(historical.getSubject()).isNull();
      assertThat(historical.getFiveYearIf()).isNull();
    }

    @Test
    @DisplayName("年份应从 key 后半段提取（2024-2025 → 2025）")
    void shouldExtractYearFromKey() {
      var data = LetPubVenueData.builder().impactFactorTrend(Map.of("2021-2022", 69.504)).build();

      List<JcrRatingEntity> ratings = mapper.mapToJcrRatings(data, VENUE_ID);

      assertThat(ratings)
          .singleElement()
          .satisfies(r -> assertThat(r.getYear()).isEqualTo((short) 2022));
    }

    @Test
    @DisplayName("无 IF 趋势时应返回空列表")
    void shouldReturnEmptyWhenNoTrend() {
      var data = LetPubVenueData.builder().build();

      List<JcrRatingEntity> ratings = mapper.mapToJcrRatings(data, VENUE_ID);

      assertThat(ratings).isEmpty();
    }
  }

  @Nested
  @DisplayName("CAS 评级映射测试")
  class CasMappingTests {

    @Test
    @DisplayName("应生成一行 CAS rating，包含完整字段")
    void shouldCreateSingleCasRatingWithAllFields() {
      var data = createFullData();

      CasRatingEntity rating = mapper.mapToCasRating(data, VENUE_ID);

      assertThat(rating).isNotNull();
      assertThat(rating.getVenueId()).isEqualTo(VENUE_ID);
      assertThat(rating.getYear()).isEqualTo((short) 2025);
      assertThat(rating.getEdition()).isEqualTo("升级版");
      assertThat(rating.getMajorCategory()).isEqualTo("综合性期刊");
      assertThat(rating.getMajorQuartile()).isEqualTo("1区");
      assertThat(rating.getMinorSubject()).isEqualTo("MULTIDISCIPLINARY SCIENCES");
      assertThat(rating.getMinorQuartile()).isEqualTo("1区");
      assertThat(rating.getIsTopJournal()).isTrue();
      assertThat(rating.getIsReviewJournal()).isFalse();
    }

    @Test
    @DisplayName("年份应从 casVersion 提取（2023年12月升级版 → 2023）")
    void shouldExtractYearFromCasVersion() {
      var data = LetPubVenueData.builder().casVersion("2023年12月升级版").casMajorQuartile("2区").build();

      CasRatingEntity rating = mapper.mapToCasRating(data, VENUE_ID);

      assertThat(rating.getYear()).isEqualTo((short) 2023);
      assertThat(rating.getEdition()).isEqualTo("升级版");
    }

    @Test
    @DisplayName("无 CAS 数据时应返回 null")
    void shouldReturnNullWhenNoCasData() {
      var data = LetPubVenueData.builder().build();

      CasRatingEntity rating = mapper.mapToCasRating(data, VENUE_ID);

      assertThat(rating).isNull();
    }
  }

  @Nested
  @DisplayName("版本提取测试")
  class EditionExtractionTests {

    @Test
    @DisplayName("应从标准格式提取版本名（2025年3月升级版 → 升级版）")
    void shouldExtractEditionFromStandardFormat() {
      assertThat(mapper.extractCasEdition("2025年3月升级版")).isEqualTo("升级版");
    }

    @Test
    @DisplayName("应从新锐版格式提取版本名")
    void shouldExtractXinruiEdition() {
      assertThat(mapper.extractCasEdition("2026年6月新锐版")).isEqualTo("新锐版");
    }

    @Test
    @DisplayName("应从无月份格式提取版本名（2024年基础版 → 基础版）")
    void shouldExtractEditionWithoutMonth() {
      assertThat(mapper.extractCasEdition("2024年基础版")).isEqualTo("基础版");
    }

    @Test
    @DisplayName("无法匹配时应默认返回升级版")
    void shouldDefaultToUpgradeEdition() {
      assertThat(mapper.extractCasEdition("未知格式")).isEqualTo("升级版");
    }
  }
}
