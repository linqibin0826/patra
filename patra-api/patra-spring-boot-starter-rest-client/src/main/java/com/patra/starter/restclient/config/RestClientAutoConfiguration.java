package com.patra.starter.restclient.config;

import com.patra.starter.restclient.interceptor.ClientInterceptor;
import com.patra.starter.restclient.interceptor.LoggingInterceptor;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/// REST Client 自动配置类。
///
/// <p>提供默认的 RestClient Bean 和拦截器扩展点。
///
/// <h2>自动配置内容</h2>
/// <ul>
///   <li>{@code defaultRestClient}: 默认 RestClient Bean
///   <li>{@code defaultHttpRequestFactory}: HTTP 请求工厂（配置超时）
///   <li>{@code loggingInterceptor}: 日志拦截器（可选）
/// </ul>
///
/// <h2>扩展点</h2>
/// <p>外部模块（如 patra-spring-boot-starter-observability）可以实现 {@link ClientInterceptor}
/// 接口来注入可观测性、安全、审计等横切关注点。
///
/// <h2>设计说明</h2>
/// <p>追踪传播和指标收集功能已移至 patra-spring-boot-starter-observability，
/// 通过 {@link ClientInterceptor} 扩展点机制提供，符合关注点分离原则。
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
  /// @param standardInterceptorsProvider Spring 标准拦截器提供者
  /// @param clientInterceptorsProvider ClientInterceptor 扩展点提供者
  /// @param requestFactory HTTP 请求工厂
  /// @return 配置完成的 RestClient
  @Bean
  @ConditionalOnMissingBean(name = "defaultRestClient")
  public RestClient defaultRestClient(
      RestClientProperties properties,
      ObjectProvider<ClientHttpRequestInterceptor> standardInterceptorsProvider,
      ObjectProvider<ClientInterceptor> clientInterceptorsProvider,
      JdkClientHttpRequestFactory requestFactory) {

    var clientConfig =
        properties.getClients().getOrDefault("default", new RestClientProperties.ClientConfig());

    var builder = RestClient.builder().requestFactory(requestFactory);

    // 应用默认 Headers
    if (clientConfig.getDefaultHeaders() != null && !clientConfig.getDefaultHeaders().isEmpty()) {
      builder.defaultHeaders(headers -> clientConfig.getDefaultHeaders().forEach(headers::add));
    }

    // 注入所有拦截器
    builder.requestInterceptors(
        list -> {
          // 1. 添加所有标准拦截器（如 LoggingInterceptor）
          list.addAll(standardInterceptorsProvider.orderedStream().toList());

          // 2. 将 ClientInterceptor 转换为 ClientHttpRequestInterceptor 并添加
          List<ClientInterceptor> clientInterceptors =
              clientInterceptorsProvider.orderedStream()
                  .sorted(Comparator.comparingInt(ClientInterceptor::getOrder))
                  .toList();

          for (ClientInterceptor clientInterceptor : clientInterceptors) {
            list.add(new ClientInterceptorAdapter(clientInterceptor));
          }
        });

    return builder.build();
  }

  /// ClientInterceptor 适配器，将 ClientInterceptor 转换为 ClientHttpRequestInterceptor。
  ///
  /// <p>这是一个内部类，用于桥接 ClientInterceptor 扩展点和 Spring 的
  /// ClientHttpRequestInterceptor 接口。
  private static class ClientInterceptorAdapter implements ClientHttpRequestInterceptor {

    private final ClientInterceptor delegate;

    public ClientInterceptorAdapter(ClientInterceptor delegate) {
      this.delegate = delegate;
    }

    @Override
    public ClientHttpResponse intercept(
        HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
        throws IOException {

      // 前置拦截
      delegate.beforeRequest(request);

      try {
        // 执行请求
        ClientHttpResponse response = execution.execute(request, body);

        // 后置拦截
        delegate.afterResponse(request, response);

        return response;
      } catch (IOException e) {
        // 异常拦截
        delegate.onError(request, e);
        throw e;
      }
    }
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
  /// <p>日志拦截器不属于可观测性范畴，而是基础设施层的调试工具，因此保留在 core 模块中。
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
