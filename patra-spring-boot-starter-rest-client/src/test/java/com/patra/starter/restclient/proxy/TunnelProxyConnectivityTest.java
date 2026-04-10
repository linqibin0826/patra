package com.patra.starter.restclient.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/// 隧道代理连通性测试。
///
/// 需要环境变量 `PROXY_AUTH_KEY` 和 `PROXY_AUTH_PWD`，未设置时自动跳过。
@DisplayName("隧道代理连通性测试")
class TunnelProxyConnectivityTest {

  private static final String PROXY_HOST = "112.49.20.234";
  private static final int PROXY_PORT = 15057;
  private static final String TEST_URL = "https://test.ipw.cn";

  private static String authKey;
  private static String authPwd;

  @BeforeAll
  static void checkEnvVars() {
    authKey = System.getenv("PROXY_AUTH_KEY");
    authPwd = System.getenv("PROXY_AUTH_PWD");
    assumeTrue(
        authKey != null && !authKey.isBlank() && authPwd != null && !authPwd.isBlank(),
        "跳过：未设置 PROXY_AUTH_KEY / PROXY_AUTH_PWD 环境变量");
  }

  @Test
  @DisplayName("通过隧道代理应该能访问 HTTPS 站点")
  void shouldConnectThroughProxy() {
    var configurer = new TunnelProxyConfigurer(PROXY_HOST, PROXY_PORT, authKey, authPwd);
    var factory = configurer.createRequestFactory(Duration.ofSeconds(15), Duration.ofSeconds(15));

    var restClient = RestClient.builder().requestFactory(factory).build();

    String body = restClient.get().uri(TEST_URL).retrieve().body(String.class);

    System.out.println("代理出口 IP: " + body.trim());
    assertThat(body).isNotBlank();
  }
}
