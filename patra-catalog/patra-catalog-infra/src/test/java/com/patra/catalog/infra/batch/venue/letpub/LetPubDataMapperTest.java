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
/// - CAS 映射：多行 CasRatingEntity，每个版本（新锐版/升级版/旧版）一行
/// - 版本提取：从 version 字符串提取 year 和 edition
/// - 边界情况：空数据、缺少字段
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LetPubDataMapper 单元测试")
@Timeout(2)
class LetPubDataMapperTest {

  private static final Long VENUE_ID = 1001L;
  private static final String SOURCE_URL =
      "https://www.letpub.com.cn/index.php?journalid=6054&page=journalapp&view=detail";

  private LetPubDataMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new LetPubDataMapper();
  }

  /// 创建包含完整数据的 LetPubVenueData。
  private LetPubVenueData createFullData() {
    var xinruiPartition =
        LetPubVenueData.CasPartition.builder()
            .version("2026年3月新锐版")
            .majorCategory("综合性期刊")
            .majorQuartile("1区")
            .minorSubject("MULTIDISCIPLINARY SCIENCES")
            .minorQuartile("1区")
            .topJournal(true)
            .reviewJournal(false)
            .build();
    var shengjiPartition =
        LetPubVenueData.CasPartition.builder()
            .version("2025年3月升级版")
            .majorCategory("综合性期刊")
            .majorQuartile("1区")
            .minorSubject("MULTIDISCIPLINARY SCIENCES")
            .minorQuartile("1区")
            .topJournal(true)
            .reviewJournal(false)
            .build();

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
        .casPartitions(List.of(xinruiPartition, shengjiPartition))
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

      List<JcrRatingEntity> ratings = mapper.mapToJcrRatings(data, VENUE_ID, SOURCE_URL);

      assertThat(ratings).hasSize(3);
      assertThat(ratings).allMatch(r -> r.getVenueId().equals(VENUE_ID));
      assertThat(ratings).allMatch(r -> SOURCE_URL.equals(r.getSourceUrl()));
      assertThat(ratings).allMatch(r -> r.getFetchedAt() != null);
    }

    @Test
    @DisplayName("最新年份应包含分区、排名、学科等完整详情")
    void shouldIncludeFullDetailsForLatestYear() {
      var data = createFullData();

      List<JcrRatingEntity> ratings = mapper.mapToJcrRatings(data, VENUE_ID, SOURCE_URL);

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

      List<JcrRatingEntity> ratings = mapper.mapToJcrRatings(data, VENUE_ID, SOURCE_URL);

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

      List<JcrRatingEntity> ratings = mapper.mapToJcrRatings(data, VENUE_ID, SOURCE_URL);

      assertThat(ratings)
          .singleElement()
          .satisfies(r -> assertThat(r.getYear()).isEqualTo((short) 2022));
    }

    @Test
    @DisplayName("无 IF 趋势时应返回空列表")
    void shouldReturnEmptyWhenNoTrend() {
      var data = LetPubVenueData.builder().build();

      List<JcrRatingEntity> ratings = mapper.mapToJcrRatings(data, VENUE_ID, SOURCE_URL);

      assertThat(ratings).isEmpty();
    }
  }

  @Nested
  @DisplayName("CAS 评级映射测试")
  class CasMappingTests {

    @Test
    @DisplayName("每个 CAS 版本应生成一行 rating")
    void shouldCreateOneRatingPerPartition() {
      var data = createFullData();

      List<CasRatingEntity> ratings = mapper.mapToCasRatings(data, VENUE_ID, SOURCE_URL);

      assertThat(ratings).hasSize(2);
      assertThat(ratings).allMatch(r -> r.getVenueId().equals(VENUE_ID));
      assertThat(ratings).allMatch(r -> SOURCE_URL.equals(r.getSourceUrl()));
      assertThat(ratings).allMatch(r -> r.getFetchedAt() != null);
    }

    @Test
    @DisplayName("新锐版应映射完整字段")
    void shouldMapXinruiPartitionWithAllFields() {
      var data = createFullData();

      List<CasRatingEntity> ratings = mapper.mapToCasRatings(data, VENUE_ID, SOURCE_URL);

      CasRatingEntity xinrui =
          ratings.stream().filter(r -> "新锐版".equals(r.getEdition())).findFirst().orElseThrow();

      assertThat(xinrui.getYear()).isEqualTo((short) 2026);
      assertThat(xinrui.getEdition()).isEqualTo("新锐版");
      assertThat(xinrui.getMajorCategory()).isEqualTo("综合性期刊");
      assertThat(xinrui.getMajorQuartile()).isEqualTo("1区");
      assertThat(xinrui.getMinorSubject()).isEqualTo("MULTIDISCIPLINARY SCIENCES");
      assertThat(xinrui.getMinorQuartile()).isEqualTo("1区");
      assertThat(xinrui.getIsTopJournal()).isTrue();
      assertThat(xinrui.getIsReviewJournal()).isFalse();
    }

    @Test
    @DisplayName("升级版应正确提取年份和版本名")
    void shouldExtractShengjiYearAndEdition() {
      var data = createFullData();

      List<CasRatingEntity> ratings = mapper.mapToCasRatings(data, VENUE_ID, SOURCE_URL);

      CasRatingEntity shengji =
          ratings.stream().filter(r -> "升级版".equals(r.getEdition())).findFirst().orElseThrow();

      assertThat(shengji.getYear()).isEqualTo((short) 2025);
      assertThat(shengji.getEdition()).isEqualTo("升级版");
    }

    @Test
    @DisplayName("无 CAS 分区时应返回空列表")
    void shouldReturnEmptyWhenNoCasPartitions() {
      var data = LetPubVenueData.builder().build();

      List<CasRatingEntity> ratings = mapper.mapToCasRatings(data, VENUE_ID, SOURCE_URL);

      assertThat(ratings).isEmpty();
    }

    @Test
    @DisplayName("分区缺少必填字段时应被跳过")
    void shouldSkipPartitionMissingRequiredFields() {
      var incompletePartition =
          LetPubVenueData.CasPartition.builder()
              .version("2025年3月升级版")
              // majorQuartile 缺失
              .build();
      var data = LetPubVenueData.builder().casPartitions(List.of(incompletePartition)).build();

      List<CasRatingEntity> ratings = mapper.mapToCasRatings(data, VENUE_ID, SOURCE_URL);

      assertThat(ratings).isEmpty();
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
