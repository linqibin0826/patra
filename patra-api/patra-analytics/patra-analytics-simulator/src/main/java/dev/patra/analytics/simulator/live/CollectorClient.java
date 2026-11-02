package dev.patra.analytics.simulator.live;

import dev.patra.analytics.simulator.support.Sleeper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// 向采集端点 `POST /events` 发一批（spec §9）：在途请求数有上限，满了就排队（排队时间计入延迟，开环压力不被吞掉）；
/// 503、其他 5xx、超时、连接错误按指数退避重试同一请求体（`event_id` 不变），400 / 413 等不重试。
public final class CollectorClient {

  private static final System.Logger LOG = System.getLogger(CollectorClient.class.getName());
  private static final long FIRST_BACKOFF_MILLIS = 200;
  private static final long MAX_BACKOFF_MILLIS = 5_000;
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private final URI endpoint;
  private final Semaphore permits;
  private final int maxAttempts;
  private final Duration requestTimeout;
  private final LiveStats stats;
  private final Sleeper sleeper;
  private final HttpClient http;

  /// 创建发送端。
  ///
  /// @param endpoint 采集端点
  /// @param maxInFlight 同时在途的请求上限
  /// @param maxAttempts 最多尝试几次（含第一次）
  /// @param requestTimeout 单次请求超时
  /// @param stats 统计
  /// @param sleeper 退避等待
  public CollectorClient(
      URI endpoint,
      int maxInFlight,
      int maxAttempts,
      Duration requestTimeout,
      LiveStats stats,
      Sleeper sleeper) {
    this.endpoint = endpoint;
    this.permits = new Semaphore(maxInFlight, true);
    this.maxAttempts = maxAttempts;
    this.requestTimeout = requestTimeout;
    this.stats = stats;
    this.sleeper = sleeper;
    this.http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
    stats.inFlight(() -> maxInFlight - permits.availablePermits());
  }

  /// 发一批（阻塞，供虚拟线程调用）。
  ///
  /// @param body `collect-batch` 请求体
  /// @param eventCount 批里的事件数
  /// @return 结果
  public SendOutcome send(String body, int eventCount) {
    int lastStatus = -1;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      long start = System.nanoTime();
      int status;
      String response = "";
      try {
        permits.acquire();
        try {
          HttpResponse<String> reply =
              http.send(
                  HttpRequest.newBuilder(endpoint)
                      .timeout(requestTimeout)
                      .header("Content-Type", "application/json")
                      .POST(HttpRequest.BodyPublishers.ofString(body))
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
          status = reply.statusCode();
          response = reply.body();
        } finally {
          permits.release();
        }
      } catch (IOException e) {
        status = -1;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        stats.uncertain(eventCount);
        return new SendOutcome.Failed(-1, "被中断");
      }
      stats.request(status, System.nanoTime() - start, eventCount);
      lastStatus = status;
      if (status == 202) {
        return delivered(response);
      }
      if (status >= 400 && status < 500) {
        stats.failed(eventCount);
        LOG.log(System.Logger.Level.ERROR, "采集端点拒收（{0}），不重试：{1}", status, response);
        return new SendOutcome.Failed(status, response);
      }
      if (attempt < maxAttempts) {
        sleeper.sleep(Math.min(MAX_BACKOFF_MILLIS, FIRST_BACKOFF_MILLIS << (attempt - 1)));
      }
    }
    stats.uncertain(eventCount);
    return new SendOutcome.Failed(lastStatus, "重试 " + maxAttempts + " 次仍失败");
  }

  /// 解析 202 的响应体。
  ///
  /// @param response `{"accepted": n, "rejected": m}`
  /// @return 结果
  private SendOutcome delivered(String response) {
    JsonNode node = MAPPER.readTree(response);
    int accepted = node.get("accepted").asInt();
    int rejected = node.get("rejected").asInt();
    stats.delivered(accepted);
    if (rejected > 0) {
      stats.rejected(rejected);
      LOG.log(System.Logger.Level.ERROR, "有 {0} 条合成事件进了 dlq，模拟器可能造出了不合格事件", rejected);
    }
    return new SendOutcome.Delivered(accepted, rejected);
  }
}
