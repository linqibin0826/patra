package com.patra.starter.restclient.config;

import com.patra.starter.restclient.interceptor.LoggingInterceptor;
import java.net.http.HttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
/// 追踪传播和指标收集功能已移至 patra-spring-boot-starter-observability，
/// 通过 Spring 标准的 {@link ClientHttpRequestInterceptor} 接口提供，符合关注点分离原则。
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
  /// @param properties 配置属性
  /// @param interceptorsProvider Spring 标准拦截器提供者
  /// @param requestFactory HTTP 请求工厂
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
    builder.requestInterceptors(
        list -> list.addAll(interceptorsProvider.orderedStream().toList()));

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
