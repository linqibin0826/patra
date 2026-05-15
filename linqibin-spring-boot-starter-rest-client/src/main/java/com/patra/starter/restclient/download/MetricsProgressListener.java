package com.patra.starter.restclient.download;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

/// Micrometer 指标监听器。
///
/// 仅在下载完成或失败时记录聚合指标，不记录瞬时进度。
///
/// **设计决策**：下载进度（bytes、speed）是瞬时任务状态，不适合 Prometheus Gauge。
/// 多个并发下载会导致指标覆盖或 cardinality 爆炸。因此只记录聚合指标。
///
/// **记录的指标**：
///
/// | 指标名 | 类型 | Tags | 说明 |
/// |--------|------|------|------|
/// | `download.duration.seconds` | Timer | - | 下载耗时分布 |
/// | `download.bytes.total` | Counter | - | 累计下载字节数 |
/// | `download.count.total` | Counter | `status=success/failure` | 累计下载次数 |
/// | `download.failure.total` | Counter | `error_type=...` | 失败次数 |
///
/// @author linqibin
/// @since 0.1.0
public class MetricsProgressListener implements ProgressListener {

  private static final String METRIC_DURATION = "download.duration.seconds";
  private static final String METRIC_BYTES = "download.bytes.total";
  private static final String METRIC_COUNT = "download.count.total";
  private static final String METRIC_FAILURE = "download.failure.total";

  private static final String TAG_STATUS = "status";
  private static final String TAG_ERROR_TYPE = "error_type";

  private static final String STATUS_SUCCESS = "success";
  private static final String STATUS_FAILURE = "failure";

  private final MeterRegistry registry;
  private final Timer durationTimer;
  private final Counter bytesCounter;
  private final Counter successCounter;
  private final Counter failureCounter;

  /// 创建指标监听器。
  ///
  /// @param registry Micrometer 指标注册中心
  public MetricsProgressListener(MeterRegistry registry) {
    this.registry = registry;
    // 预创建 Meter 实例，避免每次回调时重复查找
    this.durationTimer = Timer.builder(METRIC_DURATION).description("下载耗时分布").register(registry);
    this.bytesCounter = Counter.builder(METRIC_BYTES).description("累计下载字节数").register(registry);
    this.successCounter =
        Counter.builder(METRIC_COUNT)
            .description("累计下载次数")
            .tag(TAG_STATUS, STATUS_SUCCESS)
            .register(registry);
    this.failureCounter =
        Counter.builder(METRIC_COUNT)
            .description("累计下载次数")
            .tag(TAG_STATUS, STATUS_FAILURE)
            .register(registry);
  }

  @Override
  public void onProgress(DownloadProgress progress) {
    // 不记录瞬时进度，避免并发覆盖和 cardinality 问题
  }

  @Override
  public void onComplete(DownloadProgress finalProgress) {
    // 记录耗时分布
    durationTimer.record(finalProgress.elapsedMillis(), TimeUnit.MILLISECONDS);

    // 累计下载字节数
    bytesCounter.increment(finalProgress.bytesDownloaded());

    // 累计下载次数（成功）
    successCounter.increment();
  }

  @Override
  public void onError(Exception exception, DownloadProgress lastProgress) {
    // 记录失败次数（按错误类型分组）
    // 注意：错误类型 Counter 无法预创建，因为 error_type 标签值是动态的
    Counter.builder(METRIC_FAILURE)
        .description("下载失败次数")
        .tag(TAG_ERROR_TYPE, classifyError(exception))
        .register(registry)
        .increment();

    // 累计下载次数（失败）
    failureCounter.increment();
  }

  /// 分类错误类型。
  ///
  /// @param exception 异常
  /// @return 错误类型标签值
  private String classifyError(Exception exception) {
    if (exception instanceof SocketTimeoutException) {
      return "timeout";
    }
    if (exception instanceof IOException) {
      return "io_error";
    }
    if (exception instanceof DownloadException) {
      return "download_error";
    }
    return "unknown";
  }
}
