package dev.patra.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 下游停住超过读超时（设计第 6、8 节）：发完响应头和一部分响应体后停住，网关切断响应体；
/// 不读请求体，网关写不下去，回 504。
///
/// WireMock 的分块延迟在第一块之前就开始等，响应头要等第一块一起发，模拟不出「头已到、体停住」，
/// 所以这里用 JDK 自带的 `HttpServer` 当 catalog：先发头和半截体并 flush，再停 2 秒（`test` profile
/// 的读超时是 1 秒），最后发剩下的一半。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = GatewayITSigningKeyInitializer.class)
class GatewayStalledDownstreamIT {

  private static final int HALF_BODY_LENGTH = 5_000;

  private static final long STALL_MILLIS = 2_000;

  /// 上传 32 MB：远大于本机回环的 socket 缓冲，下游不读时网关的写一定会卡住
  private static final int UPLOAD_LENGTH = 32 * 1024 * 1024;

  /// 不读请求体的下游挂住的时长，远超 1 秒读超时
  private static final long UNREAD_HOLD_MILLIS = 8_000;

  private static final HttpServer catalog = startStallingCatalog();

  @Autowired private RestTestClient restClient;
  @Autowired private Environment environment;

  @DynamicPropertySource
  static void downstreams(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri",
        () -> "http://127.0.0.1:" + catalog.getAddress().getPort());
  }

  @AfterAll
  static void stopCatalog() {
    catalog.stop(0);
  }

  /// 流式响应逐块 flush、状态码已经发出，网关不再往后拼任何东西，Tomcat 干净地结束分块传输：
  /// 客户端拿到 200 和一段只含已到字节的半截响应体。
  @Test
  void should_end_committed_streaming_response_cleanly_when_downstream_stalls() throws Exception {
    HttpResponse<String> response;
    try (HttpClient client = HttpClient.newHttpClient()) {
      response =
          client.send(
              HttpRequest.newBuilder(URI.create(gatewayUrl("/patra-catalog/stalled-stream")))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
    }

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isNotEmpty().hasSizeLessThan(2 * HALF_BODY_LENGTH).matches("x+");
  }

  /// 非流式响应此时还没提交（Tomcat 缓冲没满）：网关清掉半截响应体，回一份完整的 ProblemDetail。
  /// 断在响应体阶段时异常不经 `GatewayProxyFailureAdvice` 分类，按 500 走。
  @Test
  void should_answer_500_problem_detail_when_downstream_stalls_before_commit() {
    restClient
        .get()
        .uri("/patra-catalog/stalled-body")
        .exchange()
        .expectStatus()
        .isEqualTo(500)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GW-0500")
        .jsonPath("$.status")
        .isEqualTo(500);
  }

  /// 下游收下请求头后迟迟不读请求体（比如处理器卡住），网关往下游写请求体会卡在缓冲已满之后。
  /// 写也要有上限：停住超过读超时就放弃，回 504，而不是一直挂着等下游。
  ///
  /// 网关回 504 时客户端多半还在上传，剩下的请求体远超 Tomcat 愿意吞掉的量（`max-swallow-size`
  /// 默认 2 MB），Tomcat 回完就关连接，客户端可能只看到连接被重置。所以这里断言网关在下游挂住期间
  /// 就结束了这次交换；能读到响应时它必须是 504。写超时归 504 由 `ProxyFailureClassifierTest` 钉住。
  @Test
  void should_give_up_with_504_when_downstream_stops_reading_request_body() throws Exception {
    byte[] upload = new byte[UPLOAD_LENGTH];
    long start = System.nanoTime();

    Integer status = null;
    try (HttpClient client = HttpClient.newHttpClient()) {
      status =
          client
              .send(
                  HttpRequest.newBuilder(URI.create(gatewayUrl("/patra-catalog/unread-upload")))
                      .POST(HttpRequest.BodyPublishers.ofByteArray(upload))
                      .build(),
                  HttpResponse.BodyHandlers.discarding())
              .statusCode();
    } catch (IOException resetWhileUploading) {
      // 网关已回 504 并关闭连接，客户端还在上传：读不到响应，但交换已经结束
    }
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

    assertThat(elapsed).isLessThan(Duration.ofMillis(UNREAD_HOLD_MILLIS / 2));
    if (status != null) {
      assertThat(status).isEqualTo(504);
    }
  }

  /// 起一个会中途停住的 catalog：两个路径只差 `Content-Type`。
  ///
  /// @return 已启动的服务器
  private static HttpServer startStallingCatalog() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/stalled-stream", exchange -> stall(exchange, "text/event-stream"));
      server.createContext("/stalled-body", exchange -> stall(exchange, "text/plain"));
      server.createContext("/unread-upload", GatewayStalledDownstreamIT::holdWithoutReading);
      // 每个请求一个虚拟线程：不读请求体的那个处理器挂着时，不挡住别的用例
      server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
      server.start();
      return server;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// 发响应头和半截响应体，停住超过读超时，再发另一半。网关切断连接后最后一次写会失败，忽略即可。
  ///
  /// @param exchange 当前请求
  /// @param contentType 响应的内容类型
  private static void stall(HttpExchange exchange, String contentType) throws IOException {
    byte[] half = "x".repeat(HALF_BODY_LENGTH).getBytes(StandardCharsets.US_ASCII);
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(200, 0);
    try (OutputStream body = exchange.getResponseBody()) {
      body.write(half);
      body.flush();
      Thread.sleep(STALL_MILLIS);
      body.write(half);
    } catch (IOException | InterruptedException cutByGateway) {
      // 网关已经断开：这正是要验证的行为
    }
  }

  /// 不读请求体，挂住远超读超时之后才回 200。网关若不设写超时，就会一直等到这里回应。
  ///
  /// @param exchange 当前请求
  private static void holdWithoutReading(HttpExchange exchange) throws IOException {
    try {
      Thread.sleep(UNREAD_HOLD_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    exchange.sendResponseHeaders(200, -1);
    exchange.close();
  }

  private String gatewayUrl(String path) {
    return "http://localhost:" + environment.getRequiredProperty("local.server.port") + path;
  }
}
