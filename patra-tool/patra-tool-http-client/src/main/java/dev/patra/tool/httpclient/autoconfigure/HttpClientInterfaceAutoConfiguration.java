package dev.patra.tool.httpclient.autoconfigure;

import dev.patra.tool.httpclient.error.ProblemDetailErrorHandler;
import dev.patra.tool.httpclient.factory.RestClientFactory;
import dev.patra.tool.httpclient.interceptor.InternalCallInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/// HTTP Interface 自动配置：注册错误处理器和 RestClient 自定义器
///
/// 此配置在以下条件下激活：
///
/// - 类路径存在 `RestClient` 和 `ProblemDetail`
/// - 配置属性 `patra.http-client.http-interface.enabled` 为 `true`（默认）
///
/// **使用 Apache HttpClient 5.x 替代 JDK HttpClient**
///
/// 解决 "HTTP/1.1 header parser received no bytes" stale connection 问题：
///
/// - `validateAfterInactivity`：使用前验证连接有效性
/// - `evictIdleConnections`：主动清理空闲连接
/// - `evictExpiredConnections`：清理过期连接
///
/// **注册的 Bean：**
///
/// - {@link ProblemDetailErrorHandler} - RFC 7807 错误响应处理器
/// - {@link RestClientCustomizer} - RestClient.Builder 自定义器
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@AutoConfiguration(after = HttpClientAutoConfiguration.class)
@EnableConfigurationProperties(HttpClientProperties.class)
@ConditionalOnClass(
    name = {"org.springframework.web.client.RestClient", "org.springframework.http.ProblemDetail"})
