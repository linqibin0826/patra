package dev.patra.catalog.config;

import dev.patra.catalog.infra.fulltext.config.FullTextSourceProperties;
import dev.patra.catalog.infra.fulltext.source.PmcCloudArticleSourceAdapter;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/// PMC Cloud Service 客户端配置（公开 S3 桶，不走隧道代理）。
///
/// @author linqibin
/// @since 0.11.0
@Configuration
public class PmcConfiguration {

  private static final String USER_AGENT = "Patra/0.11 (+https://github.com/linqibin0826/patra)";

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

  private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

  /// 访问 PMC 桶的 RestClient。
  ///
  /// @param properties 全文数据源配置
  /// @return RestClient
  @Bean
  public RestClient pmcRestClient(FullTextSourceProperties properties) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder()
        .baseUrl(properties.pmc().baseUrl())
        .defaultHeader("User-Agent", USER_AGENT)
        .requestFactory(requestFactory)
        .build();
  }

  /// PMC 文章读取适配器。
  ///
  /// @param pmcRestClient PMC RestClient
  /// @param jsonMapper JSON 读取器
  /// @param properties 全文数据源配置
  /// @return 适配器
  @Bean
  public PmcCloudArticleSourceAdapter pmcCloudArticleSourceAdapter(
      RestClient pmcRestClient, JsonMapper jsonMapper, FullTextSourceProperties properties) {
    return new PmcCloudArticleSourceAdapter(pmcRestClient, jsonMapper, properties.pmc().baseUrl());
  }
}
