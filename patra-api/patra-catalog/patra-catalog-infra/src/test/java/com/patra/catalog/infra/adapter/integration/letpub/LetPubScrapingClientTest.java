package com.patra.catalog.infra.adapter.integration.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.client.RestClient;

/// LetPubScrapingClient 单元测试。
///
/// **测试策略**：
///
/// - HTML 解析逻辑：使用本地 HTML 样本文件测试 Jsoup 解析
/// - 限流检测：验证 `isRateLimited()` 的边界条件
/// - 完整流程：通过 mock RestClient 测试 `findByIssn()` 两步爬取
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LetPubScrapingClient 单元测试")
@Timeout(5)
class LetPubScrapingClientTest {

  private LetPubScrapingClient client;

  @BeforeEach
  void setUp() {
    // 使用测试构造器：无 HTTP 延迟
    client = new LetPubScrapingClient(mock(RestClient.class), 0, 0);
  }

  /// 从 classpath 加载 HTML 样本文件。
  private String loadHtml(String filename) {
    try (InputStream is = getClass().getResourceAsStream("/letpub/" + filename)) {
      assertThat(is).as("HTML sample file: " + filename).isNotNull();
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load HTML sample: " + filename, e);
    }
  }

  @Nested
  @DisplayName("parseSearchResult() 测试")
  class ParseSearchResultTests {

    @Test
    @DisplayName("应该从搜索结果中提取 journalId")
    void shouldExtractJournalId() {
      // Given
      String html = loadHtml("search-result.html");

      // When
      Optional<String> journalId = client.parseSearchResult(html);

      // Then
      assertThat(journalId).isPresent().contains("10000");
    }

    @Test
    @DisplayName("应该忽略 journalid=0 的评论链接")
    void shouldIgnoreCommentLinks() {
      // Given — 仅包含 journalid=0 的链接
      String html =
          """
          <html><body>
            <a href="index.php?journalid=0&page=journalapp&view=comment">评论</a>
          </body></html>
          """;

      // When
      Optional<String> journalId = client.parseSearchResult(html);

      // Then
      assertThat(journalId).isEmpty();
    }

    @Test
    @DisplayName("搜索无结果时应返回 empty")
    void shouldReturnEmptyWhenNoResult() {
      // Given
      String html = "<html><body><p>未找到匹配的期刊</p></body></html>";

      // When
      Optional<String> journalId = client.parseSearchResult(html);

      // Then
      assertThat(journalId).isEmpty();
    }
  }

  @Nested
  @DisplayName("parseDetailPage() 测试")
  class ParseDetailPageTests {

    @Test
    @DisplayName("应该提取期刊名称")
    void shouldExtractJournalName() {
      String html = loadHtml("detail-page.html");
      LetPubVenueData data = client.parseDetailPage(html, "10000");

      assertThat(data.letPubName()).isEqualTo("Nature");
    }

    @Test
    @DisplayName("应该提取基本信息字段")
    void shouldExtractBasicInfo() {
      String html = loadHtml("detail-page.html");
      LetPubVenueData data = client.parseDetailPage(html, "10000");

      assertThat(data.letPubJournalId()).isEqualTo("10000");
      assertThat(data.researchDirection()).isEqualTo("综合性期刊");
      assertThat(data.country()).isEqualTo("ENGLAND");
      assertThat(data.language()).isEqualTo("English");
      assertThat(data.frequency()).isEqualTo("Weekly");
      assertThat(data.startYear()).isEqualTo(1869);
      assertThat(data.articlesPerYear()).isEqualTo(860);
      assertThat(data.goldOaPercent()).isEqualTo("49.32%");
      assertThat(data.researchArticlePercent()).isEqualTo("52.24%");
    }

