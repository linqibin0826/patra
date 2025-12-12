package com.patra.ingest.app.usecase.plan;

import cn.hutool.core.collection.CollUtil;
import com.patra.ingest.app.usecase.plan.dto.PlanIngestionResult;
import com.patra.ingest.app.usecase.plan.publisher.TaskOutboxPublisher;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 计划发布协调器。
///
/// 职责：
///
/// - 从任务聚合根收集领域事件
///   - 通过 Outbox 模式发布事件
///
/// 注意：该协调器不使用 `@Transactional`，依赖主编排器的外部事务边界来确保与持久化的原子性。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanPublishingCoordinator {

  private final TaskOutboxPublisher taskOutboxPublisher;

  /// 为新计划发布任务入队事件。
  ///
  /// @param queuedEvents 任务入队事件
  /// @param plan 计划聚合根
  /// @param schedule 调度实例
  public void publishNewPlanEvents(
      List<TaskQueuedEvent> queuedEvents, PlanAggregate plan, ScheduleInstanceAggregate schedule) {
    log.debug("发布 {} 个任务就绪事件到 Outbox，计划 [{}]", queuedEvents.size(), plan.getId());
    taskOutboxPublisher.publish(queuedEvents, plan, schedule);
  }

  /// 为现有计划发布重试事件。
  ///
  /// @param retryEvents 重试事件
  /// @param plan 计划聚合根
  /// @param schedule 调度实例
  public void publishRetryEvents(
      List<TaskQueuedEvent> retryEvents, PlanAggregate plan, ScheduleInstanceAggregate schedule) {
    taskOutboxPublisher.publishRetry(retryEvents, plan, schedule);
    log.info("重新入队 {} 个失败任务，现有计划 [{}]", retryEvents.size(), plan.getId());
  }

  /// 从任务聚合根收集 TaskQueuedEvent 实例。
  ///
  /// 显式触发 {@link TaskAggregate#raiseQueuedEvent()} 以确保事件存在。
  ///
  /// @param tasks 任务集合
  /// @return 入队事件列表（无任务时返回空列表）
  public List<TaskQueuedEvent> collectQueuedEvents(List<TaskAggregate> tasks) {
    if (CollUtil.isEmpty(tasks)) {
      return List.of();
    }
    List<TaskQueuedEvent> events = new ArrayList<>(tasks.size());
    for (TaskAggregate task : tasks) {
      task.raiseQueuedEvent();
      task.pullDomainEvents().stream()
          .filter(TaskQueuedEvent.class::isInstance)
          .map(TaskQueuedEvent.class::cast)
          .forEach(events::add);
    }
    return events;
  }

  /// 从计划数据构建计划接入结果。
  ///
  /// @param schedule 调度实例
  /// @param plan 计划聚合根
  /// @param slices 计划切片
  /// @param tasks 任务
  /// @return 计划接入结果
  public PlanIngestionResult buildIngestionResult(
      ScheduleInstanceAggregate schedule,
      PlanAggregate plan,
      List<PlanSliceAggregate> slices,
      List<TaskAggregate> tasks) {
    return new PlanIngestionResult(
        schedule.getId().value(),
        plan.getId().value(),
        slices.stream().map(s -> s.getId().value()).collect(Collectors.toList()),
        tasks.size(),
        plan.getStatus().name());
  }

  /// 从组装状态构建计划接入结果。
  ///
  /// @param schedule 调度实例
  /// @param plan 计划聚合根
  /// @param slices 计划切片
  /// @param tasksCount 任务数量
  /// @param statusName 状态名称
  /// @return 计划接入结果
  public PlanIngestionResult buildIngestionResult(
      ScheduleInstanceAggregate schedule,
      PlanAggregate plan,
      List<PlanSliceAggregate> slices,
      int tasksCount,
      String statusName) {
    return new PlanIngestionResult(
        schedule.getId().value(),
        plan.getId().value(),
        slices.stream().map(s -> s.getId().value()).collect(Collectors.toList()),
        tasksCount,
        statusName);
  }
}
