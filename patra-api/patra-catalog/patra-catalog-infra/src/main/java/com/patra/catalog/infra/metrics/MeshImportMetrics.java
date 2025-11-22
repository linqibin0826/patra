package com.patra.catalog.infra.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// MeSH 导入自定义监控指标。
///
/// 使用 Micrometer 提供以下监控指标：
///
/// - 任务耗时 (Timer) - mesh.import.task.duration
///   - 批次处理速度 (Gauge) - mesh.import.batch.speed
///   - 失败率 (Counter) - mesh.import.task.failed.count
///   - 表级别进度 (Gauge) - mesh.import.table.progress
///   - 成功任务数 (Counter) - mesh.import.task.success.count
///   - 总处理记录数 (Counter) - mesh.import.records.processed
///
/// **使用方式**：
///
/// ```java
/// // 记录任务开始
/// Timer.Sample sample = meshImportMetrics.startTaskTimer();
///
/// // 记录批次处理速度
/// meshImportMetrics.recordBatchSpeed("descriptor", 1000.0);
///
/// // 记录任务完成
/// meshImportMetrics.stopTaskTimer(sample, "SUCCESS");
/// ```
///
/// **Grafana 监控面板**：
///
/// - 任务成功率：(mesh.import.task.success.count / (mesh.import.task.success.count +
///       mesh.import.task.failed.count)) * 100
///   - 平均批次处理速度：avg(mesh.import.batch.speed)
///   - 各表进度：mesh.import.table.progress{table="descriptor"}
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class MeshImportMetrics {

  private final MeterRegistry meterRegistry;

  // 表级别进度缓存（用于 Gauge 指标）
  private final ConcurrentHashMap<String, AtomicInteger> tableProgressMap =
      new ConcurrentHashMap<>();

  // 批次处理速度缓存（用于 Gauge 指标）
  private final ConcurrentHashMap<String, Double> batchSpeedMap = new ConcurrentHashMap<>();

  public MeshImportMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    registerGauges();
  }

  /// 注册 Gauge 指标。
  ///
  /// Gauge 指标用于监控动态变化的值（如进度、速度）
  private void registerGauges() {
    // 为5张MeSH表注册进度指标
    String[] tables = {"descriptor", "qualifier", "treeNumber", "entryTerm", "concept"};
    for (String table : tables) {
      tableProgressMap.put(table, new AtomicInteger(0));

      Gauge.builder(
              "mesh.import.table.progress", tableProgressMap.get(table), AtomicInteger::doubleValue)
          .description("MeSH 表级别进度百分比")
          .tag("table", table)
          .register(meterRegistry);

      batchSpeedMap.put(table, 0.0);

      Gauge.builder("mesh.import.batch.speed", () -> batchSpeedMap.getOrDefault(table, 0.0))
          .description("MeSH 批次处理速度（记录/秒）")
          .tag("table", table)
          .baseUnit("records_per_second")
          .register(meterRegistry);
    }

    log.info("MeSH 导入监控指标已注册：5张表的进度和速度指标");
  }

  /// 开始任务计时。
  ///
  /// @return Timer Sample
  public Timer.Sample startTaskTimer() {
    return Timer.start(meterRegistry);
  }

  /// 停止任务计时并记录。
  ///
  /// @param sample Timer Sample
  /// @param status 任务状态（SUCCESS/FAILED/CANCELLED）
  public void stopTaskTimer(Timer.Sample sample, String status) {
    sample.stop(
        Timer.builder("mesh.import.task.duration")
            .description("MeSH 导入任务总耗时")
            .tag("status", status)
            .register(meterRegistry));

    log.debug("记录任务耗时指标，状态：{}", status);
  }

  /// 记录任务成功。
  public void recordTaskSuccess() {
    Counter.builder("mesh.import.task.success.count")
        .description("MeSH 导入成功任务数")
        .register(meterRegistry)
        .increment();

    log.debug("记录任务成功指标");
  }

  /// 记录任务失败。
  ///
  /// @param errorType 错误类型（DOWNLOAD_ERROR/PARSE_ERROR/DB_ERROR等）
  public void recordTaskFailure(String errorType) {
    Counter.builder("mesh.import.task.failed.count")
        .description("MeSH 导入失败任务数")
        .tag("error_type", errorType)
        .register(meterRegistry)
        .increment();

    log.warn("记录任务失败指标，错误类型：{}", errorType);
  }

  /// 更新表级别进度。
  ///
  /// @param tableName 表名
  /// @param progressPercentage 进度百分比（0-100）
  public void updateTableProgress(String tableName, double progressPercentage) {
    AtomicInteger progress = tableProgressMap.get(tableName);
    if (progress != null) {
      progress.set((int) progressPercentage);
      log.trace("更新表 {} 进度：{}%", tableName, progressPercentage);
    } else {
      log.warn("未知的表名：{}，无法更新进度", tableName);
    }
  }

  /// 记录批次处理速度。
  ///
  /// @param tableName 表名
  /// @param speed 处理速度（记录/秒）
  public void recordBatchSpeed(String tableName, double speed) {
    batchSpeedMap.put(tableName, speed);
    log.trace("记录表 {} 批次处理速度：{} 记录/秒", tableName, speed);
  }

  /// 记录处理的记录数。
  ///
  /// @param tableName 表名
  /// @param count 记录数
  public void recordProcessedRecords(String tableName, long count) {
    Counter.builder("mesh.import.records.processed")
        .description("MeSH 导入已处理记录总数")
        .tag("table", tableName)
        .register(meterRegistry)
        .increment(count);

    log.trace("记录表 {} 处理记录数：{}", tableName, count);
  }

  /// 记录批次失败。
  ///
  /// @param tableName 表名
  /// @param batchNum 批次号
  public void recordBatchFailure(String tableName, int batchNum) {
    Counter.builder("mesh.import.batch.failed.count")
        .description("MeSH 导入失败批次数")
        .tag("table", tableName)
        .register(meterRegistry)
        .increment();

    log.warn("记录批次失败：表 {}，批次号 {}", tableName, batchNum);
  }

  /// 重置所有表的进度指标。
  ///
  /// 用于清除任务重新开始时重置监控指标
  public void resetTableProgress() {
    tableProgressMap.values().forEach(progress -> progress.set(0));
    batchSpeedMap.replaceAll((k, v) -> 0.0);
    log.info("已重置所有表的进度和速度指标");
  }
}
