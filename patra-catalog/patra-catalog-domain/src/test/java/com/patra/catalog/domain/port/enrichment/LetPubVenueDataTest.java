package com.patra.catalog.domain.port.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// LetPubVenueData 值对象单元测试。
///
/// **测试策略**：
///
/// - Builder 构建：验证 `@Builder` 正确构造全部字段
/// - 防御性拷贝：集合字段的不可变性
/// - null 安全：集合字段为 null 时自动转为空集合
/// - Record 等值语义：同值相等、异值不等
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LetPubVenueData 单元测试")
@Timeout(2)
class LetPubVenueDataTest {

  @Nested
  @DisplayName("@Builder 构建测试")
  class BuilderTests {

    @Test
    @DisplayName("应该通过 Builder 创建包含所有字段的 VO")
    void shouldBuildWithAllFields() {
      // Given
      var xinruiPartition =
          LetPubVenueData.CasPartition.builder()
              .version("2026年3月新锐版")
              .majorCategory("综合性期刊")
              .majorQuartile("1区")
              .minorSubject("综合性期刊")
              .minorQuartile("1区")
              .topJournal(true)
              .reviewJournal(false)
              .build();
      var shengjiPartition =
          LetPubVenueData.CasPartition.builder()
              .version("2025年3月升级版")
              .majorCategory("综合性期刊")
              .majorQuartile("1区")
              .minorSubject("综合性期刊")
              .minorQuartile("1区")
              .topJournal(true)
              .reviewJournal(false)
              .build();

      // When
      var data =
          LetPubVenueData.builder()
              .letPubJournalId("10000")
              .letPubName("Nature")
              .researchDirection("综合性期刊")
              .articlesPerYear(860)
              .goldOaPercent("49.32%")
              .researchArticlePercent("52.24%")
              .jcrSubject("MULTIDISCIPLINARY SCIENCES - SCIE")
              .jcrCollection("MULTIDISCIPLINARY SCIENCES")
              .jifQuartile("Q1")
              .jifRank("1/73")
              .jciQuartile("Q1")
              .jciRank("1/73")
              .casPartitions(List.of(xinruiPartition, shengjiPartition))
              .reviewSpeedOfficial("较慢，>12周")
              .reviewSpeedUser("平均6.0个月")
              .acceptanceRate("较难")
              .apcInfo("US$11390")
              .impactFactorTrend(Map.of("2024-2025", 48.5, "2023-2024", 50.5))
              .fiveYearImpactFactor(55.0)
              .indexedIn(List.of("SCI", "SCIE", "PubMed", "Scopus"))
              .build();

      // Then
      assertThat(data.letPubJournalId()).isEqualTo("10000");
      assertThat(data.letPubName()).isEqualTo("Nature");
      assertThat(data.researchDirection()).isEqualTo("综合性期刊");
      assertThat(data.articlesPerYear()).isEqualTo(860);
      assertThat(data.goldOaPercent()).isEqualTo("49.32%");
      assertThat(data.researchArticlePercent()).isEqualTo("52.24%");
      assertThat(data.jcrSubject()).isEqualTo("MULTIDISCIPLINARY SCIENCES - SCIE");
      assertThat(data.jcrCollection()).isEqualTo("MULTIDISCIPLINARY SCIENCES");
      assertThat(data.jifQuartile()).isEqualTo("Q1");
      assertThat(data.jifRank()).isEqualTo("1/73");
      assertThat(data.jciQuartile()).isEqualTo("Q1");
      assertThat(data.jciRank()).isEqualTo("1/73");
      assertThat(data.casPartitions())
          .hasSize(2)
          .extracting(LetPubVenueData.CasPartition::version)
          .containsExactly("2026年3月新锐版", "2025年3月升级版");
      assertThat(data.casPartitions().getFirst().majorQuartile()).isEqualTo("1区");
      assertThat(data.casPartitions().getFirst().topJournal()).isTrue();
      assertThat(data.reviewSpeedOfficial()).isEqualTo("较慢，>12周");
      assertThat(data.reviewSpeedUser()).isEqualTo("平均6.0个月");
      assertThat(data.acceptanceRate()).isEqualTo("较难");
      assertThat(data.apcInfo()).isEqualTo("US$11390");
      assertThat(data.impactFactorTrend())
          .hasSize(2)
          .containsEntry("2024-2025", 48.5)
          .containsEntry("2023-2024", 50.5);
      assertThat(data.fiveYearImpactFactor()).isEqualTo(55.0);
      assertThat(data.indexedIn()).containsExactly("SCI", "SCIE", "PubMed", "Scopus");
    }

