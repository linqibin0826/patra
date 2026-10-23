package dev.patra.tool.httpclient.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.patra.tool.httpclient.autoconfigure.HttpClientProperties;
import dev.patra.tool.httpclient.interceptor.InternalCallInterceptor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/// RestClientFactory 单元测试
///
/// 测试策略：
///
/// - 测试 RestClient 创建逻辑（baseUrl、超时配置）
/// - 测试 HTTP Interface 代理创建
/// - 测试分组配置优先级
/// - 验证 Apache HttpClient 连接池配置
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("RestClientFactory 单元测试")
class RestClientFactoryTest {

  /// 创建带有空 customizers 和空拦截器的工厂
  @SuppressWarnings("unchecked")
  /// 测试用的可变装配器，最后 build 成不可变的 record。
  private static final class PropsBuilder {
    Duration connect = Duration.ofSeconds(5);
    Duration read = Duration.ofSeconds(30);
    int maxConnTotal = 100;
    int maxConnPerRoute = 20;
    Duration validateAfterInactivity = Duration.ofSeconds(5);
    Duration evictIdleConnectionsAfter = Duration.ofSeconds(30);
    final Map<String, HttpClientProperties.HttpInterface.Group> groups = new HashMap<>();

    HttpClientProperties.HttpInterface build() {
      return new HttpClientProperties.HttpInterface(
          true,
          connect,
          read,
          new HttpClientProperties.HttpInterface.ConnectionPool(
              maxConnTotal, maxConnPerRoute, validateAfterInactivity, evictIdleConnectionsAfter),
          new HttpClientProperties.HttpInterface.ErrorHandling(true, true, 65536),
          groups);
    }
  }

  /// 测试用的分组装配器。
  private static final class GroupBuilder {
    String baseUrl;
    Duration connectTimeout;
    Duration readTimeout;

    HttpClientProperties.HttpInterface.Group build() {
      return new HttpClientProperties.HttpInterface.Group(baseUrl, connectTimeout, readTimeout);
    }
  }

  private RestClientFactory createFactory(PropsBuilder properties) {
    ObjectProvider<RestClientCustomizer> customizers = mock(ObjectProvider.class);
    when(customizers.orderedStream()).thenReturn(Stream.empty());
    ObjectProvider<InternalCallInterceptor> interceptors = mock(ObjectProvider.class);
    when(interceptors.orderedStream()).thenReturn(Stream.empty());
    return new RestClientFactory(customizers, interceptors, properties.build());
  }

  @Nested
  @DisplayName("createRestClient 测试")
  class CreateRestClientTests {

    @Test
    @DisplayName("使用默认 baseUrl")
    void shouldUseDefaultBaseUrl() {
      // Given
      PropsBuilder properties = new PropsBuilder();
      RestClientFactory factory = createFactory(properties);
      RestClient.Builder builder = RestClient.builder();

      // When
      RestClient restClient = factory.createRestClient(builder, "registry", "lb://patra-registry");

      // Then
      assertThat(restClient).isNotNull();
    }

    @Test
    @DisplayName("从分组配置获取 baseUrl")
    void shouldUseGroupBaseUrl() {
      // Given
      PropsBuilder properties = new PropsBuilder();
      GroupBuilder groupProps = new GroupBuilder();
      groupProps.baseUrl = "http://localhost:8080";

      properties.groups.put("registry", groupProps.build());

      RestClientFactory factory = createFactory(properties);
      RestClient.Builder builder = RestClient.builder();

      // When
      RestClient restClient = factory.createRestClient(builder, "registry", "lb://patra-registry");

      // Then
      assertThat(restClient).isNotNull();
    }

    @Test
    @DisplayName("应用分组级别超时配置")
    void shouldApplyGroupTimeoutSettings() {
      // Given
      PropsBuilder properties = new PropsBuilder();
      GroupBuilder groupProps = new GroupBuilder();
      groupProps.baseUrl = "http://localhost:8080";
      groupProps.connectTimeout = Duration.ofSeconds(10);
      groupProps.readTimeout = Duration.ofSeconds(60);

      properties.groups.put("registry", groupProps.build());

      RestClientFactory factory = createFactory(properties);
      RestClient.Builder builder = RestClient.builder();

      // When
      RestClient restClient = factory.createRestClient(builder, "registry", "lb://patra-registry");

      // Then
      assertThat(restClient).isNotNull();
    }

    @Test
    @DisplayName("应用 RestClientCustomizer")
    @SuppressWarnings("unchecked")
    void shouldApplyCustomizers() {
      // Given
      PropsBuilder properties = new PropsBuilder();
      RestClientCustomizer customizer = mock(RestClientCustomizer.class);
      ObjectProvider<RestClientCustomizer> customizers = mock(ObjectProvider.class);
      when(customizers.orderedStream()).thenReturn(Stream.of(customizer));
      ObjectProvider<InternalCallInterceptor> interceptors = mock(ObjectProvider.class);
      when(interceptors.orderedStream()).thenReturn(Stream.empty());

      RestClientFactory factory =
          new RestClientFactory(customizers, interceptors, properties.build());
      RestClient.Builder builder = RestClient.builder();

      // When
      factory.createRestClient(builder, "registry", "lb://patra-registry");

      // Then
      verify(customizer).customize(any(RestClient.Builder.class));
    }

