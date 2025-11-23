package com.patra.catalog.domain.model.valueobject;

import com.patra.catalog.domain.model.enums.MeshTableImportStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/// 表导入进度值对象。
///
/// 表示单张表的导入进度，支持断点续传和进度计算。
///
/// **设计原则**：
///
/// - 不可变性：使用 @Value 注解确保不可变对象
///   - 值语义：任何修改都返回新实例
///   - 预期值与实际值分离：expectedCount 用于估算，actualTotalCount 在完成后设置
///   - 显式状态转换：通过专用方法管理状态，不依赖数量匹配
///   - 支持断点续传：记录最后处理批次号
///
/// **核心概念**：
///
/// - `expectedCount` - 配置的预期值（用于进度估算和数据验证）
///   - `actualTotalCount` - 实际总数（导入完成后才确定，可能与预期值有差异）
///   - `processedCount` - 当前已处理数量
///
/// **状态转换**：
///
/// ```
///
/// NOT_STARTED → IN_PROGRESS → COMPLETED
///         ↓                       ↑
///         +------→ FAILED --------+
///
/// ```
///
/// **使用示例**：
///
/// ```java
/// // 1. 创建初始进度（预期 35000 条）
/// TableProgress progress = TableProgress.create("descriptor", 35000);
///
/// // 2. 开始处理（状态自动变为 IN_PROGRESS）
/// TableProgress inProgress = progress.updateProgress(5000, 5);
///
/// // 3. 继续处理
/// inProgress = inProgress.updateProgress(10000, 10);
///
/// // 4. 导入完成（实际 34800 条，与预期略有差异）
/// TableProgress completed = inProgress.markAsCompleted(34800);
///
/// // 5. 查询进度
/// completed.getProgressPercentage(); // 100.0%
/// completed.isCompleted(); // true
/// completed.getActualTotalCount(); // 34800
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Value
@Builder(toBuilder = true)
public class TableProgress {

  /// 表名（如 "descriptor"、"qualifier"）
  String tableName;

  /// 预期记录数（来自配置，用于进度估算和验证）
  Integer expectedCount;

  /// 实际总记录数（导入完成后设置，可能与预期值不同）
  Integer actualTotalCount;

  /// 已处理数
  Integer processedCount;

  /// 失败数
  Integer failedCount;

  /// 表状态（NOT_STARTED/IN_PROGRESS/COMPLETED/FAILED）
  MeshTableImportStatus status;

  /// 最后处理批次号（用于断点续传）
  Integer lastBatchNum;

  /// 最后更新时间
  Instant lastUpdateTime;

  /// 创建初始进度（工厂方法）。
  ///
  /// @param tableName 表名
  /// @param expectedCount 预期记录数
  /// @return 初始进度对象
  public static TableProgress create(String tableName, Integer expectedCount) {
    return TableProgress.builder()
        .tableName(tableName)
        .expectedCount(expectedCount)
        .actualTotalCount(null) // 未知
        .processedCount(0)
        .failedCount(0)
        .status(MeshTableImportStatus.NOT_STARTED)
        .lastBatchNum(0)
        .lastUpdateTime(Instant.now())
        .build();
  }

  /// 计算进度百分比。
  ///
  /// 优先使用实际总数，如未设置则使用预期值估算。
  ///
  /// @return 进度百分比（0.0 ~ 100.0）
  public Double getProgressPercentage() {
    // 优先使用实际总数
    Integer total = actualTotalCount != null ? actualTotalCount : expectedCount;

    if (total == null || total == 0) {
      return 0.0;
    }

    if (processedCount == null) {
      return 0.0;
    }

    // 防止超过 100%（在使用预期值估算时可能发生）
    double percentage = (processedCount * 100.0) / total;
    return Math.min(percentage, 100.0);
  }

  /// 更新进度（返回新实例）。
  ///
  /// 状态转换规则：
  ///
  /// - processedCount == 0 → NOT_STARTED
  ///   - processedCount > 0 → IN_PROGRESS
  ///
  /// @param newProcessedCount 新的已处理数
  /// @param newLastBatchNum 新的最后批次号
  /// @return 更新后的新实例
  public TableProgress updateProgress(Integer newProcessedCount, Integer newLastBatchNum) {
    // 只有未完成的任务才能更新进度
    if (this.status == MeshTableImportStatus.COMPLETED) {
      throw new IllegalStateException("已完成的表不能更新进度: " + tableName);
    }

    // 自动判断状态
    MeshTableImportStatus newStatus =
        newProcessedCount == 0 ? MeshTableImportStatus.NOT_STARTED : MeshTableImportStatus.IN_PROGRESS;

    return this.toBuilder()
        .processedCount(newProcessedCount)
        .status(newStatus)
        .lastBatchNum(newLastBatchNum)
        .lastUpdateTime(Instant.now())
        .build();
  }

  /// 标记为已完成（返回新实例）。
  ///
  /// 设置实际总数并将状态标记为 COMPLETED。
  ///
  /// @param actualTotal 实际导入的总数
  /// @return 已完成的新实例
  public TableProgress markAsCompleted(Integer actualTotal) {
    if (this.status == MeshTableImportStatus.COMPLETED) {
      // 已经完成，直接返回自身
      return this;
    }

    return this.toBuilder()
        .actualTotalCount(actualTotal)
        .processedCount(actualTotal)
        .status(MeshTableImportStatus.COMPLETED)
        .lastUpdateTime(Instant.now())
        .build();
  }

  /// 标记为失败（返回新实例）。
  ///
  /// @param errorMessage 失败原因（可选）
  /// @return 失败状态的新实例
  public TableProgress markAsFailed(String errorMessage) {
    return this.toBuilder()
        .status(MeshTableImportStatus.FAILED)
        .lastUpdateTime(Instant.now())
        .build();
  }

  /// 增加失败数（返回新实例）。
  ///
  /// @param increment 增量
  /// @return 更新后的新实例
  public TableProgress incrementFailedCount(Integer increment) {
    return this.toBuilder().failedCount(this.failedCount + increment).lastUpdateTime(Instant.now()).build();
  }

  /// 判断是否已完成。
  ///
  /// @return true 如果状态为 COMPLETED
  public boolean isCompleted() {
    return this.status == MeshTableImportStatus.COMPLETED;
  }

  /// 判断是否进行中。
  ///
  /// @return true 如果状态为 IN_PROGRESS
  public boolean isInProgress() {
    return this.status == MeshTableImportStatus.IN_PROGRESS;
  }

  /// 判断是否未开始。
  ///
  /// @return true 如果状态为 NOT_STARTED
  public boolean isNotStarted() {
    return this.status == MeshTableImportStatus.NOT_STARTED;
  }

  /// 判断是否失败。
  ///
  /// @return true 如果状态为 FAILED
  public boolean isFailed() {
    return this.status == MeshTableImportStatus.FAILED;
  }

  /// 获取实际总数（如果已完成）。
  ///
  /// @return 实际总数，如未完成则返回 null
  public Integer getActualTotalCount() {
    return actualTotalCount;
  }

  /// 获取数量差异（实际 vs 预期）。
  ///
  /// 仅在导入完成后有意义。
  ///
  /// @return 差异百分比（正值表示实际多于预期，负值表示少于预期），如未完成返回 null
  public Double getCountDifference() {
    if (actualTotalCount == null || expectedCount == null || expectedCount == 0) {
      return null;
    }

    int difference = actualTotalCount - expectedCount;
    return (difference * 100.0) / expectedCount;
  }
}
