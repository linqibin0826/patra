package com.patra.ingest.app.eventhandler;

import com.patra.ingest.domain.event.SliceStatusChangedEvent;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.service.PlanStatusCalculator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/// Slice 状态变更事件处理器
///
/// 处理的领域事件: {@link SliceStatusChangedEvent} (Slice 状态变更事件)
///
/// 触发的后续操作:
///
/// - 查询该 Plan 下所有 Slice 的状态
///   - 使用 {@link PlanStatusCalculator} 聚合计算 Plan 状态
///   - 更新 Plan 状态 (PENDING → ASSIGNED → FINISHED)
///
/// 状态流转规则: 当 Slice 状态变化 (PENDING → ASSIGNED → FINISHED) 时,聚合所有 Slice 状态 来更新 Plan 状态 (READY →
/// ARCHIVED)
///
/// Plan 状态仅反映生命周期，不区分 Task 成功/失败。所有 Slice 为 FINISHED 时，Plan 变为 ARCHIVED。
///
/// 并发处理: 使用乐观锁防止并发冲突,发生冲突时跳过本次更新
@Component
@RequiredArgsConstructor
@Slf4j
public class SliceStatusChangedEventHandler {

  private final PlanSliceRepository sliceRepository;
  private final PlanRepository planRepository;

  /// 处理 Slice 状态变更事件 (事务提交后触发)
  ///
  /// @param event Slice 状态变更事件
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(SliceStatusChangedEvent event) {
    try {
      log.debug(
          "处理 SliceStatusChangedEvent: sliceId={} planId={} {} -> {}",
          event.sliceId(),
          event.planId(),
          event.oldStatus(),
          event.newStatus());

      // 1. 查询该 Plan 下所有 Slice
      List<PlanSliceAggregate> slices = sliceRepository.findByPlanId(event.planId());
      List<SliceStatus> sliceStatuses = slices.stream().map(PlanSliceAggregate::getStatus).toList();

      // 2. 加载 Plan 聚合根
      PlanAggregate plan =
          planRepository
              .findById(event.planId())
              .orElseThrow(() -> new IllegalStateException("Plan 不存在: planId=" + event.planId()));

      PlanStatus oldStatus = plan.getStatus();

      // 3. 使用领域服务聚合计算 Plan 状态
      PlanStatus newStatus = PlanStatusCalculator.calculate(sliceStatuses, oldStatus);

      // 4. 检查状态是否变化 (幂等性)
      if (oldStatus == newStatus) {
        log.debug("Plan 状态未变化,跳过更新: planId={}", event.planId());
        return;
      }

      // 5. 更新并保存
      plan.updateStatus(newStatus);
      planRepository.save(plan);

      log.info("Plan 状态已更新: planId={} {} -> {}", event.planId(), oldStatus, newStatus);

    } catch (OptimisticLockingFailureException e) {
      // 乐观锁冲突: 其他线程已更新,跳过本次更新
      log.warn("Plan 更新发生乐观锁冲突,跳过: planId={}", event.planId());
    } catch (Exception e) {
      // 其他异常: 记录错误,依赖对账任务修复
      log.error(
          "处理 SliceStatusChangedEvent 失败: sliceId={} planId={}",
          event.sliceId(),
          event.planId(),
          e);
    }
  }
}
