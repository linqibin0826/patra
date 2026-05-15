package dev.linqibin.starter.restclient.config;

import dev.linqibin.starter.restclient.proxy.TunnelProxyConfigurer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;

/// 隧道代理自动配置。
///
/// 当 {@code patra.rest-client.proxy.tunnel.enabled=true} 时，
/// 创建 {@link TunnelProxyConfigurer} Bean。
///
/// ## 使用方式
///
/// 在需要代理的配置类中通过 {@code Optional<TunnelProxyConfigurer>} 注入：
///
/// ```java
/// @Bean
/// public RestClient scrapingRestClient(Optional<TunnelProxyConfigurer> proxyConfigurer) {
///     if (proxyConfigurer.isPresent()) {
///         var factory = proxyConfigurer.get().createRequestFactory(connectTimeout, readTimeout);
///         return RestClient.builder().requestFactory(factory).build();
///     }
///     // fallback: 直连
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(RestClientProperties.class)
@ConditionalOnProperty(
    prefix = "patra.rest-client.proxy.tunnel",
    name = "enabled",
    havingValue = "true")
public class TunnelProxyAutoConfiguration {

  /// 创建隧道代理配置器。
  ///
  /// @param properties RestClient 配置属性
  /// @return 隧道代理配置器
  /// @throws IllegalArgumentException 如果必填配置项缺失
  @Bean
  public TunnelProxyConfigurer tunnelProxyConfigurer(RestClientProperties properties) {
    var tunnel = properties.getProxy().getTunnel();

    Assert.hasText(tunnel.getHost(), "patra.rest-client.proxy.tunnel.host 未配置");
    Assert.isTrue(tunnel.getPort() > 0, "patra.rest-client.proxy.tunnel.port 必须大于 0");
    Assert.hasText(tunnel.getAuthKey(), "patra.rest-client.proxy.tunnel.auth-key 未配置");
    Assert.hasText(tunnel.getAuthPwd(), "patra.rest-client.proxy.tunnel.auth-pwd 未配置");

    log.info("隧道代理已启用: {}:{}", tunnel.getHost(), tunnel.getPort());
    return new TunnelProxyConfigurer(
        tunnel.getHost(), tunnel.getPort(), tunnel.getAuthKey(), tunnel.getAuthPwd());
  }
}
