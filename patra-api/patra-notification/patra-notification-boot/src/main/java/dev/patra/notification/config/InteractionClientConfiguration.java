package dev.patra.notification.config;

import dev.patra.interaction.api.post.endpoint.PostEndpoint;
import dev.patra.interaction.api.subject.endpoint.SubjectEndpoint;
import dev.patra.tool.httpclient.factory.RestClientFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/// interaction 内部接口的 HTTP Interface 代理：提醒列表与提醒邮件读时现查对象视图与经验结果（PAP-143）。
///
/// 走 Spring Cloud LoadBalancer 的服务发现（`lb://patra-interaction`），不经网关。分组 `interaction` 的超时在
/// `application.yml` 的 `patra.http-client.http-interface.groups`。写法同 `IdentityClientConfiguration`。
@Configuration
@ConditionalOnProperty(
    prefix = "patra.http-client.http-interface",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class InteractionClientConfiguration {

  /// interaction 服务的 RestClient：负载均衡、分组超时、错误处理。
  ///
  /// @param loadBalancedBuilder 负载均衡的 RestClient.Builder
  /// @param factory RestClient 工厂
  /// @return RestClient
  @Bean
  public RestClient interactionRestClient(
      @Qualifier("httpInterfaceLoadBalancedRestClientBuilder")
          RestClient.Builder loadBalancedBuilder,
      RestClientFactory factory) {
    return factory.createRestClient(loadBalancedBuilder, "interaction", "lb://patra-interaction");
  }

  /// 对象视图接口代理。
  ///
  /// @param interactionRestClient interaction 的 RestClient
  /// @param factory RestClient 工厂
  /// @return 代理
  @Bean
  public SubjectEndpoint subjectEndpoint(
      RestClient interactionRestClient, RestClientFactory factory) {
    return factory.createProxy(interactionRestClient, SubjectEndpoint.class);
  }

  /// 经验结果接口代理。
  ///
  /// @param interactionRestClient interaction 的 RestClient
  /// @param factory RestClient 工厂
  /// @return 代理
  @Bean
  public PostEndpoint postEndpoint(RestClient interactionRestClient, RestClientFactory factory) {
    return factory.createProxy(interactionRestClient, PostEndpoint.class);
  }
}
