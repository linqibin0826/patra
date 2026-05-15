package com.patra.starter.httpinterface.factory;

import com.patra.starter.httpinterface.config.HttpInterfaceProperties;
import com.patra.starter.httpinterface.config.HttpInterfaceProperties.ConnectionPoolProperties;
import com.patra.starter.httpinterface.config.HttpInterfaceProperties.ServiceGroupProperties;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/// RestClient 工厂类
///
/// 提供通用的 RestClient 创建逻辑，简化各服务的 HTTP Interface 配置。
/// 自动应用分组级别的超时配置、错误处理器和 TraceId 拦截器。
///
/// **使用 Apache HttpClient 5.x**
///
/// 解决 JDK HttpClient 的 stale connection 检测不可靠问题：
///
/// - 支持 `validateAfterInactivity` 在使用前验证连接有效性
/// - 内置 `evictIdleConnections` / `evictExpiredConnections` 主动清理无效连接
/// - 解决 "HTTP/1.1 header parser received no bytes" 错误
///
/// **使用示例：**
///
/// ```java
/// @Configuration
/// public class HttpClientConfiguration {
///
///   @Bean
///   public RestClientFactory restClientFactory(
///       ObjectProvider<RestClientCustomizer> customizers,
///       HttpInterfaceProperties properties) {
///     return new RestClientFactory(customizers, properties);
///   }
///
///   @Bean
///   public RestClient registryRestClient(
///       @Qualifier("httpInterfaceLoadBalancedRestClientBuilder") RestClient.Builder
// loadBalancedBuilder,
///       RestClientFactory factory) {
///     return factory.createRestClient(loadBalancedBuilder, "registry", "lb://patra-registry");
///   }
///
///   @Bean
///   public ProvenanceEndpoint provenanceEndpoint(RestClient registryRestClient, RestClientFactory
// factory) {
///     return factory.createProxy(registryRestClient, ProvenanceEndpoint.class);
///   }
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class RestClientFactory {

  private final ObjectProvider<RestClientCustomizer> customizers;
  private final HttpInterfaceProperties properties;

  /// 构造 RestClient 工厂
  ///
  /// @param customizers RestClient 自定义器提供者
  /// @param properties HTTP Interface 配置属性
  public RestClientFactory(
      ObjectProvider<RestClientCustomizer> customizers, HttpInterfaceProperties properties) {
    this.customizers = customizers;
    this.properties = properties;
  }

  /// 创建配置好的 RestClient
  ///
  /// 根据分组名从配置中获取 baseUrl 和超时设置，自动应用所有 RestClientCustomizer。
  ///
  /// @param builder RestClient.Builder（建议使用 httpInterfaceLoadBalancedRestClientBuilder）
  /// @param groupName 服务分组名称（对应 `patra.http.interface.groups.{groupName}`）
  /// @param defaultBaseUrl 默认基础 URL（当配置中未指定时使用）
  /// @return 配置好的 RestClient
  public RestClient createRestClient(
      RestClient.Builder builder, String groupName, String defaultBaseUrl) {

    ServiceGroupProperties groupProps = properties.getGroups().get(groupName);

    // 确定 baseUrl
    String baseUrl =
        (groupProps != null && groupProps.getBaseUrl() != null)
            ? groupProps.getBaseUrl()
            : defaultBaseUrl;

    log.info("创建 {} RestClient，baseUrl={}", groupName, baseUrl);

    // 克隆 builder 避免污染原始实例
    RestClient.Builder clientBuilder = builder.clone().baseUrl(baseUrl);

    // 应用超时配置（使用 Apache HttpClient）
    ClientHttpRequestFactory requestFactory = createRequestFactory(groupProps);
    if (requestFactory != null) {
      clientBuilder.requestFactory(requestFactory);
    }

    // 应用所有 RestClientCustomizer（错误处理器、TraceId 拦截器等）
    customizers.orderedStream().forEach(customizer -> customizer.customize(clientBuilder));

    return clientBuilder.build();
  }

  /// 创建 HTTP Interface 代理
  ///
  /// 使用 HttpServiceProxyFactory 为指定接口创建代理实例。
  ///
  /// @param restClient 配置好的 RestClient
  /// @param serviceInterface 要代理的接口类型
  /// @param <T> 接口类型
  /// @return 代理实例
  public <T> T createProxy(RestClient restClient, Class<T> serviceInterface) {
    RestClientAdapter adapter = RestClientAdapter.create(restClient);
    HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
    T proxy = factory.createClient(serviceInterface);
    log.info("已创建 {} HTTP Interface 代理", serviceInterface.getSimpleName());
    return proxy;
  }

  /// 创建带有超时配置的 ClientHttpRequestFactory
  ///
  /// 使用 Apache HttpClient 5.x，支持连接池管理和空闲连接清理。
  /// 优先使用分组配置，其次使用全局配置。
  ///
  /// @param groupProps 分组配置（可为 null）
  /// @return 配置好的 RequestFactory，如果无需自定义超时则返回 null
  private ClientHttpRequestFactory createRequestFactory(ServiceGroupProperties groupProps) {
    // 确定连接超时
    Duration connectTimeout = resolveConnectTimeout(groupProps);

    // 确定读取超时
    Duration readTimeout = resolveReadTimeout(groupProps);

    // 如果超时配置与全局配置相同，不创建自定义 RequestFactory
    if (connectTimeout.equals(properties.getConnectTimeout())
        && readTimeout.equals(properties.getReadTimeout())) {
      return null;
    }

    // 创建独立的 Apache HttpClient（使用分组超时配置）
    ConnectionPoolProperties poolProps = properties.getConnectionPool();

    PoolingHttpClientConnectionManager connManager =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(poolProps.getMaxConnTotal())
            .setMaxConnPerRoute(poolProps.getMaxConnPerRoute())
            .setValidateAfterInactivity(
                TimeValue.ofSeconds(poolProps.getValidateAfterInactivity().toSeconds()))
            .build();

    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectTimeout(Timeout.of(connectTimeout))
            .setResponseTimeout(Timeout.of(readTimeout))
            .build();

    HttpClient httpClient =
        HttpClients.custom()
            .setConnectionManager(connManager)
            .setDefaultRequestConfig(requestConfig)
            .evictIdleConnections(
                TimeValue.ofSeconds(poolProps.getEvictIdleConnectionsAfter().toSeconds()))
            .evictExpiredConnections()
            .build();

    log.debug(
        "配置分组超时：connectTimeout={}ms, readTimeout={}ms",
        connectTimeout.toMillis(),
        readTimeout.toMillis());

    return new HttpComponentsClientHttpRequestFactory(httpClient);
  }

  /// 解析连接超时配置
  ///
  /// 优先使用分组配置，其次使用全局配置。
  private Duration resolveConnectTimeout(ServiceGroupProperties groupProps) {
    return (groupProps != null && groupProps.getConnectTimeout() != null)
        ? groupProps.getConnectTimeout()
        : properties.getConnectTimeout();
  }

  /// 解析读取超时配置
  ///
  /// 优先使用分组配置，其次使用全局配置。
  private Duration resolveReadTimeout(ServiceGroupProperties groupProps) {
    return (groupProps != null && groupProps.getReadTimeout() != null)
        ? groupProps.getReadTimeout()
        : properties.getReadTimeout();
  }
}
