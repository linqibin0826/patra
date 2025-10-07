package com.patra.ingest.app.usecase.execution.support;

/**
 * 执行会话管理器。
 * <p>创建TaskRun、启动心跳续租、封装清理（停止心跳+释放租约）。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ExecutionSessionManager {

    /**
     * 创建执行会话（含TaskRun创建、心跳启动）。
     *
     * @param taskId 任务ID
     * @param leaseOwner 租约持有者
     * @param schedulerRunId 调度运行ID
     * @param correlationId 关联ID
     * @return 执行会话
     */
    ExecutionSession createSession(Long taskId,
                                    String leaseOwner,
                                    String schedulerRunId,
                                    String correlationId);
}
