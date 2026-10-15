package dev.patra.tool.httpclient.autoconfigure;

import dev.patra.tool.httpclient.proxy.TunnelProxyConfigurer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;

/// 隧道代理自动配置。
///
/// 当 {@code patra.http-client.proxy.tunnel.enabled=true} 时，
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
@AutoConfiguration(after = HttpClientAutoConfiguration.class)
@EnableConfigurationProperties(HttpClientProperties.class)
@ConditionalOnProperty(
    prefix = "patra.http-client",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnProperty(
    prefix = "patra.http-client.proxy.tunnel",
    name = "enabled",
    havingValue = "true")
public final class HttpClientProxyAutoConfiguration {

  /// 创建隧道代理配置器。
  ///
  /// @param properties RestClient 配置属性
  /// @return 隧道代理配置器
  /// @throws IllegalArgumentException 如果必填配置项缺失
  @Bean
  public TunnelProxyConfigurer tunnelProxyConfigurer(HttpClientProperties properties) {
    var tunnel = properties.proxy().tunnel();

    Assert.hasText(tunnel.host(), "patra.http-client.proxy.tunnel.host 未配置");
    Assert.isTrue(tunnel.port() > 0, "patra.http-client.proxy.tunnel.port 必须大于 0");
    Assert.hasText(tunnel.authKey(), "patra.http-client.proxy.tunnel.auth-key 未配置");
    Assert.hasText(tunnel.authPwd(), "patra.http-client.proxy.tunnel.auth-pwd 未配置");

    log.info("隧道代理已启用: {}:{}", tunnel.host(), tunnel.port());
    return new TunnelProxyConfigurer(
        tunnel.host(), tunnel.port(), tunnel.authKey(), tunnel.authPwd());
  }
}
