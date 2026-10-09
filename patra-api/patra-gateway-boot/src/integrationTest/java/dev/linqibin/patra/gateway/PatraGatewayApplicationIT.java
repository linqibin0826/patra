package dev.linqibin.patra.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.identity.session.RedisSessionStore;
import dev.linqibin.starter.test.container.initializer.RedisContainerInitializer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.JdkClientHttpRequestFactoryBuilder;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.OncePerRequestFilter;

/// 网关以 servlet 应用启动，HTTP 客户端是显式指定的 JDK HttpClient，请求跑在虚拟线程上。
///
/// 用真实端口：虚拟线程只有真的经过 Tomcat 才能观察到。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ContextConfiguration(
    initializers = {RedisContainerInitializer.class, GatewayITSigningKeyInitializer.class})
class PatraGatewayApplicationIT {

  @Autowired private ApplicationContext context;
  @Autowired private RestTestClient restClient;
  @Autowired private RequestThreadProbe probe;
  @Autowired private StringRedisTemplate redis;

  @Test
  void should_start_as_servlet_application_without_webflux_gateway() {
    assertThat(context).isInstanceOf(WebApplicationContext.class);
    assertThat(
            ClassUtils.isPresent(
                "org.springframework.cloud.gateway.server.mvc.GatewayServerMvcAutoConfiguration",
                null))
        .isTrue();
    assertThat(
            ClassUtils.isPresent(
                "org.springframework.cloud.gateway.config.GatewayAutoConfiguration", null))
        .isFalse();
  }

  @Test
  void should_use_explicitly_configured_jdk_http_client() {
    assertThat(context.getBean(ClientHttpRequestFactoryBuilder.class))
        .isInstanceOf(JdkClientHttpRequestFactoryBuilder.class);
    // Boot 4 用这个 builder 加 spring.http.clients.* 的设置装出一个 ClientHttpRequestFactory Bean，
    // 网关的 RestClient 会优先拿容器里的这个 Bean：它必须是 JDK 那一个
    assertThat(context.getBean(ClientHttpRequestFactory.class))
        .isInstanceOf(JdkClientHttpRequestFactory.class);
  }

  @Test
  void should_have_the_session_store_ready() {
    assertThat(context.getBean(RedisSessionStore.class)).isNotNull();
    assertThat(redis.getRequiredConnectionFactory().getConnection().ping()).isEqualTo("PONG");
  }

  @Test
  void should_handle_requests_on_virtual_threads() {
    restClient.get().uri("/actuator/health").exchange().expectStatus().isOk();

    assertThat(probe.lastRequestOnVirtualThread()).isTrue();
  }

  /// 记录最近一次请求是否跑在虚拟线程上。
  @TestConfiguration(proxyBeanMethods = false)
  static class ProbeConfiguration {

    /// 注册探针过滤器；Boot 会把容器里的 Filter Bean 挂到 servlet 容器上。
    ///
    /// @return 探针
    @Bean
    RequestThreadProbe requestThreadProbe() {
      return new RequestThreadProbe();
    }
  }

  /// 探针过滤器。
  static class RequestThreadProbe extends OncePerRequestFilter {

    private final AtomicReference<Boolean> virtual = new AtomicReference<>();

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      virtual.set(Thread.currentThread().isVirtual());
      chain.doFilter(request, response);
    }

    /// 最近一次请求是否在虚拟线程上。
    ///
    /// @return 是则 true
    boolean lastRequestOnVirtualThread() {
      return Boolean.TRUE.equals(virtual.get());
    }
  }
}