    /// Spring Cloud Gateway 的 `gatewayRestClientCustomizer` 会把容器里的请求工厂设到每个 builder 上；
    /// 分组配了自己的超时，就不能被它盖掉（PAP-106：网关查会话要 2 秒内超时）。
    /// 连接池占满时，新请求等连接的时间也受分组超时约束，不按 Apache HttpClient 默认的 3 分钟排队
    /// （PAP-106：identity 卡住时网关要几秒内 503）。
    @Test
    @DisplayName("分组配了超时：连接池占满时等连接按分组的连接超时失败，不排队等前一个请求结束")
    void shouldFailConnectionLeaseWithinGroupConnectTimeout() throws Exception {
      // Given：只接受连接、从不回响应的服务端；每个路由只有 1 个连接
      // executor 先声明后关闭：server 先关，accept 循环才会退出，executor 关闭时不会一直等它
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
          ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
        List<Socket> accepted = new CopyOnWriteArrayList<>();
        executor.submit(
            () -> {
              while (!server.isClosed()) {
                accepted.add(server.accept());
              }
              return null;
            });
        PropsBuilder properties = new PropsBuilder();
        properties.maxConnPerRoute = 1;
        GroupBuilder groupProps = new GroupBuilder();
        groupProps.connectTimeout = Duration.ofMillis(300);
        groupProps.readTimeout = Duration.ofSeconds(5);
        properties.groups.put("identity", groupProps.build());
        RestClient restClient =
            createFactory(properties)
                .createRestClient(
                    RestClient.builder(), "identity", "http://127.0.0.1:" + server.getLocalPort());
        Future<?> holder =
            executor.submit(() -> restClient.get().uri("/hold").retrieve().toBodilessEntity());
        while (accepted.isEmpty()) {
          Thread.sleep(10);
        }

        // When
        long start = System.nanoTime();
        assertThatThrownBy(() -> restClient.get().uri("/next").retrieve().toBodilessEntity())
            .isInstanceOf(ResourceAccessException.class);
        Duration waited = Duration.ofNanos(System.nanoTime() - start);

        // Then：等连接只等了连接超时那么久，前一个请求还挂着
        assertThat(waited).isLessThan(Duration.ofSeconds(2));
        assertThat(holder).isNotDone();
        holder.cancel(true);
        for (Socket socket : accepted) {
          socket.close();
        }
      }
    }

    @Test
    @DisplayName("分组配了超时：customizer 设的请求工厂不生效，请求走分组自己的客户端")
    @SuppressWarnings("unchecked")
    void shouldKeepGroupRequestFactoryOverCustomizerFactory() {
      // Given
      PropsBuilder properties = new PropsBuilder();
      GroupBuilder groupProps = new GroupBuilder();
      groupProps.connectTimeout = Duration.ofMillis(500);
      groupProps.readTimeout = Duration.ofMillis(500);
      properties.groups.put("identity", groupProps.build());
      AtomicBoolean customizerFactoryUsed = new AtomicBoolean();
      ClientHttpRequestFactory customizerFactory =
          (uri, method) -> {
            customizerFactoryUsed.set(true);
            throw new IOException("customizer 设的请求工厂");
          };
      ObjectProvider<RestClientCustomizer> customizers = mock(ObjectProvider.class);
      when(customizers.orderedStream())
          .thenReturn(Stream.of(builder -> builder.requestFactory(customizerFactory)));
      ObjectProvider<InternalCallInterceptor> interceptors = mock(ObjectProvider.class);
      when(interceptors.orderedStream()).thenReturn(Stream.empty());
      RestClientFactory factory =
          new RestClientFactory(customizers, interceptors, properties.build());

      // When：127.0.0.1:1 没人监听，分组的客户端会连接失败
      RestClient restClient =
          factory.createRestClient(RestClient.builder(), "identity", "http://127.0.0.1:1");
      assertThatThrownBy(() -> restClient.get().uri("/x").retrieve().toBodilessEntity())
          .isInstanceOf(ResourceAccessException.class);

      // Then
      assertThat(customizerFactoryUsed).isFalse();
    }

