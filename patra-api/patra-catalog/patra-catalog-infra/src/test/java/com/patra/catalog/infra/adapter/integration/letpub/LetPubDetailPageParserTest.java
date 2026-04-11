package com.patra.catalog.infra.adapter.integration.letpub;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// LetPubDetailPageParser 单元测试。
///
/// 使用贴近真实 LetPub 页面结构的 HTML fixture 测试各字段提取。
/// fixture 包含 `table.table_yjfx` 主表、嵌套 JCR/CAS 子表、
/// `display:none` span 历史值、ECharts JS 等关键结构。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LetPubDetailPageParser 单元测试")
@Timeout(5)
class LetPubDetailPageParserTest {

  private static final String JOURNAL_ID = "10000";

  private static LetPubDetailPageParser parser;
  private static LetPubVenueData data;

  @BeforeAll
  static void parseOnce() {
    parser = new LetPubDetailPageParser();
    String html = loadHtml("detail-page.html");
    data = parser.parse(html, JOURNAL_ID);
  }

  /// 从 classpath 加载 HTML 样本文件。
  private static String loadHtml(String filename) {
    try (InputStream is =
        LetPubDetailPageParserTest.class.getResourceAsStream("/letpub/" + filename)) {
      assertThat(is).as("HTML sample file: " + filename).isNotNull();
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load HTML sample: " + filename, e);
    }
  }

  @Nested
  @DisplayName("期刊名称提取")
  class JournalNameTests {

    @Test
    @DisplayName("应从可见 h1 提取期刊名，去除'期刊收藏夹'后缀")
    void shouldExtractJournalName() {
      assertThat(data.letPubName()).isEqualTo("Nature");
    }

    @Test
    @DisplayName("应忽略 display:none 的品牌 h1 和页脚装饰 h1")
    void shouldIgnoreHiddenAndFooterH1() {
      // 品牌 h1 "美国ACCDON公司旗下品牌" 和页脚 "哇咔咔咔咔" 不应影响期刊名
      assertThat(data.letPubName()).doesNotContain("ACCDON", "哇咔咔");
    }
  }

  @Nested
  @DisplayName("基本信息提取")
  class BasicInfoTests {

    @Test
    @DisplayName("应设置 journalId")
    void shouldSetJournalId() {
      assertThat(data.letPubJournalId()).isEqualTo(JOURNAL_ID);
    }

    @Test
    @DisplayName("应提取研究方向")
    void shouldExtractResearchDirection() {
      assertThat(data.researchDirection()).isEqualTo("综合性期刊");
    }

    @Test
    @DisplayName("应提取年文章数")
    void shouldExtractArticlesPerYear() {
      assertThat(data.articlesPerYear()).isEqualTo(860);
    }

    @Test
    @DisplayName("应提取 Gold OA 占比")
    void shouldExtractGoldOaPercent() {
      assertThat(data.goldOaPercent()).isEqualTo("49.32%");
    }

    @Test
    @DisplayName("应提取研究类文章占比")
    void shouldExtractResearchArticlePercent() {
      assertThat(data.researchArticlePercent()).isEqualTo("52.24%");
    }
  }

  @Nested
  @DisplayName("JCR 分区提取")
  class JcrPartitionTests {

    @Test
    @DisplayName("应从 JIF 子表格提取学科分类")
    void shouldExtractJcrSubject() {
      assertThat(data.jcrSubject()).isEqualTo("MULTIDISCIPLINARY SCIENCES");
    }

    @Test
    @DisplayName("应提取收录子集")
    void shouldExtractJcrCollection() {
      assertThat(data.jcrCollection()).isEqualTo("SCIE");
    }

    @Test
    @DisplayName("应提取 JIF 分区")
    void shouldExtractJifQuartile() {
      assertThat(data.jifQuartile()).isEqualTo("Q1");
    }

    @Test
    @DisplayName("应提取 JIF 排名")
    void shouldExtractJifRank() {
      assertThat(data.jifRank()).isEqualTo("2/136");
    }

    @Test
    @DisplayName("应从 JCI 子表格提取分区")
    void shouldExtractJciQuartile() {
      assertThat(data.jciQuartile()).isEqualTo("Q1");
    }

    @Test
    @DisplayName("应从 JCI 子表格提取排名")
    void shouldExtractJciRank() {
      assertThat(data.jciRank()).isEqualTo("3/144");
    }
  }

