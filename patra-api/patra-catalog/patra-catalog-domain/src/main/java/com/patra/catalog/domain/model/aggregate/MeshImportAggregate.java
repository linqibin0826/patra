package com.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import com.patra.catalog.domain.event.MeshImportCompleted;
import com.patra.catalog.domain.event.MeshImportFailed;
import com.patra.catalog.domain.event.MeshImportStarted;
import com.patra.catalog.domain.model.enums.MeshImportTaskStatus;
import com.patra.catalog.domain.model.enums.MeshTableImportStatus;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.catalog.domain.model.valueobject.TableProgress;
import com.patra.common.domain.AggregateRoot;
import java.io.Serial;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * MeSH 导入任务聚合根。
 *
 * <p>管理整个 MeSH 数据导入任务的生命周期，包括：
 *
 * <ul>
 *   <li>任务状态转换（PENDING → PROCESSING → SUCCESS/FAILED）
 *   <li>进度追踪（各表的处理进度）
 *   <li>错误恢复（失败批次管理和重试）
 *   <li>数据完整性验证（文件 MD5 校验）
 * </ul>
 *
 * <p><b>领域事件</b>：
 *
 * <ul>
 *   <li>{@link MeshImportStarted} - 任务启动时发布
 *   <li>{@link MeshImportCompleted} - 任务成功完成时发布
 *   <li>{@link MeshImportFailed} - 任务失败时发布
 * </ul>
 *
 * <p><b>一致性边界</b>：
 *
 * <ul>
 *   <li>任务状态转换必须符合状态机规则
 *   <li>只有所有表完成才能标记任务成功
 *   <li>失败任务必须是 FAILED 状态才能重试
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Getter
public class MeshImportAggregate extends AggregateRoot<MeshImportId> {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 基本信息 ==========

  /** 任务名称 */
  private String taskName;

  /** 任务状态（PENDING/PROCESSING/SUCCESS/FAILED/CANCELLED） */
  private MeshImportTaskStatus status;

  /** 开始时间 */
  private Instant startTime;

  /** 结束时间 */
  private Instant endTime;

  // ========== 数据源信息 ==========

  /** NLM 数据源 URL */
  private String sourceUrl;

  /** XML 文件 MD5 哈希（验证完整性） */
  private String xmlFileHash;

  /** XML 文件大小（字节） */
  private Long xmlFileSize;

  // ========== 进度信息 ==========

  /** 各表导入进度（值对象列表） */
  private List<TableProgress> tableProgressList;

  /** 总记录数（约 350,000） */
  private Integer totalRecords;

  /** 已处理记录数 */
  private Integer processedRecords;

  /** 失败批次数 */
  private Integer failedBatchCount;

  /** 最后错误信息 */
  private String lastErrorMessage;

  /**
   * 全参数构造函数（用于测试和完整初始化）。
   *
   * @param id 任务 ID
   * @param taskName 任务名称
   * @param status 任务状态
   * @param startTime 开始时间
   * @param endTime 结束时间
   * @param sourceUrl 数据源 URL
   * @param xmlFileHash XML 文件哈希
   * @param xmlFileSize XML 文件大小
   * @param tableProgressList 表进度列表
   * @param totalRecords 总记录数
   * @param processedRecords 已处理记录数
   * @param failedBatchCount 失败批次数
   * @param lastErrorMessage 最后错误信息
   */
  public MeshImportAggregate(
      MeshImportId id,
      String taskName,
      MeshImportTaskStatus status,
      Instant startTime,
      Instant endTime,
      String sourceUrl,
      String xmlFileHash,
      Long xmlFileSize,
      List<TableProgress> tableProgressList,
      Integer totalRecords,
      Integer processedRecords,
      Integer failedBatchCount,
      String lastErrorMessage) {
    super(id);
    this.taskName = taskName;
    this.status = status;
    this.startTime = startTime;
    this.endTime = endTime;
    this.sourceUrl = sourceUrl;
    this.xmlFileHash = xmlFileHash;
    this.xmlFileSize = xmlFileSize;
    this.tableProgressList = tableProgressList != null ? new ArrayList<>(tableProgressList) : new ArrayList<>();
    this.totalRecords = totalRecords;
    this.processedRecords = processedRecords;
    this.failedBatchCount = failedBatchCount;
    this.lastErrorMessage = lastErrorMessage;
  }

