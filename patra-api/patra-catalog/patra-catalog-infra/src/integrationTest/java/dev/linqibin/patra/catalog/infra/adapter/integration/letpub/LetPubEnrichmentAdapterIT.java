package dev.linqibin.patra.catalog.infra.adapter.integration.letpub;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.catalog.domain.port.enrichment.LetPubVenueData;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/// LetPubEnrichmentAdapter 手动集成测试（真实 HTTP）。
///
/// **用途**：本地调试，验证 LetPub 网站的真实爬取流程与 HTML 解析结果。
///
/// **运行方式**：
///
/// ```shell
/// ./gradlew :patra-catalog:patra-catalog-infra:test \
///   --tests="*.LetPubEnrichmentAdapterIT.shouldFindNatureByIssn"
/// ```
///
/// **延迟设置**：调试时使用 2s 基础延迟（生产为 8s），减少等待时间。
///
/// @author linqibin
/// @since 0.1.0
@Disabled("手动调试测试，通过 --tests 指定运行，不应在 CI 中自动执行")
@DisplayName("LetPubEnrichmentAdapter 手动集成测试（真实 HTTP）")
class LetPubEnrichmentAdapterIT {

  /// 连接超时。
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  /// 读取超时。
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  /// 调试用基础延迟（缩短为 2s，保持对网站的基本礼貌访问）。
  private static final long DEBUG_BASE_DELAY_MS = 2000;

  /// 随机抖动（毫秒）。
  private static final long DEBUG_JITTER_MS = 500;

  private LetPubEnrichmentAdapter adapter;

  @BeforeEach
  void setUp() {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(READ_TIMEOUT);

    RestClient restClient =
        RestClient.builder()
            .baseUrl("https://www.letpub.com.cn")
            .defaultHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .requestFactory(requestFactory)
            .build();

    // 使用包私有测试构造器，缩短延迟以便快速调试
    LetPubScrapingClient scrapingClient =
        new LetPubScrapingClient(restClient, DEBUG_BASE_DELAY_MS, DEBUG_JITTER_MS);
    adapter = new LetPubEnrichmentAdapter(scrapingClient);
  }

  @Test
  // 真实网络调用，方法级超时覆盖全局 fallback
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  @DisplayName("查询 Nature（ISSN-L: 0028-0836）— 验证完整字段解析")
  void shouldFindNatureByIssn() {
    // When
    Optional<LetPubVenueData> result = adapter.findByIssn("0028-0836");

    // Then
    assertThat(result).isPresent();
    LetPubVenueData data = result.get();

    printData("Nature", data);

    assertThat(data.basicInfo().letPubName()).isNotBlank();
    assertThat(data.basicInfo().letPubJournalId()).isNotBlank();
    assertThat(data.jcrMetrics().jifQuartile()).isNotBlank();
    assertThat(data.casData().partitions()).isNotEmpty();
    assertThat(data.casData().partitions().getFirst().majorQuartile()).isNotBlank();
  }

  @Test
  // 真实网络调用，方法级超时覆盖全局 fallback
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  @DisplayName("查询 Science（ISSN-L: 0036-8075）— 验证多分区期刊解析")
  void shouldFindScienceByIssn() {
    // When
    Optional<LetPubVenueData> result = adapter.findByIssn("0036-8075");

    // Then
    assertThat(result).isPresent();
    LetPubVenueData data = result.get();

    printData("Science", data);

    assertThat(data.basicInfo().letPubName()).isNotBlank();
    assertThat(data.casData().partitions()).isNotEmpty();
    assertThat(data.casData().partitions().getFirst().majorCategory()).isNotBlank();
  }

  @Test
  // 真实网络调用，方法级超时覆盖全局 fallback
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  @DisplayName("查询不存在的 ISSN（9999-9999）— 应返回 empty")
  void shouldReturnEmptyForUnknownIssn() {
    // When
    Optional<LetPubVenueData> result = adapter.findByIssn("9999-9999");

    // Then
    assertThat(result).isEmpty();
    System.out.println(">>> 不存在的 ISSN 正确返回 Optional.empty()");
  }

