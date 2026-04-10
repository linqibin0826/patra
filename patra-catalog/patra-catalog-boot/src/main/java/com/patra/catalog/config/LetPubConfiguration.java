package com.patra.catalog.config;

import com.patra.catalog.infra.adapter.integration.letpub.LetPubScrapingClient;
import com.patra.starter.restclient.proxy.TunnelProxyConfigurer;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
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
@Configuration
public class LetPubConfiguration {

  private static final String LETPUB_BASE_URL = "https://www.letpub.com.cn";

  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

  /// 连接超时。
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  /// 读取超时（LetPub 页面加载可能较慢）。
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  /// 使用代理时的请求基础延迟（毫秒）。
  ///
  /// 代理模式下每次请求自动分配不同 IP，无需长时间等待。
  private static final long PROXIED_BASE_DELAY_MS = 500;

  /// 使用代理时的随机抖动（毫秒）。
  private static final long PROXIED_JITTER_MS = 500;

  /// 创建 LetPub 专用 RestClient。
  ///
  /// 当隧道代理可用时，使用 Apache HttpClient 5 的 {@code HttpComponentsClientHttpRequestFactory}
  /// 发送请求（通过代理转发）；否则使用 JDK HttpClient 直连。
  ///
  /// @param proxyConfigurer 隧道代理配置器（可选）
  @Bean
  public RestClient letPubRestClient(Optional<TunnelProxyConfigurer> proxyConfigurer) {
    var builder =
        RestClient.builder().baseUrl(LETPUB_BASE_URL).defaultHeader("User-Agent", USER_AGENT);

    if (proxyConfigurer.isPresent()) {
      builder.requestFactory(
          proxyConfigurer.get().createRequestFactory(CONNECT_TIMEOUT, READ_TIMEOUT));
    } else {
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
      return new LetPubScrapingClient(letPubRestClient, PROXIED_BASE_DELAY_MS, PROXIED_JITTER_MS);
    }
    return new LetPubScrapingClient(letPubRestClient);
  }
}
