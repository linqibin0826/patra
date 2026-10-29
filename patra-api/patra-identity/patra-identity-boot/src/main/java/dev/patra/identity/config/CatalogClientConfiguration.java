package dev.patra.identity.config;

import dev.patra.catalog.api.mesh.endpoint.MeshEndpoint;
import dev.patra.catalog.api.organization.endpoint.OrganizationEndpoint;
import dev.patra.tool.httpclient.factory.RestClientFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/// catalog 内部接口的 HTTP Interface 代理（PAP-95）：保存资料时校验 ROR ID / MeSH UI 并取名称快照。
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

  /// catalog 服务的 RestClient：负载均衡、分组超时、错误处理、身份断言转发。
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

  /// 机构内部接口代理。
  ///
  /// @param catalogRestClient catalog 的 RestClient
  /// @param factory RestClient 工厂
  /// @return 代理
  @Bean
  public OrganizationEndpoint organizationEndpoint(
      RestClient catalogRestClient, RestClientFactory factory) {
    return factory.createProxy(catalogRestClient, OrganizationEndpoint.class);
  }

  /// MeSH 内部接口代理。
  ///
  /// @param catalogRestClient catalog 的 RestClient
  /// @param factory RestClient 工厂
  /// @return 代理
  @Bean
  public MeshEndpoint meshEndpoint(RestClient catalogRestClient, RestClientFactory factory) {
    return factory.createProxy(catalogRestClient, MeshEndpoint.class);
  }
}
