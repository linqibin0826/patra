package dev.linqibin.patra.catalog.infra.adapter.integration.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.linqibin.patra.catalog.domain.port.enrichment.LetPubVenueData;
import dev.linqibin.starter.restclient.proxy.TunnelProxyConfigurer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.client.RestClient;

/// LetPub 隧道代理连通性测试。
///
/// 通过隧道代理访问 LetPub 网站，验证代理模式下的爬取和解析流程。
///
/// **运行条件**：需设置环境变量 `PROXY_AUTH_KEY` 和 `PROXY_AUTH_PWD`，未设置时自动跳过。
///
/// **运行方式**：
///
/// ```shell
/// ./gradlew :patra-catalog:patra-catalog-infra:test \
///   --tests="*.LetPubProxyConnectivityTest"
/// ```
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LetPub 隧道代理连通性测试")
class LetPubProxyConnectivityTest {

  private static final String LETPUB_BASE_URL = "https://www.letpub.com.cn";

  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

  /// 使用 IP 而非域名，避免 Clash fake-ip DNS 劫持。
  private static final String PROXY_HOST = "112.49.20.234";

  private static final int PROXY_PORT = 15057;

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  /// 代理模式下的请求延迟（每次请求不同出口 IP，无需长等待）。
  private static final long PROXIED_BASE_DELAY_MS = 500;

  private static final long PROXIED_JITTER_MS = 500;

  private static String authKey;
  private static String authPwd;

  private LetPubEnrichmentAdapter adapter;

  @BeforeAll
  static void checkEnvVars() {
    authKey = System.getenv("PROXY_AUTH_KEY");
    authPwd = System.getenv("PROXY_AUTH_PWD");
    assumeTrue(
        authKey != null && !authKey.isBlank() && authPwd != null && !authPwd.isBlank(),
        "跳过：未设置 PROXY_AUTH_KEY / PROXY_AUTH_PWD 环境变量");
  }

  @BeforeEach
  void setUp() {
    var configurer = new TunnelProxyConfigurer(PROXY_HOST, PROXY_PORT, authKey, authPwd);
    var factory = configurer.createRequestFactory(CONNECT_TIMEOUT, READ_TIMEOUT);

    RestClient restClient =
        RestClient.builder()
            .baseUrl(LETPUB_BASE_URL)
            .defaultHeader("User-Agent", USER_AGENT)
            .requestFactory(factory)
            .build();

    var scrapingClient =
        new LetPubScrapingClient(restClient, PROXIED_BASE_DELAY_MS, PROXIED_JITTER_MS);
    adapter = new LetPubEnrichmentAdapter(scrapingClient);
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  @DisplayName("通过代理查询 Nature（ISSN-L: 0028-0836）— 验证代理模式下的完整爬取流程")
  void shouldFindNatureByIssnThroughProxy() {
    // When
    Optional<LetPubVenueData> result = adapter.findByIssn("0028-0836");

    // Then
    assertThat(result).isPresent();
    LetPubVenueData data = result.get();

    System.out.println();
    System.out.println("╔══════════════════════════════════════");
    System.out.println("║ Nature（代理模式）LetPub 数据");
    System.out.println("╠══════════════════════════════════════");
    System.out.println("║ 期刊名:          " + data.basicInfo().letPubName());
    System.out.println("║ LetPub ID:       " + data.basicInfo().letPubJournalId());
    System.out.println("║ JIF 分区:        " + data.jcrMetrics().jifQuartile());
    System.out.println("║ CAS 版本数:      " + data.casData().partitions().size());
    data.casData()
        .partitions()
        .forEach(
            p ->
                System.out.println(
                    "║   - " + p.version() + ": " + p.majorCategory() + " " + p.majorQuartile()));
    System.out.println("║ IF 趋势年份数:   " + data.jcrMetrics().impactFactorTrend().size());
    System.out.println("╚══════════════════════════════════════");

    assertThat(data.basicInfo().letPubName()).isNotBlank();
    assertThat(data.basicInfo().letPubJournalId()).isNotBlank();
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  @DisplayName("通过代理查询不存在的 ISSN — 应返回 empty")
  void shouldReturnEmptyForUnknownIssnThroughProxy() {
    // When
    Optional<LetPubVenueData> result = adapter.findByIssn("9999-9999");

    // Then
    assertThat(result).isEmpty();
    System.out.println(">>> 代理模式：不存在的 ISSN 正确返回 Optional.empty()");
  }
}
