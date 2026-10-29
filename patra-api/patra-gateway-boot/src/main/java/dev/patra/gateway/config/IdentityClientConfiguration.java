package dev.patra.gateway.config;

import dev.patra.identity.api.session.endpoint.SessionEndpoint;
import dev.patra.tool.httpclient.factory.RestClientFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/// identity 查会话接口的 HTTP Interface 代理（PAP-106）：网关每个带令牌的请求都经它查会话。
///
/// 走 Spring Cloud LoadBalancer 的服务发现（`lb://patra-identity`），不经网关自己的路由，所以拒绝名单对
/// `/_internal/**` 的 403 不影响它。分组 `identity` 的超时在 `application.yml` 的
/// `patra.http-client.http-interface.groups`。
@Configuration(proxyBeanMethods = false)
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

  /// 查会话接口代理。
  ///
  /// @param identityRestClient identity 的 RestClient
  /// @param factory RestClient 工厂
  /// @return 代理
  @Bean
  public SessionEndpoint sessionEndpoint(RestClient identityRestClient, RestClientFactory factory) {
    return factory.createProxy(identityRestClient, SessionEndpoint.class);
  }
}
