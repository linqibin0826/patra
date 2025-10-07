package com.patra.ingest.app.usecase.execution.prepare;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.app.usecase.execution.support.*;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    private final IdempotencyChecker idempotencyChecker;
    private final LeaseManagementService leaseManagementService;
    private final ExecutionSessionManager sessionManager;
    private final ExecutionContextLoader contextLoader;

    @Value("${task.execution.lease.duration:60}")
    private int leaseDurationSeconds;

    /**
     * 准备执行（包含幂等检查、租约抢占、会话创建、上下文加载）。
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

        // 2. 生成租约持有者标识（workerId:execId 或简化为 execId）
        String leaseOwner = generateLeaseOwner(command);

        // 3. 租约抢占
        Duration leaseDuration = Duration.ofSeconds(leaseDurationSeconds);
        boolean acquired = leaseManagementService.tryAcquireLease(taskId, leaseOwner, leaseDuration);
        if (!acquired) {
            throw new LeaseAcquisitionFailedException(
                "租约抢占失败 taskId=" + taskId + " owner=" + leaseOwner
            );
        }

        log.info("[INGEST][APP] lease acquired taskId={} owner={}", taskId, leaseOwner);

        // 4. 会话初始化（创建 TaskRun、启动心跳）
        String schedulerRunId = command.getSchedulerRunId();
        String correlationId = command.getCorrelationId();
        ExecutionSession session = sessionManager.createSession(
            taskId,
            leaseOwner,
            schedulerRunId,
            correlationId
        );

        log.info("[INGEST][APP] session created taskId={} runId={} owner={}",
                 taskId, session.runId(), leaseOwner);

        // 5. 上下文加载（配置还原、表达式编译）
        ExecutionContext context = contextLoader.loadContext(taskId, session.runId());

        log.info("[INGEST][APP] prepare task execution completed taskId={} runId={}",
                 taskId, session.runId());

        return new PrepareResult(session, context);
    }

    /**
     * 生成租约持有者标识。
     * <p>
     * 格式：workerId:execId 或 execId（execId 使用 UUID）
     * </p>
     */
    private String generateLeaseOwner(TaskReadyCommand command) {
        String execId = UUID.randomUUID().toString().substring(0, 8);
        // 可选：从 headers 中提取 workerId
        String workerId = command.headers() != null
            ? (String) command.headers().get("workerId")
            : null;

        if (workerId != null && !workerId.isBlank()) {
            return workerId + ":" + execId;
        }
        return execId;
    }
}
