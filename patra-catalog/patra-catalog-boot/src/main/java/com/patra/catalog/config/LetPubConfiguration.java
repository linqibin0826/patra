package com.patra.catalog.config;

import com.patra.catalog.infra.adapter.integration.letpub.LetPubScrapingClient;
import java.net.http.HttpClient;
import java.time.Duration;
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

  /// 创建 LetPub 专用 RestClient。
  ///
  /// 使用独立的 RestClient 实例，不经过 Spring Cloud LoadBalancer
  /// （LetPub 是外部网站，非注册中心托管服务）。
  /// 配置连接超时（10s）和读取超时（30s），并设置浏览器 User-Agent。
  @Bean
  public RestClient letPubRestClient() {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(READ_TIMEOUT);

    return RestClient.builder()
        .baseUrl(LETPUB_BASE_URL)
        .defaultHeader("User-Agent", USER_AGENT)
        .requestFactory(requestFactory)
        .build();
  }

  /// 创建 LetPubScrapingClient Bean。
  ///
  /// @param letPubRestClient LetPub 专用 RestClient
  /// @return LetPubScrapingClient 实例
  @Bean
  public LetPubScrapingClient letPubScrapingClient(RestClient letPubRestClient) {
    return new LetPubScrapingClient(letPubRestClient);
  }
}