  @Test
  // 真实网络调用，方法级超时覆盖全局 fallback
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  @DisplayName("查询空白 ISSN — 应返回 empty（不发起 HTTP 请求）")
  void shouldReturnEmptyForBlankIssn() {
    // When
    Optional<LetPubVenueData> result = adapter.findByIssn("   ");

    // Then
    assertThat(result).isEmpty();
    System.out.println(">>> 空白 ISSN 正确返回 Optional.empty()（短路，无 HTTP 请求）");
  }

  /// 打印期刊数据到控制台，便于调试对比。
  private void printData(String label, LetPubVenueData data) {
    System.out.println();
    System.out.println("╔══════════════════════════════════════");
    System.out.printf("║ %s LetPub 数据%n", label);
    System.out.println("╠══════════════════════════════════════");
    System.out.println("║ 期刊名:          " + data.basicInfo().letPubName());
    System.out.println("║ LetPub ID:       " + data.basicInfo().letPubJournalId());
    System.out.println("║ 研究方向:        " + data.basicInfo().researchDirection());
    System.out.println("║ 年文章数:        " + data.basicInfo().articlesPerYear());
    System.out.println("╠══ JCR / WOS ═════════════════════════");
    System.out.println("║ WOS 综合分区:    " + data.jcrMetrics().wosOverallQuartile());
    System.out.println("║ ─ JIF ─");
    System.out.println("║ JIF 学科:        " + data.jcrMetrics().jcrSubject());
    System.out.println("║ JIF 收录:        " + data.jcrMetrics().jcrCollection());
    System.out.println(
        "║ JIF 分区:        "
            + data.jcrMetrics().jifQuartile()
            + "  排名: "
            + data.jcrMetrics().jifRank()
            + "  百分位: "
            + data.jcrMetrics().jifPercentile());
    System.out.println("║ ─ JCI ─");
    System.out.println("║ JCI 学科:        " + data.jcrMetrics().jciSubject());
    System.out.println("║ JCI 收录:        " + data.jcrMetrics().jciCollection());
    System.out.println(
        "║ JCI 分区:        "
            + data.jcrMetrics().jciQuartile()
            + "  排名: "
            + data.jcrMetrics().jciRank()
            + "  百分位: "
            + data.jcrMetrics().jciPercentile());
    System.out.println("║ JCI 数值:        " + data.jcrMetrics().jciValue());
    System.out.println("║ 自引率:          " + data.jcrMetrics().selfCitationRate());
    System.out.println("╠══ CAS ═══════════════════════════════");
    System.out.println("║ 版本数:          " + data.casData().partitions().size());
    data.casData()
        .partitions()
        .forEach(
            p -> {
              System.out.println("║ ─── " + p.version() + " ───");
              System.out.println("║   大类: " + p.majorCategory() + " " + p.majorQuartile());
              System.out.println("║   小类: " + p.minorSubject() + " " + p.minorQuartile());
              System.out.println("║   Top期刊: " + p.topJournal() + ", 综述: " + p.reviewJournal());
            });
    System.out.println("╠══ 预警名单（按版本时间序列） ══════════");
    if (data.casData().warnings() != null && !data.casData().warnings().isEmpty()) {
      data.casData()
          .warnings()
          .forEach(
              w ->
                  System.out.println(
                      "║   "
                          + w.publishedYear()
                          + (w.publishedMonth() != null ? "-" + w.publishedMonth() : "")
                          + " "
                          + w.editionLabel()
                          + ": "
                          + (w.inWarningList() ? "⚠ 预警" : "✓ 不在预警")
                          + (w.warningLevel() != null ? " [" + w.warningLevel() + "]" : "")));
    } else {
      System.out.println("║   (无预警名单数据)");
    }
    System.out.println("╠══ 其他 ══════════════════════════════");
    System.out.println("║ 审稿速度(官网):  " + data.submissionInfo().reviewSpeedOfficial());
    System.out.println("║ 审稿速度(网友):  " + data.submissionInfo().reviewSpeedUser());
    System.out.println("║ 录用率:          " + data.submissionInfo().acceptanceRate());
    System.out.println("║ APC:             " + data.submissionInfo().apcInfo());
    System.out.println("║ 收录数据库:      " + data.basicInfo().indexedIn());
    System.out.println("╚══════════════════════════════════════");
    System.out.println();
  }
}