  @Nested
  @DisplayName("WOS 增强字段提取（综合分区 / 百分位 / JCI 独立字段 / 自引率）")
  class WosEnhancedTests {

    @Test
    @DisplayName("应从 JCR 行顶部提取 WOS 综合分区等级")
    void shouldExtractWosOverallQuartile() {
      assertThat(data.wosOverallQuartile()).isEqualTo("1区");
    }

    @Test
    @DisplayName("应从 JIF 子表第 5 列 lay-percent 属性提取百分位")
    void shouldExtractJifPercentile() {
      assertThat(data.jifPercentile()).isEqualTo(99.0);
    }

    @Test
    @DisplayName("应从 JCI 子表独立提取 JCI 学科（非复用 JIF subject）")
    void shouldExtractJciSubjectIndependently() {
      assertThat(data.jciSubject()).isEqualTo("NATURAL SCIENCE FLAGSHIP");
    }

    @Test
    @DisplayName("应从 JCI 子表独立提取 JCI 收录子集")
    void shouldExtractJciCollectionIndependently() {
      assertThat(data.jciCollection()).isEqualTo("SCIE");
    }

    @Test
    @DisplayName("应从 JCI 子表第 5 列 lay-percent 属性提取百分位")
    void shouldExtractJciPercentile() {
      assertThat(data.jciPercentile()).isEqualTo(98.9);
    }

    @Test
    @DisplayName("应从独立行 \"JCI期刊引文指标\" 提取数值")
    void shouldExtractJciValue() {
      assertThat(data.jciValue()).isEqualTo(11.14);
    }

    @Test
    @DisplayName("应从独立行 \"自引率\" 提取百分比（剥离趋势图按钮 span）")
    void shouldExtractSelfCitationRate() {
      assertThat(data.selfCitationRate()).isEqualTo(1.6);
    }
  }

  @Nested
  @DisplayName("CAS 分区提取（多版本）")
  class CasPartitionTests {

    @Test
    @DisplayName("应提取全部 3 个 CAS 版本（新锐版/升级版/旧的升级版）")
    void shouldExtractAllCasVersions() {
      assertThat(data.casPartitions()).hasSize(3);
      assertThat(data.casPartitions())
          .extracting(LetPubVenueData.CasPartition::version)
          .containsExactly("2026年3月新锐版", "2025年3月升级版", "2023年12月旧的升级版");
    }

    @Test
    @DisplayName("新锐版应排在第一位")
    void shouldOrderXinruiFirst() {
      assertThat(data.casPartitions().getFirst().version()).isEqualTo("2026年3月新锐版");
    }

    @Test
    @DisplayName("应提取大类学科名称（过滤 span 分区标签）")
    void shouldExtractMajorCategory() {
      var xinrui = data.casPartitions().getFirst();
      assertThat(xinrui.majorCategory()).isEqualTo("综合性期刊");
    }

    @Test
    @DisplayName("应从可见 span 提取大类分区（忽略 display:none 历史值）")
    void shouldExtractMajorQuartileFromVisibleSpan() {
      var xinrui = data.casPartitions().getFirst();
      assertThat(xinrui.majorQuartile()).isEqualTo("1区");
    }

    @Test
    @DisplayName("应提取小类学科名称")
    void shouldExtractMinorSubject() {
      var xinrui = data.casPartitions().getFirst();
      assertThat(xinrui.minorSubject()).contains("MULTIDISCIPLINARY SCIENCES");
    }

    @Test
    @DisplayName("应从可见 span 提取小类分区")
    void shouldExtractMinorQuartileFromVisibleSpan() {
      var xinrui = data.casPartitions().getFirst();
      assertThat(xinrui.minorQuartile()).isEqualTo("1区");
    }

    @Test
    @DisplayName("应提取 Top 期刊标识")
    void shouldExtractTopJournalFlag() {
      var xinrui = data.casPartitions().getFirst();
      assertThat(xinrui.topJournal()).isTrue();
    }

    @Test
    @DisplayName("应提取综述期刊标识")
    void shouldExtractReviewJournalFlag() {
      var xinrui = data.casPartitions().getFirst();
      assertThat(xinrui.reviewJournal()).isFalse();
    }

