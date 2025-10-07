package com.patra.ingest.app.usecase.execution.support;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.domain.port.TaskRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 执行会话管理器实现。
 * <p>
 * 职责：创建 TaskRun 运行记录、启动心跳续租、封装会话清理逻辑。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>获取任务的最新 attemptNo，生成新的 TaskRun（attemptNo + 1）。</li>
 *   <li>保存 TaskRun 到仓储，获取 runId。</li>
 *   <li>调用 HeartbeatRenewalService 启动心跳续租。</li>
 *   <li>返回 ExecutionSession，包含 taskId/runId/leaseOwner/heartbeatHandle。</li>
 * </ul>
 * </p>
 * <p>
 * 配置项：
 * <ul>
 *   <li>lease.duration：租约时长（秒），默认 60 秒。</li>
 *   <li>lease.renewal-interval：续租间隔（秒），默认 20 秒。</li>
 * </ul>
 * </p>
 * <p>
 * 日志策略：INFO 记录会话创建关键信息（taskId/runId/attemptNo）。
 * </p>
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

    /**
     * 创建执行会话（含TaskRun创建、心跳启动）。
     * TODO 有两个参数并没有被使用到，评估一下，如果没有使用，就移除
     * @param taskId 任务ID
     * @param leaseOwner 租约持有者
     * @param schedulerRunId 调度运行ID
     * @param correlationId 关联ID
     * @return 执行会话
     */
    @Override
    public ExecutionSession createSession(Long taskId,
                                          String leaseOwner,
                                          String schedulerRunId,
                                          String correlationId) {
        // 1. 读取任务聚合，获取来源/操作编码
        TaskAggregate task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在 taskId=" + taskId));

        // 2. 获取最新 attemptNo，生成新的 attemptNo
        int latestAttemptNo = taskRunRepository.getLatestAttemptNo(taskId);
        int newAttemptNo = latestAttemptNo + 1;

        // 3. 创建 TaskRun 实体
        TaskRun taskRun = new TaskRun(
            null,  // id 为空，insert 时由数据库生成
            taskId,
            newAttemptNo,
            task.getProvenanceCode(),
            task.getOperationCode()
        );

        // 4. 保存 TaskRun，获取生成的 runId
        TaskRun savedRun = taskRunRepository.save(taskRun);
        Long runId = savedRun.getId();

        log.info("[INGEST][APP] execution session created taskId={} runId={} attemptNo={} owner={}",
                 taskId, runId, newAttemptNo, leaseOwner);

        // 5. 启动心跳续租
        Duration leaseDuration = Duration.ofSeconds(leaseDurationSeconds);
        Duration renewalInterval = Duration.ofSeconds(renewalIntervalSeconds);
        ExecutionSession.HeartbeatHandle heartbeatHandle = heartbeatRenewalService.startHeartbeat(
            taskId,
            leaseOwner,
            leaseDuration,
            renewalInterval
        );

        // 6. 返回执行会话
        return new ExecutionSession(
            taskId,
            runId,
            leaseOwner,
            heartbeatHandle,
            false  // 初始状态：租约未撤销
        );
    }
}
