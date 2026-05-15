package dev.linqibin.patra.ingest.app.eventhandler;

import dev.linqibin.patra.ingest.domain.event.SliceStatusChangedEvent;
import dev.linqibin.patra.ingest.domain.event.TaskCompletedEvent;
import dev.linqibin.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import dev.linqibin.patra.ingest.domain.model.aggregate.TaskAggregate;
import dev.linqibin.patra.ingest.domain.model.enums.SliceStatus;
import dev.linqibin.patra.ingest.domain.model.enums.TaskStatus;
import dev.linqibin.patra.ingest.domain.port.PlanSliceRepository;
import dev.linqibin.patra.ingest.domain.port.TaskRepository;
import dev.linqibin.patra.ingest.domain.service.SliceStatusCalculator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/// Task 完成事件处理器 (强制 1:1 Slice-Task 关系)
///
/// 处理的领域事件: {@link TaskCompletedEvent} (Task 完成事件)
///
/// 触发的后续操作:
///
/// - 查询该 Slice 对应的 Task (1:1 关系)
///   - 使用 {@link SliceStatusCalculator} 直接映射 Task 状态到 Slice 状态
///   - 更新 Slice 状态
///   - 如果 Slice 状态变化,发布 {@link SliceStatusChangedEvent} 触发 Plan 状态更新
///
/// Slice:Task 为 1:1 关系。Task 完成时（SUCCEEDED/FAILED）直接映射到 Slice 状态。
///
/// 事件流转: TaskCompletedEvent → SliceStatusChangedEvent → Plan 状态更新
///
/// 并发处理: 使用乐观锁防止并发冲突,发生冲突时跳过本次更新
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskCompletedEventHandler {

  private final TaskRepository taskRepository;
  private final PlanSliceRepository sliceRepository;
  private final ApplicationEventPublisher eventPublisher;

  /// 处理 Task 完成事件 (事务提交后触发)
  ///
  /// @param event Task 完成事件
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(TaskCompletedEvent event) {
    try {
      log.debug(
          "处理 TaskCompletedEvent: taskId={} sliceId={} planId={} status={}",
          event.taskId(),
          event.sliceId(),
          event.planId(),
          event.status());

      // 1. 查询该 Slice 对应的 Task (1:1 关系)
      Optional<TaskAggregate> taskOpt = taskRepository.findBySliceId(event.sliceId());
      if (taskOpt.isEmpty()) {
        log.warn("未找到 sliceId={} 对应的 Task,跳过 Slice 状态更新 (可能存在数据不一致)", event.sliceId());
        return;
      }

      TaskAggregate task = taskOpt.get();
      TaskStatus taskStatus = task.getStatus();

      // 2. 使用领域服务计算 Slice 状态 (直接 1:1 映射)
      SliceStatus newStatus = SliceStatusCalculator.calculate(taskStatus);

      // 3. 更新 Slice 状态
      PlanSliceAggregate slice =
          sliceRepository
              .findById(event.sliceId())
              .orElseThrow(
                  () -> new IllegalStateException("Slice 不存在: sliceId=" + event.sliceId()));

      SliceStatus oldStatus = slice.getStatus();

      // 4. 检查状态是否变化 (幂等性)
      if (oldStatus == newStatus) {
        log.debug("Slice 状态未变化,跳过更新: sliceId={}", event.sliceId());
        return;
      }

      // 5. 更新并保存
      slice.updateStatus(newStatus);
      sliceRepository.save(slice);

      log.info("Slice 状态已更新: sliceId={} {} -> {}", event.sliceId(), oldStatus, newStatus);

      // 6. 发布 SliceStatusChangedEvent (如果状态变化)
      SliceStatusChangedEvent sliceEvent =
          SliceStatusChangedEvent.of(
              event.sliceId(), event.planId(), oldStatus.getCode(), newStatus.getCode());
      eventPublisher.publishEvent(sliceEvent);

      log.debug(
          "已发布 SliceStatusChangedEvent: sliceId={} planId={}", event.sliceId(), event.planId());

    } catch (OptimisticLockingFailureException e) {
      // 乐观锁冲突: 其他线程已更新,跳过本次更新
      log.warn("Slice 更新发生乐观锁冲突,跳过: sliceId={}", event.sliceId());
    } catch (Exception e) {
      // 其他异常: 记录错误,依赖对账任务修复
      log.error(
          "处理 TaskCompletedEvent 失败: taskId={} sliceId={} planId={}",
          event.taskId(),
          event.sliceId(),
          event.planId(),
          e);
    }
  }
}
