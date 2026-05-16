package dev.linqibin.patra.ingest.app.usecase.relay.metrics;

import dev.linqibin.patra.ingest.domain.model.enums.RelayStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 使用 Micrometer 的 Outbox 中继指标收集器
///
/// ### 指标概览
///
/// 此组件公开以下用于监控和告警的指标:
///
/// - **outbox.relay.attempts** (Counter): 按状态和通道的中继尝试总数
///   - **outbox.relay.duration** (Timer): 中继执行持续时间分布
///   - **outbox.relay.batch.size** (DistributionSummary): 批大小分布
///   - **outbox.relay.errors** (Counter): 按错误代码和通道的错误计数
///
/// ### 标签维度
///
/// - `channel`: Outbox 通道 (例如 INGEST_TASK, REGISTRY_UPDATE)
///   - `status`: 中继结果状态 (PUBLISHED, DEFERRED, FAILED, LEASE_MISSED)
///   - `error_code`: 错误分类代码 (仅用于失败)
///
/// ### 使用示例
///
/// ```java
/// // 记录成功发布
/// metrics.recordPublished("INGEST_TASK");
///
/// // 记录延迟重试
/// metrics.recordDeferred("INGEST_TASK", "NETWORK_TIMEOUT");
///
/// // 记录批次执行
/// metrics.recordBatchSize(150);
/// metrics.recordBatchDuration("INGEST_TASK", Duration.ofMillis(250));
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class OutboxRelayMetrics {

  private static final String METRIC_PREFIX = "outbox.relay";
  private static final String TAG_CHANNEL = "channel";
  private static final String TAG_STATUS = "status";
  private static final String TAG_ERROR_CODE = "error_code";

  private final MeterRegistry meterRegistry;

  public OutboxRelayMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    log.info("已初始化 OutboxRelayMetrics,注册表: {}", meterRegistry.getClass().getSimpleName());
  }

  /// 记录成功的消息发布
  ///
  /// 递增计数器: `outbox.relay.attempts{channel=X, status=PUBLISHED`}
  ///
  /// @param channel Outbox 通道名称
  public void recordPublished(String channel) {
    counter("attempts", channel, RelayStatus.PUBLISHED.getCode()).increment();
  }

  /// 记录延迟重试 (暂时性错误)
  ///
  /// 递增计数器:
  ///
  /// - `outbox.relay.attempts{channel=X, status=DEFERRED`}
  ///   - `outbox.relay.errors{channel=X, error_code=Y`}
  ///
  /// @param channel Outbox 通道名称
  /// @param errorCode 错误分类代码
  public void recordDeferred(String channel, String errorCode) {
    counter("attempts", channel, RelayStatus.DEFERRED.getCode()).increment();
    errorCounter(channel, errorCode).increment();
  }

  /// 记录永久失败
  ///
  /// 递增计数器:
  ///
  /// - `outbox.relay.attempts{channel=X, status=FAILED`}
  ///   - `outbox.relay.errors{channel=X, error_code=Y`}
  ///
  /// @param channel Outbox 通道名称
  /// @param errorCode 错误分类代码
  public void recordFailed(String channel, String errorCode) {
    counter("attempts", channel, RelayStatus.FAILED.getCode()).increment();
    errorCounter(channel, errorCode).increment();
  }

  /// 记录租约获取丢失 (并发竞争)
  ///
  /// 递增计数器: `outbox.relay.attempts{channel=X, status=LEASE_MISSED`}
  ///
  /// @param channel Outbox 通道名称
  public void recordLeaseMissed(String channel) {
    counter("attempts", channel, RelayStatus.LEASE_MISSED.getCode()).increment();
  }

  /// 记录批次执行持续时间
  ///
  /// 记录计时器: `outbox.relay.duration{channel=X`}
  ///
  /// 用于监控:
  ///
  /// - P50, P95, P99 延迟
  ///   - 随时间推移的性能下降
  ///   - SLA 合规性 (例如 95% 的批次在 500ms 内完成)
  ///
  /// @param channel Outbox 通道名称
  /// @param duration 批次执行持续时间
  public void recordBatchDuration(String channel, Duration duration) {
    timer(channel).record(duration.toMillis(), TimeUnit.MILLISECONDS);
  }

  /// 记录批大小 (处理的消息数量)
  ///
  /// 记录分布摘要: `outbox.relay.batch.size`
  ///
  /// 用于监控:
  ///
  /// - 平均批大小
  ///   - 吞吐量估算 (批大小 × 批频率)
  ///   - 资源利用率 (更大的批次 = 更好的效率)
  ///
  /// @param batchSize 批次中的消息数量
  public void recordBatchSize(int batchSize) {
    batchSizeSummary().record(batchSize);
  }

  /// 获取或创建中继尝试的计数器
  ///
  /// 计数器名称: `outbox.relay.attempts`
  ///
  /// 标签:
  ///
  /// - channel: Outbox 通道名称
  ///   - status: 中继结果状态
  ///
  private Counter counter(String metric, String channel, String status) {
    return Counter.builder(METRIC_PREFIX + "." + metric)
        .tag(TAG_CHANNEL, channel)
        .tag(TAG_STATUS, status)
        .description("按状态和通道的 Outbox 中继尝试")
        .register(meterRegistry);
  }

  /// 获取或创建中继错误的计数器
  ///
  /// 计数器名称: `outbox.relay.errors`
  ///
  /// 标签:
  ///
  /// - channel: Outbox 通道名称
  ///   - error_code: 错误分类代码
  ///
  private Counter errorCounter(String channel, String errorCode) {
    return Counter.builder(METRIC_PREFIX + ".errors")
        .tag(TAG_CHANNEL, channel)
        .tag(TAG_ERROR_CODE, errorCode)
        .description("按错误代码和通道的 Outbox 中继错误")
        .register(meterRegistry);
  }

  /// 获取或创建批次执行持续时间的计时器
  ///
  /// 计时器名称: `outbox.relay.duration`
  ///
  /// 标签:
  ///
  /// - channel: Outbox 通道名称
  ///
  private Timer timer(String channel) {
    return Timer.builder(METRIC_PREFIX + ".duration")
        .tag(TAG_CHANNEL, channel)
        .description("Outbox 中继批次执行持续时间")
        .publishPercentiles(0.5, 0.95, 0.99) // P50, P95, P99
        .register(meterRegistry);
  }

  /// 获取或创建批大小的分布摘要
  ///
  /// 摘要名称: `outbox.relay.batch.size`
  ///
  /// 无标签 (所有通道的全局指标)
  private DistributionSummary batchSizeSummary() {
    return DistributionSummary.builder(METRIC_PREFIX + ".batch.size")
        .description("Outbox 中继批大小分布")
        .publishPercentiles(0.5, 0.95, 0.99) // P50, P95, P99
        .register(meterRegistry);
  }
}
