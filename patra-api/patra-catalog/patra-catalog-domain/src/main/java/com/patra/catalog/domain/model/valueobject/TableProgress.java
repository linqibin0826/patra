package com.patra.catalog.domain.model.valueobject;

import com.patra.catalog.domain.model.enums.MeshTableImportStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/**
 * 表导入进度值对象。
 *
 * <p>表示单张表的导入进度，支持断点续传和进度计算。
 *
 * <p><b>设计原则</b>：
 *
 * <ul>
 *   <li>不可变性：使用 @Value 注解确保不可变对象
 *   <li>值语义：任何修改都返回新实例
 *   <li>状态自动计算：根据已处理数和总数自动计算状态
 *   <li>支持断点续传：记录最后处理批次号
 * </ul>
 *
 * <p><b>使用示例</b>：
 *
 * <pre>{@code
 * // 创建初始进度
 * TableProgress progress = TableProgress.builder()
 *     .tableName("descriptor")
 *     .totalCount(35000)
 *     .processedCount(0)
 *     .failedCount(0)
 *     .status(MeshTableImportStatus.NOT_STARTED)
 *     .lastBatchNum(0)
 *     .build();
 *
 * // 更新进度（返回新实例）
 * TableProgress updated = progress.updateProgress(5000, 5);
 *
 * // 增加失败数（返回新实例）
 * TableProgress withFailures = updated.incrementFailedCount(10);
 *
 * // 计算进度百分比
 * Double percentage = withFailures.getProgressPercentage(); // 14.29%
 * }</pre>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Value
@Builder
public class TableProgress {

  /** 表名（如 "descriptor"、"qualifier"） */
  String tableName;

  /** 总记录数 */
  Integer totalCount;

  /** 已处理数 */
  Integer processedCount;

  /** 失败数 */
  Integer failedCount;

  /** 表状态（NOT_STARTED/IN_PROGRESS/COMPLETED/FAILED） */
  MeshTableImportStatus status;

  /** 最后处理批次号（用于断点续传） */
  Integer lastBatchNum;

  /** 最后更新时间 */
  Instant lastUpdateTime;

  /**
   * 计算进度百分比。
   *
   * @return 进度百分比（0.0 ~ 100.0）
   */
  public Double getProgressPercentage() {
    if (totalCount == null || totalCount == 0) {
      return 0.0;
    }
    return (processedCount * 100.0) / totalCount;
  }

  /**
   * 更新进度（返回新实例）。
   *
   * <p>状态会根据已处理数自动计算：
   *
   * <ul>
   *   <li>processedCount == 0 → NOT_STARTED
   *   <li>processedCount == totalCount → COMPLETED
   *   <li>0 < processedCount < totalCount → IN_PROGRESS
   * </ul>
   *
   * @param newProcessedCount 新的已处理数
   * @param newLastBatchNum 新的最后批次号
   * @return 更新后的新实例
   */
  public TableProgress updateProgress(Integer newProcessedCount, Integer newLastBatchNum) {
    MeshTableImportStatus newStatus = calculateStatus(newProcessedCount);
    return TableProgress.builder()
        .tableName(this.tableName)
        .totalCount(this.totalCount)
        .processedCount(newProcessedCount)
        .failedCount(this.failedCount)
        .status(newStatus)
        .lastBatchNum(newLastBatchNum)
        .lastUpdateTime(Instant.now())
        .build();
  }

  /**
   * 增加失败数（返回新实例）。
   *
   * @param increment 增量
   * @return 更新后的新实例
   */
  public TableProgress incrementFailedCount(Integer increment) {
    return TableProgress.builder()
        .tableName(this.tableName)
        .totalCount(this.totalCount)
        .processedCount(this.processedCount)
        .failedCount(this.failedCount + increment)
        .status(this.status)
        .lastBatchNum(this.lastBatchNum)
        .lastUpdateTime(Instant.now())
        .build();
  }

  /**
   * 根据已处理数自动计算状态。
   *
   * @param processedCount 已处理数
   * @return 计算后的状态
   */
  private MeshTableImportStatus calculateStatus(Integer processedCount) {
    if (processedCount == 0) {
      return MeshTableImportStatus.NOT_STARTED;
    } else if (processedCount.equals(totalCount)) {
      return MeshTableImportStatus.COMPLETED;
    } else {
      return MeshTableImportStatus.IN_PROGRESS;
    }
  }
}
