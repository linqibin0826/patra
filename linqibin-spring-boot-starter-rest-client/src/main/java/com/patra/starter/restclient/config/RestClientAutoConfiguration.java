package com.patra.starter.restclient.config;

import com.patra.starter.restclient.interceptor.LoggingInterceptor;
import java.net.http.HttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/// REST Client 自动配置类。
///
/// 提供默认的 RestClient Bean 和标准的拦截器支持。
///
/// ## 自动配置内容
///
/// - `defaultRestClient`: 默认 RestClient Bean
/// - `defaultHttpRequestFactory`: HTTP 请求工厂（配置超时）
/// - `loggingInterceptor`: 日志拦截器（可选）
///
/// ## 拦截器扩展
///
/// 外部模块（如 patra-spring-boot-starter-observability）可以注册 Spring 标准的
/// {@link ClientHttpRequestInterceptor} 来注入可观测性、安全、审计等横切关注点。
///
/// ## 设计说明
///
/// 1. 追踪传播和指标收集功能已移至 patra-spring-boot-starter-observability，
///    通过 Spring 标准的 {@link ClientHttpRequestInterceptor} 接口提供，符合关注点分离原则。
///
/// 2. **LoadBalancer 拦截器隔离**：`defaultRestClient` 设计用于调用外部 API（如 PubMed、EPMC），
///    会自动过滤掉 Spring Cloud LoadBalancer 注册的拦截器（如 `RetryLoadBalancerInterceptor`），
///    避免外部域名被错误解析为服务 ID。
///
/// @author linqibin
/// @since 0.1.0
@AutoConfiguration
@EnableConfigurationProperties(RestClientProperties.class)
@ConditionalOnProperty(
    prefix = "patra.rest-client",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RestClientAutoConfiguration {

  /// 创建默认的 RestClient Bean。
  ///
  /// 自动注入所有已注册的 {@link ClientHttpRequestInterceptor}，并应用全局超时配置。
  ///
  /// **设计说明**：直接创建 `JdkClientHttpRequestFactory` 而非通过 Bean 注入，
  /// 避免被 Spring Cloud LoadBalancer 的 BeanPostProcessor 包装，
  /// 确保外部 URL 调用不会被错误地当作服务发现处理。
  ///
  /// @param properties 配置属性
  /// @param interceptorsProvider Spring 标准拦截器提供者
  /// @return 配置完成的 RestClient
  @Bean
  @ConditionalOnMissingBean(name = "defaultRestClient")
  public RestClient defaultRestClient(
      RestClientProperties properties,
      ObjectProvider<ClientHttpRequestInterceptor> interceptorsProvider) {

    var clientConfig =
        properties.getClients().getOrDefault("default", new RestClientProperties.ClientConfig());

    // 直接创建 Factory，不通过 Bean 注入，避免被 LoadBalancer 包装
    var factory = createHttpRequestFactory(properties);
    var builder = RestClient.builder().requestFactory(factory);

    // 应用默认 Headers
    if (clientConfig.getDefaultHeaders() != null && !clientConfig.getDefaultHeaders().isEmpty()) {
      builder.defaultHeaders(headers -> clientConfig.getDefaultHeaders().forEach(headers::add));
    }

    // 注入拦截器（按 @Order 排序），过滤掉 LoadBalancer 拦截器
    builder.requestInterceptors(
        list ->
            list.addAll(
                interceptorsProvider
                    .orderedStream()
                    .filter(i -> !isLoadBalancerInterceptor(i))
                    .toList()));

    return builder.build();
  }

  /// 判断是否为 Spring Cloud LoadBalancer 拦截器。
  ///
  /// LoadBalancer 拦截器会将请求 URL 的 host 解析为服务 ID，
  /// 导致外部 API 调用（如 `nlmpubs.nlm.nih.gov`）失败。
  ///
  /// @param interceptor 待检查的拦截器
  /// @return 如果是 LoadBalancer 拦截器返回 true
  private boolean isLoadBalancerInterceptor(ClientHttpRequestInterceptor interceptor) {
    String className = interceptor.getClass().getName();
    return className.contains("LoadBalancer");
  }

  /// 创建 HTTP 请求工厂（配置超时）。
  ///
  /// 使用 JDK HttpClient（支持 HTTP/2），配置连接超时和读取超时。
  ///
  /// @param properties 配置属性
  /// @return JDK HTTP 请求工厂
  private JdkClientHttpRequestFactory createHttpRequestFactory(RestClientProperties properties) {
    var timeout = properties.getTimeout();

    // 创建 JDK HttpClient（支持 HTTP/2）
    var httpClient = HttpClient.newBuilder().connectTimeout(timeout.connect()).build();

    var factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(timeout.read());

    return factory;
  }

  /// 创建长时间运行操作的 RestClient Bean。
  ///
  /// 适用于大文件下载、批量数据传输等需要较长超时的场景。
  /// 默认超时：connect=30s, read=600s (10分钟), write=30s。
  ///
  /// **使用方式**：通过 `@Qualifier("longRunningRestClient")` 注入。
  ///
  /// **禁用方式**：设置 `patra.rest-client.clients.long-running.enabled=false`。
  ///
  /// @param properties 配置属性
  /// @param interceptorsProvider Spring 标准拦截器提供者
  /// @return 配置长超时的 RestClient
  @Bean
  @ConditionalOnMissingBean(name = "longRunningRestClient")
  @Conditional(LongRunningClientEnabledCondition.class)
  public RestClient longRunningRestClient(
      RestClientProperties properties,
      ObjectProvider<ClientHttpRequestInterceptor> interceptorsProvider) {

    var clientConfig = properties.getClients().get("long-running");

    // 使用配置的超时或默认值
    var timeout =
        (clientConfig != null && clientConfig.getTimeout() != null)
            ? clientConfig.getTimeout()
            : defaultLongRunningTimeout();

    // 创建专用 Factory（长超时）
    var httpClient = HttpClient.newBuilder().connectTimeout(timeout.connect()).build();
    var factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(timeout.read());

    var builder = RestClient.builder().requestFactory(factory);

    // 注入拦截器，过滤 LoadBalancer
    builder.requestInterceptors(
        list ->
            list.addAll(
                interceptorsProvider
                    .orderedStream()
                    .filter(i -> !isLoadBalancerInterceptor(i))
                    .toList()));

    return builder.build();
  }

  /// 创建长时间运行客户端的默认超时配置。
  ///
  /// @return 默认超时配置（connect=30s, read=600s, write=30s）
  private RestClientProperties.TimeoutConfig defaultLongRunningTimeout() {
    return new RestClientProperties.TimeoutConfig(
        java.time.Duration.ofSeconds(30),
        java.time.Duration.ofSeconds(600),
        java.time.Duration.ofSeconds(30));
  }

  /// 创建日志拦截器（可选）。
  ///
  /// 日志拦截器不属于可观测性范畴，而是基础设施层的调试工具，因此保留在 core 模块中。
  ///
  /// @param properties 配置属性
  /// @return 日志拦截器
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "patra.rest-client.interceptors.logging",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  @Order(100)
  public LoggingInterceptor loggingInterceptor(RestClientProperties properties) {
    var config = properties.getInterceptors().getLogging();
    return new LoggingInterceptor(
        config.isLogHeaders(), config.isLogBody(), config.getMaxBodyLogLength());
  }
}
