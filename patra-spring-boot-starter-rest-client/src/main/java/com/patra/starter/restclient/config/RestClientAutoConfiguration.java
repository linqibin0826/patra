package com.patra.starter.restclient.config;

import com.patra.starter.restclient.interceptor.LoggingInterceptor;
import com.patra.starter.restclient.interceptor.MetricsInterceptor;
import com.patra.starter.restclient.interceptor.TracingInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/// REST Client 自动配置类。
///
/// <p>提供默认的 RestClient Bean 和拦截器配置。
///
/// <h2>自动配置内容</h2>
/// <ul>
///   <li>{@code defaultRestClient}: 默认 RestClient Bean
///   <li>{@code defaultHttpRequestFactory}: HTTP 请求工厂（配置超时）
///   <li>{@code loggingInterceptor}: 日志拦截器
///   <li>{@code tracingInterceptor}: 追踪拦截器
///   <li>{@code metricsInterceptor}: 指标拦截器
/// </ul>
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
  /// <p>自动注入所有已注册的拦截器，并应用全局超时配置。
  ///
  /// @param properties 配置属性
  /// @param interceptorsProvider 拦截器提供者
  /// @return 配置完成的 RestClient
  @Bean
  @ConditionalOnMissingBean(name = "defaultRestClient")
  public RestClient defaultRestClient(
      RestClientProperties properties,
      ObjectProvider<ClientHttpRequestInterceptor> interceptorsProvider,
      JdkClientHttpRequestFactory requestFactory) {

    var clientConfig =
        properties.getClients().getOrDefault("default", new RestClientProperties.ClientConfig());

    var builder = RestClient.builder().requestFactory(requestFactory);

    // 应用默认 Headers
    if (clientConfig.getDefaultHeaders() != null && !clientConfig.getDefaultHeaders().isEmpty()) {
      builder.defaultHeaders(headers -> clientConfig.getDefaultHeaders().forEach(headers::add));
    }

    // 注入所有拦截器（按 @Order 排序）
    var interceptors = interceptorsProvider.orderedStream().toList();
    if (!interceptors.isEmpty()) {
      builder.requestInterceptors(list -> list.addAll(interceptors));
    }

    return builder.build();
  }

  /// 创建 HTTP 请求工厂（配置超时）。
  ///
  /// @param properties 配置属性
  /// @return JDK HTTP 请求工厂
  @Bean
  @ConditionalOnMissingBean
  public JdkClientHttpRequestFactory defaultHttpRequestFactory(RestClientProperties properties) {

    var timeout = properties.getTimeout();

    // 创建 JDK HttpClient（支持 HTTP/2）
    var httpClient = HttpClient.newBuilder().connectTimeout(timeout.connect()).build();

    var factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(timeout.read());

    return factory;
  }

  /// 创建日志拦截器。
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

  /// 创建追踪拦截器。
  ///
  /// @param properties 配置属性
  /// @return 追踪拦截器
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "patra.rest-client.interceptors.tracing",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  @Order(50)
  public TracingInterceptor restClientTracingInterceptor(RestClientProperties properties) {
    var headerNames = properties.getInterceptors().getTracing().getHeaderNames();
    return new TracingInterceptor(headerNames);
  }

  /// 创建指标拦截器。
  ///
  /// @param meterRegistry Micrometer 指标注册表
  /// @return 指标拦截器
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnProperty(
      prefix = "patra.rest-client.interceptors.metrics",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  @Order(10)
  public MetricsInterceptor metricsInterceptor(MeterRegistry meterRegistry) {
    return new MetricsInterceptor(meterRegistry);
  }
}
