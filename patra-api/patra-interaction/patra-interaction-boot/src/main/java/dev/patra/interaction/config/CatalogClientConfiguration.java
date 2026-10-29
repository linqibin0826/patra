package dev.patra.interaction.config;

import dev.patra.catalog.api.publication.endpoint.PublicationEndpoint;
import dev.patra.catalog.api.venue.endpoint.VenueEndpoint;
import dev.patra.tool.httpclient.factory.RestClientFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/// catalog 内部接口的 HTTP Interface 代理：对象登记表首次解析文献（PAP-112）与期刊（PAP-120），每日按 PMID、
/// NLM ID 对账。两个代理共用同一个 `RestClient`。
///
/// 走 Spring Cloud LoadBalancer 的服务发现（`lb://patra-catalog`），不经网关，所以网关对 `/_internal/**`
/// 的拒绝名单不影响它。分组 `catalog` 的超时在 `application.yml` 的 `patra.http-client.http-interface.groups`。
@Configuration
@ConditionalOnProperty(
    prefix = "patra.http-client.http-interface",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CatalogClientConfiguration {

  /// catalog 服务的 RestClient：负载均衡、分组超时、错误处理。
  ///
  /// @param loadBalancedBuilder 负载均衡的 RestClient.Builder
  /// @param factory RestClient 工厂
  /// @return RestClient
  @Bean
  public RestClient catalogRestClient(
      @Qualifier("httpInterfaceLoadBalancedRestClientBuilder")
          RestClient.Builder loadBalancedBuilder,
      RestClientFactory factory) {
    return factory.createRestClient(loadBalancedBuilder, "catalog", "lb://patra-catalog");
  }

  /// 文献内部接口代理。
  ///
  /// @param catalogRestClient catalog 的 RestClient
  /// @param factory RestClient 工厂
  /// @return 代理
  @Bean
  public PublicationEndpoint publicationEndpoint(
      RestClient catalogRestClient, RestClientFactory factory) {
    return factory.createProxy(catalogRestClient, PublicationEndpoint.class);
  }

  /// 期刊内部接口代理。
  ///
  /// @param catalogRestClient catalog 的 RestClient
  /// @param factory RestClient 工厂
  /// @return 代理
  @Bean
  public VenueEndpoint venueEndpoint(RestClient catalogRestClient, RestClientFactory factory) {
    return factory.createProxy(catalogRestClient, VenueEndpoint.class);
  }
}
