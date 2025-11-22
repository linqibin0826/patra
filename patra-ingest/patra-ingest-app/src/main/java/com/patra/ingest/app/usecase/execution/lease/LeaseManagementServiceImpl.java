package com.patra.ingest.app.usecase.execution.lease;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.port.TaskRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 租约管理服务实现。
/// 
/// 职责:封装与任务租约相关的仓储操作并提供统一 API。
/// 
/// 设计要点:
/// 
/// - tryAcquireLease: 委托给 TaskRepository.tryAcquireLease() 进行 CAS 获取
///   - renewLease: 委托给 TaskRepository.renewLease()
///   - releaseLease: 加载聚合,调用 releaseLease(),然后保存
///   - validateLease: 加载聚合并检查 leaseInfo.owner
/// 
/// 日志记录:关键租约操作(获取、释放、验证失败)记录 INFO 级别日志。
/// 
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaseManagementServiceImpl implements LeaseManagementService {

  private final TaskRepository taskRepository;
  private final Clock clock;

  /// 尝试获取租约。
  @Override
  public boolean tryAcquireLease(Long taskId, String owner, Duration leaseDuration) {
    // 加载任务以获取幂等键(tryAcquireLease 需要)
    TaskAggregate task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("未找到任务 taskId=" + taskId));

    Instant now = clock.instant();
    int ttlSeconds = (int) leaseDuration.toSeconds();
    boolean acquired =
        taskRepository.tryAcquireLease(taskId, owner, now, ttlSeconds, task.getIdempotentKey());

    if (acquired) {
      log.info("租约已获取 taskId={} owner={}", taskId, owner);
    }
    return acquired;
  }

  /// 续约租约。
  @Override
  public boolean renewLease(Long taskId, String owner, Duration leaseDuration) {
    Instant now = clock.instant();
    int ttlSeconds = (int) leaseDuration.toSeconds();
    boolean renewed = taskRepository.renewLease(taskId, owner, now, ttlSeconds);
    log.debug("租约续约结果 taskId={} owner={} renewed={}", taskId, owner, renewed);
    return renewed;
  }

  /// 释放租约。
  @Override
  public void releaseLease(Long taskId) {
    TaskAggregate task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("未找到任务 taskId=" + taskId));

    // 调用领域对象的释放方法然后保存
    task.releaseLease();
    taskRepository.save(task);
    log.info("租约已释放 taskId={}", taskId);
  }

  /// 验证租约(持有者仍然是当前节点)。
  @Override
  public boolean validateLease(Long taskId, String owner) {
    TaskAggregate task = taskRepository.findById(taskId).orElse(null);

    if (task == null) {
      log.warn("租约验证失败: 未找到任务 taskId={}", taskId);
      return false;
    }

    boolean valid = task.getLeaseInfo().isHeld() && owner.equals(task.getLeaseInfo().owner());

    if (!valid) {
      log.warn(
          "租约验证失败 taskId={} expectedOwner={} actualOwner={}",
          taskId,
          owner,
          task.getLeaseInfo().owner());
    }
    return valid;
  }
}