    @Test
    @DisplayName("应该允许所有字段为 null（最小构建）")
    void shouldAllowMinimalBuild() {
      // When
      var data = LetPubVenueData.builder().build();

      // Then
      assertThat(data.letPubJournalId()).isNull();
      assertThat(data.letPubName()).isNull();
      assertThat(data.articlesPerYear()).isNull();
      assertThat(data.casPartitions()).isEmpty();
      assertThat(data.fiveYearImpactFactor()).isNull();
      assertThat(data.impactFactorTrend()).isEmpty();
      assertThat(data.indexedIn()).isEmpty();
    }
  }

  @Nested
  @DisplayName("indexedIn 防御性拷贝测试")
  class DefensiveCopyTests {

    @Test
    @DisplayName("impactFactorTrend 为 null 时应转为空 Map")
    void shouldConvertNullImpactFactorTrendToEmptyMap() {
      // When
      var data = LetPubVenueData.builder().impactFactorTrend(null).build();

      // Then
      assertThat(data.impactFactorTrend()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("impactFactorTrend 应进行防御性拷贝")
    void shouldDefensivelyCopyImpactFactorTrend() {
      // Given
      var mutableMap = new HashMap<>(Map.of("2024-2025", 48.5));

      // When
      var data = LetPubVenueData.builder().impactFactorTrend(mutableMap).build();

      // Then — 修改原始 Map 不应影响 VO
      mutableMap.put("2023-2024", 50.5);
      assertThat(data.impactFactorTrend()).hasSize(1).containsEntry("2024-2025", 48.5);
    }

    @Test
    @DisplayName("impactFactorTrend 应为不可变 Map")
    void shouldReturnUnmodifiableImpactFactorTrend() {
      // Given
      var data = LetPubVenueData.builder().impactFactorTrend(Map.of("2024-2025", 48.5)).build();

      // Then
      assertThat(data.impactFactorTrend()).isUnmodifiable().hasSize(1);
    }

    @Test
    @DisplayName("indexedIn 为 null 时应转为空列表")
    void shouldConvertNullIndexedInToEmptyList() {
      // When
      var data = LetPubVenueData.builder().indexedIn(null).build();

      // Then
      assertThat(data.indexedIn()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("indexedIn 应进行防御性拷贝")
    void shouldDefensivelyCopyIndexedIn() {
      // Given
      var mutableList = new ArrayList<>(List.of("SCI", "SCIE"));

      // When
      var data = LetPubVenueData.builder().indexedIn(mutableList).build();

      // Then — 修改原始列表不应影响 VO
      mutableList.add("PubMed");
      assertThat(data.indexedIn()).hasSize(2).containsExactly("SCI", "SCIE");
    }

    @Test
    @DisplayName("indexedIn 应为不可变列表")
    void shouldReturnUnmodifiableIndexedIn() {
      // Given
      var data = LetPubVenueData.builder().indexedIn(List.of("SCI")).build();

      // Then
      assertThat(data.indexedIn()).isUnmodifiable().hasSize(1);
    }
  }

  @Nested
  @DisplayName("Record 等值语义测试")
  class EqualityTests {

    @Test
    @DisplayName("同值对象应相等")
    void shouldBeEqualWhenSameValues() {
      var a = LetPubVenueData.builder().letPubJournalId("10000").letPubName("Nature").build();
      var b = LetPubVenueData.builder().letPubJournalId("10000").letPubName("Nature").build();
      assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("不同值对象不应相等")
    void shouldNotBeEqualWhenDifferentValues() {
      var a = LetPubVenueData.builder().letPubJournalId("10000").build();
      var b = LetPubVenueData.builder().letPubJournalId("20000").build();
      assertThat(a).isNotEqualTo(b);
    }
  }
}