  // ========== 领域行为 ==========

  /**
   * 开始导入任务。
   *
   * <p>前置条件：任务状态为 PENDING
   *
   * <p>后置条件：任务状态变为 PROCESSING，发布 MeshImportStarted 事件
   *
   * @throws IllegalStateException 如果任务不是 PENDING 状态
   */
  public void startImport() {
    if (this.status != MeshImportTaskStatus.PENDING) {
      throw new IllegalStateException("只有 PENDING 状态的任务可以开始导入");
    }
    this.status = MeshImportTaskStatus.PROCESSING;
    this.startTime = Instant.now();
    // 发布领域事件
    addDomainEvent(new MeshImportStarted(this.getId(), this.sourceUrl, this.startTime));
  }

  /**
   * 更新指定表的进度。
   *
   * <p>用于断点续传，记录每张表的最后处理批次号
   *
   * @param tableName 表名
   * @param processedCount 已处理数
   * @param lastBatchNum 最后批次号
   * @throws IllegalArgumentException 如果表不存在
   */
  public void updateTableProgress(String tableName, Integer processedCount, Integer lastBatchNum) {
    TableProgress progress = findTableProgress(tableName);
    TableProgress updated = progress.updateProgress(processedCount, lastBatchNum);
    replaceTableProgress(tableName, updated);
    recalculateOverallProgress();
  }

  /**
   * 标记任务完成。
   *
   * <p>前置条件：所有表的状态为 COMPLETED
   *
   * <p>后置条件：任务状态变为 SUCCESS，发布 MeshImportCompleted 事件
   *
   * @throws IllegalStateException 如果有表未完成
   */
  public void markAsCompleted() {
    if (!allTablesCompleted()) {
      throw new IllegalStateException("所有表必须完成才能标记任务完成");
    }
    this.status = MeshImportTaskStatus.SUCCESS;
    this.endTime = Instant.now();
    long elapsedSeconds = Duration.between(startTime, endTime).getSeconds();
    addDomainEvent(
        new MeshImportCompleted(this.getId(), this.totalRecords, elapsedSeconds, this.endTime));
  }

  /**
   * 标记任务失败。
   *
   * @param errorMessage 失败原因
   */
  public void markAsFailed(String errorMessage) {
    this.status = MeshImportTaskStatus.FAILED;
    this.lastErrorMessage = errorMessage;
    this.endTime = Instant.now();
    addDomainEvent(
        new MeshImportFailed(this.getId(), errorMessage, this.processedRecords, this.endTime));
  }

  /**
   * 重试失败任务。
   *
   * <p>前置条件：任务状态为 FAILED
   *
   * <p>后置条件：任务状态变为 PROCESSING，重置失败批次计数
   *
   * @throws IllegalStateException 如果任务不是 FAILED 状态
   */
  public void retry() {
    if (this.status != MeshImportTaskStatus.FAILED) {
      throw new IllegalStateException("只有 FAILED 状态的任务可以重试");
    }
    this.status = MeshImportTaskStatus.PROCESSING;
    this.failedBatchCount = 0;
    this.lastErrorMessage = null;
  }

  // ========== 私有辅助方法 ==========

