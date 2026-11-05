package dev.patra.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.config.IdentityProperties.OAuth.Outbound;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.HttpMethod;

/// 访问第三方平台的超时：用只接受连接、不说话的服务端，看请求到底在什么时候、以什么方式放弃。
@DisplayName("IdentityOAuth2ClientConfiguration 出站超时")
class IdentityOAuth2ClientConfigurationTest {

  private static Outbound defaults() {
    return new Binder(new MapConfigurationPropertySource(Map.of()))
        .bindOrCreate("patra.identity.oauth.outbound", Outbound.class);
  }

  /// 发一个请求，返回从发出到失败用了多久；断言它确实失败了。
  private static Duration failingRequest(
      Outbound outbound, URI uri, Class<? extends IOException> type) {
    long start = System.nanoTime();
    assertThatThrownBy(
            () ->
                IdentityOAuth2ClientConfiguration.outboundRequestFactory(outbound)
                    .createRequest(uri, HttpMethod.GET)
                    .execute())
        .isInstanceOf(type);
    return Duration.ofNanos(System.nanoTime() - start);
  }

  /// 线上的出口 TLS 握手要 2.3～3.2 秒，还有波动。默认值要等得起比这慢一倍的握手：既不能被建连上限掐断，
  /// 也不能被一次请求的总上限掐断。这里握手最后是对方断开，所以报的不是超时。
  @Test
  @DisplayName("TLS 握手慢到 6 秒：默认值还在等，不会超时")
  void should_wait_for_slow_tls_handshake_with_defaults() throws Exception {
    Duration handshake = Duration.ofSeconds(6);
    try (SilentServer server = SilentServer.closingAfter(handshake)) {
      long start = System.nanoTime();
      assertThatThrownBy(
              () ->
                  IdentityOAuth2ClientConfiguration.outboundRequestFactory(defaults())
                      .createRequest(server.uri("https"), HttpMethod.GET)
                      .execute())
          .isInstanceOf(IOException.class)
          .isNotInstanceOf(HttpTimeoutException.class);

      assertThat(Duration.ofNanos(System.nanoTime() - start))
          .isGreaterThanOrEqualTo(handshake.minusMillis(300));
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
    try (SilentServer server = SilentServer.closingAfter(Duration.ofSeconds(30))) {
      Duration elapsed =
          failingRequest(
              new Outbound(Duration.ofMillis(300), Duration.ofSeconds(20)),
              server.uri("https"),
              HttpConnectTimeoutException.class);

      assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }
  }

  @Test
  @DisplayName("总上限管住不回响应的平台：连上了、发完了、一直不回，到点放弃")
  void should_give_up_on_silent_provider_at_request_timeout() throws Exception {
    try (SilentServer server = SilentServer.closingAfter(Duration.ofSeconds(30))) {
      Duration elapsed =
          failingRequest(
              new Outbound(Duration.ofMillis(100), Duration.ofMillis(500)),
              server.uri("http"),
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

  /// 只接受连接、一个字节都不回的服务端，到点断开。
  private static final class SilentServer implements AutoCloseable {

    private final ServerSocket socket;
    private final Thread acceptor;

    private SilentServer(ServerSocket socket, Thread acceptor) {
      this.socket = socket;
      this.acceptor = acceptor;
    }

    /// 启动。
    ///
    /// @param after 接受连接之后多久断开
    /// @return 服务端
    static SilentServer closingAfter(Duration after) throws IOException {
      ServerSocket socket = new ServerSocket(0);
      Thread acceptor =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try (Socket client = socket.accept()) {
                      Thread.sleep(after);
                    } catch (IOException | InterruptedException _) {
                      // 用例结束时关闭，这里没有要做的
                    }
                  });
      return new SilentServer(socket, acceptor);
    }

    /// 服务端地址。
    ///
    /// @param scheme `http` 或 `https`
    /// @return 地址
    URI uri(String scheme) {
      return URI.create(scheme + "://127.0.0.1:" + socket.getLocalPort() + "/");
    }

    /// 关闭。
    @Override
    public void close() throws IOException {
      acceptor.interrupt();
      socket.close();
    }
  }
}
