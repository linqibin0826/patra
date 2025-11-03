package com.patra.ingest.app.usecase.execution.session;

import com.patra.ingest.app.usecase.execution.lease.HeartbeatRenewalService;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.domain.port.TaskRunRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 执行会话管理器实现
 *
 * <p>职责: 创建 TaskRun、启动心跳续期、封装会话清理逻辑。
 *
 * <h3>设计要点</h3>
 *
 * <ul>
 *   <li>获取最新 attemptNo,然后创建新的 TaskRun (attemptNo + 1)
 *   <li>持久化 TaskRun 以获取 runId
 *   <li>通过 {@link HeartbeatRenewalService} 启动心跳续期
 *   <li>返回包含 taskId/runId/leaseOwner/heartbeatHandle 的 {@link ExecutionSession}
 * </ul>
 *
 * <h3>配置项</h3>
 *
 * <ul>
 *   <li><b>task.execution.lease.duration</b>: 租约持续时间(秒,默认 60)
 *   <li><b>task.execution.lease.renewal-interval</b>: 续期间隔(秒,默认 20)
 * </ul>
 *
 * <h3>日志</h3>
 *
 * <p>会话创建时记录 INFO 级别日志(taskId/runId/attemptNo)。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionSessionManagerImpl implements ExecutionSessionManager {

  private final TaskRepository taskRepository;
  private final TaskRunRepository taskRunRepository;
  private final HeartbeatRenewalService heartbeatRenewalService;

  @Value("${task.execution.lease.duration:60}")
  private int leaseDurationSeconds;

  @Value("${task.execution.lease.renewal-interval:20}")
  private int renewalIntervalSeconds;

  /** 创建执行会话(TaskRun + 心跳) */
  @Override
  public ExecutionSession createSession(Long taskId, String leaseOwner, String correlationId) {
    // 查询任务并委托给重载方法
    TaskAggregate task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found taskId=" + taskId));

    return createSession(task, leaseOwner, correlationId);
  }

  /** 创建执行会话(TaskRun + 心跳) - 优化版,避免重复加载 Task */
  @Override
  public ExecutionSession createSession(
      TaskAggregate task, String leaseOwner, String correlationId) {
    Long taskId = task.getId();

    // 1) 获取最新 attemptNo 并计算下一个
    int latestAttemptNo = taskRunRepository.getLatestAttemptNo(taskId);
    int newAttemptNo = latestAttemptNo + 1;

    // 2) 创建 TaskRun 实体
    TaskRun taskRun =
        new TaskRun(
            null, // id 为 null;由数据库在插入时生成
            taskId,
            newAttemptNo,
            task.getProvenanceCode(),
            task.getOperationCode());

    // 3) 持久化 TaskRun 并获取生成的 runId
    TaskRun savedRun = taskRunRepository.save(taskRun);
    Long runId = savedRun.getId();

    log.info(
        "已创建执行会话: taskId={}, runId={}, attemptNo={}, owner={}",
        taskId,
        runId,
        newAttemptNo,
        leaseOwner);

    // 4) 启动心跳续期
    Duration leaseDuration = Duration.ofSeconds(leaseDurationSeconds);
    Duration renewalInterval = Duration.ofSeconds(renewalIntervalSeconds);
    ExecutionSession.HeartbeatHandle heartbeatHandle =
        heartbeatRenewalService.startHeartbeat(taskId, leaseOwner, leaseDuration, renewalInterval);

    // 5) 返回执行会话
    return new ExecutionSession(
        taskId, runId, leaseOwner, heartbeatHandle, false // 初始状态：租约未撤销
        );
  }
}