  /**
   * 查找指定表的进度。
   *
   * @param tableName 表名
   * @return 表进度
   * @throws IllegalArgumentException 如果表不存在
   */
  private TableProgress findTableProgress(String tableName) {
    return tableProgressList.stream()
        .filter(p -> p.getTableName().equals(tableName))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("表不存在: " + tableName));
  }

  /**
   * 替换指定表的进度。
   *
   * @param tableName 表名
   * @param updated 更新后的进度
   */
  private void replaceTableProgress(String tableName, TableProgress updated) {
    tableProgressList.removeIf(p -> p.getTableName().equals(tableName));
    tableProgressList.add(updated);
  }

  /**
   * 重新计算整体进度。
   */
  private void recalculateOverallProgress() {
    this.processedRecords =
        tableProgressList.stream().mapToInt(TableProgress::getProcessedCount).sum();
  }

  /**
   * 判断所有表是否已完成。
   *
   * @return true 如果所有表都完成
   */
  private boolean allTablesCompleted() {
    return tableProgressList.stream()
        .allMatch(p -> p.getStatus() == MeshTableImportStatus.COMPLETED);
  }

  // ========== 公共查询方法 ==========

  /**
   * 获取表进度列表（不可变视图）。
   *
   * @return 表进度列表
   */
  public List<TableProgress> getTableProgressList() {
    return Collections.unmodifiableList(tableProgressList);
  }

  /**
   * 计算处理速度（记录数/秒）。
   *
   * <p>用于实时监控导入性能和估算剩余时间。
   *
   * <p>计算逻辑：
   * <ul>
   *   <li>已处理记录数 ÷ 已用时间（秒）= 处理速度（记录/秒）
   *   <li>如果任务未开始（startTime 为 null），返回 null
   *   <li>如果未处理任何记录，返回 0.0
   * </ul>
   *
   * @return 处理速度（记录/秒），如果任务未开始返回 null
   * @since 0.2.0 (User Story 2 - 实时监控导入进度)
   */
  public Double calculateProcessSpeed() {
    if (this.startTime == null) {
      return null; // 任务未开始
    }

    long elapsedSeconds = Duration.between(this.startTime, Instant.now()).getSeconds();
    if (elapsedSeconds == 0) {
      return 0.0; // 刚开始，避免除以 0
    }

    if (this.processedRecords == null || this.processedRecords == 0) {
      return 0.0; // 未处理任何记录
    }

    return (double) this.processedRecords / elapsedSeconds;
  }

  /**
   * 估算剩余时间（秒）。
   *
   * <p>基于当前处理速度和剩余记录数估算完成时间。
   *
   * <p>计算逻辑：
   * <ul>
   *   <li>剩余记录数 = totalRecords - processedRecords
   *   <li>预计剩余时间 = 剩余记录数 ÷ 处理速度
   *   <li>如果任务未开始或无进度，返回 null
   *   <li>如果所有记录已处理，返回 0
   * </ul>
   *
   * @return 剩余时间（秒），如果无法估算返回 null
   * @since 0.2.0 (User Story 2 - 实时监控导入进度)
   */
  public Long estimateRemainingTime() {
    if (this.startTime == null || this.processedRecords == null || this.processedRecords == 0) {
      return null; // 无法估算（任务未开始或无进度）
    }

    int remaining = this.totalRecords - this.processedRecords;
    if (remaining <= 0) {
      return 0L; // 所有记录已处理
    }

    Double speed = calculateProcessSpeed();
    if (speed == null || speed == 0.0) {
      return null; // 无法计算速度
    }

    return (long) Math.ceil(remaining / speed);
  }

  /**
   * 获取整体进度百分比（0.0 - 100.0）。
   *
   * <p>计算逻辑：
   * <ul>
   *   <li>进度百分比 = (已处理记录数 ÷ 总记录数) × 100
   *   <li>如果总记录数为 0，返回 0.0
   * </ul>
   *
   * @return 整体进度百分比（0.0 - 100.0）
   * @since 0.2.0 (User Story 2 - 实时监控导入进度)
   */
  public Double getOverallProgress() {
    if (this.totalRecords == null || this.totalRecords == 0) {
      return 0.0;
    }

    if (this.processedRecords == null) {
      return 0.0;
    }

    return ((double) this.processedRecords / this.totalRecords) * 100.0;
  }

  /**
   * 清空领域事件（用于测试）。
   */
  public void clearDomainEvents() {
    pullDomainEvents();
  }

  /**
   * 获取领域事件列表（用于测试）。
   *
   * @return 领域事件列表
   */
  public List<com.patra.common.domain.DomainEvent> getDomainEvents() {
    return peekDomainEvents();
  }

  // ========== 不变量验证 ==========

  @Override
  protected void assertInvariants() {
    Assert.notBlank(taskName, "任务名称不能为空");
    Assert.notNull(status, "任务状态不能为空");
    Assert.notBlank(sourceUrl, "数据源 URL 不能为空");
  }

  @Override
  public String toString() {
    return String.format(
        "MeshImportAggregate[id=%s, taskName=%s, status=%s, processedRecords=%d/%d]",
        getId(), taskName, status.getDisplayName(), processedRecords, totalRecords);
  }
}
