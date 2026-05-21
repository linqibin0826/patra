package dev.linqibin.starter.restclient.proxy;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/// 隧道代理配置器。
///
/// 基于 Apache HttpClient 5 创建已配置代理的 {@link HttpComponentsClientHttpRequestFactory}，
/// 供需要代理的 RestClient 使用。
///
/// ## 为什么使用 Apache HttpClient 5
///
/// JDK 内置的 {@code java.net.http.HttpClient} 对部分隧道代理（如青果网络）的
/// HTTPS CONNECT 认证支持不完整。Apache HttpClient 5 提供完善的代理认证支持，
/// 且无需设置全局 {@link java.net.Authenticator} 或 JVM 系统属性。
///
/// ## 使用方式
///
/// ```java
/// var factory = configurer.createRequestFactory(
///     Duration.ofSeconds(10), Duration.ofSeconds(30));
/// var restClient = RestClient.builder()
///     .requestFactory(factory)
///     .build();
/// ```
///
/// @author linqibin
/// @since 0.1.0
public class TunnelProxyConfigurer {

  private final HttpHost proxyHost;
  private final BasicCredentialsProvider credentialsProvider;

  /// 创建隧道代理配置器。
  ///
  /// @param host 代理服务器地址（IP 或域名）
  /// @param port 代理服务器端口
  /// @param authKey 认证密钥
  /// @param authPwd 认证密码
  public TunnelProxyConfigurer(String host, int port, String authKey, String authPwd) {
    this.proxyHost = new HttpHost("http", host, port);
    this.credentialsProvider = new BasicCredentialsProvider();
    this.credentialsProvider.setCredentials(
        new AuthScope(proxyHost), new UsernamePasswordCredentials(authKey, authPwd.toCharArray()));
  }

  /// 获取代理主机信息。
  ///
  /// @return Apache HttpClient 代理主机
  public HttpHost getProxyHost() {
    return proxyHost;
  }

  /// 创建已配置代理的 HTTP 请求工厂（默认行为，不做额外定制）。
  ///
  /// @param connectTimeout 连接超时
  /// @param readTimeout 读取超时
  /// @return 已配置代理的请求工厂
  public HttpComponentsClientHttpRequestFactory createRequestFactory(
      Duration connectTimeout, Duration readTimeout) {
    return createRequestFactory(connectTimeout, readTimeout, builder -> {});
  }

  /// 创建已配置代理的 HTTP 请求工厂，并允许调用方对底层 {@link HttpClientBuilder} 做额外定制。
  ///
  /// 典型的定制场景（以爬虫为例）：
  ///
  /// - 禁用 cookie 管理：`builder.disableCookieManagement()`
  ///   避免跨请求携带相同会话标识被目标站点识别
  /// - 禁用连接复用：`builder.setConnectionReuseStrategy((req, resp, ctx) -> false)`
  ///   强制每次请求建立新的 TCP 连接，触发代理分配新的上游 IP
  /// - 自定义 UA 拦截器：`builder.addRequestInterceptorFirst(...)`
  ///
  /// @param connectTimeout 连接超时
  /// @param readTimeout 读取超时
  /// @param customizer 对底层 HttpClient 构建器的定制逻辑
  /// @return 已配置代理的请求工厂
  public HttpComponentsClientHttpRequestFactory createRequestFactory(
      Duration connectTimeout, Duration readTimeout, Consumer<HttpClientBuilder> customizer) {
    var connectionConfig =
        ConnectionConfig.custom()
            .setConnectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .setSocketTimeout((int) readTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .build();

    var connectionManager =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(connectionConfig)
            .build();

    var requestConfig =
        RequestConfig.custom()
            .setResponseTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .build();

    var builder =
        HttpClients.custom()
            .setProxy(proxyHost)
            .setDefaultCredentialsProvider(credentialsProvider)
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig);

    customizer.accept(builder);

    return new HttpComponentsClientHttpRequestFactory(builder.build());
  }
}
