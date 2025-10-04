package com.patra.ingest.app.usecase.execution;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonNormalizer;
import com.patra.common.util.HashUtils;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.ExecutionWindow;
import com.patra.ingest.domain.model.vo.SliceSpecDefinition;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.domain.port.TaskRunRepository;
import com.patra.starter.expr.compiler.ExprCompiler;
import com.patra.starter.expr.compiler.model.CompileResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static cn.hutool.core.text.CharSequenceUtil.isBlank;

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

    /**
     * 租约续期时间阈值分母：当步骤耗时超过 TTL / DIVISOR 时，触发一次续租。
     */
    private static final int LEASE_RENEW_THRESHOLD_DIVISOR = 3;

    /**
     * 默认 JSON 规范化器（用于配置快照哈希计算）。
     */
    private static final JsonNormalizer DEFAULT_NORMALIZER = JsonNormalizer.usingDefault();

    private final TaskRepository taskRepository;
    private final TaskRunRepository taskRunRepository;
    private final PlanRepository planRepository;
    private final PlanSliceRepository planSliceRepository;
    private final ScheduledExecutorService heartbeatScheduler;
    private final ObjectMapper objectMapper;

    private final ExprCompiler exprCompiler;

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

        // 从任务聚合中解析配置（planId/sliceId → plan/slice/window）
        TaskConfigResolution configResolution = resolveTaskConfig(task);

        // 步骤 1：初始化执行会话（事务内完成）
        TaskRun run;
        try {
            run = initializeExecutionSession(task, command, configResolution.window(), owner, now);
        } catch (Exception e) {
            log.error("[INGEST][APP] failed to initialize execution session taskId={} owner={}", taskId, owner, e);
            throw new RuntimeException("执行会话初始化失败", e); // 抛出异常触发 MQ 重试
        }

        // 安排心跳续租
        scheduleHeartbeat(taskId, owner);

        // 步骤 2：还原配置快照
        ConfigurationSnapshot configSnapshot;
        try {
            configSnapshot = restoreConfigurationSnapshot(task, run, configResolution, owner);
        } catch (Exception e) {
            handleStep2Failure(task, run, owner, "STEP2_UNEXPECTED: " + StrUtil.sub(e.getMessage(), 0, 512));
            throw new RuntimeException("配置快照还原失败", e);
        }

        if (!configSnapshot.valid()) {
            return;
        }

        // 步骤3：渲染expr并执行（交由下游组件处理）
        Expr expr = Exprs.fromJson(configSnapshot.exprSnapshotJson);
        CompileResult compileResult = exprCompiler.compile(expr, ProvenanceCode.parse(task.getProvenanceCode()));




        log.info("[INGEST][APP] task execution steps 0-2 done taskId={} owner={} runId={}", taskId, owner, run.getId());
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
     * @param task    任务聚合
     * @param command 任务就绪命令
     * @param window  执行窗口（从 resolveTaskConfig 解析）
     * @param owner   租约持有者
     * @param now     当前时间
     * @return 创建并持久化的 TaskRun 实体
     */
    @Transactional(rollbackFor = Exception.class)
    protected TaskRun initializeExecutionSession(TaskAggregate task,
                                                TaskReadyCommand command,
                                                ExecutionWindow window,
                                                String owner,
                                                Instant now) {
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

        // 设置执行窗口（从 resolveTaskConfig 解析）
        if (window != null && window.defined()) {
            run.assignWindow(window);
        }

        // 持久化 TaskRun
        TaskRun persisted = taskRunRepository.save(run);

        log.info("[INGEST][APP] task run created taskId={} runId={} attemptNo={} status=RUNNING",
                taskId, persisted.getId(), nextAttemptNo);
        return persisted;
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
     * 还原配置快照(步骤 2)。
     * <p>
     * 职责：
     * <ul>
     *   <li>从 plan 中读取 provenance 配置快照并解析校验</li>
     *   <li>从 slice 中获取表达式快照与规格定义</li>
     *   <li>计算并验证配置哈希一致性</li>
     *   <li>刷新心跳维持租约</li>
     *   <li>返回封装后的配置快照供后续执行阶段使用</li>
     * </ul>
     * </p>
     *
     * @param task              任务聚合
     * @param run               运行记录
     * @param configResolution  任务配置解析结果
     * @param owner             租约持有者
     * @return 配置快照结果（包含 provenance 配置、表达式快照、执行窗口等）
     */
    protected ConfigurationSnapshot restoreConfigurationSnapshot(TaskAggregate task,
                                                                 TaskRun run,
                                                                 TaskConfigResolution configResolution,
                                                                 String owner) {
        long taskId = task.getId();
        log.info("[INGEST][APP] task execution step2 start taskId={} planId={} sliceId={} owner={}",
                taskId, configResolution.planId(), configResolution.sliceId(), owner);

        if (!configResolution.complete()) {
            failStep2(task, run, owner, configResolution.missingReason());
            return ConfigurationSnapshot.invalid(configResolution.missingReason());
        }

        PlanAggregate plan = configResolution.plan();
        PlanSliceAggregate slice = configResolution.slice();

        String snapshotJson = plan.getProvenanceConfigSnapshotJson();
        if (isBlank(snapshotJson)) {
            String reason = "CONFIG_INVALID: plan.provenance_config_snapshot_json 为空";
            failStep2(task, run, owner, reason);
            return ConfigurationSnapshot.invalid(reason);
        }

        ProvenanceConfigSnapshot provenanceSnapshot;
        try {
            provenanceSnapshot = objectMapper.readValue(snapshotJson, ProvenanceConfigSnapshot.class);
        } catch (Exception ex) {
            String reason = "SNAPSHOT_PARSE_ERROR: " + StrUtil.sub(ex.getMessage(), 0, 512);
            failStep2(task, run, owner, reason);
            return ConfigurationSnapshot.invalid(reason);
        }

        try {
            validateSnapshot(provenanceSnapshot);
        } catch (IllegalStateException ex) {
            String reason = "CONFIG_INVALID: " + StrUtil.sub(ex.getMessage(), 0, 512);
            failStep2(task, run, owner, reason);
            return ConfigurationSnapshot.invalid(reason);
        }

        Instant stepStart = Instant.now();
        JsonNormalizer.Result normalized = DEFAULT_NORMALIZER.normalize(provenanceSnapshot);
        String configHashFromPlan = plan.getProvenanceConfigHash();
        String configHashRuntime = HashUtils.sha256Hex(normalized.getHashMaterial());
        String snapshotHash = HashUtils.sha256Hex(normalized.getCanonicalJson() + "|" + slice.getExprHash());

        Instant heartbeatAt = Instant.now();
        if (!taskRunRepository.touchHeartbeat(run.getId(), heartbeatAt)) {
            String reason = "HEARTBEAT_UPDATE_MISS: runId=" + run.getId();
            failStep2(task, run, owner, reason);
            return ConfigurationSnapshot.invalid(reason);
        }

        long costMs = Duration.between(stepStart, Instant.now()).toMillis();
        boolean hashMatched = configHashFromPlan != null && configHashFromPlan.equals(configHashRuntime);

        if (leaseTtlSeconds > 0) {
            long thresholdMs = (long) leaseTtlSeconds * 1000L / LEASE_RENEW_THRESHOLD_DIVISOR;
            if (costMs > thresholdMs) {
                boolean renewed = taskRepository.renewLease(taskId, owner, Instant.now(), leaseTtlSeconds);
                if (!renewed) {
                    log.warn("[INGEST][APP] task execution step2 lease renew failed taskId={} owner={} costMs={}", taskId, owner, costMs);
                    String reason = "LEASE_RENEW_FAILED: 租约续期失败";
                    failStep2(task, run, owner, reason);
                    return ConfigurationSnapshot.invalid(reason);
                }
            }
        }

        log.info("[INGEST][APP] task execution step2 done taskId={} planId={} sliceId={} owner={} costMs={} hashMatched={}",
                taskId, configResolution.planId(), configResolution.sliceId(), owner, costMs, hashMatched);
        if (log.isDebugEnabled()) {
            log.debug("[INGEST][APP] task execution step2 hash detail taskId={} planHash={} runtimeHash={} snapshotHash={} sliceExprHash={}",
                    taskId, configHashFromPlan, configHashRuntime, snapshotHash, slice.getExprHash());
        }

        // 返回完整的配置快照供后续执行阶段使用
        return ConfigurationSnapshot.valid(
                provenanceSnapshot,
                slice.getExprSnapshotJson(),
                slice.getExprHash(),
                configResolution.window(),
                configHashFromPlan,
                configHashRuntime,
                snapshotHash,
                hashMatched
        );
    }

    private void failStep2(TaskAggregate task, TaskRun run, String owner, String reason) {
        handleStep2Failure(task, run, owner, reason);
    }

    private void handleStep2Failure(TaskAggregate task, TaskRun run, String owner, String reason) {
        Long runId = run == null ? null : run.getId();
        Instant now = Instant.now();
        String trimmed = StrUtil.sub(reason, 0, 2000);
        if (runId != null) {
            taskRunRepository.markFailed(runId, trimmed, now);
        }
        log.warn("[INGEST][APP] task execution step2 failed taskId={} runId={} owner={} reason={}",
                task.getId(), runId, owner, trimmed);
    }

    private void validateSnapshot(ProvenanceConfigSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalStateException("配置快照缺失");
        }
        ProvenanceConfigSnapshot.EndpointDefinition endpoint = snapshot.endpoint();
        if (endpoint == null) {
            throw new IllegalStateException("endpoint 定义缺失");
        }
        if (isBlank(endpoint.endpointName())) {
            throw new IllegalStateException("endpointName 不能为空");
        }
        if (isBlank(endpoint.httpMethodCode())) {
            throw new IllegalStateException("httpMethod 不能为空");
        }
        if (isBlank(endpoint.pathTemplate())) {
            throw new IllegalStateException("pathTemplate 不能为空");
        }

        String baseUrlDefault = snapshot.provenance() == null ? null : snapshot.provenance().baseUrlDefault();
        String baseUrlOverride = snapshot.http() == null ? null : snapshot.http().baseUrlOverride();
        if (isBlank(baseUrlDefault) && isBlank(baseUrlOverride)) {
            throw new IllegalStateException("HTTP 基础地址缺失（baseUrlDefault/baseUrlOverride 均为空）");
        }

        ProvenanceConfigSnapshot.PaginationConfig pagination = snapshot.pagination();
        if (pagination != null && isBlank(pagination.paginationModeCode())) {
            throw new IllegalStateException("paginationMode 缺失");
        }

        ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset = snapshot.windowOffset();
        if (windowOffset != null) {
            if (isBlank(windowOffset.offsetTypeCode())) {
                throw new IllegalStateException("windowOffset.offsetType 缺失");
            }
            // fixedOffsetUnit 检查已移除，因为 WindowOffsetConfig 可能没有这个字段
        }
    }

    private TaskConfigResolution resolveTaskConfig(TaskAggregate task) {
        Long planId = task.getPlanId();
        Long sliceId = task.getSliceId();

        PlanAggregate plan = null;
        PlanSliceAggregate slice = null;
        String failureReason = null;

        if (planId == null) {
            failureReason = "CONFIG_INVALID: task.planId 缺失";
        } else {
            plan = planRepository.findById(planId).orElse(null);
            if (plan == null) {
                failureReason = "CONFIG_INVALID: plan 不存在 planId=" + planId;
            }
        }

        if (failureReason == null) {
            if (sliceId == null) {
                failureReason = "CONFIG_INVALID: task.sliceId 缺失";
            } else {
                slice = planSliceRepository.findById(sliceId).orElse(null);
                if (slice == null) {
                    failureReason = "CONFIG_INVALID: slice 不存在 sliceId=" + sliceId;
                }
            }
        }

        // 从 slice_spec JSON 中解析执行窗口（仅当 strategy=TIME 时有效）
        ExecutionWindow window = ExecutionWindow.empty();
        if (slice != null && failureReason == null) {
            String sliceSpecJson = slice.getSliceSpecJson();
            if (sliceSpecJson != null && !sliceSpecJson.isBlank()) {
                try {
                    SliceSpecDefinition sliceSpec = objectMapper.readValue(sliceSpecJson, SliceSpecDefinition.class);
                    if (sliceSpec.isTimeStrategy()) {
                        window = sliceSpec.toExecutionWindow();
                        if (log.isDebugEnabled()) {
                            log.debug("[INGEST][APP] resolved execution window from slice_spec taskId={} sliceId={} strategy={} window={}",
                                    task.getId(), sliceId, sliceSpec.strategy(), window);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("[INGEST][APP] failed to parse slice_spec JSON taskId={} sliceId={} error={}",
                            task.getId(), sliceId, StrUtil.sub(ex.getMessage(), 0, 256));
                    // 解析失败不阻断流程，使用空窗口
                }
            }
        }

        return new TaskConfigResolution(planId, sliceId, plan, slice, window, failureReason);
    }

    /**
     * 任务配置解析结果。
     * <p>
     * 封装任务执行前所需配置的解析结果，包括：
     * <ul>
     *   <li>planId/sliceId：关联的计划和切片 ID</li>
     *   <li>plan/slice：加载的聚合对象</li>
     *   <li>window：从 slice_spec 解析的执行窗口</li>
     *   <li>missingReason：配置缺失或解析失败的原因</li>
     * </ul>
     * </p>
     */
    private record TaskConfigResolution(Long planId,
                                        Long sliceId,
                                        PlanAggregate plan,
                                        PlanSliceAggregate slice,
                                        ExecutionWindow window,
                                        String missingReason) {

        /**
         * 判断配置是否完整（无缺失）。
         *
         * @return true 表示配置完整，可继续执行
         */
        boolean complete() {
            return missingReason == null;
        }

        @Override
        public ExecutionWindow window() {
            return window == null ? ExecutionWindow.empty() : window;
        }
    }

    /**
     * 配置快照还原结果。
     * <p>
     * 封装 Step 2（还原配置快照）的执行结果，包括：
     * <ul>
     *   <li>provenanceSnapshot：解析后的 provenance 配置快照</li>
     *   <li>exprSnapshotJson/exprHash：表达式快照和哈希</li>
     *   <li>window：执行窗口（从 slice_spec 解析）</li>
     *   <li>configHashFromPlan/configHashRuntime/snapshotHash：配置哈希验证</li>
     *   <li>hashMatched：运行时哈希与计划哈希是否匹配</li>
     *   <li>valid：还原是否成功</li>
     *   <li>failureReason：失败原因（valid=false 时非空）</li>
     * </ul>
     * </p>
     *
     * @since 0.1.0
     */
    private record ConfigurationSnapshot(ProvenanceConfigSnapshot provenanceSnapshot,
                                         String exprSnapshotJson,
                                         String exprHash,
                                         ExecutionWindow window,
                                         String configHashFromPlan,
                                         String configHashRuntime,
                                         String snapshotHash,
                                         boolean hashMatched,
                                         boolean valid,
                                         String failureReason) {

        /**
         * 创建一个有效的配置快照结果。
         *
         * @return ConfigurationSnapshot 实例，valid=true
         */
        static ConfigurationSnapshot valid(ProvenanceConfigSnapshot provenanceSnapshot,
                                           String exprSnapshotJson,
                                           String exprHash,
                                           ExecutionWindow window,
                                           String configHashFromPlan,
                                           String configHashRuntime,
                                           String snapshotHash,
                                           boolean hashMatched) {
            return new ConfigurationSnapshot(
                    provenanceSnapshot,
                    exprSnapshotJson,
                    exprHash,
                    window,
                    configHashFromPlan,
                    configHashRuntime,
                    snapshotHash,
                    hashMatched,
                    true,
                    null
            );
        }

        /**
         * 创建一个失败的配置快照结果。
         *
         * @param reason 失败原因
         * @return ConfigurationSnapshot 实例，valid=false
         */
        static ConfigurationSnapshot invalid(String reason) {
            return new ConfigurationSnapshot(
                    null,
                    null,
                    null,
                    ExecutionWindow.empty(),
                    null,
                    null,
                    null,
                    false,
                    false,
                    reason
            );
        }
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
