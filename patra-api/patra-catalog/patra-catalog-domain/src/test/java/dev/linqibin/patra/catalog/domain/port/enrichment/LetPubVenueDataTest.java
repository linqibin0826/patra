package dev.linqibin.patra.catalog.domain.port.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.catalog.domain.model.enums.CasWarningLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// LetPubVenueData 值对象单元测试。
///
/// **测试策略**：
///
/// - 4 个子 record（BasicInfo/JcrMetrics/CasData/SubmissionInfo）独立构造 + 组合
/// - 防御性拷贝：每个含集合字段的子 record 紧凑构造器验证
/// - null 安全：顶层和子 record 的 null 归一化
/// - Record 等值语义：同值相等、异值不等
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LetPubVenueData 单元测试")
class LetPubVenueDataTest {

  @Nested
  @DisplayName("子 record 构造与组合测试")
  class CompositionTests {

    @Test
    @DisplayName("应通过 4 个子 record 组合完整 LetPubVenueData")
    void shouldComposeFromSubRecords() {
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
      var warningRecord =
          LetPubVenueData.CasWarningRecord.builder()
              .publishedYear(2025)
              .publishedMonth(3)
              .editionLabel("2025版")
              .inWarningList(false)
              .warningLevel(null)
              .rawText("2025年03月发布的2025版：不在预警名单中")
              .build();

      var basicInfo =
          LetPubVenueData.BasicInfo.builder()
              .letPubJournalId("10000")
              .letPubName("Nature")
              .coverImageSourceUrl("https://cdn.letpub.com/cover/journal/10000.jpg")
              .researchDirection("综合性期刊")
              .articlesPerYear(860)
              .goldOaPercent("49.32%")
              .researchArticlePercent("52.24%")
              .indexedIn(List.of("SCI", "SCIE", "PubMed", "Scopus"))
              .build();

      var jcrMetrics =
          LetPubVenueData.JcrMetrics.builder()
              .wosOverallQuartile("1区")
              .jcrSubject("MULTIDISCIPLINARY SCIENCES")
              .jcrCollection("SCIE")
              .jifQuartile("Q1")
              .jifRank("1/73")
              .jifPercentile(99.0)
              .jciSubject("MULTIDISCIPLINARY SCIENCES")
              .jciCollection("SCIE")
              .jciQuartile("Q1")
              .jciRank("1/73")
              .jciPercentile(98.9)
              .jciValue(11.14)
              .selfCitationRate(1.6)
              .impactFactorTrend(Map.of("2024-2025", 48.5, "2023-2024", 50.5))
              .build();

      var casData =
          LetPubVenueData.CasData.of(
              List.of(xinruiPartition, shengjiPartition), List.of(warningRecord));

      var submissionInfo =
          LetPubVenueData.SubmissionInfo.of("较慢，>12周", "平均6.0个月", "较难", "US$11390");

      // When
      var data = LetPubVenueData.of(basicInfo, jcrMetrics, casData, submissionInfo);

      // Then
      assertThat(data.basicInfo().letPubJournalId()).isEqualTo("10000");
      assertThat(data.basicInfo().letPubName()).isEqualTo("Nature");
      assertThat(data.basicInfo().researchDirection()).isEqualTo("综合性期刊");
      assertThat(data.basicInfo().articlesPerYear()).isEqualTo(860);
      assertThat(data.basicInfo().goldOaPercent()).isEqualTo("49.32%");
      assertThat(data.basicInfo().indexedIn()).containsExactly("SCI", "SCIE", "PubMed", "Scopus");

      assertThat(data.jcrMetrics().wosOverallQuartile()).isEqualTo("1区");
      assertThat(data.jcrMetrics().jcrSubject()).isEqualTo("MULTIDISCIPLINARY SCIENCES");
      assertThat(data.jcrMetrics().jifQuartile()).isEqualTo("Q1");
      assertThat(data.jcrMetrics().jifPercentile()).isEqualTo(99.0);
      assertThat(data.jcrMetrics().jciPercentile()).isEqualTo(98.9);
      assertThat(data.jcrMetrics().jciValue()).isEqualTo(11.14);
      assertThat(data.jcrMetrics().selfCitationRate()).isEqualTo(1.6);
      assertThat(data.jcrMetrics().impactFactorTrend())
          .hasSize(2)
          .containsEntry("2024-2025", 48.5)
          .containsEntry("2023-2024", 50.5);

      assertThat(data.casData().partitions())
          .hasSize(2)
          .extracting(LetPubVenueData.CasPartition::version)
          .containsExactly("2026年3月新锐版", "2025年3月升级版");
      assertThat(data.casData().partitions().getFirst().majorQuartile()).isEqualTo("1区");
      assertThat(data.casData().warnings()).hasSize(1);

      assertThat(data.submissionInfo().reviewSpeedOfficial()).isEqualTo("较慢，>12周");
      assertThat(data.submissionInfo().reviewSpeedUser()).isEqualTo("平均6.0个月");
      assertThat(data.submissionInfo().acceptanceRate()).isEqualTo("较难");
      assertThat(data.submissionInfo().apcInfo()).isEqualTo("US$11390");
    }

    @Test
    @DisplayName("LetPubVenueData.empty() 所有子 record 都应为空实例")
    void shouldProvideFullyEmptyInstance() {
      var data = LetPubVenueData.empty();

      assertThat(data.basicInfo().letPubJournalId()).isNull();
      assertThat(data.basicInfo().indexedIn()).isEmpty();
      assertThat(data.jcrMetrics().jcrSubject()).isNull();
      assertThat(data.jcrMetrics().impactFactorTrend()).isEmpty();
      assertThat(data.casData().partitions()).isEmpty();
      assertThat(data.casData().warnings()).isEmpty();
      assertThat(data.submissionInfo().reviewSpeedOfficial()).isNull();
    }