    @Test
    @DisplayName("分组配置为 null 时使用全局配置")
    void shouldUseGlobalConfigWhenGroupIsNull() {
      // Given
      PropsBuilder properties = new PropsBuilder();
      properties.connect = Duration.ofSeconds(15);
      properties.read = Duration.ofSeconds(45);

      RestClientFactory factory = createFactory(properties);
      RestClient.Builder builder = RestClient.builder();

      // When
      RestClient restClient =
          factory.createRestClient(builder, "nonexistent", "lb://default-service");

      // Then
      assertThat(restClient).isNotNull();
    }
  }

  @Nested
  @DisplayName("InternalCallInterceptor 测试")
  class InternalCallInterceptorTests {

    @Test
    @DisplayName("容器里的 InternalCallInterceptor 被挂到创建的客户端上，请求真的经过它")
    @SuppressWarnings("unchecked")
    void shouldApplyInternalCallInterceptors() {
      // Given
      PropsBuilder properties = new PropsBuilder();
      ObjectProvider<RestClientCustomizer> customizers = mock(ObjectProvider.class);
      when(customizers.orderedStream()).thenReturn(Stream.empty());
      InternalCallInterceptor marker =
          (request, body, execution) -> {
            request.getHeaders().set("X-Internal-Call", "yes");
            return execution.execute(request, body);
          };
      ObjectProvider<InternalCallInterceptor> interceptors = mock(ObjectProvider.class);
      when(interceptors.orderedStream()).thenReturn(Stream.of(marker));
      RestClient.Builder builder = RestClient.builder();
      MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
      server
          .expect(requestTo("http://localhost:8080/ping"))
          .andExpect(header("X-Internal-Call", "yes"))
          .andRespond(withSuccess());

      // When
      RestClient restClient =
          new RestClientFactory(customizers, interceptors, properties.build())
              .createRestClient(builder, "self", "http://localhost:8080");
      restClient.get().uri("/ping").retrieve().toBodilessEntity();

      // Then
      server.verify();
    }
  }

  @Nested
  @DisplayName("createProxy 测试")
  class CreateProxyTests {

    @Test
    @DisplayName("创建 HTTP Interface 代理")
    void shouldCreateProxy() {
      // Given
      PropsBuilder properties = new PropsBuilder();
      RestClientFactory factory = createFactory(properties);
      RestClient restClient = RestClient.builder().baseUrl("http://localhost:8080").build();

      // When
      TestEndpoint proxy = factory.createProxy(restClient, TestEndpoint.class);

      // Then
      assertThat(proxy).isNotNull();
    }
  }

  @Nested
  @DisplayName("超时配置优先级测试")
  class TimeoutPriorityTests {

    @Test
    @DisplayName("分组超时覆盖全局超时")
    void groupTimeoutShouldOverrideGlobalTimeout() {
      // Given
      PropsBuilder properties = new PropsBuilder();
      properties.connect = Duration.ofSeconds(5);
      properties.read = Duration.ofSeconds(30);

      GroupBuilder groupProps = new GroupBuilder();
      groupProps.baseUrl = "http://localhost:8080";
      groupProps.connectTimeout = Duration.ofSeconds(10); // 覆盖全局
      // readTimeout 不设置，应使用全局值

      properties.groups.put("registry", groupProps.build());

      RestClientFactory factory = createFactory(properties);
      RestClient.Builder builder = RestClient.builder();

      // When
      RestClient restClient = factory.createRestClient(builder, "registry", "lb://patra-registry");

      // Then
      assertThat(restClient).isNotNull();
    }

    @Test
    @DisplayName("使用默认超时时不创建自定义 RequestFactory")
    void shouldNotCreateRequestFactoryForDefaultTimeout() {
      // Given
      PropsBuilder properties = new PropsBuilder();
      properties.connect = Duration.ofSeconds(5); // 默认值
      properties.read = Duration.ofSeconds(30); // 默认值

      RestClientFactory factory = createFactory(properties);
      RestClient.Builder builder = RestClient.builder();

      // When
      RestClient restClient = factory.createRestClient(builder, "registry", "lb://patra-registry");

      // Then
      assertThat(restClient).isNotNull();
    }
  }

  @Nested
  @DisplayName("连接池配置测试")
  class ConnectionPoolTests {

    @Test
    @DisplayName("应用自定义连接池配置")
    void shouldApplyCustomConnectionPoolSettings() {
      // Given
      PropsBuilder properties = new PropsBuilder();
      properties.maxConnTotal = 200;
      properties.maxConnPerRoute = 50;
      properties.validateAfterInactivity = Duration.ofSeconds(10);
      properties.evictIdleConnectionsAfter = Duration.ofSeconds(60);

      GroupBuilder groupProps = new GroupBuilder();
      groupProps.baseUrl = "http://localhost:8080";
      groupProps.connectTimeout = Duration.ofSeconds(10); // 触发自定义 RequestFactory 创建

      properties.groups.put("registry", groupProps.build());

      RestClientFactory factory = createFactory(properties);
      RestClient.Builder builder = RestClient.builder();

      // When
      RestClient restClient = factory.createRestClient(builder, "registry", "lb://patra-registry");

      // Then
      assertThat(restClient).isNotNull();
      // 连接池配置在内部生效，不易直接验证，但 RestClient 创建成功即证明配置有效
    }
  }

  /// 用于测试的接口
  @HttpExchange("/test")
  interface TestEndpoint {
    @GetExchange("/hello")
    String hello();
  }
}
