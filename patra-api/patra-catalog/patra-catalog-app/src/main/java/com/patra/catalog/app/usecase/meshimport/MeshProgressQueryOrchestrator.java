package com.patra.catalog.app.usecase.meshimport;

import com.patra.catalog.api.dto.MeshProgressDTO;
import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.valueobject.FailedBatch;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.catalog.domain.model.valueobject.TableProgress;
import com.patra.catalog.domain.port.MeshBatchDetailRepository;
import com.patra.catalog.domain.port.MeshImportRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// MeSH 进度查询编排器。
///
/// 职责：
///
/// - 查询 MeSH 导入任务的实时进度
///   - 编排聚合根的进度计算方法
///   - 查询失败批次详情
///   - 组装进度 DTO 返回给适配层
///
/// **编排流程**（queryProgress 方法）：
///
/// **事务管理**：
///
/// - 只读事务：`@Transactional(readOnly = true)`
///   - 无副作用：不修改聚合根状态
///
/// **依赖注入**：
///
/// - {@link MeshImportRepository} - 任务仓储（查询聚合根）
///   - {@link MeshBatchDetailRepository} - 批次详情仓储（查询失败批次）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class MeshProgressQueryOrchestrator {

  private final MeshImportRepository meshImportPort;
  private final MeshBatchDetailRepository meshBatchDetailPort;

  /// 查询导入任务的实时进度。
  ///
  /// 包含整体进度、各表进度、失败批次、处理速度和剩余时间估算。
  ///
  /// @param importId 任务 ID
  /// @return 进度 DTO
  /// @throws IllegalArgumentException 如果任务不存在
  @Transactional(readOnly = true)
  public MeshProgressDTO queryProgress(MeshImportId importId) {
    log.debug("查询任务进度，任务 ID：{}", importId.value());

    // 1. 查询任务聚合根
    MeshImportAggregate aggregate =
        meshImportPort
            .findById(importId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + importId.value()));

    // 2. 调用聚合根的进度计算方法
    Double overallProgress = aggregate.getOverallProgress();
    Double processSpeed = aggregate.calculateProcessSpeed();
    Long estimatedRemaining = aggregate.estimateRemainingTime();

    log.debug(
        "进度计算结果 - 整体进度：{}%, 处理速度：{} 记录/秒, 剩余时间：{} 秒",
        overallProgress, processSpeed, estimatedRemaining);

    // 3. 查询失败批次
    List<FailedBatch> failedBatches = meshBatchDetailPort.findFailedBatches(importId);

    log.debug("查询到 {} 个失败批次", failedBatches.size());

    // 4. 组装 DTO 并返回
    return buildProgressDTO(
        aggregate, overallProgress, processSpeed, estimatedRemaining, failedBatches);
  }

  // ========== 私有辅助方法 ==========

  /// 构建进度 DTO。
  ///
  /// @param aggregate 任务聚合根
  /// @param overallProgress 整体进度百分比
  /// @param processSpeed 处理速度（记录/秒）
  /// @param estimatedRemaining 预计剩余时间（秒）
  /// @param failedBatches 失败批次列表
  /// @return 进度 DTO
  private MeshProgressDTO buildProgressDTO(
      MeshImportAggregate aggregate,
      Double overallProgress,
      Double processSpeed,
      Long estimatedRemaining,
      List<FailedBatch> failedBatches) {

    // 计算已用时间
    Long elapsedSeconds = calculateElapsedSeconds(aggregate);

    // 转换表进度列表
    List<MeshProgressDTO.TableProgressDTO> tableProgressDTOs =
        aggregate.getTableProgressList().stream().map(this::toTableProgressDTO).toList();

    // 转换失败批次列表
    List<MeshProgressDTO.FailedBatchDTO> failedBatchDTOs =
        failedBatches.stream().map(this::toFailedBatchDTO).toList();

    // 构建 DTO
    return MeshProgressDTO.builder()
        .taskId(aggregate.getId().value().toString())
        .taskName(aggregate.getTaskName())
        .status(aggregate.getStatus().getCode())
        .totalRecords(aggregate.getTotalRecords())
        .processedRecords(aggregate.getProcessedRecords())
        .overallProgress(overallProgress)
        .processSpeed(processSpeed)
        .estimatedRemainingSeconds(estimatedRemaining)
        .startTime(aggregate.getStartTime())
        .endTime(aggregate.getEndTime())
        .elapsedSeconds(elapsedSeconds)
        .tableProgress(tableProgressDTOs)
        .failedBatches(failedBatchDTOs)
        .build();
  }

  /// 计算已用时间（秒）。
  ///
  /// @param aggregate 任务聚合根
  /// @return 已用时间（秒），如果任务未开始返回 0
  private Long calculateElapsedSeconds(MeshImportAggregate aggregate) {
    if (aggregate.getStartTime() == null) {
      return 0L; // 任务未开始
    }

    Instant endTimeOrNow = aggregate.getEndTime() != null ? aggregate.getEndTime() : Instant.now();
    return Duration.between(aggregate.getStartTime(), endTimeOrNow).getSeconds();
  }

  /// 转换表进度为 DTO。
  ///
  /// @param tableProgress 表进度值对象
  /// @return 表进度 DTO
  private MeshProgressDTO.TableProgressDTO toTableProgressDTO(TableProgress tableProgress) {
    return MeshProgressDTO.TableProgressDTO.builder()
        .tableName(tableProgress.getTableName())
        .displayName(getDisplayName(tableProgress.getTableName()))
        .totalCount(tableProgress.getTotalCount())
        .processedCount(tableProgress.getProcessedCount())
        .failedCount(tableProgress.getFailedCount())
        .progressPercentage(tableProgress.getProgressPercentage())
        .status(tableProgress.getStatus().getCode())
        .build();
  }

  /// 转换失败批次为 DTO。
  ///
  /// @param failedBatch 失败批次值对象
  /// @return 失败批次 DTO
  private MeshProgressDTO.FailedBatchDTO toFailedBatchDTO(FailedBatch failedBatch) {
    return MeshProgressDTO.FailedBatchDTO.builder()
        .batchId(failedBatch.getBatchId())
        .tableName(failedBatch.getTableName())
        .batchNum(failedBatch.getBatchNum())
        .failureReason(failedBatch.getFailureReason())
        .failureTime(failedBatch.getFailureTime())
        .retryCount(failedBatch.getRetryCount())
        .build();
  }

  /// 获取表的显示名称（中文）。
  ///
  /// @param tableName 表名（如 "descriptor"）
  /// @return 显示名称（如 "主题词"）
  private String getDisplayName(String tableName) {
    return switch (tableName) {
      case "descriptor" -> "主题词";
      case "qualifier" -> "副主题词";
      case "tree-number" -> "树形编号";
      case "entry-term" -> "入口术语";
      case "concept" -> "概念";
      default -> tableName; // 未知表名，直接返回
    };
  }
}
