package dev.patra.analytics.simulator.live;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.IntSupplier;

/// 实时压测的统计：每秒一行 `live-stats.csv`，另累计全程总数与全程延迟直方图（报告用）。
public final class LiveStats {

  private static final String HEADER =
      "second,sent,delivered,failed,status_202,status_503,status_other,p50_ms,p95_ms,p99_ms,online,in_flight";

  private final Clock clock;
  private final LatencyHistogram overall = new LatencyHistogram();
  private LatencyHistogram second = new LatencyHistogram();
  private IntSupplier online = () -> 0;
  private IntSupplier inFlight = () -> 0;
  private boolean headerWritten;
  private long sent;
  private long delivered;
  private long failed;
  private long status202;
  private long status503;
  private long statusOther;
  private long totalRequests;
  private long totalSent;
  private long totalDelivered;
  private long totalFailed;
  private long totalRejected;
  private long totalUncertain;

  /// 创建统计。
  ///
  /// @param clock 时钟（每行的时间戳）
  public LiveStats(Clock clock) {
    this.clock = clock;
  }

  /// 在线会话数的来源。
  ///
  /// @param supplier 来源
  public synchronized void onlineSessions(IntSupplier supplier) {
    this.online = supplier;
  }

  /// 在途请求数的来源。
  ///
  /// @param supplier 来源
  public synchronized void inFlight(IntSupplier supplier) {
    this.inFlight = supplier;
  }

  /// 记一次请求（含重试中的每一次）。
  ///
  /// @param status 状态码；网络错误为 -1
  /// @param latencyNanos 延迟（含排队等在途名额的时间）
  /// @param events 请求里的事件数
  public synchronized void request(int status, long latencyNanos, int events) {
    overall.record(latencyNanos);
    second.record(latencyNanos);
    sent += events;
    totalSent += events;
    totalRequests++;
    if (status == 202) {
      status202++;
    } else if (status == 503) {
      status503++;
    } else {
      statusOther++;
    }
  }

  /// 若干条送达。
  ///
  /// @param events 条数
  public synchronized void delivered(int events) {
    delivered += events;
    totalDelivered += events;
  }

  /// 若干条进了 dlq（合成事件不该有）。
  ///
  /// @param events 条数
  public synchronized void rejected(int events) {
    totalRejected += events;
  }

  /// 若干条最终没送达。
  ///
  /// @param events 条数
  public synchronized void failed(int events) {
    failed += events;
    totalFailed += events;
  }

  /// 若干条结果不明（重试用尽时最后一次是超时、连接错误或 5xx）。
  ///
  /// @param events 条数
  public synchronized void uncertain(int events) {
    failed += events;
    totalUncertain += events;
  }

  /// 写出这一秒的一行并清零（第一次先写表头）。
  ///
  /// @param csv 输出
  public synchronized void flushSecond(Writer csv) {
    try {
      if (!headerWritten) {
        csv.write(HEADER + "\n");
        headerWritten = true;
      }
      Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
      csv.write(
          now
              + ","
              + sent
              + ","
              + delivered
              + ","
              + failed
              + ","
              + status202
              + ","
              + status503
              + ","
              + statusOther
              + ","
              + millis(second, 0.50)
              + ","
              + millis(second, 0.95)
              + ","
              + millis(second, 0.99)
              + ","
              + online.getAsInt()
              + ","
              + inFlight.getAsInt()
              + "\n");
      csv.flush();
    } catch (IOException e) {
      throw new UncheckedIOException("写每秒统计失败", e);
    }
    second = new LatencyHistogram();
    sent = 0;
    delivered = 0;
    failed = 0;
    status202 = 0;
    status503 = 0;
    statusOther = 0;
  }

  /// 全程总数。
  ///
  /// @return 总数
  public synchronized Totals totals() {
    return new Totals(
        totalRequests, totalSent, totalDelivered, totalFailed, totalUncertain, totalRejected);
  }

  /// 全程延迟直方图。
  ///
  /// @return 直方图
  public LatencyHistogram overall() {
    return overall;
  }

  /// 毫秒百分位。
  ///
  /// @param histogram 直方图
  /// @param quantile 百分位
  /// @return 毫秒
  private static long millis(LatencyHistogram histogram, double quantile) {
    return histogram.percentile(quantile).toMillis();
  }

  /// 全程总数。
  ///
  /// @param requests 请求数（含重试）
  /// @param sent 发出的事件数（含重试）
  /// @param delivered 送达的事件数
  /// @param failed 明确没送达的事件数（4xx）
  /// @param uncertain 结果不明的事件数（重试用尽时最后一次是超时、连接错误或 5xx）
  /// @param rejected 进 dlq 的事件数
  public record Totals(
      long requests, long sent, long delivered, long failed, long uncertain, long rejected) {}
}
