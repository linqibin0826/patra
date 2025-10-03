package com.patra.ingest.app.usecase.execution;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.vo.ExecutionWindow;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.domain.port.TaskRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 任务执行编排器（Task Execution Orchestrator）。
 * <p>
 * 职责：协调任务从 MQ 消费到执行会话初始化的全流程：
 * <ol>
 *   <li>步骤 0：CAS 抢占租约（≤1s）</li>
 *   <li>步骤 1：初始化执行会话（置 RUNNING + 创建 TaskRun + 安排心跳）</li>
 * </ol>
 * </p>
 * <p>
 * 幂等保障：
 * <ul>
 *   <li>SUCCEEDED 且 idempotentKey 匹配 → 直接跳过</li>
 *   <li>租约抢占失败（已被他人持有）→ 优雅退出，不抛异常</li>
 *   <li>数据库/解析异常 → 抛出异常，由 MQ binder 本地重试</li>
 * </ul>
 * </p>
 * <p>
 * 设计约束：
 * <ul>
 *   <li>应用层不引入框架依赖（除 Spring 注解），仅依赖领域接口与 patra-common</li>
 *   <li>复杂 SQL（CAS/续租）由 infra 层的 Mapper XML 实现</li>
 *   <li>事务边界：步骤 1 在同一事务内完成（markRunning + insert TaskRun）</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskExecutionOrchestrator implements TaskExecutionUseCase {

    private final TaskRepository taskRepository;
    private final TaskRunRepository taskRunRepository;
    private final ScheduledExecutorService heartbeatScheduler;

    /**
     * TODO 选择一个合适的租约时间 租约 TTL（秒），默认 60 秒
     */
    @Value("${papertrace.ingest.exec.lease-ttl-seconds:60}")
    private int leaseTtlSeconds;

    /**
     * 心跳间隔（秒），默认为 TTL 的 1/3
     */
    @Value("${papertrace.ingest.exec.heartbeat-interval-seconds:20}")
    private int heartbeatIntervalSeconds;

    /**
     * 工作节点 ID（appName@hostname#pid），用于标识租约持有者
     */
    @Value("${spring.application.name:patra-ingest}")
    private String appName;

    private volatile String workerId;

    /**
     * 从消息队列消费 INGEST_TASK_READY 消息后启动任务执行。
     *
     * @param command 任务就绪命令
     */
    @Override
    public void startFromReady(TaskReadyCommand command) {
        long taskId = command.taskId();
        String idempotentKey = command.idempotentKey();

        log.info("[INGEST][APP] task execution start taskId={} idemKey={} msgId={}",
                taskId, idempotentKey, command.getMessageId());

        // 幂等闸门：查询任务状态，若已 SUCCEEDED 且 idempotentKey 匹配则跳过
        Optional<TaskAggregate> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            log.warn("[INGEST][APP] task not found taskId={} idemKey={}", taskId, idempotentKey);
            return; // 任务不存在，可能已被删除或 ID 错误，优雅退出
        }

        TaskAggregate task = taskOpt.get();
        if (task.getStatus() == TaskStatus.SUCCEEDED && idempotentKey.equals(task.getIdempotentKey())) {
            log.info("[INGEST][APP] task already succeeded, skip taskId={} idemKey={}", taskId, idempotentKey);
            return; // 幂等跳过
        }

        // 步骤 0：CAS 抢占租约
        String owner = getWorkerId() + ":" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        boolean acquired = taskRepository.tryAcquireLease(taskId, owner, now, leaseTtlSeconds, idempotentKey);

        if (!acquired) {
            log.info("[INGEST][APP] task lease miss (held by others) taskId={} owner={}", taskId, owner);
            return; // 租约抢占失败，优雅退出（他人持有或条件不满足）
        }

        // 步骤 1：初始化执行会话（事务内完成）
        try {
            initializeExecutionSession(task, command, owner, now);
        } catch (Exception e) {
            log.error("[INGEST][APP] failed to initialize execution session taskId={} owner={}", taskId, owner, e);
            throw new RuntimeException("执行会话初始化失败", e); // 抛出异常触发 MQ 重试
        }

        // 安排心跳续租
        scheduleHeartbeat(taskId, owner);

        log.info("[INGEST][APP] task execution session initialized taskId={} owner={}", taskId, owner);
    }

    /**
     * 初始化执行会话（步骤 1）。
     * <p>
     * 在同一事务内完成：
     * <ol>
     *   <li>置任务为 RUNNING 状态并更新租约</li>
     *   <li>创建 TaskRun 记录（新 attempt）</li>
     * </ol>
     * </p>
     *
     * @param task 任务聚合
     * @param command 任务就绪命令
     * @param owner 租约持有者
     * @param now 当前时间
     */
    @Transactional(rollbackFor = Exception.class)
    protected void initializeExecutionSession(TaskAggregate task, TaskReadyCommand command, String owner, Instant now) {
        long taskId = task.getId();

        // 置任务为 RUNNING 状态并更新租约
        boolean marked = taskRepository.markRunningWithLease(taskId, owner, now, leaseTtlSeconds);
        if (!marked) {
            throw new IllegalStateException("租约已丢失，无法置为 RUNNING，taskId=" + taskId);
        }

        // 获取下一个 attemptNo
        int latestAttemptNo = taskRunRepository.getLatestAttemptNo(taskId);
        int nextAttemptNo = latestAttemptNo + 1;

        // 创建 TaskRun 记录
        TaskRun run = new TaskRun(null, taskId, nextAttemptNo, task.getProvenanceCode(), task.getOperationCode());
        run.start(now);
        run.heartbeat(now);
        run.bindRunContext(command.getSchedulerRunId(), command.getCorrelationId());

        // 设置执行窗口（从命令中获取）
        if (command.planWindowFrom() != null || command.planWindowTo() != null) {
            run.assignWindow(new ExecutionWindow(command.planWindowFrom(), command.planWindowTo()));
        }

        // 持久化 TaskRun
        taskRunRepository.save(run);

        log.info("[INGEST][APP] task run created taskId={} attemptNo={} status=RUNNING", taskId, nextAttemptNo);
    }

    /**
     * 安排心跳续租。
     * <p>
     * 定时任务以 {@code heartbeatIntervalSeconds} 为周期，调用 renewLease 续租。
     * 若续租失败（租约丢失），则终止心跳。
     * </p>
     *
     * @param taskId 任务 ID
     * @param owner 租约持有者
     */
    protected void scheduleHeartbeat(long taskId, String owner) {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                Instant now = Instant.now();
                boolean renewed = taskRepository.renewLease(taskId, owner, now, leaseTtlSeconds);
                if (!renewed) {
                    log.warn("[INGEST][APP] task lease lost on heartbeat, stop renewing taskId={} owner={}", taskId, owner);
                    // TODO: 发布租约丢失事件，触发任务恢复逻辑
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("[INGEST][APP] task heartbeat renewed taskId={} owner={}", taskId, owner);
                    }
                }
            } catch (Exception e) {
                log.error("[INGEST][APP] heartbeat renewal failed taskId={} owner={}", taskId, owner, e);
            }
        }, heartbeatIntervalSeconds, heartbeatIntervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * 获取工作节点 ID（appName@hostname#pid），懒加载。
     *
     * @return 工作节点 ID
     */
    private String getWorkerId() {
        if (workerId == null) {
            synchronized (this) {
                if (workerId == null) {
                    String hostname;
                    try {
                        hostname = InetAddress.getLocalHost().getHostName();
                    } catch (Exception e) {
                        hostname = "unknown";
                    }
                    String pid = String.valueOf(ProcessHandle.current().pid());
                    workerId = appName + "@" + hostname + "#" + pid;
                }
            }
        }
        return workerId;
    }
}
