package dev.patra.analytics.config;

import dev.patra.catalog.api.publication.endpoint.PublicationEndpoint;
import dev.patra.catalog.api.publication.endpoint.PublicationNeighborEndpoint;
import dev.patra.tool.httpclient.factory.RestClientFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/// catalog 内部接口的 HTTP Interface 代理：热读与看过还看了核对文献（`PublicationEndpoint.resolveByIds`）、
/// 取最新收录与同主题邻居（`PublicationNeighborEndpoint`）。
///
/// 走 Spring Cloud LoadBalancer 的服务发现（`lb://patra-catalog`），不经网关。分组 `catalog` 的超时在
/// `application.yml` 的 `patra.http-client.http-interface.groups`。写法同 [IdentityClientConfiguration]。
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

  /// 文献解析接口代理。
  ///
  /// @param catalogRestClient catalog 的 RestClient
  /// @param factory RestClient 工厂
  /// @return 代理
  @Bean
  public PublicationEndpoint publicationEndpoint(
      RestClient catalogRestClient, RestClientFactory factory) {
    return factory.createProxy(catalogRestClient, PublicationEndpoint.class);
  }

  /// 文献邻居接口代理。
  ///
  /// @param catalogRestClient catalog 的 RestClient
  /// @param factory RestClient 工厂
  /// @return 代理
  @Bean
  public PublicationNeighborEndpoint publicationNeighborEndpoint(
      RestClient catalogRestClient, RestClientFactory factory) {
    return factory.createProxy(catalogRestClient, PublicationNeighborEndpoint.class);
  }
}
