package com.patra.ingest.app.usecase.execution.prepare;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.app.usecase.execution.support.*;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import com.patra.ingest.domain.port.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;

/**
 * 准备任务执行用例实现。
 * <p>
 * 职责：幂等检查 → 租约抢占 → 会话初始化 → 上下文加载。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>幂等检查：调用 IdempotencyChecker.isAlreadySucceeded()，已成功则抛异常跳过执行。</li>
 *   <li>租约抢占：调用 LeaseManagementService.tryAcquireLease()，抢占失败则抛异常。</li>
 *   <li>会话初始化：调用 ExecutionSessionManager.createSession()，创建 TaskRun、启动心跳。</li>
 *   <li>上下文加载：调用 ExecutionContextLoader.loadContext()，还原配置、编译表达式。</li>
 * </ul>
 * </p>
 * <p>
 * 异常处理：
 * <ul>
 *   <li>任务已成功执行：抛出 TaskAlreadySucceededException。</li>
 *   <li>租约抢占失败：抛出 LeaseAcquisitionFailedException。</li>
 *   <li>上下文加载失败：传播 IllegalStateException。</li>
 * </ul>
 * </p>
 * <p>
 * 日志策略：
 * <ul>
 *   <li>INFO：关键步骤（幂等检查、租约抢占、会话创建、上下文加载）。</li>
 *   <li>WARN：幂等跳过、租约抢占失败。</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrepareTaskExecutionUseCaseImpl implements PrepareTaskExecutionUseCase {

    private final TaskRepository taskRepository;
    private final IdempotencyChecker idempotencyChecker;
    private final LeaseManagementService leaseManagementService;
    private final ExecutionSessionManager sessionManager;
    private final ExecutionContextLoader contextLoader;

    @Value("${task.execution.lease.duration:60}")
    private int leaseDurationSeconds;

    /**
     * 准备执行（包含幂等检查、租约抢占、会话创建、上下文加载）。
     * <p>
     * 优化点：
     * <ul>
     *   <li>统一查询 Task，避免 createSession 和 loadContext 重复查询。</li>
     *   <li>添加异常资源清理逻辑，确保心跳停止、租约释放。</li>
     * </ul>
     * </p>
     *
     * @param command 任务就绪命令
     * @return 准备结果（包含session和context）
     */
    @Override
    public PrepareResult prepare(TaskReadyCommand command) {
        long taskId = command.taskId();
        String idempotentKey = command.idempotentKey();

        log.info("[INGEST][APP] prepare task execution start taskId={} idemKey={}",
                taskId, idempotentKey);

        // 1. 幂等检查
        if (idempotencyChecker.isAlreadySucceeded(taskId, idempotentKey)) {
            throw new TaskAlreadySucceededException(
                    "任务已成功执行 taskId=" + taskId + " idemKey=" + idempotentKey
            );
        }

        // 2. 生成租约持有者标识
        String leaseOwner = generateLeaseOwner();

        // 3. 租约抢占
        Duration leaseDuration = Duration.ofSeconds(leaseDurationSeconds);
        boolean acquired = leaseManagementService.tryAcquireLease(taskId, leaseOwner, leaseDuration);
        if (!acquired) {
            throw new LeaseAcquisitionFailedException(
                    "租约抢占失败 taskId=" + taskId + " owner=" + leaseOwner
            );
        }

        log.info("[INGEST][APP] lease acquired taskId={} owner={}", taskId, leaseOwner);

        // 4. 查询 Task（统一查询，避免后续重复）
        TaskAggregate task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在 taskId=" + taskId));

        ExecutionSession session = null;
        try {
            // 5. 会话初始化（创建 TaskRun、启动心跳）
            String schedulerRunId = command.getSchedulerRunId();
            String correlationId = command.getCorrelationId();
            session = sessionManager.createSession(
                    task,  // Use pre-queried task
                    leaseOwner,
                    schedulerRunId,
                    correlationId
            );

            log.info("[INGEST][APP] session created taskId={} runId={} owner={}",
                    taskId, session.runId(), leaseOwner);

            // 6. 上下文加载（配置还原、表达式编译）
            ExecutionContext context = contextLoader.loadContext(task, session.runId());

            log.info("[INGEST][APP] prepare task execution completed taskId={} runId={}",
                    taskId, session.runId());

            return new PrepareResult(session, context);

        } catch (Exception e) {
            // Resource cleanup on failure
            if (session != null) {
                log.warn("[INGEST][APP] prepare failed, cleaning up resources taskId={} runId={}",
                        taskId, session.runId(), e);
                try {
                    // Stop heartbeat
                    session.heartbeatHandle().stop();
                    // Release lease
                    leaseManagementService.releaseLease(taskId);
                } catch (Exception cleanupEx) {
                    log.error("[INGEST][APP] resource cleanup failed taskId={} runId={}",
                            taskId, session.runId(), cleanupEx);
                }
            }
            throw e;
        }
    }

    /**
     * 生成租约持有者标识。
     * <p>
     * 格式：hostname:pid:execId
     * </p>
     * <p>
     * 使用机器标识 (hostname) + 进程标识 (PID) + 执行标识 (UUID) 确保唯一性和可追溯性。
     * </p>
     */
    private String generateLeaseOwner() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            String execId = UUID.randomUUID().toString().substring(0, 8);
            return String.format("%s:%s:%s", hostname, pid, execId);
        } catch (UnknownHostException e) {
            // Fallback: use "unknown" as hostname if unable to resolve
            log.warn("[INGEST][APP] unable to resolve hostname, using fallback", e);
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            String execId = UUID.randomUUID().toString().substring(0, 8);
            return String.format("unknown:%s:%s", pid, execId);
        }
    }
}
