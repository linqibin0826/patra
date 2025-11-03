package com.patra.ingest.app.usecase.relay.metrics;

import com.patra.ingest.domain.model.enums.RelayStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 使用 Micrometer 的 Outbox 中继指标收集器
 *
 * <h3>指标概览</h3>
 *
 * <p>此组件公开以下用于监控和告警的指标:
 *
 * <ul>
 *   <li><strong>outbox.relay.attempts</strong> (Counter): 按状态和通道的中继尝试总数
 *   <li><strong>outbox.relay.duration</strong> (Timer): 中继执行持续时间分布
 *   <li><strong>outbox.relay.batch.size</strong> (DistributionSummary): 批大小分布
 *   <li><strong>outbox.relay.errors</strong> (Counter): 按错误代码和通道的错误计数
 * </ul>
 *
 * <h3>标签维度</h3>
 *
 * <ul>
 *   <li><code>channel</code>: Outbox 通道 (例如 INGEST_TASK, REGISTRY_UPDATE)
 *   <li><code>status</code>: 中继结果状态 (PUBLISHED, DEFERRED, FAILED, LEASE_MISSED)
 *   <li><code>error_code</code>: 错误分类代码 (仅用于失败)
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 记录成功发布
 * metrics.recordPublished("INGEST_TASK");
 *
 * // 记录延迟重试
 * metrics.recordDeferred("INGEST_TASK", "NETWORK_TIMEOUT");
 *
 * // 记录批次执行
 * metrics.recordBatchSize(150);
 * metrics.recordBatchDuration("INGEST_TASK", Duration.ofMillis(250));
 * }</pre>
 *
 * @author Papertrace Team
 * @since 2.0
 */
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

  /**
   * 记录成功的消息发布
   *
   * <p>递增计数器: {@code outbox.relay.attempts{channel=X, status=PUBLISHED}}
   *
   * @param channel Outbox 通道名称
   */
  public void recordPublished(String channel) {
    counter("attempts", channel, RelayStatus.PUBLISHED.getCode()).increment();
  }

  /**
   * 记录延迟重试 (暂时性错误)
   *
   * <p>递增计数器:
   *
   * <ul>
   *   <li>{@code outbox.relay.attempts{channel=X, status=DEFERRED}}
   *   <li>{@code outbox.relay.errors{channel=X, error_code=Y}}
   * </ul>
   *
   * @param channel Outbox 通道名称
   * @param errorCode 错误分类代码
   */
  public void recordDeferred(String channel, String errorCode) {
    counter("attempts", channel, RelayStatus.DEFERRED.getCode()).increment();
    errorCounter(channel, errorCode).increment();
  }

  /**
   * 记录永久失败
   *
   * <p>递增计数器:
   *
   * <ul>
   *   <li>{@code outbox.relay.attempts{channel=X, status=FAILED}}
   *   <li>{@code outbox.relay.errors{channel=X, error_code=Y}}
   * </ul>
   *
   * @param channel Outbox 通道名称
   * @param errorCode 错误分类代码
   */
  public void recordFailed(String channel, String errorCode) {
    counter("attempts", channel, RelayStatus.FAILED.getCode()).increment();
    errorCounter(channel, errorCode).increment();
  }

  /**
   * 记录租约获取丢失 (并发竞争)
   *
   * <p>递增计数器: {@code outbox.relay.attempts{channel=X, status=LEASE_MISSED}}
   *
   * @param channel Outbox 通道名称
   */
  public void recordLeaseMissed(String channel) {
    counter("attempts", channel, RelayStatus.LEASE_MISSED.getCode()).increment();
  }

  /**
   * 记录批次执行持续时间
   *
   * <p>记录计时器: {@code outbox.relay.duration{channel=X}}
   *
   * <p>用于监控:
   *
   * <ul>
   *   <li>P50, P95, P99 延迟
   *   <li>随时间推移的性能下降
   *   <li>SLA 合规性 (例如 95% 的批次在 500ms 内完成)
   * </ul>
   *
   * @param channel Outbox 通道名称
   * @param duration 批次执行持续时间
   */
  public void recordBatchDuration(String channel, Duration duration) {
    timer(channel).record(duration.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * 记录批大小 (处理的消息数量)
   *
   * <p>记录分布摘要: {@code outbox.relay.batch.size}
   *
   * <p>用于监控:
   *
   * <ul>
   *   <li>平均批大小
   *   <li>吞吐量估算 (批大小 × 批频率)
   *   <li>资源利用率 (更大的批次 = 更好的效率)
   * </ul>
   *
   * @param batchSize 批次中的消息数量
   */
  public void recordBatchSize(int batchSize) {
    batchSizeSummary().record(batchSize);
  }

  /**
   * 获取或创建中继尝试的计数器
   *
   * <p>计数器名称: {@code outbox.relay.attempts}
   *
   * <p>标签:
   *
   * <ul>
   *   <li>channel: Outbox 通道名称
   *   <li>status: 中继结果状态
   * </ul>
   */
  private Counter counter(String metric, String channel, String status) {
    return Counter.builder(METRIC_PREFIX + "." + metric)
        .tag(TAG_CHANNEL, channel)
        .tag(TAG_STATUS, status)
        .description("按状态和通道的 Outbox 中继尝试")
        .register(meterRegistry);
  }

  /**
   * 获取或创建中继错误的计数器
   *
   * <p>计数器名称: {@code outbox.relay.errors}
   *
   * <p>标签:
   *
   * <ul>
   *   <li>channel: Outbox 通道名称
   *   <li>error_code: 错误分类代码
   * </ul>
   */
  private Counter errorCounter(String channel, String errorCode) {
    return Counter.builder(METRIC_PREFIX + ".errors")
        .tag(TAG_CHANNEL, channel)
        .tag(TAG_ERROR_CODE, errorCode)
        .description("按错误代码和通道的 Outbox 中继错误")
        .register(meterRegistry);
  }

  /**
   * 获取或创建批次执行持续时间的计时器
   *
   * <p>计时器名称: {@code outbox.relay.duration}
   *
   * <p>标签:
   *
   * <ul>
   *   <li>channel: Outbox 通道名称
   * </ul>
   */
  private Timer timer(String channel) {
    return Timer.builder(METRIC_PREFIX + ".duration")
        .tag(TAG_CHANNEL, channel)
        .description("Outbox 中继批次执行持续时间")
        .publishPercentiles(0.5, 0.95, 0.99) // P50, P95, P99
        .register(meterRegistry);
  }

  /**
   * 获取或创建批大小的分布摘要
   *
   * <p>摘要名称: {@code outbox.relay.batch.size}
   *
   * <p>无标签 (所有通道的全局指标)
   */
  private DistributionSummary batchSizeSummary() {
    return DistributionSummary.builder(METRIC_PREFIX + ".batch.size")
        .description("Outbox 中继批大小分布")
        .publishPercentiles(0.5, 0.95, 0.99) // P50, P95, P99
        .register(meterRegistry);
  }
}
