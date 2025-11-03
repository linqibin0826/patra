package com.patra.ingest.app.usecase.execution.prepare;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.app.usecase.execution.idempotency.IdempotencyChecker;
import com.patra.ingest.app.usecase.execution.lease.LeaseManagementService;
import com.patra.ingest.app.usecase.execution.session.ExecutionContextLoader;
import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import com.patra.ingest.app.usecase.execution.session.ExecutionSessionManager;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.domain.port.TaskRunRepository;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 准备阶段实现。
 *
 * <p>职责:幂等性检查 → 租约获取 → 会话初始化 → 上下文加载。
 *
 * <p>设计要点:
 *
 * <ul>
 *   <li>幂等性:调用 IdempotencyChecker.isAlreadySucceeded();如果已完成则抛出异常跳过
 *   <li>租约:调用 LeaseManagementService.tryAcquireLease();获取失败时抛出异常
 *   <li>会话:调用 ExecutionSessionManager.createSession() 创建 TaskRun 并启动心跳
 *   <li>上下文:调用 ExecutionContextLoader.loadContext() 恢复配置并编译表达式
 * </ul>
 *
 * <p>错误处理:
 *
 * <ul>
 *   <li>TaskAlreadySucceededException 用于幂等跳过
 *   <li>LeaseAcquisitionFailedException 当租约获取失败时
 *   <li>传播 IllegalStateException 用于上下文加载失败
 * </ul>
 *
 * <p>日志记录:
 *
 * <ul>
 *   <li>INFO: 关键步骤(幂等性、租约、会话、上下文)
 *   <li>WARN: 幂等跳过、租约失败
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrepareTaskExecutionUseCaseImpl implements PrepareTaskExecutionUseCase {

  private final TaskRepository taskRepository;
  private final PlanSliceRepository planSliceRepository;
  private final IdempotencyChecker idempotencyChecker;
  private final LeaseManagementService leaseManagementService;
  private final ExecutionSessionManager sessionManager;
  private final ExecutionContextLoader contextLoader;
  private final TaskRunRepository taskRunRepository;
  private final Clock clock;

  @Value("${task.execution.lease.duration:60}")
  private int leaseDurationSeconds;

  /**
   * 执行准备(幂等性检查、租约获取、会话创建、上下文加载)。
   *
   * <p>优化:
   *
   * <ul>
   *   <li>一次性加载 Task 以避免 createSession/loadContext 的重复读取
   *   <li>异常时清理以确保心跳停止和租约释放
   * </ul>
   */
  @Override
  public PrepareResult prepare(TaskReadyCommand command) {
    long taskId = command.taskId();
    String idempotentKey = command.idempotentKey();

    log.info("开始准备任务执行 taskId={} idemKey={}", taskId, idempotentKey);

    // 1) 幂等性检查
    log.debug("检查幂等性 taskId={} idemKey={}", taskId, idempotentKey);
    if (idempotencyChecker.isAlreadySucceeded(taskId, idempotentKey)) {
      throw new TaskAlreadySucceededException(
          "任务已成功 taskId=" + taskId + " idemKey=" + idempotentKey);
    }

    // 2) 生成租约持有者 ID
    String leaseOwner = generateLeaseOwner();

    // 3) 尝试获取租约
    log.debug("尝试获取租约 taskId={} owner={} duration={}s", taskId, leaseOwner, leaseDurationSeconds);
    Duration leaseDuration = Duration.ofSeconds(leaseDurationSeconds);
    boolean acquired = leaseManagementService.tryAcquireLease(taskId, leaseOwner, leaseDuration);
    if (!acquired) {
      throw new LeaseAcquisitionFailedException("租约获取失败 taskId=" + taskId + " owner=" + leaseOwner);
    }

    log.info("租约已获取 taskId={} owner={}", taskId, leaseOwner);

    // 4) 加载 Task(单次读取以避免重复)
    TaskAggregate task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("未找到任务 taskId=" + taskId));

    // 标记 Slice 为 EXECUTING(如果仍为 PENDING)
    PlanSliceAggregate slice =
        planSliceRepository
            .findById(task.getSliceId())
            .orElseThrow(() -> new IllegalStateException("未找到切片: sliceId=" + task.getSliceId()));

    if (slice.getStatus() == SliceStatus.PENDING) {
      slice.markAssigned();
      planSliceRepository.save(slice);
      log.info("切片已标记为 ASSIGNED sliceId={}", slice.getId());
    }

    ExecutionSession session = null;
    try {
      // 5) 初始化会话(创建 TaskRun,启动心跳)
      String correlationId = command.getCorrelationId();
      session =
          sessionManager.createSession(
              task, // 使用预查询的 task
              leaseOwner,
              correlationId);

      Long runId = session.runId();
      log.info("会话已创建 taskId={} runId={} owner={}", taskId, runId, leaseOwner);

      // 6) 加载执行上下文(恢复配置,编译表达式)
      log.debug("加载执行上下文 taskId={} runId={}", taskId, runId);
      ExecutionContext context = contextLoader.loadContext(task, runId);

      // 7) 标记 task/run 为 RUNNING
      TaskRun taskRun =
          taskRunRepository
              .findById(runId)
              .orElseThrow(() -> new IllegalStateException("未找到 TaskRun runId=" + runId));
      Instant now = clock.instant();
      taskRun.bindRunContext(correlationId);
      taskRun.start(now);
      taskRunRepository.save(taskRun);
      task.markRunning(now, correlationId);
      taskRepository.save(task);

      log.info("准备任务执行已完成 taskId={} runId={}", taskId, runId);

      return new PrepareResult(session, context);

    } catch (Exception e) {
      // 失败时清理资源
      if (session != null) {
        log.warn("准备失败,清理资源 taskId={} runId={}", taskId, session.runId(), e);
        try {
          // 停止心跳
          session.heartbeatHandle().stop();
          // 释放租约
          leaseManagementService.releaseLease(taskId);
        } catch (Exception cleanupEx) {
          log.error("资源清理失败 taskId={} runId={}", taskId, session.runId(), cleanupEx);
        }
      }
      throw e;
    }
  }

  /**
   * 生成租约持有者标识符。
   *
   * <p>格式: hostname:pid:execId
   *
   * <p>结合机器 ID(hostname) + 进程 ID(PID) + 执行 ID(UUID) 以确保唯一性和可追溯性。
   */
  private String generateLeaseOwner() {
    try {
      String hostname = InetAddress.getLocalHost().getHostName();
      String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
      String execId = UUID.randomUUID().toString().substring(0, 8);
      return String.format("%s:%s:%s", hostname, pid, execId);
    } catch (UnknownHostException e) {
      // 回退:如果无法解析则使用 "unknown" 作为主机名
      log.warn("无法解析主机名,使用回退方案", e);
      String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
      String execId = UUID.randomUUID().toString().substring(0, 8);
      return String.format("unknown:%s:%s", pid, execId);
    }
  }
}
