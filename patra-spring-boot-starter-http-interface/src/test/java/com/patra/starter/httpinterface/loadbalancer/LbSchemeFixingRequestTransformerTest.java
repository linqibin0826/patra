package com.patra.starter.httpinterface.loadbalancer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;

/// LbSchemeFixingRequestTransformer 单元测试
///
/// 验证 lb:// scheme 修复转换器的正确行为
@DisplayName("LbSchemeFixingRequestTransformer 单元测试")
class LbSchemeFixingRequestTransformerTest {

  private LbSchemeFixingRequestTransformer transformer;
  private ServiceInstance serviceInstance;

  @BeforeEach
  void setUp() {
    transformer = new LbSchemeFixingRequestTransformer();
    serviceInstance = mock(ServiceInstance.class);
    when(serviceInstance.getHost()).thenReturn("192.168.1.100");
    when(serviceInstance.getPort()).thenReturn(8080);
  }

  @Nested
  @DisplayName("lb:// scheme 修复测试")
  class LbSchemeFixingTests {

    @Test
    @DisplayName("lb:// scheme 应替换为 http://（非安全服务）")
    void shouldReplaceLbSchemeWithHttp_whenServiceIsNotSecure() throws Exception {
      // Given
      when(serviceInstance.isSecure()).thenReturn(false);
      URI originalUri = new URI("lb://patra-registry/api/v1/dictionary/resolve");
      HttpRequest request = new MockClientHttpRequest(HttpMethod.GET, originalUri);

      // When
      HttpRequest transformed = transformer.transformRequest(request, serviceInstance);

      // Then
      assertThat(transformed.getURI().getScheme()).isEqualTo("http");
      assertThat(transformed.getURI().getHost()).isEqualTo("patra-registry");
      assertThat(transformed.getURI().getPath()).isEqualTo("/api/v1/dictionary/resolve");
    }

    @Test
    @DisplayName("lb:// scheme 应替换为 https://（安全服务）")
    void shouldReplaceLbSchemeWithHttps_whenServiceIsSecure() throws Exception {
      // Given
      when(serviceInstance.isSecure()).thenReturn(true);
      URI originalUri = new URI("lb://patra-registry/api/v1/dictionary/resolve");
      HttpRequest request = new MockClientHttpRequest(HttpMethod.GET, originalUri);

      // When
      HttpRequest transformed = transformer.transformRequest(request, serviceInstance);

      // Then
      assertThat(transformed.getURI().getScheme()).isEqualTo("https");
    }

    @Test
    @DisplayName("应保留 URI 的其他部分（路径、查询参数、端口）")
    void shouldPreserveOtherUriComponents() throws Exception {
      // Given
      when(serviceInstance.isSecure()).thenReturn(false);
      URI originalUri = new URI("lb://patra-registry:8080/api/v1/dictionary?type=country&standard=ISO");
      HttpRequest request = new MockClientHttpRequest(HttpMethod.POST, originalUri);

      // When
      HttpRequest transformed = transformer.transformRequest(request, serviceInstance);

      // Then
      URI transformedUri = transformed.getURI();
      assertThat(transformedUri.getScheme()).isEqualTo("http");
      assertThat(transformedUri.getHost()).isEqualTo("patra-registry");
      assertThat(transformedUri.getPort()).isEqualTo(8080);
      assertThat(transformedUri.getPath()).isEqualTo("/api/v1/dictionary");
      assertThat(transformedUri.getQuery()).isEqualTo("type=country&standard=ISO");
    }
  }

  @Nested
  @DisplayName("非 lb:// scheme 处理测试")
  class NonLbSchemeTests {

    @Test
    @DisplayName("http:// scheme 应保持不变")
    void shouldNotModifyHttpScheme() throws Exception {
      // Given
      URI originalUri = new URI("http://192.168.1.100:8080/api/v1/dictionary");
      HttpRequest request = new MockClientHttpRequest(HttpMethod.GET, originalUri);

      // When
      HttpRequest transformed = transformer.transformRequest(request, serviceInstance);

      // Then
      assertThat(transformed.getURI()).isEqualTo(originalUri);
      assertThat(transformed).isSameAs(request); // 应返回原始请求对象
    }

    @Test
    @DisplayName("https:// scheme 应保持不变")
    void shouldNotModifyHttpsScheme() throws Exception {
      // Given
      URI originalUri = new URI("https://192.168.1.100:8443/api/v1/dictionary");
      HttpRequest request = new MockClientHttpRequest(HttpMethod.GET, originalUri);

      // When
      HttpRequest transformed = transformer.transformRequest(request, serviceInstance);

      // Then
      assertThat(transformed.getURI()).isEqualTo(originalUri);
      assertThat(transformed).isSameAs(request);
    }

    @Test
    @DisplayName("其他 scheme（如 ws://）应保持不变")
    void shouldNotModifyOtherSchemes() throws Exception {
      // Given
      URI originalUri = new URI("ws://192.168.1.100:8080/websocket");
      HttpRequest request = new MockClientHttpRequest(HttpMethod.GET, originalUri);

      // When
      HttpRequest transformed = transformer.transformRequest(request, serviceInstance);

      // Then
      assertThat(transformed.getURI()).isEqualTo(originalUri);
      assertThat(transformed).isSameAs(request);
    }
  }

  @Nested
  @DisplayName("HTTP 方法保留测试")
  class HttpMethodPreservationTests {

    @Test
    @DisplayName("应保留原始请求的 HTTP 方法")
    void shouldPreserveHttpMethod() throws Exception {
      // Given
      when(serviceInstance.isSecure()).thenReturn(false);
      URI originalUri = new URI("lb://patra-registry/api/v1/dictionary");
      HttpRequest request = new MockClientHttpRequest(HttpMethod.POST, originalUri);

      // When
      HttpRequest transformed = transformer.transformRequest(request, serviceInstance);

      // Then
      assertThat(transformed.getMethod()).isEqualTo(HttpMethod.POST);
    }
  }
}
