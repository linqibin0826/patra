package com.patra.catalog.config;

import com.patra.catalog.infra.adapter.integration.wikidata.WikidataSparqlClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/// Wikidata 集成配置。
///
/// 配置 Wikidata SPARQL 端点的 RestClient 和 WikidataSparqlClient Bean。
///
/// **设计说明**：
///
/// - Wikidata 是外部公共 API，不经过 Spring Cloud LoadBalancer
/// - 使用独立的 RestClient 实例，避免与内部服务的 RestClient 混淆
/// - WikidataSparqlClient 是纯 Java 类（非 `@Component`），由此配置类创建
/// - 配置连接和读取超时，防止线程阻塞
///
/// @author linqibin
/// @since 0.1.0
@Configuration
public class WikidataConfiguration {

  private static final String WIKIDATA_SPARQL_ENDPOINT = "https://query.wikidata.org";
  private static final String USER_AGENT = "Patra/0.1 (academic-catalog; patra-api)";

  /// 连接超时
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

  /// 读取超时（SPARQL 查询可能较慢，特别是大批量查询）
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  /// 创建 Wikidata SPARQL 端点专用 RestClient。
  ///
  /// 使用独立的 RestClient 实例，不经过 Spring Cloud LoadBalancer
  /// （Wikidata 是外部公共 API，非注册中心托管服务）。
  /// 配置连接超时（5s）和读取超时（30s）。
  @Bean
  public RestClient wikidataRestClient() {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(READ_TIMEOUT);

    return RestClient.builder()
        .baseUrl(WIKIDATA_SPARQL_ENDPOINT)
        .defaultHeader("User-Agent", USER_AGENT)
        .requestFactory(requestFactory)
        .build();
  }

  /// 创建 WikidataSparqlClient Bean。
  ///
  /// @param wikidataRestClient Wikidata 专用 RestClient
  /// @param objectMapper Jackson ObjectMapper（Spring Boot 自动配置）
  /// @return WikidataSparqlClient 实例
  @Bean
  public WikidataSparqlClient wikidataSparqlClient(
      RestClient wikidataRestClient, ObjectMapper objectMapper) {
    return new WikidataSparqlClient(wikidataRestClient, objectMapper);
  }
}
