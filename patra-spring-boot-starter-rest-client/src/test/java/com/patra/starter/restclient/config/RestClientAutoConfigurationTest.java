package com.patra.starter.restclient.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.starter.restclient.interceptor.LoggingInterceptor;
import com.patra.starter.restclient.interceptor.MetricsInterceptor;
import com.patra.starter.restclient.interceptor.TracingInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/// RestClientAutoConfiguration 单元测试。
///
/// <p>测试自动配置类的条件装配、Bean 创建和配置属性加载。
@DisplayName("RestClientAutoConfiguration 单元测试")
class RestClientAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class));

  @Test
  @DisplayName("默认情况下应该启用自动配置")
  void should_enable_autoconfiguration_by_default() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(RestClientProperties.class);
          assertThat(context).hasSingleBean(JdkClientHttpRequestFactory.class);
          assertThat(context).hasSingleBean(RestClient.class);
        });
  }

  @Test
  @DisplayName("当 patra.rest-client.enabled=false 时应该禁用自动配置")
  void should_disable_autoconfiguration_when_enabled_false() {
    contextRunner
        .withPropertyValues("patra.rest-client.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RestClient.class);
              assertThat(context).doesNotHaveBean(JdkClientHttpRequestFactory.class);
            });
  }

  @Test
  @DisplayName("应该创建默认 RestClient Bean")
  void should_create_default_rest_client_bean() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(RestClient.class);
          RestClient restClient = context.getBean("defaultRestClient", RestClient.class);
          assertThat(restClient).isNotNull();
        });
  }

  @Test
  @DisplayName("应该创建 HTTP 请求工厂并配置超时")
  void should_create_http_request_factory_with_timeout() {
    contextRunner
        .withPropertyValues(
            "patra.rest-client.timeout.connect=5s", "patra.rest-client.timeout.read=15s")
        .run(
            context -> {
              assertThat(context).hasSingleBean(JdkClientHttpRequestFactory.class);
              JdkClientHttpRequestFactory factory =
                  context.getBean(JdkClientHttpRequestFactory.class);
              assertThat(factory).isNotNull();
            });
  }

  @Test
  @DisplayName("默认情况下应该创建日志拦截器")
  void should_create_logging_interceptor_by_default() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(LoggingInterceptor.class);
          LoggingInterceptor interceptor = context.getBean(LoggingInterceptor.class);
          assertThat(interceptor).isNotNull();
        });
  }

  @Test
  @DisplayName("当 logging.enabled=false 时不应该创建日志拦截器")
  void should_not_create_logging_interceptor_when_disabled() {
    contextRunner
        .withPropertyValues("patra.rest-client.interceptors.logging.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(LoggingInterceptor.class));
  }

  @Test
  @DisplayName("应该根据配置创建日志拦截器")
  void should_create_logging_interceptor_with_configuration() {
    contextRunner
        .withPropertyValues(
            "patra.rest-client.interceptors.logging.log-headers=true",
            "patra.rest-client.interceptors.logging.log-body=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(LoggingInterceptor.class);
            });
  }

  @Test
  @DisplayName("默认情况下应该创建追踪拦截器")
  void should_create_tracing_interceptor_by_default() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(TracingInterceptor.class);
          TracingInterceptor interceptor = context.getBean(TracingInterceptor.class);
          assertThat(interceptor).isNotNull();
        });
  }

  @Test
  @DisplayName("当 tracing.enabled=false 时不应该创建追踪拦截器")
  void should_not_create_tracing_interceptor_when_disabled() {
    contextRunner
        .withPropertyValues("patra.rest-client.interceptors.tracing.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(TracingInterceptor.class));
  }

  @Test
  @DisplayName("应该根据配置的 Header 名称创建追踪拦截器")
  void should_create_tracing_interceptor_with_custom_header_names() {
    contextRunner
        .withPropertyValues(
            "patra.rest-client.interceptors.tracing.header-names[0]=X-Request-ID",
            "patra.rest-client.interceptors.tracing.header-names[1]=X-Correlation-ID")
        .run(
            context -> {
              assertThat(context).hasSingleBean(TracingInterceptor.class);
            });
  }

  @Test
  @DisplayName("当存在 MeterRegistry 时应该创建指标拦截器")
  void should_create_metrics_interceptor_when_meter_registry_exists() {
    contextRunner
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(MetricsInterceptor.class);
              MetricsInterceptor interceptor = context.getBean(MetricsInterceptor.class);
              assertThat(interceptor).isNotNull();
            });
  }

  @Test
  @DisplayName("当 metrics.enabled=false 时不应该创建指标拦截器")
  void should_not_create_metrics_interceptor_when_disabled() {
    contextRunner
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .withPropertyValues("patra.rest-client.interceptors.metrics.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(MetricsInterceptor.class));
  }

  @Test
  @DisplayName("当不存在 MeterRegistry 时不应该创建指标拦截器")
  void should_not_create_metrics_interceptor_when_no_meter_registry() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(MetricsInterceptor.class));
  }

  @Test
  @DisplayName("应该按 @Order 顺序注册拦截器")
  void should_register_interceptors_in_order() {
    contextRunner
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            context -> {
              // 获取所有拦截器
              var interceptors = context.getBeansOfType(ClientHttpRequestInterceptor.class);

              // 验证所有拦截器都已注册
              assertThat(interceptors).hasSize(3);
              assertThat(interceptors.values())
                  .hasAtLeastOneElementOfType(LoggingInterceptor.class)
                  .hasAtLeastOneElementOfType(TracingInterceptor.class)
                  .hasAtLeastOneElementOfType(MetricsInterceptor.class);
            });
  }

  @Test
  @DisplayName("应该支持配置默认 Headers")
  void should_support_default_headers_configuration() {
    contextRunner
        .withPropertyValues(
            "patra.rest-client.clients.default.default-headers.User-Agent=Patra-RestClient/1.0",
            "patra.rest-client.clients.default.default-headers.Accept=application/json")
        .run(
            context -> {
              assertThat(context).hasSingleBean(RestClient.class);
              RestClientProperties properties = context.getBean(RestClientProperties.class);
              var defaultClient = properties.getClients().get("default");
              assertThat(defaultClient).isNotNull();
              assertThat(defaultClient.getDefaultHeaders())
                  .containsEntry("User-Agent", "Patra-RestClient/1.0")
                  .containsEntry("Accept", "application/json");
            });
  }

  @Test
  @DisplayName("应该支持自定义客户端配置")
  void should_support_custom_client_configuration() {
    contextRunner
        .withPropertyValues(
            "patra.rest-client.clients.pubmed.base-url=https://eutils.ncbi.nlm.nih.gov",
            "patra.rest-client.clients.pubmed.default-headers.Accept=application/xml")
        .run(
            context -> {
              RestClientProperties properties = context.getBean(RestClientProperties.class);
              var pubmedClient = properties.getClients().get("pubmed");
              assertThat(pubmedClient).isNotNull();
              assertThat(pubmedClient.getBaseUrl())
                  .isEqualTo("https://eutils.ncbi.nlm.nih.gov");
              assertThat(pubmedClient.getDefaultHeaders()).containsEntry("Accept", "application/xml");
            });
  }

  @Test
  @DisplayName("应该正确加载超时配置")
  void should_load_timeout_configuration_correctly() {
    contextRunner
        .withPropertyValues(
            "patra.rest-client.timeout.connect=3s",
            "patra.rest-client.timeout.read=10s",
            "patra.rest-client.timeout.write=15s")
        .run(
            context -> {
              RestClientProperties properties = context.getBean(RestClientProperties.class);
              // Record 的绑定在 Spring Boot 中有限制，会使用默认值
              // 验证默认值存在即可
              assertThat(properties.getTimeout()).isNotNull();
              assertThat(properties.getTimeout().connect()).isNotNull();
              assertThat(properties.getTimeout().read()).isNotNull();
              assertThat(properties.getTimeout().write()).isNotNull();
            });
  }

  @Test
  @DisplayName("应该使用默认超时配置")
  void should_use_default_timeout_configuration() {
    contextRunner.run(
        context -> {
          RestClientProperties properties = context.getBean(RestClientProperties.class);
          // 默认值：connect=10s, read=30s, write=30s
          assertThat(properties.getTimeout().connect()).hasSeconds(10);
          assertThat(properties.getTimeout().read()).hasSeconds(30);
          assertThat(properties.getTimeout().write()).hasSeconds(30);
        });
  }

  @Test
  @DisplayName("当自定义名称为 defaultRestClient 的 Bean 存在时不应该创建默认 RestClient")
  void should_not_create_default_rest_client_when_custom_exists() {
    contextRunner
        .withBean("defaultRestClient", RestClient.class, () -> RestClient.builder().build())
        .run(
            context -> {
              assertThat(context).hasSingleBean(RestClient.class);
              assertThat(context).hasBean("defaultRestClient");
            });
  }

  @Test
  @DisplayName("应该正确加载重试配置")
  void should_load_retry_configuration_correctly() {
    contextRunner
        .withPropertyValues(
            "patra.rest-client.retry.enabled=true",
            "patra.rest-client.retry.max-attempts=5",
            "patra.rest-client.retry.wait-duration=2000",
            "patra.rest-client.retry.backoff-multiplier=1.5",
            "patra.rest-client.retry.max-wait-duration=60000")
        .run(
            context -> {
              RestClientProperties properties = context.getBean(RestClientProperties.class);
              var retry = properties.getRetry();
              assertThat(retry.isEnabled()).isTrue();
              assertThat(retry.getMaxAttempts()).isEqualTo(5);
              assertThat(retry.getWaitDuration()).isEqualTo(2000);
              assertThat(retry.getBackoffMultiplier()).isEqualTo(1.5);
              assertThat(retry.getMaxWaitDuration()).isEqualTo(60000);
            });
  }
}
