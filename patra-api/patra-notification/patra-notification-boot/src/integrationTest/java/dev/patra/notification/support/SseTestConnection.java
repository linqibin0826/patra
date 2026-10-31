package dev.patra.notification.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/// 测试里的一条推送连接：JDK HTTP 客户端开流，后台线程逐行读进队列，用例按「事件」「注释」「流结束」取。
///
/// 流正常结束时队列里放一个结束标记；读的时候出 I/O 错误（不是正常结束）也放结束标记，并记下错误。
public final class SseTestConnection implements AutoCloseable {

  /// 流结束的标记（正常的行里不会出现 NUL）。
  private static final String END = "\0END";

  private final HttpClient client;
  private final HttpResponse<InputStream> response;
  private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
  private volatile IOException failure;

  private SseTestConnection(HttpClient client, HttpResponse<InputStream> response) {
    this.client = client;
    this.response = response;
    Thread.ofVirtual().start(this::readLines);
  }

  /// 开一条连接，拿到响应头就返回。
  ///
  /// @param url 完整地址
  /// @param headers 请求头（如身份断言）
  /// @return 连接
  /// @throws IOException 连不上时
  /// @throws InterruptedException 等待时被打断
  public static SseTestConnection open(String url, Map<String, String> headers)
      throws IOException, InterruptedException {
    HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(url)).header("Accept", "text/event-stream");
    headers.forEach(request::header);
    return new SseTestConnection(
        client, client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream()));
  }

  /// 响应状态码。
  ///
  /// @return 状态码
  public int status() {
    return response.statusCode();
  }

  /// 响应头。
  ///
  /// @return 响应头
  public HttpHeaders headers() {
    return response.headers();
  }

  /// 取下一条事件（以空行结束的一组非注释行），跳过注释行。
  ///
  /// @param timeout 最多等多久
  /// @return 事件的各行；超时或流结束时为空
  /// @throws InterruptedException 等待时被打断
  public Optional<List<String>> nextEvent(Duration timeout) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    List<String> event = new ArrayList<>();
    while (true) {
      String line = lines.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
      if (line == null || line.equals(END)) {
        if (line != null) {
          lines.add(END);
        }
        return Optional.empty();
      }
      if (line.startsWith(":")) {
        continue;
      }
      if (line.isEmpty()) {
        if (!event.isEmpty()) {
          return Optional.of(event);
        }
        continue;
      }
      event.add(line);
    }
  }

  /// 等下一行注释（心跳），跳过事件行。
  ///
  /// @param timeout 最多等多久
  /// @return 等到了注释
  /// @throws InterruptedException 等待时被打断
  public boolean awaitComment(Duration timeout) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      String line = lines.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
      if (line == null || line.equals(END)) {
        if (line != null) {
          lines.add(END);
        }
        return false;
      }
      if (line.startsWith(":")) {
        return true;
      }
    }
  }

  /// 等流结束，跳过其间的全部行。
  ///
  /// @param timeout 最多等多久
  /// @return 流正常结束（读到结尾）；超时为假
  /// @throws IOException 流是出错断开的，不是正常结束
  /// @throws InterruptedException 等待时被打断
  public boolean awaitEnd(Duration timeout) throws IOException, InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      String line = lines.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
      if (line == null) {
        return false;
      }
      if (line.equals(END)) {
        lines.add(END);
        if (failure != null) {
          throw failure;
        }
        return true;
      }
    }
  }

  /// 读完整个响应体（错误响应用），最多等 5 秒。
  ///
  /// @return 全部行，按换行拼回
  /// @throws InterruptedException 等待时被打断
  public String body() throws InterruptedException {
    StringBuilder body = new StringBuilder();
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    String line;
    while ((line = lines.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) != null
        && !line.equals(END)) {
      body.append(line).append('\n');
    }
    return body.toString();
  }

  /// 从事件里取 `data:` 行的内容。
  ///
  /// @param event 事件的各行
  /// @return `data:` 之后的内容
  public static String data(List<String> event) {
    return field(event, "data");
  }

  /// 从事件里取某个字段的值。
  ///
  /// @param event 事件的各行
  /// @param name 字段名，如 `event`、`retry`
  /// @return 值；没有这个字段时为 `null`
  public static String field(List<String> event, String name) {
    return event.stream()
        .filter(line -> line.startsWith(name + ":"))
        .map(line -> line.substring(name.length() + 1).strip())
        .findFirst()
        .orElse(null);
  }

  /// 浏览器那边关掉连接。
  @Override
  public void close() {
    try {
      response.body().close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      client.shutdownNow();
    }
  }

  /// 后台线程：逐行读进队列，结束时放结束标记。
  private void readLines() {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lines.add(line);
      }
    } catch (IOException e) {
      failure = e;
    } finally {
      lines.add(END);
    }
  }
}
