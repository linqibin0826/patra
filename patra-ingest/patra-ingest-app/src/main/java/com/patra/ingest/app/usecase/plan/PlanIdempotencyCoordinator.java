package com.patra.ingest.app.usecase.plan;

import com.patra.ingest.app.usecase.plan.dto.PlanIngestionResult;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 计划幂等性协调器。
///
/// 职责：
///
/// - 处理重复计划检测
///   - 识别符合重试条件的任务
///   - 协调重试操作
///
/// 注意：该协调器不使用 `@Transactional`，依赖主编排器的外部事务边界来确保与持久化和发布的原子性。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanIdempotencyCoordinator {

  private final PlanSliceRepository planSliceRepository;
  private final TaskRepository taskRepository;
  private final PlanPersistenceCoordinator persistenceCoordinator;
  private final PlanPublishingCoordinator publishingCoordinator;

  /// 当发现相同 planKey 的现有计划时处理幂等计划复用。
  ///
  /// 该方法：
  ///
  /// @param existingPlan 现有计划聚合根
  /// @param schedule 当前调度实例
  /// @param planKey 幂等键（用于日志）
  /// @return 基于现有计划的接入结果
  public PlanIngestionResult handleIdempotentPlanReuse(
      PlanAggregate existingPlan, ScheduleInstanceAggregate schedule, String planKey) {
    logDuplicatePlanDetection(existingPlan, planKey);

    List<PlanSliceAggregate> existingSlices =
        planSliceRepository.findByPlanId(existingPlan.getId().value());
    List<TaskAggregate> existingTasks = taskRepository.findByPlanId(existingPlan.getId().value());
    List<TaskAggregate> retryTasks = prepareTasksForRetry(existingTasks);

    if (!retryTasks.isEmpty()) {
      processRetryTasks(existingPlan, schedule, retryTasks);
    } else {
      log.info("现有计划 [{}] 无需重试任务，返回现有状态", existingPlan.getId());
    }

    return publishingCoordinator.buildIngestionResult(
        schedule, existingPlan, existingSlices, existingTasks);
  }

  /// 记录重复计划检测日志。
  ///
  /// @param existingPlan 现有计划聚合根
  /// @param planKey 计划幂等键
  private void logDuplicatePlanDetection(PlanAggregate existingPlan, String planKey) {
    log.info(
        "检测到重复计划接入 数据源 [{}] 操作 [{}]: 复用现有计划 [{}]，planKey={}",
        existingPlan.getProvenanceCode(),
        existingPlan.getOperationCode(),
        existingPlan.getId(),
        planKey);
  }

  /// 通过发布重试事件处理重试任务。
  ///
  /// 注意：重构后，Plan 状态在重试期间不变。Plan 保持当前状态（通常为 READY），而失败任务被重新入队。
  ///
  /// @param existingPlan 现有计划聚合根
  /// @param schedule 调度实例
  /// @param retryTasks 待重试任务
  private void processRetryTasks(
      PlanAggregate existingPlan,
      ScheduleInstanceAggregate schedule,
      List<TaskAggregate> retryTasks) {
    // 计划状态不变 - 任务仅被重新入队执行
    List<TaskQueuedEvent> retryEvents = publishingCoordinator.collectQueuedEvents(retryTasks);
    publishingCoordinator.publishRetryEvents(retryEvents, existingPlan, schedule);
  }

  /// 通过重置失败任务来准备重试任务。
  ///
  /// @param tasks 待检查的任务列表
  /// @return 已准备重试的任务列表
  private List<TaskAggregate> prepareTasksForRetry(List<TaskAggregate> tasks) {
    List<TaskAggregate> retryTasks = new ArrayList<>();
    for (TaskAggregate task : tasks) {
      if (shouldRetry(task)) {
        task.prepareForRetry();
        persistenceCoordinator.saveTask(task);
        retryTasks.add(task);
      }
    }
    return retryTasks;
  }

  /// 判断任务是否符合补偿重试条件。
  ///
  /// 注意：重构后仅检查 FAILED 状态。
  ///
  /// @param task 任务聚合根
  /// @return 如需重试则返回 true（仅 FAILED 状态）
  private boolean shouldRetry(TaskAggregate task) {
    TaskStatus status = task.getStatus();
    return status == TaskStatus.FAILED;
  }
}