    @Test
    @DisplayName("应该提取 JCR 分区")
    void shouldExtractJcrPartition() {
      String html = loadHtml("detail-page.html");
      LetPubVenueData data = client.parseDetailPage(html, "10000");

      assertThat(data.jcrSubject()).isEqualTo("MULTIDISCIPLINARY SCIENCES");
      assertThat(data.jcrCollection()).isEqualTo("SCIE");
      assertThat(data.jifQuartile()).isEqualTo("Q1");
      assertThat(data.jifRank()).isEqualTo("1/73");
      assertThat(data.jciQuartile()).isEqualTo("Q1");
      assertThat(data.jciRank()).isEqualTo("2/73");
    }

    @Test
    @DisplayName("应该提取 CAS 分区")
    void shouldExtractCasPartition() {
      String html = loadHtml("detail-page.html");
      LetPubVenueData data = client.parseDetailPage(html, "10000");

      assertThat(data.casVersion()).isEqualTo("2023年12月升级版");
      assertThat(data.casMajorCategory()).isEqualTo("综合性期刊");
      assertThat(data.casMajorQuartile()).isEqualTo("1区");
      assertThat(data.casMinorSubject()).contains("MULTIDISCIPLINARY SCIENCES");
      assertThat(data.casMinorQuartile()).isEqualTo("1区");
      assertThat(data.casTopJournal()).isTrue();
      assertThat(data.casReviewJournal()).isFalse();
    }

    @Test
    @DisplayName("应该提取审稿速度和费用信息")
    void shouldExtractReviewAndApcInfo() {
      String html = loadHtml("detail-page.html");
      LetPubVenueData data = client.parseDetailPage(html, "10000");

      assertThat(data.reviewSpeedOfficial()).contains("较慢");
      assertThat(data.reviewSpeedUser()).contains("6.0个月");
      assertThat(data.acceptanceRate()).contains("7.69%");
      assertThat(data.apcInfo()).contains("US$11390");
    }

    @Test
    @DisplayName("应该提取影响因子趋势数据（近10年）")
    void shouldExtractImpactFactorTrend() {
      String html = loadHtml("detail-page.html");
      LetPubVenueData data = client.parseDetailPage(html, "10000");

      assertThat(data.impactFactorTrend())
          .isNotNull()
          .hasSize(10)
          .containsEntry("2024-2025", 48.5)
          .containsEntry("2015-2016", 38.138)
          .containsEntry("2021-2022", 69.504);
    }

    @Test
    @DisplayName("应该提取五年影响因子")
    void shouldExtractFiveYearImpactFactor() {
      String html = loadHtml("detail-page.html");
      LetPubVenueData data = client.parseDetailPage(html, "10000");

      assertThat(data.fiveYearImpactFactor()).isEqualTo(55.0);
    }

    @Test
    @DisplayName("应该提取预警名单状态")
    void shouldExtractWarningListStatus() {
      String html = loadHtml("detail-page.html");
      LetPubVenueData data = client.parseDetailPage(html, "10000");

      assertThat(data.warningListStatus()).contains("2024");
    }

    @Test
    @DisplayName("应该提取收录信息")
    void shouldExtractIndexedIn() {
      String html = loadHtml("detail-page.html");
      LetPubVenueData data = client.parseDetailPage(html, "10000");

      assertThat(data.indexedIn()).isNotEmpty();
      assertThat(data.indexedIn())
          .anyMatch(s -> s.contains("SCI") || s.contains("Science Citation Index"));
    }
  }

  @Nested
  @DisplayName("isRateLimited() 测试")
  class IsRateLimitedTests {

    @Test
    @DisplayName("包含 '速度过快' 且内容短时应判定为限流")
    void shouldDetectRateLimit() {
      String html = loadHtml("rate-limited.html");
      assertThat(client.isRateLimited(html)).isTrue();
    }

    @Test
    @DisplayName("正常页面不应判定为限流")
    void shouldNotDetectRateLimitForNormalPage() {
      String html = loadHtml("detail-page.html");
      assertThat(client.isRateLimited(html)).isFalse();
    }

    @Test
    @DisplayName("空内容不应判定为限流")
    void shouldNotDetectRateLimitForEmptyContent() {
      assertThat(client.isRateLimited("")).isFalse();
      assertThat(client.isRateLimited(null)).isFalse();
    }
  }
}