    @Test
    @DisplayName("顶层 record 的 null 子字段应被归一为空实例（避免下游 NPE）")
    void shouldNormalizeNullSubRecordsToEmpty() {
      var data = new LetPubVenueData(null, null, null, null);

      assertThat(data.basicInfo()).isNotNull();
      assertThat(data.jcrMetrics()).isNotNull();
      assertThat(data.casData()).isNotNull();
      assertThat(data.submissionInfo()).isNotNull();
      assertThat(data.casData().partitions()).isEmpty();
    }
  }

  @Nested
  @DisplayName("防御性拷贝测试（子 record 集合字段）")
  class DefensiveCopyTests {

    @Test
    @DisplayName("JcrMetrics.impactFactorTrend 为 null 时应转为空 Map")
    void shouldConvertNullImpactFactorTrendToEmptyMap() {
      var jcrMetrics = LetPubVenueData.JcrMetrics.builder().impactFactorTrend(null).build();
      assertThat(jcrMetrics.impactFactorTrend()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("JcrMetrics.impactFactorTrend 应进行防御性拷贝")
    void shouldDefensivelyCopyImpactFactorTrend() {
      var mutableMap = new HashMap<>(Map.of("2024-2025", 48.5));
      var jcrMetrics = LetPubVenueData.JcrMetrics.builder().impactFactorTrend(mutableMap).build();

      mutableMap.put("2023-2024", 50.5);
      assertThat(jcrMetrics.impactFactorTrend()).hasSize(1).containsEntry("2024-2025", 48.5);
    }

    @Test
    @DisplayName("JcrMetrics.impactFactorTrend 应为不可变 Map")
    void shouldReturnUnmodifiableImpactFactorTrend() {
      var jcrMetrics =
          LetPubVenueData.JcrMetrics.builder().impactFactorTrend(Map.of("2024-2025", 48.5)).build();
      assertThat(jcrMetrics.impactFactorTrend()).isUnmodifiable().hasSize(1);
    }

    @Test
    @DisplayName("BasicInfo.indexedIn 为 null 时应转为空列表")
    void shouldConvertNullIndexedInToEmptyList() {
      var basicInfo = LetPubVenueData.BasicInfo.builder().indexedIn(null).build();
      assertThat(basicInfo.indexedIn()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("BasicInfo.indexedIn 应进行防御性拷贝")
    void shouldDefensivelyCopyIndexedIn() {
      var mutableList = new ArrayList<>(List.of("SCI", "SCIE"));
      var basicInfo = LetPubVenueData.BasicInfo.builder().indexedIn(mutableList).build();

      mutableList.add("PubMed");
      assertThat(basicInfo.indexedIn()).hasSize(2).containsExactly("SCI", "SCIE");
    }

    @Test
    @DisplayName("BasicInfo.indexedIn 应为不可变列表")
    void shouldReturnUnmodifiableIndexedIn() {
      var basicInfo = LetPubVenueData.BasicInfo.builder().indexedIn(List.of("SCI")).build();
      assertThat(basicInfo.indexedIn()).isUnmodifiable().hasSize(1);
    }

    @Test
    @DisplayName("CasData.partitions 为 null 时应转为空列表")
    void shouldConvertNullCasPartitionsToEmptyList() {
      var casData = LetPubVenueData.CasData.of(null, null);
      assertThat(casData.partitions()).isNotNull().isEmpty();
      assertThat(casData.warnings()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("CasData.partitions 应进行防御性拷贝")
    void shouldDefensivelyCopyCasPartitions() {
      var partition =
          LetPubVenueData.CasPartition.builder().version("2025年3月升级版").majorQuartile("1区").build();
      var mutableList = new ArrayList<>(List.of(partition));
      var casData = LetPubVenueData.CasData.of(mutableList, List.of());

      mutableList.add(partition);
      assertThat(casData.partitions()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Record 等值语义测试")
  class EqualityTests {

    @Test
    @DisplayName("同值 LetPubVenueData 应相等")
    void shouldBeEqualWhenSameValues() {
      var bi1 = LetPubVenueData.BasicInfo.builder().letPubJournalId("10000").build();
      var bi2 = LetPubVenueData.BasicInfo.builder().letPubJournalId("10000").build();
      var a = LetPubVenueData.of(bi1, null, null, null);
      var b = LetPubVenueData.of(bi2, null, null, null);
      assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("不同值 LetPubVenueData 不应相等")
    void shouldNotBeEqualWhenDifferentValues() {
      var a =
          LetPubVenueData.of(
              LetPubVenueData.BasicInfo.builder().letPubJournalId("10000").build(),
              null,
              null,
              null);
      var b =
          LetPubVenueData.of(
              LetPubVenueData.BasicInfo.builder().letPubJournalId("20000").build(),
              null,
              null,
              null);
      assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("CasWarningRecord 的枚举 warningLevel 相等语义正确")
    void shouldCompareCasWarningLevelEnumCorrectly() {
      var r1 =
          LetPubVenueData.CasWarningRecord.builder()
              .publishedYear(2024)
              .editionLabel("2024版")
              .inWarningList(true)
              .warningLevel(CasWarningLevel.HIGH)
              .rawText("r")
              .build();
      var r2 =
          LetPubVenueData.CasWarningRecord.builder()
              .publishedYear(2024)
              .editionLabel("2024版")
              .inWarningList(true)
              .warningLevel(CasWarningLevel.HIGH)
              .rawText("r")
              .build();
      assertThat(r1).isEqualTo(r2);
    }
  }
}
