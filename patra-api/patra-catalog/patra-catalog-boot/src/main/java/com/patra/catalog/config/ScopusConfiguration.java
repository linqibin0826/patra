package com.patra.catalog.config;

import com.patra.catalog.infra.adapter.integration.scopus.ScopusApiClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/// Scopus 集成配置。
///
/// 配置 Scopus Serial Title API 的 RestClient 和 ScopusApiClient Bean。
///
/// **设计说明**：
///
/// - Scopus 是外部 Elsevier API，需要 API Key 认证
/// - API Key 通过 `X-ELS-APIKey` 请求头传递（环境变量注入，不硬编码）
/// - 使用独立的 RestClient 实例，不经过 Spring Cloud LoadBalancer
/// - ScopusApiClient 是纯 Java 类（非 `@Component`），由此配置类创建
///
/// @author linqibin
/// @since 0.1.0
@Configuration
public class ScopusConfiguration {

  private static final String SCOPUS_BASE_URL = "https://api.elsevier.com";

  /// 连接超时
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

  /// 读取超时（Scopus API 响应较快，15s 足够）
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

  /// 创建 Scopus API 专用 RestClient。
  ///
  /// 使用独立的 RestClient 实例，通过 `X-ELS-APIKey` 请求头传递 API Key。
  /// 配置连接超时（5s）和读取超时（15s）。
  @Bean
  public RestClient scopusRestClient(@Value("${patra.catalog.scopus.api-key}") String apiKey) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(READ_TIMEOUT);

    return RestClient.builder()
        .baseUrl(SCOPUS_BASE_URL)
        .defaultHeader("X-ELS-APIKey", apiKey)
        .defaultHeader("Accept", "application/json")
        .requestFactory(requestFactory)
        .build();
  }

  /// 创建 ScopusApiClient Bean。
  ///
  /// @param scopusRestClient Scopus 专用 RestClient
  /// @param objectMapper Jackson ObjectMapper（Spring Boot 自动配置）
  /// @return ScopusApiClient 实例
  @Bean
  public ScopusApiClient scopusApiClient(RestClient scopusRestClient, ObjectMapper objectMapper) {
    return new ScopusApiClient(scopusRestClient, objectMapper);
  }
}
