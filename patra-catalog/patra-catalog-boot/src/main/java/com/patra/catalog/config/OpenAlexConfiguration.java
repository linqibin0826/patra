package com.patra.catalog.config;

import com.patra.catalog.infra.adapter.integration.openalex.OpenAlexSourcesClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/// OpenAlex 集成配置。
///
/// 配置 OpenAlex Sources API 的 RestClient 和 OpenAlexSourcesClient Bean。
///
/// **设计说明**：
///
/// - OpenAlex 是外部公共 API，不经过 Spring Cloud LoadBalancer
/// - 使用独立的 RestClient 实例，避免与内部服务的 RestClient 混淆
/// - OpenAlexSourcesClient 是纯 Java 类（非 `@Component`），由此配置类创建
/// - User-Agent 包含 `mailto:` 以进入 polite pool（100 req/s vs 匿名 10 req/s）
///
/// @author linqibin
/// @since 0.1.0
@Configuration
public class OpenAlexConfiguration {

  private static final String OPENALEX_BASE_URL = "https://api.openalex.org";
  private static final String USER_AGENT = "Patra/0.1 (mailto:contact@patra.dev; academic-catalog)";

  /// 连接超时
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

  /// 读取超时（REST API 响应较快，15s 足够）
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

  /// 创建 OpenAlex Sources API 专用 RestClient。
  ///
  /// 使用独立的 RestClient 实例，不经过 Spring Cloud LoadBalancer
  /// （OpenAlex 是外部公共 API，非注册中心托管服务）。
  /// 配置连接超时（5s）和读取超时（15s）。
  @Bean
  public RestClient openAlexRestClient() {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(READ_TIMEOUT);

    return RestClient.builder()
        .baseUrl(OPENALEX_BASE_URL)
        .defaultHeader("User-Agent", USER_AGENT)
        .requestFactory(requestFactory)
        .build();
  }

  /// 创建 OpenAlexSourcesClient Bean。
  ///
  /// @param openAlexRestClient OpenAlex 专用 RestClient
  /// @param objectMapper Jackson ObjectMapper（Spring Boot 自动配置）
  /// @return OpenAlexSourcesClient 实例
  @Bean
  public OpenAlexSourcesClient openAlexSourcesClient(
      RestClient openAlexRestClient, ObjectMapper objectMapper) {
    return new OpenAlexSourcesClient(openAlexRestClient, objectMapper);
  }
}
