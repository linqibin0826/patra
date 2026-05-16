package dev.linqibin.patra.catalog.infra.adapter.integration.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.linqibin.patra.catalog.domain.port.enrichment.LetPubVenueData;
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
/// **测试范围**：
///
/// - 搜索结果解析（`parseSearchResult`）
/// - 限流检测（`isRateLimited`）
/// - 详情页解析委托（`parseDetailPage` → `LetPubDetailPageParser`）
///
/// 详情页各字段的详细解析测试见 {@link LetPubDetailPageParserTest}。
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
      String html = loadHtml("search-result.html");

      Optional<String> journalId = client.parseSearchResult(html);

      assertThat(journalId).isPresent().contains("10000");
    }

    @Test
    @DisplayName("应该忽略 journalid=0 的评论链接")
    void shouldIgnoreCommentLinks() {
      String html =
          """
          <html><body>
            <a href="index.php?journalid=0&page=journalapp&view=comment">评论</a>
          </body></html>
          """;

      Optional<String> journalId = client.parseSearchResult(html);

      assertThat(journalId).isEmpty();
    }

    @Test
    @DisplayName("搜索无结果时应返回 empty")
    void shouldReturnEmptyWhenNoResult() {
      String html = "<html><body><p>未找到匹配的期刊</p></body></html>";

      Optional<String> journalId = client.parseSearchResult(html);

      assertThat(journalId).isEmpty();
    }
  }

  @Nested
  @DisplayName("parseDetailPage() 委托测试")
  class ParseDetailPageTests {

    @Test
    @DisplayName("应正确委托给 LetPubDetailPageParser 并返回完整数据")
    void shouldDelegateToParser() {
      String html = loadHtml("detail-page.html");

      LetPubVenueData data = client.parseDetailPage(html, "10000");

      assertThat(data.basicInfo().letPubJournalId()).isEqualTo("10000");
      assertThat(data.basicInfo().letPubName()).isEqualTo("Nature");
      assertThat(data.jcrMetrics().jifQuartile()).isEqualTo("Q1");
      assertThat(data.casData().partitions())
          .hasSize(3)
          .extracting(LetPubVenueData.CasPartition::version)
          .containsExactly("2026年3月新锐版", "2025年3月升级版", "2023年12月旧的升级版");
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

    @Test
    @DisplayName("包含 '您使用的IP地址' 且内容短时应判定为限流（IP 封锁页）")
    void shouldDetectIpBlockKeyword() {
      String html = "<html><body><p>您使用的IP地址访问过于频繁，请稍后再试</p></body></html>";
      assertThat(client.isRateLimited(html)).isTrue();
    }

    @Test
    @DisplayName("包含 '注册或登录后，查看影响因子和历年趋势图' 且内容短时应判定为限流（软降级页）")
    void shouldDetectLoginPromptKeyword() {
      String html = "<html><body><p>注册或登录后，查看影响因子和历年趋势图</p></body></html>";
      assertThat(client.isRateLimited(html)).isTrue();
    }

    @Test
    @DisplayName("正常长页面即使偶然出现关键词也不应误判（长度阈值兜底）")
    void shouldNotMisclassifyLongPageContainingKeyword() {
      // 构造一个长度 >= RATE_LIMIT_THRESHOLD 的页面，内容里包含关键词作为 UX 文案
      String longPage =
          "<html><body>"
              + "<p>欢迎使用 LetPub 期刊查询系统</p>"
              + "您使用的IP地址访问过于频繁（这句话只是文档举例，不是真的限流）"
              + "x".repeat(2000)
              + "</body></html>";
      assertThat(client.isRateLimited(longPage)).isFalse();
    }
  }
}
