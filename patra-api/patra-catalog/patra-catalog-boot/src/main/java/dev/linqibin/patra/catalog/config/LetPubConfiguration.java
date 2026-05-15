package dev.linqibin.patra.catalog.config;

import dev.linqibin.patra.catalog.infra.adapter.integration.letpub.LetPubScrapingClient;
import com.patra.starter.restclient.proxy.TunnelProxyConfigurer;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/// LetPub 集成配置。
///
/// 配置 LetPub 网站的 RestClient 和 LetPubScrapingClient Bean。
///
/// **设计说明**：
///
/// - LetPub 是外部网站（非 API），不经过 Spring Cloud LoadBalancer
/// - 使用独立的 RestClient 实例，配置较长读取超时（爬取页面可能较慢）
/// - LetPubScrapingClient 是纯 Java 类（非 `@Component`），由此配置类创建
/// - User-Agent 模拟普通浏览器访问，降低被反爬拦截的风险
/// - 支持隧道代理：启用后通过代理自动轮换 IP，大幅降低请求间隔
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Configuration
public class LetPubConfiguration {

  private static final String LETPUB_BASE_URL = "https://www.letpub.com.cn";

  /// 连接超时。
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  /// 读取超时。
  ///
  /// 说明：正常爬取单次 HTTP 调用 < 10s（实测 4-9s 往返），
  /// 若 >15s 无响应，大概率是 LetPub 的隐式限流（挂连接不给响应），
  /// 此时应尽快失败并触发重试（配合 `SOCKET_TIMEOUT` 可重试类别 + 代理 IP 轮换）。
  /// 早期设为 30s 是为了容忍慢页，但实测反而放大了单次失败的等待成本。
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

  /// 使用代理时的请求基础延迟（毫秒）。
  ///
  /// 说明：LetPub 的软限流不仅看 IP，还看会话行为时序。
  /// 实测此值决定单 ISSN 总耗时：800ms 基础 + 最多 1200ms 抖动
  /// （总计 0.8-2.0s 不规则分布），配合 UA 池与禁用 Keep-Alive 是速率与稳定性的折中。
  private static final long PROXIED_BASE_DELAY_MS = 800;

  /// 使用代理时的随机抖动（毫秒）。
  ///
  /// 抖动超过 1 秒有助于请求时序"去机器化"，避免固定周期被反爬识别。
  private static final long PROXIED_JITTER_MS = 1200;

  /// 创建 LetPub 专用 RestClient。
  ///
  /// 当隧道代理可用时，使用 Apache HttpClient 5 的 {@code HttpComponentsClientHttpRequestFactory}
  /// 发送请求（通过代理转发）；否则使用 JDK HttpClient 直连。
  ///
  /// @param proxyConfigurer 隧道代理配置器（可选）
  @Bean
  public RestClient letPubRestClient(Optional<TunnelProxyConfigurer> proxyConfigurer) {
    // 不设默认 User-Agent：LetPubScrapingClient 会按请求从 UA 池轮换，避免固定 UA 被指纹识别。
    var builder = RestClient.builder().baseUrl(LETPUB_BASE_URL);

    if (proxyConfigurer.isPresent()) {
      var proxyHost = proxyConfigurer.get().getProxyHost();
      log.info(
          "LetPub RestClient 已启用隧道代理（Apache HttpClient 5，禁用 cookie / 禁用连接复用）: {}:{}",
          proxyHost.getHostName(),
          proxyHost.getPort());
      // 爬虫专用定制：
      // 1. disableCookieManagement —— 禁用 cookie 持久化，避免跨请求携带相同会话指纹
      // 2. setConnectionReuseStrategy → false —— 禁用 HTTP Keep-Alive，强制每次请求新建 TCP 连接，
      //    促使隧道代理重新 CONNECT 并分配新的上游 IP
      builder.requestFactory(
          proxyConfigurer
              .get()
              .createRequestFactory(
                  CONNECT_TIMEOUT,
                  READ_TIMEOUT,
                  httpClientBuilder ->
                      httpClientBuilder
                          .disableCookieManagement()
                          .setConnectionReuseStrategy((req, resp, ctx) -> false)));
    } else {
      // 警告：JDK HttpClient 会默认读取 JVM 系统属性 https.proxyHost/Port，
      // 但其 HTTPS CONNECT 代理认证实现不完整，使用青果等隧道代理时会随机 503。
      // 如需代理，请设置环境变量 PROXY_TUNNEL_ENABLED=true + PROXY_AUTH_KEY + PROXY_AUTH_PWD
      // 使 TunnelProxyConfigurer Bean 生效（走 Apache HttpClient 5）。
      String jvmProxyHost = System.getProperty("https.proxyHost");
      if (jvmProxyHost != null && !jvmProxyHost.isBlank()) {
        log.warn(
            "⚠️  LetPub RestClient 使用 JDK HttpClient 直连，但检测到 JVM 系统属性 "
                + "https.proxyHost={}。JDK HttpClient 对 HTTPS CONNECT 代理认证支持不完整，"
                + "可能导致随机 'Tunnel failed, got: 503'。请改用 PROXY_TUNNEL_ENABLED=true 启用 "
                + "TunnelProxyConfigurer（Apache HttpClient 5）。",
            jvmProxyHost);
      } else {
        log.info("LetPub RestClient 使用 JDK HttpClient 直连（无代理）");
      }
      HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
      var factory = new JdkClientHttpRequestFactory(httpClient);
      factory.setReadTimeout(READ_TIMEOUT);
      builder.requestFactory(factory);
    }

    return builder.build();
  }

  /// 创建 LetPubScrapingClient Bean。
  ///
  /// 当隧道代理可用时，降低请求间隔（1.5-2.5s 代替 8-11s），
  /// 因为每次请求通过不同 IP 发出，被限流的风险大幅降低。
  ///
  /// @param letPubRestClient LetPub 专用 RestClient
  /// @param proxyConfigurer 隧道代理配置器（可选）
  /// @return LetPubScrapingClient 实例
  @Bean
  public LetPubScrapingClient letPubScrapingClient(
      RestClient letPubRestClient, Optional<TunnelProxyConfigurer> proxyConfigurer) {
    if (proxyConfigurer.isPresent()) {
      log.info(
          "LetPubScrapingClient 使用代理模式延迟：baseDelay={}ms, jitter={}ms",
          PROXIED_BASE_DELAY_MS,
          PROXIED_JITTER_MS);
      return new LetPubScrapingClient(letPubRestClient, PROXIED_BASE_DELAY_MS, PROXIED_JITTER_MS);
    }
    log.info("LetPubScrapingClient 使用直连模式延迟：baseDelay=8000ms, jitter=3000ms");
    return new LetPubScrapingClient(letPubRestClient);
  }
}
