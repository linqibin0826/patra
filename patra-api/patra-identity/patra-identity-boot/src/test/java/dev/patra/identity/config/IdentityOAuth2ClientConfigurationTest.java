package dev.patra.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.config.IdentityProperties.OAuth.Outbound;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.HttpMethod;

/// 访问第三方平台的超时：用连得上、但一个字节都不回的服务端，看请求到底在什么时候放弃。
///
/// 服务端不能中途断开：断开后 JDK 客户端可能自己重连，结束时间和报错就不确定了。
@DisplayName("IdentityOAuth2ClientConfiguration 出站超时")
class IdentityOAuth2ClientConfigurationTest {

  private static Outbound defaults() {
    return new Binder(new MapConfigurationPropertySource(Map.of()))
        .bindOrCreate("patra.identity.oauth.outbound", Outbound.class);
  }

  /// 只监听不接受的端口：系统替它完成 TCP 握手，之后一个字节都不回。
  private static URI uri(String scheme, ServerSocket silent) {
    return URI.create(scheme + "://127.0.0.1:" + silent.getLocalPort() + "/");
  }

  /// 发一个请求。
  private static void execute(Outbound outbound, URI uri) throws IOException {
    IdentityOAuth2ClientConfiguration.outboundRequestFactory(outbound)
        .createRequest(uri, HttpMethod.GET)
        .execute()
        .close();
  }

  /// 发一个请求，返回从发出到失败用了多久；断言它确实失败了。
  private static Duration failingRequest(
      Outbound outbound, URI uri, Class<? extends IOException> type) {
    long start = System.nanoTime();
    assertThatThrownBy(() -> execute(outbound, uri)).isInstanceOf(type);
    return Duration.ofNanos(System.nanoTime() - start);
  }

  /// 线上的出口 TLS 握手要 2.3～3.2 秒，还有波动。默认值要等得起比这慢一倍的握手：既不能被建连上限掐断，
  /// 也不能被一次请求的总上限掐断。原来的 3 秒和 5 秒都等不到第 6 秒。
  @Test
  @DisplayName("TLS 握手慢到 6 秒：默认值还在等，没有放弃")
  void should_still_wait_for_slow_tls_handshake_with_defaults() throws Exception {
    try (ServerSocket silent = new ServerSocket(0);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<?> request =
          executor.submit(
              () -> {
                execute(defaults(), uri("https", silent));
                return null;
              });

      Thread.sleep(Duration.ofSeconds(6));

      assertThat(request.isDone()).as("第 6 秒请求还在等握手").isFalse();
      request.cancel(true);
    }
  }

  @Test
  @DisplayName("默认值：建连 10 秒，一次请求 15 秒")
  void should_default_to_ten_and_fifteen_seconds() {
    assertThat(defaults().connectTimeout()).isEqualTo(Duration.ofSeconds(10));
    assertThat(defaults().requestTimeout()).isEqualTo(Duration.ofSeconds(15));
  }

  @Test
  @DisplayName("建连上限含 TLS 握手：TCP 连上了，握手没完成，到点放弃")
  void should_count_tls_handshake_in_connect_timeout() throws Exception {
    try (ServerSocket silent = new ServerSocket(0)) {
      Duration elapsed =
          failingRequest(
              new Outbound(Duration.ofMillis(300), Duration.ofSeconds(20)),
              uri("https", silent),
              HttpConnectTimeoutException.class);

      assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }
  }

  @Test
  @DisplayName("总上限管住不回响应的平台：连上了、发完了、一直不回，到点放弃")
  void should_give_up_on_silent_provider_at_request_timeout() throws Exception {
    try (ServerSocket silent = new ServerSocket(0)) {
      Duration elapsed =
          failingRequest(
              new Outbound(Duration.ofMillis(100), Duration.ofMillis(500)),
              uri("http", silent),
              IOException.class);

      assertThat(elapsed)
          .isGreaterThanOrEqualTo(Duration.ofMillis(400))
          .isLessThan(Duration.ofSeconds(5));
    }
  }

  @Test
  @DisplayName("总上限不大于建连上限时启动失败：否则建连上限不起作用")
  void should_reject_request_timeout_not_above_connect_timeout() {
    assertThatThrownBy(() -> new Outbound(Duration.ofSeconds(10), Duration.ofSeconds(10)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