@ConditionalOnProperty(
    prefix = "patra.http-client",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnProperty(
    prefix = "patra.http-client.http-interface",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public final class HttpClientInterfaceAutoConfiguration {

  /// 注册 ProblemDetail 错误处理器 Bean
  ///
  /// 创建支持 RFC 7807 ProblemDetail 格式的错误处理器，配置容错模式。
  ///
  /// @param jsonMapper 容器里的 `JsonMapper`
  /// @param properties HTTP Interface 配置属性
  /// @return 错误处理器实例
  @Bean
  @ConditionalOnMissingBean
  public ProblemDetailErrorHandler problemDetailErrorHandler(
      JsonMapper jsonMapper, HttpClientProperties properties) {
    log.info(
        "配置 ProblemDetailErrorHandler (容错模式: {})",
        properties.httpInterface().errorHandling().tolerant());
    return new ProblemDetailErrorHandler(jsonMapper, properties.httpInterface().errorHandling());
  }

  /// 注册 RestClient 自定义器
  ///
  /// 自定义所有 RestClient.Builder 实例，添加 ProblemDetailErrorHandler 处理错误响应。
  ///
  /// > **注意**：TraceId 传播由 OpenTelemetry Java Agent 自动处理，无需手动拦截器。
  ///
  /// @param errorHandlerProvider 错误处理器提供者
  /// @param properties HTTP Interface 配置属性
  /// @return RestClient 自定义器
  @Bean
  @ConditionalOnMissingBean(name = "httpInterfaceRestClientCustomizer")
  public RestClientCustomizer httpInterfaceRestClientCustomizer(
      ObjectProvider<ProblemDetailErrorHandler> errorHandlerProvider,
      HttpClientProperties properties) {

    return builder -> {
      ProblemDetailErrorHandler errorHandler = errorHandlerProvider.getIfAvailable();
      if (errorHandler != null
          && properties.httpInterface().errorHandling().problemDetailEnabled()) {
        builder.defaultStatusHandler(HttpStatusCode::isError, errorHandler);
        log.debug("已将 ProblemDetailErrorHandler 添加到 RestClient.Builder");
      }
    };
  }

  /// 创建预配置的 RestClient.Builder
  ///
  /// 此 Builder 已配置：
  ///
  /// - Apache HttpClient 5.x 连接池（支持 stale connection 检测和清理）
  /// - 全局连接和读取超时
  /// - ProblemDetail 错误处理
  ///
  /// @param customizers 所有 RestClientCustomizer 实例
  /// @param properties HTTP Interface 配置属性
  /// @return 预配置的 RestClient.Builder
  @Bean
  @Primary
  @ConditionalOnMissingBean(name = "httpInterfaceRestClientBuilder")
  public RestClient.Builder httpInterfaceRestClientBuilder(
      ObjectProvider<RestClientCustomizer> customizers, HttpClientProperties properties) {
    RestClient.Builder builder =
        RestClient.builder().requestFactory(createDefaultRequestFactory(properties));

    // 应用所有自定义器
    customizers.orderedStream().forEach(customizer -> customizer.customize(builder));

    log.info(
        "创建 httpInterfaceRestClientBuilder (connectTimeout={}, readTimeout={}, "
            + "maxConnTotal={}, maxConnPerRoute={}, validateAfterInactivity={}, evictIdleAfter={})",
        properties.httpInterface().connectTimeout(),
        properties.httpInterface().readTimeout(),
        properties.httpInterface().connectionPool().maxConnTotal(),
        properties.httpInterface().connectionPool().maxConnPerRoute(),
        properties.httpInterface().connectionPool().validateAfterInactivity(),
        properties.httpInterface().connectionPool().evictIdleConnectionsAfter());

    return builder;
  }

  /// 创建负载均衡的 RestClient.Builder
  ///
  /// 此 Builder 用于 lb:// 服务发现调用。为了避免与 RestClientFactory 重复应用自定义器，
  /// 这里只配置全局超时，不应用 RestClientCustomizer。
  ///
  /// @param properties HTTP Interface 配置属性
  /// @return 负载均衡的 RestClient.Builder
  @Bean
  @LoadBalanced
  @ConditionalOnMissingBean(name = "httpInterfaceLoadBalancedRestClientBuilder")
  public RestClient.Builder httpInterfaceLoadBalancedRestClientBuilder(
      HttpClientProperties properties) {
    log.info(
        "创建 httpInterfaceLoadBalancedRestClientBuilder (connectTimeout={}, readTimeout={})",
        properties.httpInterface().connectTimeout(),
        properties.httpInterface().readTimeout());
    return RestClient.builder().requestFactory(createDefaultRequestFactory(properties));
  }

  /// 构建默认的请求工厂
  ///
  /// 使用 Apache HttpClient 5.x 提供：
  ///
  /// - **可靠的 stale connection 检测**：通过 `validateAfterInactivity` 配置，
  ///   连接空闲超过指定时间后，下次使用前会验证连接有效性
  /// - **主动连接清理**：`evictIdleConnections` 和 `evictExpiredConnections`
  ///   自动清理无效连接，避免复用已被服务端关闭的连接
  /// - **精细的连接池控制**：`maxConnTotal` 和 `maxConnPerRoute` 控制连接池大小
  ///
  /// 这解决了 JDK HttpClient 的以下问题：
  ///
  /// - "HTTP/1.1 header parser received no bytes" 错误
  /// - "EOF reached while reading" 错误
  /// - stale connection 检测不可靠导致连续重试都失败
  private HttpComponentsClientHttpRequestFactory createDefaultRequestFactory(
      HttpClientProperties properties) {

    HttpClientProperties.HttpInterface.ConnectionPool poolProps =
        properties.httpInterface().connectionPool();

    // 创建连接池管理器
    PoolingHttpClientConnectionManager connManager =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(poolProps.maxConnTotal())
            .setMaxConnPerRoute(poolProps.maxConnPerRoute())
            .setValidateAfterInactivity(
                TimeValue.ofSeconds(poolProps.validateAfterInactivity().toSeconds()))
            .build();

    // 创建请求配置
    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectTimeout(Timeout.of(properties.httpInterface().connectTimeout()))
            .setResponseTimeout(Timeout.of(properties.httpInterface().readTimeout()))
            .build();

    // 创建 HttpClient
    HttpClient httpClient =
        HttpClients.custom()
            .setConnectionManager(connManager)
            .setDefaultRequestConfig(requestConfig)
            .evictIdleConnections(
                TimeValue.ofSeconds(poolProps.evictIdleConnectionsAfter().toSeconds()))
            .evictExpiredConnections()
            .build();

    log.debug(
        "创建 Apache HttpClient 连接池: maxConnTotal={}, maxConnPerRoute={}, "
            + "validateAfterInactivity={}s, evictIdleAfter={}s",
        poolProps.maxConnTotal(),
        poolProps.maxConnPerRoute(),
        poolProps.validateAfterInactivity().toSeconds(),
        poolProps.evictIdleConnectionsAfter().toSeconds());

    return new HttpComponentsClientHttpRequestFactory(httpClient);
  }

  /// 注册 RestClient 工厂
  ///
  /// 提供通用的 RestClient 创建逻辑，简化各服务的 HTTP Interface 配置。
  /// 自动应用分组级别的超时配置、错误处理器，以及只给内部客户端的拦截器。
  ///
  /// @param customizers RestClient 自定义器提供者
  /// @param internalCallInterceptors 只挂到内部客户端上的拦截器提供者
  /// @param properties HTTP Interface 配置属性
  /// @return RestClient 工厂实例
  @Bean
  @ConditionalOnMissingBean
  public RestClientFactory restClientFactory(
      ObjectProvider<RestClientCustomizer> customizers,
      ObjectProvider<InternalCallInterceptor> internalCallInterceptors,
      HttpClientProperties properties) {
    log.info("注册 RestClientFactory");
    return new RestClientFactory(customizers, internalCallInterceptors, properties.httpInterface());
  }
}