    @Test
    @DisplayName("升级版也应被正确解析")
    void shouldParseShengjiEdition() {
      var shengji = data.casPartitions().get(1);
      assertThat(shengji.version()).isEqualTo("2025年3月升级版");
      assertThat(shengji.majorCategory()).isEqualTo("综合性期刊");
      assertThat(shengji.majorQuartile()).isEqualTo("1区");
    }

    @Test
    @DisplayName("旧的升级版也应被正确解析")
    void shouldParseLegacyEdition() {
      var legacy = data.casPartitions().get(2);
      assertThat(legacy.version()).isEqualTo("2023年12月旧的升级版");
      assertThat(legacy.majorQuartile()).isEqualTo("1区");
    }
  }

  @Nested
  @DisplayName("审稿与录用信息提取")
  class AdvisoryInfoTests {

    @Test
    @DisplayName("应提取官方审稿速度")
    void shouldExtractOfficialReviewSpeed() {
      assertThat(data.reviewSpeedOfficial()).contains("较慢");
    }

    @Test
    @DisplayName("应提取用户分享审稿速度")
    void shouldExtractUserReviewSpeed() {
      assertThat(data.reviewSpeedUser()).contains("6.0个月");
    }

    @Test
    @DisplayName("应提取录用比例")
    void shouldExtractAcceptanceRate() {
      assertThat(data.acceptanceRate()).contains("7.69%");
    }

    @Test
    @DisplayName("应提取 APC 费用信息")
    void shouldExtractApcInfo() {
      assertThat(data.apcInfo()).contains("US$11390");
    }
  }

  @Nested
  @DisplayName("收录与影响因子趋势")
  class TrendAndIndexTests {

    @Test
    @DisplayName("应提取数据库收录列表")
    void shouldExtractIndexedIn() {
      assertThat(data.indexedIn()).isNotEmpty();
      assertThat(data.indexedIn()).anyMatch(s -> s.contains("Science Citation Index"));
    }

    @Test
    @DisplayName("应从 ECharts JS 提取10年 IF 趋势")
    void shouldExtractImpactFactorTrend() {
      assertThat(data.impactFactorTrend())
          .isNotNull()
          .hasSize(10)
          .containsEntry("2024-2025", 48.5)
          .containsEntry("2015-2016", 38.138)
          .containsEntry("2021-2022", 69.504);
    }
  }

  @Nested
  @DisplayName("边界情况")
  class EdgeCaseTests {

    @Test
    @DisplayName("空 HTML 应返回仅含 journalId 的默认数据")
    void shouldHandleEmptyHtml() {
      LetPubVenueData emptyResult = parser.parse("<html></html>", "99999");

      assertThat(emptyResult.letPubJournalId()).isEqualTo("99999");
      assertThat(emptyResult.letPubName()).isNull();
      assertThat(emptyResult.impactFactorTrend()).isEmpty();
      assertThat(emptyResult.indexedIn()).isEmpty();
    }

    @Test
    @DisplayName("无主表格时应优雅降级")
    void shouldHandleMissingMainTable() {
      String html = "<html><body><h1>Some Journal 期刊收藏夹</h1></body></html>";
      LetPubVenueData result = parser.parse(html, "12345");

      assertThat(result.letPubName()).isEqualTo("Some Journal");
      assertThat(result.researchDirection()).isEmpty();
    }
  }

  @Nested
  @DisplayName("封面图片 URL 提取")
  class CoverImageTests {

    @Test
    @DisplayName("应从 layui-form-item 提取真实 Aliyun OSS 封面 URL")
    void shouldExtractCoverImageUrlFromLayuiFormItem() {
      assertThat(data.coverImageSourceUrl())
          .isEqualTo(
              "https://media-cdn.oss-cn-hangzhou.aliyuncs.com/statics/images/comment_center/cover/journal/6054.jpg?ver=1775839295");
    }

    @Test
    @DisplayName("页面无封面图元素时应返回 null")
    void shouldReturnNullWhenNoCoverImageElement() {
      String html = "<html><body><div>no cover here</div></body></html>";
      LetPubVenueData parsed = parser.parse(html, JOURNAL_ID);
      assertThat(parsed.coverImageSourceUrl()).isNull();
    }

