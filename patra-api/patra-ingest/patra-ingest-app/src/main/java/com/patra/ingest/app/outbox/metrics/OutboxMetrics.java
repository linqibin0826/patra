package com.patra.ingest.app.outbox.metrics;

import com.patra.ingest.app.outbox.config.OutboxPublisherProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 发件箱发布操作的指标门面。
///
/// 提供以下 Micrometer 指标:
///
/// - patra.outbox.publish.total (计数器): 发布尝试总数,带状态标签
///   - patra.outbox.publish.duration (计时器): 发布操作耗时
///   - patra.outbox.publish.batch.size (分布汇总): 批次大小分布
///
/// 所有指标包含标签: aggregateType (受 allowedAggregateTypes 控制), opType。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class OutboxMetrics {

  private final MeterRegistry meterRegistry;
  private final OutboxPublisherProperties properties;

  /// 使用 Micrometer 注册表和配置构造 OutboxMetrics。
  ///
  /// @param meterRegistry Micrometer 指标注册表 (由 Spring 注入)
  /// @param properties 发件箱发布器配置属性
  public OutboxMetrics(MeterRegistry meterRegistry, OutboxPublisherProperties properties) {
    this.meterRegistry = meterRegistry;
    this.properties = properties;
  }

  /// 记录发布操作结果(成功或失败)。
  ///
  /// 记录的指标:
  ///
  /// - patra.outbox.publish.total (计数器): 每次发布尝试时递增
  ///   - patra.outbox.publish.duration (计时器): 记录操作耗时
  ///
  /// @param aggregateType 聚合类型 (例如 "Task", "PublicationData"),必须在允许列表中
  /// @param opType 操作类型 (例如 "batch", "retry", "CREATED")
  /// @param isSuccess 操作是否成功,成功为 true,否则为 false
  /// @param duration 操作耗时 (不能为 null)
  public void recordPublish(
      String aggregateType, String opType, boolean isSuccess, Duration duration) {
    if (!properties.getMetrics().isEnabled()) {
      return;
    }

    try {
      validateAggregateType(aggregateType);

      String status = isSuccess ? "success" : "failure";

      // 计数器: patra.outbox.publish.total
      Counter.builder("patra.outbox.publish.total")
          .tag("aggregateType", aggregateType)
          .tag("opType", opType)
          .tag("status", status)
          .register(meterRegistry)
          .increment();

      // 计时器: patra.outbox.publish.duration
      Timer.builder("patra.outbox.publish.duration")
          .tag("aggregateType", aggregateType)
          .tag("opType", opType)
          .register(meterRegistry)
          .record(duration);

    } catch (Exception e) {
      log.warn("记录发件箱指标失败,aggregateType={}, opType={}: {}", aggregateType, opType, e.getMessage());
    }
  }

  /// 记录批次大小分布。
  ///
  /// 用于分析批处理模式和识别优化机会。
  ///
  /// @param aggregateType 聚合类型 (必须在允许列表中)
  /// @param batchSize 记录的批次大小 (应为正数)
  public void recordBatchSize(String aggregateType, int batchSize) {
    if (!properties.getMetrics().isEnabled()) {
      return;
    }

    try {
      validateAggregateType(aggregateType);

      DistributionSummary.builder("patra.outbox.publish.batch.size")
          .tag("aggregateType", aggregateType)
          .register(meterRegistry)
          .record(batchSize);

    } catch (Exception e) {
      log.warn("记录批次大小指标失败,aggregateType={}: {}", aggregateType, e.getMessage());
    }
  }

  /// 验证聚合类型是否在允许列表中,防止指标基数爆炸。
  ///
  /// @param aggregateType 待验证的聚合类型
  /// @throws IllegalArgumentException 如果聚合类型不在允许列表中
  private void validateAggregateType(String aggregateType) {
    Set<String> allowed = properties.getAllowedAggregateTypes();
    if (!allowed.isEmpty() && !allowed.contains(aggregateType)) {
      throw new IllegalArgumentException(
          String.format(
              "未知的 aggregateType '%s'。允许的类型: %s。"
                  + "请在配置中更新 patra.outbox.publisher.allowed-aggregate-types。",
              aggregateType, allowed));
    }
  }
}
