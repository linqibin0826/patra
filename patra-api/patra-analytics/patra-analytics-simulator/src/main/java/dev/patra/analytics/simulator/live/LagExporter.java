package dev.patra.analytics.simulator.live;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// 压测结束后，从 Prometheus 导出这段时间的入库消费滞后（`analytics.ingest.lag`，单位是「条」）→ `lag.csv`，
/// 作为新鲜度（秒）之外条数维度的佐证（spec §9）。
///
/// 只是佐证：Prometheus 连不上、超时、返回错误或没有这个指标时只告警、文件留空，不让整次压测以失败退出。
public final class LagExporter {

  private static final System.Logger LOG = System.getLogger(LagExporter.class.getName());

  /// 默认查询：按 topic 取各分区滞后的最大值（Prometheus 里带 Collector 的命名空间前缀）
  public static final String DEFAULT_QUERY = "max by (topic) (patra_analytics_ingest_lag)";

  private static final int STEP_SECONDS = 15;
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private final URI prometheus;
  private final String query;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  /// 创建导出器。
  ///
  /// @param prometheus Prometheus 地址（如 `http://host:9090`）
  /// @param query PromQL
  public LagExporter(URI prometheus, String query) {
    this.prometheus = prometheus;
    this.query = query;
  }

  /// 导出时间段内的曲线，每个点一行：`time,series,value`；取不到时告警、什么都不写。
  ///
  /// @param from 起
  /// @param to 止
  /// @param csv 输出
  public void export(Instant from, Instant to, Writer csv) {
    String rows;
    try {
      rows = fetch(from, to);
    } catch (IOException | RuntimeException e) {
      LOG.log(System.Logger.Level.WARNING, "导出消费滞后失败，lag.csv 留空：" + e);
      return;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.log(System.Logger.Level.WARNING, "导出消费滞后时被中断，lag.csv 留空");
      return;
    }
    try {
      csv.write(rows);
      csv.flush();
    } catch (IOException e) {
      throw new UncheckedIOException("写 lag.csv 失败", e);
    }
  }

  /// 查 Prometheus，拼好整份 CSV（先拼好再写，失败时文件里不留半截）。
  ///
  /// @param from 起
  /// @param to 止
  /// @return CSV 文本
  /// @throws IOException 连不上、超时
  /// @throws InterruptedException 被中断
  private String fetch(Instant from, Instant to) throws IOException, InterruptedException {
    URI uri =
        prometheus.resolve(
            "/api/v1/query_range?query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&start="
                + from.getEpochSecond()
                + "&end="
                + to.getEpochSecond()
                + "&step="
                + STEP_SECONDS);
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).build(),
            HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "Prometheus 返回 " + response.statusCode() + "：" + response.body());
    }
    StringBuilder rows = new StringBuilder("time,series,value\n");
    for (JsonNode series : MAPPER.readTree(response.body()).get("data").get("result")) {
      String name = series.get("metric").path("topic").asString("all");
      for (JsonNode point : series.get("values")) {
        rows.append(Instant.ofEpochSecond(point.get(0).asLong()))
            .append(',')
            .append(name)
            .append(',')
            .append(point.get(1).asString())
            .append('\n');
      }
    }
    return rows.toString();
  }
}
