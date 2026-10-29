package dev.patra.interaction.config;

import dev.patra.identity.api.account.endpoint.UserEndpoint;
import dev.patra.tool.httpclient.factory.RestClientFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/// identity 内部接口的 HTTP Interface 代理（PAP-113）：收藏夹所有者摘要、设公开的资格、公开收藏夹的可见性、
/// 注销清理；讨论的发表资格、作者与被 @ 的人的名片、@ 的用户名解析与联想（PAP-121）。
///
/// 走 Spring Cloud LoadBalancer 的服务发现（`lb://patra-identity`），不经网关。分组 `identity` 的超时在
/// `application.yml` 的 `patra.http-client.http-interface.groups`。
@Configuration
@ConditionalOnProperty(
    prefix = "patra.http-client.http-interface",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class IdentityClientConfiguration {

  /// identity 服务的 RestClient：负载均衡、分组超时、错误处理。
  ///
  /// @param loadBalancedBuilder 负载均衡的 RestClient.Builder
  /// @param factory RestClient 工厂
  /// @return RestClient
  @Bean
  public RestClient identityRestClient(
      @Qualifier("httpInterfaceLoadBalancedRestClientBuilder")
          RestClient.Builder loadBalancedBuilder,
      RestClientFactory factory) {
    return factory.createRestClient(loadBalancedBuilder, "identity", "lb://patra-identity");
  }

  /// 用户内部接口代理。
  ///
  /// @param identityRestClient identity 的 RestClient
  /// @param factory RestClient 工厂
  /// @return 代理
  @Bean
  public UserEndpoint userEndpoint(RestClient identityRestClient, RestClientFactory factory) {
    return factory.createProxy(identityRestClient, UserEndpoint.class);
  }
}
