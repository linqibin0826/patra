package com.patra.starter.restclient.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/// TunnelProxyConfigurer 单元测试。
///
/// 验证隧道代理配置器正确创建已配置代理的请求工厂。
@DisplayName("TunnelProxyConfigurer 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class TunnelProxyConfigurerTest {

  private static final String PROXY_HOST = "tunnel.qg.net";
  private static final int PROXY_PORT = 15561;
  private static final String AUTH_KEY = "testAuthKey";
  private static final String AUTH_PWD = "testAuthPwd";

  @Test
  @DisplayName("createRequestFactory 应该返回 HttpComponentsClientHttpRequestFactory")
  void createRequestFactory_shouldReturnHttpComponentsFactory() {
    var configurer = new TunnelProxyConfigurer(PROXY_HOST, PROXY_PORT, AUTH_KEY, AUTH_PWD);

    var factory = configurer.createRequestFactory(Duration.ofSeconds(10), Duration.ofSeconds(30));

    assertThat(factory).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
  }

  @Test
  @DisplayName("getProxy 应该返回已配置的代理地址")
  void getProxy_shouldReturnConfiguredProxyHost() {
    var configurer = new TunnelProxyConfigurer(PROXY_HOST, PROXY_PORT, AUTH_KEY, AUTH_PWD);

    var proxy = configurer.getProxyHost();

    assertThat(proxy.getHostName()).isEqualTo(PROXY_HOST);
    assertThat(proxy.getPort()).isEqualTo(PROXY_PORT);
    assertThat(proxy.getSchemeName()).isEqualTo("http");
  }
}