    @Test
    @DisplayName("img 的 src 属性为空时应返回 null")
    void shouldReturnNullWhenSrcAttributeIsBlank() {
      String html =
          "<html><body>"
              + "<div class=\"layui-form-item\"><img src=\"\" title=\"NATURE\"></div>"
              + "</body></html>";
      LetPubVenueData parsed = parser.parse(html, JOURNAL_ID);
      assertThat(parsed.coverImageSourceUrl()).isNull();
    }

    @Test
    @DisplayName("只有非 /cover/journal/ 路径的图片时应返回 null")
    void shouldIgnoreUnrelatedImagesWithoutCoverJournalPath() {
      String html =
          "<html><body>"
              + "<img src=\"/journal_cover/0028-0836.jpg\"/>"
              + "<img src=\"/banner/header.png\"/>"
              + "</body></html>";
      LetPubVenueData parsed = parser.parse(html, JOURNAL_ID);
      assertThat(parsed.coverImageSourceUrl()).isNull();
    }
  }

  @Nested
  @DisplayName("CAS 预警名单时间序列提取")
  class CasWarningListTests {

    @Test
    @DisplayName("应从多行 <br><br> 分隔的文本中解析出 6 条预警记录")
    void shouldParseSixCasWarningRecordsFromFixture() {
      assertThat(data.casWarnings()).hasSize(6);
    }

    @Test
    @DisplayName("应正确提取发布年份与版本标签")
    void shouldExtractPublishedYearAndEditionLabel() {
      assertThat(data.casWarnings())
          .extracting(LetPubVenueData.CasWarningRecord::publishedYear)
          .containsExactly(2026, 2025, 2024, 2023, 2021, 2020);

      assertThat(data.casWarnings())
          .extracting(LetPubVenueData.CasWarningRecord::editionLabel)
          .containsExactly("新锐学术版", "2025版", "2024版", "2023版", "2021版", "2020版");
    }

    @Test
    @DisplayName("应正确提取发布月份")
    void shouldExtractPublishedMonth() {
      assertThat(data.casWarnings())
          .extracting(LetPubVenueData.CasWarningRecord::publishedMonth)
          .containsExactly(3, 3, 2, 1, 12, 12);
    }

    @Test
    @DisplayName("应识别不在预警名单中的记录")
    void shouldMarkAsNotInWarningWhenContainsNegation() {
      // 2026/2025/2023/2020 这 4 条是"不在预警名单中"
      assertThat(data.casWarnings())
          .filteredOn(r -> !r.inWarningList())
          .hasSize(4)
          .extracting(LetPubVenueData.CasWarningRecord::publishedYear)
          .containsExactlyInAnyOrder(2026, 2025, 2023, 2020);
    }

    @Test
    @DisplayName("应识别中风险预警（2024 年）")
    void shouldRecognizeMediumWarningLevel() {
      LetPubVenueData.CasWarningRecord y2024 =
          data.casWarnings().stream()
              .filter(r -> r.publishedYear() == 2024)
              .findFirst()
              .orElseThrow();
      assertThat(y2024.inWarningList()).isTrue();
      assertThat(y2024.warningLevel()).isEqualTo("中");
    }

    @Test
    @DisplayName("应识别高风险预警（2021 年）")
    void shouldRecognizeHighWarningLevel() {
      LetPubVenueData.CasWarningRecord y2021 =
          data.casWarnings().stream()
              .filter(r -> r.publishedYear() == 2021)
              .findFirst()
              .orElseThrow();
      assertThat(y2021.inWarningList()).isTrue();
      assertThat(y2021.warningLevel()).isEqualTo("高");
    }

    @Test
    @DisplayName("rawText 应保留原始描述行以便追溯")
    void shouldPreserveRawTextForTraceability() {
      LetPubVenueData.CasWarningRecord y2025 =
          data.casWarnings().stream()
              .filter(r -> r.publishedYear() == 2025)
              .findFirst()
              .orElseThrow();
      assertThat(y2025.rawText()).contains("2025年03月").contains("2025版");
    }

    @Test
    @DisplayName("不在预警记录的 warningLevel 应为 null")
    void shouldHaveNullWarningLevelWhenNotInWarning() {
      LetPubVenueData.CasWarningRecord y2026 =
          data.casWarnings().stream()
              .filter(r -> r.publishedYear() == 2026)
              .findFirst()
              .orElseThrow();
      assertThat(y2026.inWarningList()).isFalse();
      assertThat(y2026.warningLevel()).isNull();
    }
  }
}
