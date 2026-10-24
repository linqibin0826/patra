package dev.patra.tool.web.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.tool.web.error.probe.CommittedResponseProbeController;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/// 响应已提交（状态行和响应头已发出）后再出错时，连接必须被中止：客户端收不到分块终止块，
/// 能识别出这是一次失败的传输，而不是一个看起来完整的 200。
///
/// 要看 TCP 层面的中止，只能起真实 Tomcat、用真实客户端；MockMvc 和 `RestTestClient` 看不到。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CommittedResponseProbeController.class)
@DisplayName("响应已提交后出错时中止连接")
class CommittedResponseAbortIT {

  /// 分块传输的终止块：前一块的结尾、长度为 0 的块、空行。
  private static final String CHUNKED_TERMINATOR = "\r\n0\r\n\r\n";

  private static final int READ_TIMEOUT_MILLIS = 10_000;

  @LocalServerPort private int port;

  @Test
  @DisplayName("流式写出超过缓冲后失败：中止连接")
  void should_abort_connection_when_streaming_body_fails_after_commit() throws Exception {
    assertAborted("/probe/committed/streaming");
  }

  @Test
  @DisplayName("同步写出并 flush 后失败：中止连接")
  void should_abort_connection_when_synchronous_write_fails_after_commit() throws Exception {
    assertAborted("/probe/committed/sync");
  }

  @Test
  @DisplayName("流式写出还没提交就失败：仍回完整的 ProblemDetail")
  void should_render_problem_detail_when_streaming_fails_before_commit() throws Exception {
    HttpResponse<String> response;
    try (HttpClient client = HttpClient.newHttpClient()) {
      response =
          client.send(
              HttpRequest.newBuilder(uri("/probe/committed/streaming-small")).build(),
              HttpResponse.BodyHandlers.ofString());
    }

    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    assertThat(response.body()).contains("\"code\":\"TEST-0422\"").doesNotContain("partial");
  }

  /// 断言响应头已发出、连接随后被中止：裸 socket 读到 200 状态行但读不到分块终止块，
  /// JDK 客户端读正文时抛 `IOException`。
  ///
  /// @param path 请求路径
  /// @throws Exception 读写 socket 失败
  private void assertAborted(String path) throws Exception {
    String raw = rawExchange(path);
    assertThat(raw).startsWith("HTTP/1.1 200");
    assertThat(raw).doesNotContain(CHUNKED_TERMINATOR);

    try (HttpClient client = HttpClient.newHttpClient()) {
      assertThatThrownBy(
              () ->
                  client.send(
                      HttpRequest.newBuilder(uri(path)).build(),
                      HttpResponse.BodyHandlers.ofString()))
          .isInstanceOf(IOException.class);
    }
  }

  /// 用裸 socket 发一次 GET，读到连接关闭、连接异常或分块终止块为止。
  ///
  /// 正常结束的响应在 keep-alive 下不会关连接，所以读到终止块就停，不等超时。
  ///
  /// @param path 请求路径
  /// @return 收到的全部字节（UTF-8 解码）
  /// @throws IOException 建立连接或发送请求失败
  private String rawExchange(String path) throws IOException {
    ByteArrayOutputStream received = new ByteArrayOutputStream();
    try (Socket socket = new Socket("localhost", port)) {
      socket.setSoTimeout(READ_TIMEOUT_MILLIS);
      socket
          .getOutputStream()
          .write(
              ("GET " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n")
                  .getBytes(StandardCharsets.US_ASCII));
      InputStream in = socket.getInputStream();
      byte[] buffer = new byte[8192];
      try {
        int read;
        while ((read = in.read(buffer)) != -1) {
          received.write(buffer, 0, read);
          if (received.toString(StandardCharsets.UTF_8).endsWith(CHUNKED_TERMINATOR)) {
            break;
          }
        }
      } catch (IOException connectionAborted) {
        // 连接被重置也是中止的一种形态，已收到的字节照样返回
      }
    }
    return received.toString(StandardCharsets.UTF_8);
  }

  /// 拼出本次测试服务器上的地址。
  ///
  /// @param path 请求路径
  /// @return 完整地址
  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }
}
